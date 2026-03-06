package com.youhua.debt.statemachine;

import com.youhua.ai.enums.OcrTaskStatus;
import com.youhua.ai.ocr.entity.OcrTask;
import com.youhua.ai.ocr.mapper.OcrTaskMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.event.ProfileRecalculationEvent;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.engine.dto.request.CalculateAprRequest;
import com.youhua.engine.dto.response.CalculateAprResponse;
import com.youhua.engine.service.EngineService;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * 债务录入状态机 Action 执行逻辑。
 * 所有写操作均记录操作日志。
 * APR 计算必须调用引擎服务（禁止调用大模型，F-02）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebtEntryAction {

    private final DebtMapper debtMapper;
    private final OcrTaskMapper ocrTaskMapper;
    private final EngineService engineService;
    private final ApplicationEventPublisher eventPublisher;
    private final OperationLogService operationLogService;

    /**
     * SUBMIT 事件 Action：validateBasicFields()。
     * 基础字段由 Guard 已校验，此处记录操作日志。
     */
    public void validateBasicFields(Debt debt) {
        log.debug("[DebtEntryAction] validateBasicFields debtId={}", debt.getId());
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                "{\"event\":\"SUBMIT\",\"desc\":\"基础字段校验通过，债务提交\"}");
    }

    /**
     * START_OCR 事件 Action：dispatchOcrTask()。
     * 创建 OCR 任务并将 ocrTaskId 关联到债务。
     */
    public OcrTask dispatchOcrTask(Debt debt, String fileUrl) {
        log.debug("[DebtEntryAction] dispatchOcrTask debtId={} fileUrl={}", debt.getId(), fileUrl);

        OcrTask ocrTask = new OcrTask();
        ocrTask.setUserId(debt.getUserId());
        ocrTask.setFileUrl(fileUrl);
        ocrTask.setStatus(OcrTaskStatus.PENDING);
        ocrTask.setRetryCount(0);
        ocrTask.setDebtId(debt.getId());
        ocrTaskMapper.insert(ocrTask);

        debt.setOcrTaskId(ocrTask.getId());
        debtMapper.updateById(debt);

        saveOperationLog(debt.getUserId(), OperationAction.CREATE, debt.getId(),
                String.format("{\"event\":\"START_OCR\",\"ocrTaskId\":%d}", ocrTask.getId()));
        return ocrTask;
    }

    /**
     * MANUAL_CONFIRM 事件 Action：calculateApr()。
     * APR 计算必须通过引擎服务（F-02），不得调用大模型。
     */
    public void calculateApr(Debt debt) {
        log.debug("[DebtEntryAction] calculateApr debtId={}", debt.getId());

        CalculateAprRequest request = new CalculateAprRequest();
        request.setPrincipal(debt.getPrincipal());
        request.setTotalRepayment(debt.getTotalRepayment());
        request.setLoanDays(debt.getLoanDays());

        CalculateAprResponse response = engineService.calculateApr(request);
        debt.setApr(response.getApr());
        debtMapper.updateById(debt);

        log.debug("[DebtEntryAction] calculateApr debtId={} apr={}", debt.getId(), response.getApr());
        saveOperationLog(debt.getUserId(), OperationAction.CALCULATE, debt.getId(),
                String.format("{\"event\":\"CALCULATE_APR\",\"apr\":\"%s\"}", response.getApr()));
    }

    /**
     * OCR_SUCCESS 事件 Action：fillExtractedFields()。
     * 将 OCR 提取的字段填入债务记录。
     */
    public void fillExtractedFields(Debt debt, OcrTask ocrTask) {
        log.debug("[DebtEntryAction] fillExtractedFields debtId={} ocrTaskId={}",
                debt.getId(), ocrTask.getId());

        debt.setConfidenceScore(ocrTask.getConfidenceScore());
        debtMapper.updateById(debt);

        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                String.format("{\"event\":\"OCR_SUCCESS\",\"ocrTaskId\":%d,\"confidence\":\"%s\"}",
                        ocrTask.getId(), ocrTask.getConfidenceScore()));
    }

    /**
     * OCR_FAIL 事件 Action：logOcrError()。
     */
    public void logOcrError(Debt debt, String errorMessage) {
        log.warn("[DebtEntryAction] logOcrError debtId={} error={}", debt.getId(), errorMessage);
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                String.format("{\"event\":\"OCR_FAIL\",\"error\":\"%s\"}", errorMessage));
    }

    /**
     * USER_CONFIRM 事件 Action：calculateApr() + mergeUserCorrections()。
     */
    public void mergeUserCorrectionsAndCalculateApr(Debt debt, OcrTask ocrTask) {
        log.debug("[DebtEntryAction] mergeUserCorrections debtId={}", debt.getId());
        // 先计算 APR（引擎服务，F-02）
        calculateApr(debt);
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                String.format("{\"event\":\"USER_CONFIRM\",\"ocrTaskId\":%d}", ocrTask.getId()));
    }

    /**
     * USER_REJECT 事件 Action：clearOcrFields()。
     */
    public void clearOcrFields(Debt debt) {
        log.debug("[DebtEntryAction] clearOcrFields debtId={}", debt.getId());
        debt.setOcrTaskId(null);
        debt.setConfidenceScore(null);
        debtMapper.updateById(debt);
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                "{\"event\":\"USER_REJECT\",\"desc\":\"清除OCR字段，债务回到草稿\"}");
    }

    /**
     * INCLUDE_IN_PROFILE 事件 Action：triggerProfileRecalculation()。
     */
    public void triggerProfileRecalculation(Debt debt, String reason) {
        log.debug("[DebtEntryAction] triggerProfileRecalculation debtId={} reason={}", debt.getId(), reason);
        eventPublisher.publishEvent(new ProfileRecalculationEvent(this, debt.getUserId(), debt.getId(), reason));
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                String.format("{\"event\":\"PROFILE_RECALCULATE\",\"reason\":\"%s\"}", reason));
    }

    /**
     * USER_EDIT（from CONFIRMED）事件 Action：clearCalculatedFields()。
     */
    public void clearCalculatedFields(Debt debt) {
        log.debug("[DebtEntryAction] clearCalculatedFields debtId={}", debt.getId());
        debt.setApr(null);
        debtMapper.updateById(debt);
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                "{\"event\":\"USER_EDIT_FROM_CONFIRMED\",\"desc\":\"清除计算字段，债务回到草稿\"}");
    }

    /**
     * RETRY_OCR 事件 Action：incrementRetryCount() + dispatchOcrTask()。
     */
    public void incrementRetryCountAndDispatchOcr(Debt debt, OcrTask ocrTask) {
        log.debug("[DebtEntryAction] incrementRetryCount ocrTaskId={}", ocrTask.getId());
        int newRetryCount = (ocrTask.getRetryCount() == null ? 0 : ocrTask.getRetryCount()) + 1;
        ocrTask.setRetryCount(newRetryCount);
        ocrTask.setStatus(OcrTaskStatus.PENDING);
        ocrTaskMapper.updateById(ocrTask);
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                String.format("{\"event\":\"RETRY_OCR\",\"retryCount\":%d}", newRetryCount));
    }

    /**
     * SWITCH_TO_MANUAL 事件 Action：setSourceType(MANUAL)。
     */
    public void setSourceTypeManual(Debt debt) {
        log.debug("[DebtEntryAction] setSourceTypeManual debtId={}", debt.getId());
        debt.setSourceType(DebtSourceType.MANUAL);
        debt.setOcrTaskId(null);
        debt.setConfidenceScore(null);
        debtMapper.updateById(debt);
        saveOperationLog(debt.getUserId(), OperationAction.UPDATE, debt.getId(),
                "{\"event\":\"SWITCH_TO_MANUAL\",\"desc\":\"切换为手动录入\"}");
    }

    private void saveOperationLog(Long userId, OperationAction action, Long targetId, String detailJson) {
        operationLogService.record(userId, OperationModule.DEBT, action, "Debt", targetId, detailJson);
    }
}
