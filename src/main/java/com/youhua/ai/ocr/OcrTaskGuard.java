package com.youhua.ai.ocr;

import com.youhua.ai.ocr.entity.OcrTask;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * OCR 任务状态机 Guard 条件校验。
 */
@Slf4j
@Component
public class OcrTaskGuard {

    /**
     * EXTRACT_SUCCESS 事件 Guard：extractedFields NOT EMPTY。
     */
    public boolean checkExtractSuccess(OcrTask task) {
        boolean valid = StringUtils.hasText(task.getExtractedFieldsJson());

        log.debug("[OcrTaskGuard] checkExtractSuccess taskId={} result={}", task.getId(), valid);

        if (!valid) {
            throw new BizException(ErrorCode.OCR_FAILED, "OCR 提取字段为空，无法标记为成功");
        }
        return true;
    }

    /**
     * RETRY 事件 Guard：retryCount < 3。
     */
    public boolean checkRetry(OcrTask task) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        boolean valid = retryCount < 3;

        log.debug("[OcrTaskGuard] checkRetry taskId={} retryCount={} result={}",
                task.getId(), retryCount, valid);

        if (!valid) {
            throw new BizException(ErrorCode.OCR_RETRY_EXCEEDED);
        }
        return true;
    }
}
