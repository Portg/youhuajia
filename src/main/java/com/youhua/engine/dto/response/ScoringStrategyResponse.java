package com.youhua.engine.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@Schema(description = "评分策略信息")
public class ScoringStrategyResponse {

    @Schema(description = "分群标识")
    private String segment;

    @Schema(description = "策略名称")
    private String strategyName;

    @Schema(description = "策略版本")
    private String version;

    @Schema(description = "策略描述")
    private String description;

    @Schema(description = "创建者")
    private String createdBy;

    @Schema(description = "创建时间")
    private String createdAt;

    @Schema(description = "风险等级边界")
    private List<BigDecimal> riskLevelBoundaries;

    @Schema(description = "重组推荐阈值")
    private BigDecimal restructureThreshold;
}
