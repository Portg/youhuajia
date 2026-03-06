package com.youhua.debt.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.dto.request.CreateDebtRequest;
import com.youhua.debt.dto.request.ListDebtsRequest;
import com.youhua.debt.dto.request.UpdateDebtRequest;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.dto.response.ListDebtsResponse;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.debt.service.DebtService;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import com.youhua.common.util.RequestContextUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DebtServiceImpl implements DebtService {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final String IDEMPOTENCY_KEY_PREFIX = "debt:requestId:";
    private static final long IDEMPOTENCY_TTL_HOURS = 24;

    private final DebtMapper debtMapper;
    private final AprCalculator aprCalculator;
    private final OperationLogService operationLogService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public ListDebtsResponse listDebts(ListDebtsRequest request) {
        Long userId = RequestContextUtil.getCurrentUserId();
        int pageSize = request.getPageSize() != null ? request.getPageSize() : DEFAULT_PAGE_SIZE;

        // Decode pageToken to lastId (lastId=0 means start from beginning)
        long lastId = 0L;
        if (request.getPageToken() != null && !request.getPageToken().isBlank()) {
            lastId = decodePageToken(request.getPageToken());
        }

        LambdaQueryWrapper<Debt> query = Wrappers.<Debt>lambdaQuery()
                .eq(Debt::getUserId, userId)
                .gt(lastId > 0, Debt::getId, lastId)
                .orderByDesc(Debt::getId)
                .last("LIMIT " + (pageSize + 1));

        // Apply simple filter on status if provided
        if (request.getFilter() != null && !request.getFilter().isBlank()) {
            String filter = request.getFilter().trim();
            if (filter.startsWith("status=")) {
                String statusValue = filter.substring("status=".length()).replace("\"", "").trim();
                try {
                    DebtStatus filterStatus = DebtStatus.valueOf(statusValue);
                    query.eq(Debt::getStatus, filterStatus);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid status filter value: {}", statusValue);
                }
            }
        }

        List<Debt> debts = debtMapper.selectList(query);

        boolean hasMore = debts.size() > pageSize;
        if (hasMore) {
            debts = debts.subList(0, pageSize);
        }

        String nextPageToken = null;
        if (hasMore && !debts.isEmpty()) {
            nextPageToken = encodePageToken(debts.get(debts.size() - 1).getId());
        }

        List<DebtResponse> debtResponses = debts.stream()
                .map(this::toDebtResponse)
                .collect(Collectors.toList());

        // TODO 优化：summary 改为聚合 SQL (SELECT COUNT, SUM)，避免第二次全量查询
        LambdaQueryWrapper<Debt> allQuery = Wrappers.<Debt>lambdaQuery()
                .eq(Debt::getUserId, userId);
        List<Debt> allDebts = debtMapper.selectList(allQuery);

        BigDecimal totalPrincipal = allDebts.stream()
                .map(d -> d.getPrincipal() != null ? d.getPrincipal() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalMonthlyPayment = allDebts.stream()
                .map(d -> d.getMonthlyPayment() != null ? d.getMonthlyPayment() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        long confirmedCount = allDebts.stream()
                .filter(d -> DebtStatus.CONFIRMED == d.getStatus() || DebtStatus.IN_PROFILE == d.getStatus())
                .count();

        return ListDebtsResponse.builder()
                .debts(debtResponses)
                .nextPageToken(nextPageToken)
                .totalSize(allDebts.size())
                .summary(ListDebtsResponse.Summary.builder()
                        .totalCount(allDebts.size())
                        .totalPrincipal(totalPrincipal)
                        .totalMonthlyPayment(totalMonthlyPayment)
                        .confirmedCount((int) confirmedCount)
                        .build())
                .build();
    }

    @Override
    public DebtResponse createDebt(CreateDebtRequest request) {
        Long userId = RequestContextUtil.getCurrentUserId();

        // Idempotency check
        if (request.getRequestId() != null && !request.getRequestId().isBlank()) {
            String key = IDEMPOTENCY_KEY_PREFIX + request.getRequestId();
            Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", IDEMPOTENCY_TTL_HOURS, TimeUnit.HOURS);
            if (Boolean.FALSE.equals(isNew)) {
                log.warn("Duplicate request detected: requestId={}", request.getRequestId());
                // Return existing debt for the same requestId if we can find it
                // For now, throw a validation error to prevent duplicate creation
                throw new BizException(ErrorCode.DUPLICATE_REQUEST);
            }
        }

        CreateDebtRequest.DebtInput input = request.getDebt();

        Debt debt = new Debt();
        debt.setUserId(userId);
        debt.setCreditor(input.getCreditor());
        debt.setDebtType(input.getDebtType());
        debt.setPrincipal(input.getPrincipal());
        debt.setTotalRepayment(input.getTotalRepayment());
        debt.setNominalRate(input.getNominalRate());
        debt.setLoanDays(input.getLoanDays());
        debt.setMonthlyPayment(input.getMonthlyPayment());
        debt.setRemainingPrincipal(input.getRemainingPrincipal());
        debt.setRemainingPeriods(input.getRemainingPeriods());
        debt.setStartDate(input.getStartDate());
        debt.setEndDate(input.getEndDate());
        debt.setOverdueStatus(input.getOverdueStatus());
        debt.setOverdueDays(input.getOverdueDays());
        debt.setRemark(input.getRemark());
        debt.setStatus(DebtStatus.DRAFT);
        debt.setSourceType(DebtSourceType.MANUAL);

        debtMapper.insert(debt);

        saveOperationLog(userId, OperationAction.CREATE, debt.getId(), "创建债务: " + input.getCreditor());

        log.debug("createDebt: userId={}, debtId={}, creditor={}", userId, debt.getId(), input.getCreditor());

        return toDebtResponse(debt);
    }

    @Override
    public DebtResponse getDebt(Long debtId) {
        Long userId = RequestContextUtil.getCurrentUserId();
        Debt debt = findDebtAndValidateOwner(debtId, userId);
        return toDebtResponse(debt);
    }

    @Override
    public DebtResponse updateDebt(Long debtId, UpdateDebtRequest request) {
        Long userId = RequestContextUtil.getCurrentUserId();
        Debt debt = findDebtAndValidateOwner(debtId, userId);

        // IN_PROFILE gets a specific error code; other non-editable statuses get generic state invalid
        if (debt.getStatus() == DebtStatus.IN_PROFILE) {
            throw new BizException(ErrorCode.DEBT_IN_PROFILE);
        }
        if (debt.getStatus() != DebtStatus.DRAFT && debt.getStatus() != DebtStatus.CONFIRMED) {
            throw new BizException(ErrorCode.DEBT_STATE_INVALID, "只有草稿或已确认状态的债务可以修改");
        }

        DebtResponse debtInput = request.getDebt();
        String updateMask = request.getUpdateMask();
        applyUpdateMask(debt, debtInput, updateMask);

        // Optimistic lock update
        int rows = debtMapper.updateById(debt);
        if (rows == 0) {
            throw new BizException(ErrorCode.DEBT_VERSION_CONFLICT);
        }

        saveOperationLog(userId, OperationAction.UPDATE, debtId, "更新债务字段: " + updateMask);

        log.debug("updateDebt: userId={}, debtId={}, updateMask={}", userId, debtId, updateMask);

        Debt updated = debtMapper.selectById(debtId);
        return toDebtResponse(updated);
    }

    @Override
    public void deleteDebt(Long debtId) {
        Long userId = RequestContextUtil.getCurrentUserId();
        Debt debt = findDebtAndValidateOwner(debtId, userId);

        if (debt.getStatus() != DebtStatus.DRAFT) {
            throw new BizException(ErrorCode.DEBT_STATE_INVALID, "只有草稿状态的债务可以删除");
        }

        // Logical delete via MyBatis-Plus
        debtMapper.deleteById(debtId);

        saveOperationLog(userId, OperationAction.DELETE, debtId, "删除债务");

        log.debug("deleteDebt: userId={}, debtId={}", userId, debtId);
    }

    @Override
    public DebtResponse confirmDebt(Long debtId) {
        Long userId = RequestContextUtil.getCurrentUserId();
        Debt debt = findDebtAndValidateOwner(debtId, userId);

        if (debt.getStatus() != DebtStatus.DRAFT && debt.getStatus() != DebtStatus.PENDING_CONFIRM) {
            throw new BizException(ErrorCode.DEBT_STATE_INVALID, "只有草稿或待确认状态的债务可以确认");
        }

        // Validate required fields for confirmation
        if (debt.getPrincipal() == null || debt.getPrincipal().compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.DEBT_CONFIRM_MISSING_FIELDS, "本金必须大于零");
        }
        if (debt.getLoanDays() == null || debt.getLoanDays() <= 0) {
            throw new BizException(ErrorCode.DEBT_CONFIRM_MISSING_FIELDS, "借款天数必须大于零");
        }
        if (debt.getTotalRepayment() == null) {
            throw new BizException(ErrorCode.DEBT_CONFIRM_MISSING_FIELDS, "总还款额不能为空");
        }

        // Calculate APR and backfill
        BigDecimal apr = aprCalculator.calculateApr(debt.getPrincipal(), debt.getTotalRepayment(), debt.getLoanDays());
        debt.setApr(apr);
        debt.setStatus(DebtStatus.CONFIRMED);

        int rows = debtMapper.updateById(debt);
        if (rows == 0) {
            throw new BizException(ErrorCode.DEBT_VERSION_CONFLICT);
        }

        saveOperationLog(userId, OperationAction.UPDATE, debtId, "确认债务，APR=" + apr);

        log.debug("confirmDebt: userId={}, debtId={}, calculatedApr={}", userId, debtId, apr);

        Debt updated = debtMapper.selectById(debtId);
        return toDebtResponse(updated);
    }

    @Override
    public DebtResponse includeDebtInProfile(Long debtId) {
        Long userId = RequestContextUtil.getCurrentUserId();
        Debt debt = findDebtAndValidateOwner(debtId, userId);

        if (debt.getStatus() != DebtStatus.CONFIRMED) {
            throw new BizException(ErrorCode.DEBT_STATE_INVALID, "只有已确认状态的债务可以纳入画像");
        }

        debt.setStatus(DebtStatus.IN_PROFILE);

        int rows = debtMapper.updateById(debt);
        if (rows == 0) {
            throw new BizException(ErrorCode.DEBT_VERSION_CONFLICT);
        }

        saveOperationLog(userId, OperationAction.UPDATE, debtId, "纳入债务画像");

        log.debug("includeDebtInProfile: userId={}, debtId={}", userId, debtId);

        Debt updated = debtMapper.selectById(debtId);
        return toDebtResponse(updated);
    }

    private Debt findDebtAndValidateOwner(Long debtId, Long userId) {
        Debt debt = debtMapper.selectById(debtId);
        if (debt == null) {
            throw new BizException(ErrorCode.DEBT_NOT_FOUND);
        }
        if (!userId.equals(debt.getUserId())) {
            throw new BizException(ErrorCode.DEBT_NOT_FOUND);
        }
        return debt;
    }

    private void applyUpdateMask(Debt debt, DebtResponse input, String updateMask) {
        String[] fields = updateMask.split(",");
        for (String field : fields) {
            switch (field.trim()) {
                case "creditor" -> debt.setCreditor(input.getCreditor());
                case "debtType" -> debt.setDebtType(input.getDebtType());
                case "principal" -> debt.setPrincipal(input.getPrincipal());
                case "totalRepayment" -> debt.setTotalRepayment(input.getTotalRepayment());
                case "nominalRate" -> debt.setNominalRate(input.getNominalRate());
                case "loanDays" -> debt.setLoanDays(input.getLoanDays());
                case "monthlyPayment" -> debt.setMonthlyPayment(input.getMonthlyPayment());
                case "remainingPrincipal" -> debt.setRemainingPrincipal(input.getRemainingPrincipal());
                case "remainingPeriods" -> debt.setRemainingPeriods(input.getRemainingPeriods());
                case "startDate" -> debt.setStartDate(input.getStartDate());
                case "endDate" -> debt.setEndDate(input.getEndDate());
                case "overdueStatus" -> debt.setOverdueStatus(input.getOverdueStatus());
                case "overdueDays" -> debt.setOverdueDays(input.getOverdueDays());
                case "remark" -> debt.setRemark(input.getRemark());
                default -> log.warn("Unknown updateMask field: {}", field.trim());
            }
        }
    }

    private void saveOperationLog(Long userId, OperationAction action, Long targetId, String detail) {
        operationLogService.record(userId, OperationModule.DEBT, action, "Debt", targetId,
                "{\"detail\":\"" + detail.replace("\"", "'") + "\"}");
    }

    private DebtResponse toDebtResponse(Debt debt) {
        return DebtResponse.fromEntity(debt);
    }

    private String encodePageToken(Long lastId) {
        return Base64.getEncoder().encodeToString(lastId.toString().getBytes());
    }

    private long decodePageToken(String pageToken) {
        try {
            String decoded = new String(Base64.getDecoder().decode(pageToken));
            return Long.parseLong(decoded);
        } catch (Exception e) {
            log.warn("Invalid pageToken: {}", pageToken);
            return 0L;
        }
    }
}
