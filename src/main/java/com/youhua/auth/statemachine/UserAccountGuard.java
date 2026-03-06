package com.youhua.auth.statemachine;

import com.youhua.auth.entity.User;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 用户账户状态机 Guard 条件校验。
 */
@Slf4j
@Component
public class UserAccountGuard {

    /**
     * FREEZE / UNFREEZE 事件 Guard：operatorRole == ADMIN。
     */
    public boolean checkAdminRole(Long userId, String operatorRole) {
        boolean valid = "ADMIN".equalsIgnoreCase(operatorRole);

        log.debug("[UserAccountGuard] checkAdminRole userId={} operatorRole={} result={}",
                userId, operatorRole, valid);

        if (!valid) {
            throw new BizException(ErrorCode.ACCOUNT_FROZEN,
                    "冻结/解冻账户操作需要管理员权限");
        }
        return true;
    }

    /**
     * REACTIVATE 事件 Guard：within 30 days of cancellation request。
     * 通过 User.cancellationTime 判断。
     */
    public boolean checkReactivate(User user) {
        if (user.getCancellationTime() == null) {
            log.debug("[UserAccountGuard] checkReactivate userId={} cancellationTime=null result=false",
                    user.getId());
            throw new BizException(ErrorCode.DEBT_STATE_INVALID, "注销时间记录缺失，无法恢复账户");
        }

        LocalDateTime deadline = user.getCancellationTime().plusDays(30);
        boolean valid = LocalDateTime.now().isBefore(deadline);

        log.debug("[UserAccountGuard] checkReactivate userId={} cancellationTime={} deadline={} result={}",
                user.getId(), user.getCancellationTime(), deadline, valid);

        if (!valid) {
            throw new BizException(ErrorCode.ACCOUNT_CANCELLED,
                    "注销冷静期已过（30天），账户无法恢复");
        }
        return true;
    }
}
