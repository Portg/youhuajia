package com.youhua.engine.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Schema(description = "策略对比请求")
public class CompareStrategiesRequest {

    @NotNull
    @Schema(description = "用户 ID")
    private Long userId;

    @NotNull
    @Schema(description = "策略 A 分群")
    private String segmentA;

    @NotNull
    @Schema(description = "策略 B 分群")
    private String segmentB;
}
