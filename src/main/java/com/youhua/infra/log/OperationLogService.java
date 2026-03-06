package com.youhua.infra.log;

import com.youhua.infra.log.entity.OperationLog;
import com.youhua.infra.log.enums.OperationAction;
import com.youhua.infra.log.enums.OperationModule;
import com.youhua.infra.log.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Centralized operation log recording service.
 * Replaces duplicated saveOperationLog() methods across action/service classes.
 */
@Service
@RequiredArgsConstructor
public class OperationLogService {

    private final OperationLogMapper operationLogMapper;

    public void record(Long userId, OperationModule module, OperationAction action,
                       String targetType, Long targetId, String detailJson) {
        OperationLog opLog = new OperationLog();
        opLog.setUserId(userId);
        opLog.setModule(module);
        opLog.setAction(action);
        opLog.setTargetType(targetType);
        opLog.setTargetId(targetId);
        opLog.setDetailJson(detailJson);
        opLog.setCreateTime(LocalDateTime.now());
        operationLogMapper.insert(opLog);
    }
}
