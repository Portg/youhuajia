package com.youhua.profile.engine;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.DebtStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.profile.entity.FinanceProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 画像生成状态机 Guard 条件校验。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProfileGenerationGuard {

    private final DebtMapper debtMapper;

    /**
     * TRIGGER_CALCULATE 事件 Guard：user has at least 1 CONFIRMED or IN_PROFILE debt。
     */
    public boolean checkTriggerCalculate(Long userId) {
        long count = debtMapper.selectCount(
                new LambdaQueryWrapper<Debt>()
                        .eq(Debt::getUserId, userId)
                        .in(Debt::getStatus, DebtStatus.CONFIRMED, DebtStatus.IN_PROFILE)
                        .eq(Debt::getDeleted, 0)
        );

        boolean valid = count > 0;

        log.debug("[ProfileGenerationGuard] checkTriggerCalculate userId={} confirmedOrInProfileDebtCount={} result={}",
                userId, count, valid);

        if (!valid) {
            throw new BizException(ErrorCode.PROFILE_NO_CONFIRMED_DEBT);
        }
        return true;
    }

    /**
     * RETRY 事件 Guard：retryCount < 3。
     */
    public boolean checkRetry(FinanceProfile profile) {
        int retryCount = profile.getGenerationRetryCount() == null ? 0 : profile.getGenerationRetryCount();
        boolean valid = retryCount < 3;

        log.debug("[ProfileGenerationGuard] checkRetry userId={} retryCount={} result={}",
                profile.getUserId(), retryCount, valid);

        if (!valid) {
            throw new BizException(ErrorCode.PROFILE_DATA_ABNORMAL,
                    "画像生成重试次数已达上限（最多 3 次）");
        }
        return true;
    }
}
