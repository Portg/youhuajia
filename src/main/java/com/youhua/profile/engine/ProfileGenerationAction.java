package com.youhua.profile.engine;

import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.mapper.FinanceProfileMapper;
import com.youhua.profile.service.FinanceProfileService;
import com.youhua.report.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 画像生成状态机 Action 执行逻辑。
 * APR/评分/加权利率等金融核心计算必须确定性可复现（F-02），不得调用大模型。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileGenerationAction {

    private final FinanceProfileMapper financeProfileMapper;
    private final OperationLogService operationLogService;
    private final DebtMapper debtMapper;

    @Lazy
    private final FinanceProfileService financeProfileService;

    @Lazy
    private final ReportService reportService;

    /**
     * TRIGGER_CALCULATE 事件 Action：collectDebtData()。
     * 收集用户债务数据，准备进入校验阶段。
     */
    public void collectDebtData(FinanceProfile profile) {
        log.debug("[ProfileGenerationAction] collectDebtData userId={}", profile.getUserId());
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                "{\"event\":\"TRIGGER_CALCULATE\",\"desc\":\"开始收集债务数据\"}");
        // Collect IN_PROFILE debts and log count for visibility
        List<Debt> debts = debtMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Debt>()
                        .eq(Debt::getUserId, profile.getUserId())
                        .eq(Debt::getStatus, DebtStatus.IN_PROFILE)
                        .eq(Debt::getDeleted, 0)
        );
        log.debug("[ProfileGenerationAction] collectDebtData: userId={} debtCount={}", profile.getUserId(), debts.size());
    }

    /**
     * VALIDATION_PASS 事件 Action：startCalculation()。
     * 启动 APR + 加权利率 + 评分计算（调用引擎服务，F-02）。
     */
    public void startCalculation(FinanceProfile profile) {
        log.debug("[ProfileGenerationAction] startCalculation userId={}", profile.getUserId());
        saveOperationLog(profile.getUserId(), OperationAction.CALCULATE, profile.getId(),
                "{\"event\":\"VALIDATION_PASS\",\"desc\":\"数据校验通过，开始计算\"}");
        // Delegate to FinanceProfileService which orchestrates all engine calls (F-02 compliant)
        financeProfileService.calculateFinanceProfile();
    }

    /**
     * VALIDATION_FAIL 事件 Action：logValidationErrors()。
     */
    public void logValidationErrors(FinanceProfile profile, String errors) {
        log.warn("[ProfileGenerationAction] logValidationErrors userId={} errors={}", profile.getUserId(), errors);
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                String.format("{\"event\":\"VALIDATION_FAIL\",\"errors\":\"%s\"}", errors));
    }

    /**
     * CALCULATION_COMPLETE 事件 Action：saveProfileSnapshot()。
     * 持久化计算结果快照。
     */
    public void saveProfileSnapshot(FinanceProfile profile) {
        log.debug("[ProfileGenerationAction] saveProfileSnapshot userId={}", profile.getUserId());
        profile.setLastCalculatedTime(LocalDateTime.now());
        financeProfileMapper.updateById(profile);
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                "{\"event\":\"CALCULATION_COMPLETE\",\"desc\":\"画像计算完成，保存快照\"}");
    }

    /**
     * CALCULATION_ERROR 事件 Action：logCalculationError()。
     */
    public void logCalculationError(FinanceProfile profile, String error) {
        log.error("[ProfileGenerationAction] logCalculationError userId={} error={}", profile.getUserId(), error);
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                String.format("{\"event\":\"CALCULATION_ERROR\",\"error\":\"%s\"}", error));
    }

    /**
     * SUGGESTION_READY 事件 Action：saveReport()。
     */
    public void saveReport(FinanceProfile profile) {
        log.debug("[ProfileGenerationAction] saveReport userId={}", profile.getUserId());
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                "{\"event\":\"SUGGESTION_READY\",\"desc\":\"AI建议已生成，保存报告\"}");
        // Delegate to report.ReportService which handles full orchestration (profile + debts + AI + persist)
        Long reportId = reportService.generateReport(profile.getUserId());
        log.debug("[ProfileGenerationAction] saveReport: userId={} reportId={}", profile.getUserId(), reportId);
    }

    /**
     * SUGGESTION_FAIL 事件 Action：logAiError() + saveReportWithoutSuggestion()。
     */
    public void logAiErrorAndSaveReportWithoutSuggestion(FinanceProfile profile, String error) {
        log.warn("[ProfileGenerationAction] logAiError userId={} error={}", profile.getUserId(), error);
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                String.format("{\"event\":\"SUGGESTION_FAIL\",\"error\":\"%s\",\"desc\":\"AI建议失败，保存无AI建议报告\"}",
                        error));
        // Delegate to report.ReportService — it handles AI failure gracefully (degraded report, suggestion=null)
        try {
            Long reportId = reportService.generateReport(profile.getUserId());
            log.debug("[ProfileGenerationAction] saveReportWithoutSuggestion: userId={} reportId={}",
                    profile.getUserId(), reportId);
        } catch (Exception ex) {
            log.error("[ProfileGenerationAction] Failed to save degraded report for userId={}", profile.getUserId(), ex);
        }
    }

    /**
     * RETRY 事件 Action：incrementRetryCount()。
     */
    public void incrementRetryCount(FinanceProfile profile) {
        int newRetryCount = (profile.getGenerationRetryCount() == null ? 0 : profile.getGenerationRetryCount()) + 1;
        profile.setGenerationRetryCount(newRetryCount);
        financeProfileMapper.updateById(profile);

        log.debug("[ProfileGenerationAction] incrementRetryCount userId={} retryCount={}",
                profile.getUserId(), newRetryCount);
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                String.format("{\"event\":\"RETRY\",\"retryCount\":%d}", newRetryCount));
    }

    /**
     * 超时 Action（GENERATING_SUGGESTION → COMPLETED_WITHOUT_AI）：logAiTimeout()。
     */
    public void logAiTimeout(FinanceProfile profile) {
        log.warn("[ProfileGenerationAction] logAiTimeout userId={} profileId={}", profile.getUserId(), profile.getId());
        saveOperationLog(profile.getUserId(), OperationAction.GENERATE, profile.getId(),
                "{\"event\":\"AI_TIMEOUT\",\"desc\":\"AI建议生成超时（30s），降级为无AI建议\"}");
    }

    /**
     * 更新画像生成状态（状态机驱动，统一入口）。
     */
    public void updateGenerationStatus(FinanceProfile profile, ProfileGenerationStatus newStatus) {
        log.debug("[ProfileGenerationAction] updateGenerationStatus userId={} {} -> {}",
                profile.getUserId(), profile.getGenerationStatus(), newStatus);
        profile.setGenerationStatus(newStatus);
        financeProfileMapper.updateById(profile);
    }

    private void saveOperationLog(Long userId, OperationAction action, Long targetId, String detailJson) {
        operationLogService.record(userId, OperationModule.PROFILE, action, "FinanceProfile", targetId, detailJson);
    }
}
