package com.youhua.debt.statemachine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.mapper.DebtMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 债务录入状态机超时处理器。
 *
 * <p>超时规则（来自 state-machines.yaml）：
 * <ul>
 *   <li>OCR_PROCESSING 状态超过 60s → 转为 OCR_FAILED，执行 logTimeout()</li>
 * </ul>
 *
 * <p>MVP 阶段使用 @Scheduled 轮询（间隔 10s），生产环境可升级为 Redis TTL 回调。
 * <p>需在启动类或配置类添加 @EnableScheduling。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DebtEntryTimeoutHandler {

    private static final int OCR_TIMEOUT_SECONDS = 60;

    private final DebtMapper debtMapper;
    private final DebtEntryAction debtEntryAction;

    /**
     * 每 10 秒轮询一次，检测 OCR_PROCESSING 超时债务。
     * 超时后通过状态机转为 OCR_FAILED（不直接 set 状态）。
     */
    @Scheduled(fixedDelay = 10_000)
    public void handleOcrProcessingTimeout() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(OCR_TIMEOUT_SECONDS);

        List<Debt> timedOutDebts = debtMapper.selectList(
                new LambdaQueryWrapper<Debt>()
                        .eq(Debt::getStatus, DebtStatus.OCR_PROCESSING)
                        .lt(Debt::getUpdateTime, timeoutThreshold)
                        .eq(Debt::getDeleted, 0)
        );

        if (timedOutDebts.isEmpty()) {
            return;
        }

        log.warn("[DebtEntryTimeoutHandler] 发现 {} 条 OCR_PROCESSING 超时债务（阈值 {}s）",
                timedOutDebts.size(), OCR_TIMEOUT_SECONDS);

        for (Debt debt : timedOutDebts) {
            try {
                log.warn("[DebtEntryTimeoutHandler] OCR超时，debtId={} updateTime={}", debt.getId(), debt.getUpdateTime());

                // 状态机驱动：OCR_PROCESSING → OCR_FAILED
                debt.setStatus(DebtStatus.OCR_FAILED);
                debtMapper.updateById(debt);

                // 执行超时 Action
                debtEntryAction.logOcrError(debt, "OCR 识别超时（60秒），自动失败");

            } catch (Exception e) {
                log.error("[DebtEntryTimeoutHandler] 处理超时失败 debtId={}", debt.getId(), e);
            }
        }
    }
}
