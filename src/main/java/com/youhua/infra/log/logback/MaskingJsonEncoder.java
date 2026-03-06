package com.youhua.infra.log.logback;

import ch.qos.logback.classic.spi.ILoggingEvent;
import net.logstash.logback.encoder.LogstashEncoder;

/**
 * JSON 格式日志脱敏 Encoder，包装 LogstashEncoder，
 * 对序列化后的 JSON 字符串做敏感数据替换（F-04 合规）。
 */
public class MaskingJsonEncoder extends LogstashEncoder {

    @Override
    public byte[] encode(ILoggingEvent event) {
        byte[] raw = super.encode(event);
        String json = new String(raw);
        String masked = MaskingPatternLayout.maskSensitive(json);
        return masked.getBytes();
    }
}
