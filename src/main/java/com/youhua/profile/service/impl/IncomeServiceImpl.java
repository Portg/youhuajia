package com.youhua.profile.service.impl;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.common.util.RequestContextUtil;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import com.youhua.profile.dto.request.BatchCreateIncomesRequest;
import com.youhua.profile.dto.response.IncomeResponse;
import com.youhua.profile.entity.IncomeRecord;
import com.youhua.profile.enums.VerificationStatus;
import com.youhua.profile.mapper.IncomeRecordMapper;
import com.youhua.profile.service.IncomeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Income record service implementation.
 *
 * <p>F-01: All amounts use BigDecimal.
 * <p>F-05: No nested @Transactional.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IncomeServiceImpl implements IncomeService {

    private final IncomeRecordMapper incomeRecordMapper;
    private final OperationLogService operationLogService;

    @Override
    public List<IncomeResponse> batchCreateIncomes(BatchCreateIncomesRequest request) {
        Long userId = RequestContextUtil.getCurrentUserId();

        List<IncomeResponse> responses = new ArrayList<>();

        for (BatchCreateIncomesRequest.IncomeInput input : request.getIncomes()) {
            // Validate amount >= 0 (F-01: BigDecimal comparison)
            if (input.getAmount() == null || input.getAmount().compareTo(BigDecimal.ZERO) < 0) {
                throw new BizException(ErrorCode.INCOME_AMOUNT_INVALID);
            }

            IncomeRecord record = new IncomeRecord();
            record.setUserId(userId);
            record.setIncomeType(input.getIncomeType());
            record.setAmount(input.getAmount());
            record.setPrimary(Boolean.TRUE.equals(input.getPrimary()));
            record.setVerificationStatus(VerificationStatus.UNVERIFIED);

            incomeRecordMapper.insert(record);

            log.debug("[IncomeService] Created incomeRecord id={} userId={} type={} amount={}",
                    record.getId(), userId, record.getIncomeType(), record.getAmount());

            // Write operation log per record
            saveOperationLog(userId, record.getId(),
                    String.format("{\"action\":\"createIncome\",\"incomeType\":\"%s\",\"primary\":%b}",
                            input.getIncomeType(), record.getPrimary()));

            responses.add(toResponse(record));
        }

        log.debug("[IncomeService] batchCreateIncomes completed: userId={} count={}", userId, responses.size());
        return responses;
    }

    // ===================== Private helpers =====================

    private void saveOperationLog(Long userId, Long targetId, String detailJson) {
        operationLogService.record(userId, OperationModule.PROFILE, OperationAction.CREATE,
                "IncomeRecord", targetId, detailJson);
    }

    private IncomeResponse toResponse(IncomeRecord record) {
        return IncomeResponse.builder()
                .name("incomes/" + record.getId())
                .incomeType(record.getIncomeType())
                .amount(record.getAmount())
                .primary(record.getPrimary())
                .verificationStatus(record.getVerificationStatus())
                .build();
    }
}
