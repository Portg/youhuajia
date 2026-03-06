package com.youhua.auth.statemachine;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.youhua.auth.entity.User;
import com.youhua.auth.enums.UserStatus;
import com.youhua.auth.mapper.UserMapper;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.infra.log.OperationLogService;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.entity.OptimizationReport;
import com.youhua.profile.mapper.FinanceProfileMapper;
import com.youhua.profile.mapper.OptimizationReportMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 用户账户状态机 Action 执行逻辑。
 * 注意：用户敏感信息（手机号、身份证、银行卡）禁止出现在日志（F-04）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserAccountAction {

    private final UserMapper userMapper;
    private final OperationLogService operationLogService;
    private final DebtMapper debtMapper;
    private final FinanceProfileMapper financeProfileMapper;
    private final OptimizationReportMapper optimizationReportMapper;

    /**
     * FREEZE 事件 Action：logFreeze()。
     */
    public void logFreeze(User user, String operatorId) {
        log.info("[UserAccountAction] logFreeze userId={} operator={}", user.getId(), operatorId);
        saveOperationLog(user.getId(), OperationAction.UPDATE, user.getId(),
                String.format("{\"event\":\"FREEZE\",\"operator\":\"%s\"}", operatorId));
    }

    /**
     * UNFREEZE 事件 Action：logUnfreeze()。
     */
    public void logUnfreeze(User user, String operatorId) {
        log.info("[UserAccountAction] logUnfreeze userId={} operator={}", user.getId(), operatorId);
        saveOperationLog(user.getId(), OperationAction.UPDATE, user.getId(),
                String.format("{\"event\":\"UNFREEZE\",\"operator\":\"%s\"}", operatorId));
    }

    /**
     * CANCEL 事件 Action：startCancellationCountdown(30days)。
     * 记录注销申请时间，用于30天冷静期判断。
     */
    public void startCancellationCountdown(User user) {
        log.info("[UserAccountAction] startCancellationCountdown userId={}", user.getId());
        user.setCancellationTime(LocalDateTime.now());
        userMapper.updateById(user);
        saveOperationLog(user.getId(), OperationAction.UPDATE, user.getId(),
                "{\"event\":\"CANCEL\",\"desc\":\"注销冷静期开始（30天）\"}");
    }

    /**
     * REACTIVATE 事件 Action：cancelCancellation()。
     */
    public void cancelCancellation(User user) {
        log.info("[UserAccountAction] cancelCancellation userId={}", user.getId());
        user.setCancellationTime(null);
        userMapper.updateById(user);
        saveOperationLog(user.getId(), OperationAction.UPDATE, user.getId(),
                "{\"event\":\"REACTIVATE\",\"desc\":\"用户取消注销申请，账号恢复正常\"}");
    }

    /**
     * EXPIRY 事件 Action：deleteUserData()。
     * 30天冷静期结束，逻辑删除用户数据。
     * 注意：仅做逻辑删除，物理数据保留合规留存期（F-04 数据合规）。
     */
    public void deleteUserData(User user) {
        log.info("[UserAccountAction] deleteUserData userId={}", user.getId());
        // 逻辑删除通过 MyBatis-Plus @TableLogic 处理
        // 此处记录日志，实际物理清理由数据合规流程负责
        saveOperationLog(user.getId(), OperationAction.DELETE, user.getId(),
                "{\"event\":\"EXPIRY\",\"desc\":\"注销冷静期结束，用户数据逻辑删除\"}");
        // Logically delete user-related data (debt, finance profile, optimization reports)
        debtMapper.delete(new LambdaUpdateWrapper<Debt>().eq(Debt::getUserId, user.getId()));
        financeProfileMapper.delete(new LambdaUpdateWrapper<FinanceProfile>().eq(FinanceProfile::getUserId, user.getId()));
        optimizationReportMapper.delete(new LambdaUpdateWrapper<OptimizationReport>().eq(OptimizationReport::getUserId, user.getId()));
        log.info("[UserAccountAction] deleteUserData: associated data logically deleted for userId={}", user.getId());
    }

    /**
     * 更新用户账户状态（状态机驱动，统一入口）。
     */
    public void updateStatus(User user, UserStatus newStatus) {
        log.info("[UserAccountAction] updateStatus userId={} {} -> {}", user.getId(), user.getStatus(), newStatus);
        user.setStatus(newStatus);
        userMapper.updateById(user);
    }

    private void saveOperationLog(Long userId, OperationAction action, Long targetId, String detailJson) {
        operationLogService.record(userId, OperationModule.AUTH, action, "User", targetId, detailJson);
    }
}
