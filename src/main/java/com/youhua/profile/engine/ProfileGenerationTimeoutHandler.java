package com.youhua.profile.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.mapper.FinanceProfileMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 画像生成状态机超时处理器。
 *
 * <p>超时规则（来自 state-machines.yaml）：
 * <ul>
 *   <li>GENERATING_SUGGESTION 状态超过 30s → 转为 COMPLETED_WITHOUT_AI，执行 logAiTimeout()</li>
 * </ul>
 *
 * <p>MVP 阶段使用 @Scheduled 轮询（间隔 10s），生产环境可升级为 Redis TTL 回调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileGenerationTimeoutHandler {

    private static final int AI_SUGGESTION_TIMEOUT_SECONDS = 30;

    private final FinanceProfileMapper financeProfileMapper;
    private final ProfileGenerationAction profileGenerationAction;

    /**
     * 每 10 秒轮询一次，检测 GENERATING_SUGGESTION 超时的画像记录。
     * 超时后通过状态机转为 COMPLETED_WITHOUT_AI（不直接 set 状态）。
     */
    @Scheduled(fixedDelay = 10_000)
    public void handleAiSuggestionTimeout() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusSeconds(AI_SUGGESTION_TIMEOUT_SECONDS);

        List<FinanceProfile> timedOutProfiles = financeProfileMapper.selectList(
                new LambdaQueryWrapper<FinanceProfile>()
                        .eq(FinanceProfile::getGenerationStatus, ProfileGenerationStatus.GENERATING_SUGGESTION)
                        .lt(FinanceProfile::getUpdateTime, timeoutThreshold)
                        .eq(FinanceProfile::getDeleted, 0)
        );

        if (timedOutProfiles.isEmpty()) {
            return;
        }

        log.warn("[ProfileGenerationTimeoutHandler] 发现 {} 条 GENERATING_SUGGESTION 超时画像（阈值 {}s）",
                timedOutProfiles.size(), AI_SUGGESTION_TIMEOUT_SECONDS);

        for (FinanceProfile profile : timedOutProfiles) {
            try {
                log.warn("[ProfileGenerationTimeoutHandler] AI建议生成超时 profileId={} userId={}",
                        profile.getId(), profile.getUserId());

                // 超时 Action
                profileGenerationAction.logAiTimeout(profile);

                // 状态机驱动：GENERATING_SUGGESTION → COMPLETED_WITHOUT_AI
                profileGenerationAction.updateGenerationStatus(profile, ProfileGenerationStatus.COMPLETED_WITHOUT_AI);

            } catch (Exception e) {
                log.error("[ProfileGenerationTimeoutHandler] 处理超时失败 profileId={}", profile.getId(), e);
            }
        }
    }
}
