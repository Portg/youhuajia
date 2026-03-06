package com.youhua.ai.ocr;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.ai.enums.OcrTaskStatus;
import com.youhua.ai.ocr.entity.OcrTask;
import com.youhua.ai.ocr.mapper.OcrTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * OCR 任务状态机超时处理器。
 *
 * <p>超时规则（来自 state-machines.yaml）：
 * <ul>
 *   <li>PROCESSING 状态超过 60s → 转为 FAILED，执行 saveTimeoutError()</li>
 * </ul>
 *
 * <p>MVP 阶段使用 @Scheduled 轮询（间隔 10s），生产环境可升级为 Redis TTL 回调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OcrTaskTimeoutHandler {

    private static final int OCR_TIMEOUT_SECONDS = 60;

    private final OcrTaskMapper ocrTaskMapper;
    private final OcrTaskAction ocrTaskAction;

    /**
     * 每 10 秒轮询一次，检测 PROCESSING 超时的 OCR 任务。
     * 超时后通过状态机转为 FAILED（不直接 set 状态）。
     */
    @Scheduled(fixedDelay = 10_000)
    public void handleProcessingTimeout() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(OCR_TIMEOUT_SECONDS);

        List<OcrTask> timedOutTasks = ocrTaskMapper.selectList(
                new LambdaQueryWrapper<OcrTask>()
                        .eq(OcrTask::getStatus, OcrTaskStatus.PROCESSING)
                        .lt(OcrTask::getUpdateTime, timeoutThreshold)
                        .eq(OcrTask::getDeleted, 0)
        );

        if (timedOutTasks.isEmpty()) {
            return;
        }

        log.warn("[OcrTaskTimeoutHandler] 发现 {} 条 PROCESSING 超时 OCR 任务（阈值 {}s）",
                timedOutTasks.size(), OCR_TIMEOUT_SECONDS);

        for (OcrTask task : timedOutTasks) {
            try {
                log.warn("[OcrTaskTimeoutHandler] OCR识别超时 taskId={} updateTime={}",
                        task.getId(), task.getUpdateTime());

                // 状态机驱动：执行超时 Action（内部更新状态为 FAILED）
                ocrTaskAction.saveTimeoutError(task);

            } catch (Exception e) {
                log.error("[OcrTaskTimeoutHandler] 处理超时失败 taskId={}", task.getId(), e);
            }
        }
    }
}
