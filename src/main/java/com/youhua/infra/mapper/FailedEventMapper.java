package com.youhua.infra.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youhua.infra.event.FailedEvent;
import org.apache.ibatis.annotations.Mapper;

/**
 * 死信事件 Mapper。
 */
@Mapper
public interface FailedEventMapper extends BaseMapper<FailedEvent> {
}
