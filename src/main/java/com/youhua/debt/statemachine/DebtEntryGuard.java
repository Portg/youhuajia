package com.youhua.debt.statemachine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.ai.ocr.entity.OcrTask;
import com.youhua.ai.ocr.mapper.OcrTaskMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtSourceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;

/**
 * 债务录入状态机 Guard 条件校验。
 * 每个方法检查对应事件的前置条件，不满足时抛 BizException。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebtEntryGuard {

    private final OcrTaskMapper ocrTaskMapper;

    /**
     * SUBMIT 事件 Guard：principal > 0 AND loanDays > 0 AND creditor NOT EMPTY。
     */
    public boolean checkSubmit(Debt debt) {
        boolean valid = debt.getPrincipal() != null
                && debt.getPrincipal().compareTo(BigDecimal.ZERO) > 0
                && debt.getLoanDays() != null
                && debt.getLoanDays() > 0
                && StringUtils.hasText(debt.getCreditor());

        log.debug("[DebtEntryGuard] checkSubmit debtId={} result={}", debt.getId(), valid);

        if (!valid) {
            if (debt.getPrincipal() == null || debt.getPrincipal().compareTo(BigDecimal.ZERO) <= 0) {
                throw new BizException(ErrorCode.DEBT_PRINCIPAL_INVALID);
            }
            if (debt.getLoanDays() == null || debt.getLoanDays() <= 0) {
                throw new BizException(ErrorCode.DEBT_LOAN_DAYS_INVALID);
            }
            throw new BizException(ErrorCode.DEBT_CREDITOR_EMPTY);
        }
        return true;
    }

    /**
     * START_OCR 事件 Guard：sourceType == OCR AND fileUrl NOT EMPTY。
     * fileUrl 由调用方（OCR任务创建时）提供，此处从 OcrTask 中取。
     */
    public boolean checkStartOcr(Debt debt, String fileUrl) {
        boolean valid = DebtSourceType.OCR.equals(debt.getSourceType())
                && StringUtils.hasText(fileUrl);

        log.debug("[DebtEntryGuard] checkStartOcr debtId={} result={}", debt.getId(), valid);

        if (!valid) {
            throw new BizException(ErrorCode.DEBT_STATE_INVALID,
                    "发起 OCR 识别失败：来源类型必须为 OCR 且文件地址不能为空");
        }
        return true;
    }

    /**
     * MANUAL_CONFIRM 事件 Guard：sourceType == MANUAL AND allRequiredFieldsFilled()。
     */
    public boolean checkManualConfirm(Debt debt) {
        boolean valid = DebtSourceType.MANUAL.equals(debt.getSourceType())
                && StringUtils.hasText(debt.getCreditor())
                && debt.getPrincipal() != null
                && debt.getPrincipal().compareTo(BigDecimal.ZERO) > 0
                && debt.getTotalRepayment() != null
                && debt.getTotalRepayment().compareTo(debt.getPrincipal()) >= 0
                && debt.getLoanDays() != null
                && debt.getLoanDays() > 0;

        log.debug("[DebtEntryGuard] checkManualConfirm debtId={} result={}", debt.getId(), valid);

        if (!valid) {
            throw new BizException(ErrorCode.DEBT_CONFIRM_MISSING_FIELDS);
        }
        return true;
    }

    /**
     * OCR_SUCCESS 事件 Guard：confidenceScore >= 0。
     */
    public boolean checkOcrSuccess(Debt debt) {
        boolean valid = debt.getConfidenceScore() != null
                && debt.getConfidenceScore().compareTo(BigDecimal.ZERO) >= 0;

        log.debug("[DebtEntryGuard] checkOcrSuccess debtId={} confidenceScore={} result={}",
                debt.getId(), debt.getConfidenceScore(), valid);

        if (!valid) {
            throw new BizException(ErrorCode.DEBT_STATE_INVALID, "OCR 识别置信度异常");
        }
        return true;
    }

    /**
     * RETRY_OCR 事件 Guard：retryCount < 3（从关联 OcrTask 获取）。
     */
    public boolean checkRetryOcr(Debt debt) {
        if (debt.getOcrTaskId() == null) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }

        OcrTask ocrTask = ocrTaskMapper.selectOne(
                new LambdaQueryWrapper<OcrTask>()
                        .eq(OcrTask::getId, debt.getOcrTaskId())
                        .eq(OcrTask::getDeleted, 0)
        );

        if (ocrTask == null) {
            throw new BizException(ErrorCode.OCR_TASK_NOT_FOUND);
        }

        int retryCount = ocrTask.getRetryCount() == null ? 0 : ocrTask.getRetryCount();
        boolean valid = retryCount < 3;

        log.debug("[DebtEntryGuard] checkRetryOcr debtId={} ocrTaskId={} retryCount={} result={}",
                debt.getId(), debt.getOcrTaskId(), retryCount, valid);

        if (!valid) {
            throw new BizException(ErrorCode.OCR_RETRY_EXCEEDED);
        }
        return true;
    }
}
