package com.youhua.infra.log.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youhua.infra.log.entity.OperationLog;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OperationLogMapper extends BaseMapper<OperationLog> {
}
