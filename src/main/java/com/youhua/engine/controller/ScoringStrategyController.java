package com.youhua.engine.controller;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.engine.dto.request.CompareStrategiesRequest;
import com.youhua.engine.dto.response.CompareStrategiesResponse;
import com.youhua.engine.dto.response.ScoringStrategyResponse;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry;
import com.youhua.engine.scoring.pmml.PmmlStrategyRegistry.StrategyEntry;
import com.youhua.engine.scoring.pmml.StrategyMetadata;
import com.youhua.engine.scoring.pmml.UserSegment;
import com.youhua.engine.service.EngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "scoring-strategy", description = "评分策略管理")
@RestController
@RequestMapping("/api/v1/scoring-strategies")
@RequiredArgsConstructor
public class ScoringStrategyController {

    private final PmmlStrategyRegistry strategyRegistry;
    private final EngineService engineService;

    @Operation(summary = "获取所有已加载的策略列表")
    @GetMapping
    public List<ScoringStrategyResponse> listStrategies() {
        Map<UserSegment, StrategyEntry> all = strategyRegistry.getAllStrategies();
        List<ScoringStrategyResponse> responses = new ArrayList<>();

        for (var entry : all.entrySet()) {
            StrategyMetadata meta = entry.getValue().metadata();
            responses.add(buildResponse(entry.getKey(), meta));
        }

        return responses;
    }

    @Operation(summary = "获取指定分群的策略详情")
    @GetMapping("/{segment}")
    public ScoringStrategyResponse getStrategy(@PathVariable String segment) {
        UserSegment userSegment;
        try {
            userSegment = UserSegment.valueOf(segment.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException(ErrorCode.STRATEGY_NOT_FOUND, "Unknown segment: " + segment);
        }

        StrategyEntry entry = strategyRegistry.getStrategy(userSegment);
        return buildResponse(userSegment, entry.metadata());
    }

    @Operation(summary = "强制刷新所有策略")
    @PostMapping(":reload")
    public List<ScoringStrategyResponse> reloadStrategies() {
        strategyRegistry.forceReload();
        return listStrategies();
    }

    @Operation(summary = "策略对比：同一用户在两个策略下的评分差异")
    @PostMapping(":compare")
    public CompareStrategiesResponse compareStrategies(
            @Valid @RequestBody CompareStrategiesRequest request) {
        return engineService.compareStrategies(request);
    }

    private ScoringStrategyResponse buildResponse(UserSegment segment, StrategyMetadata meta) {
        return ScoringStrategyResponse.builder()
                .segment(segment.name())
                .strategyName(meta != null ? meta.getStrategyName() : "unknown")
                .version(meta != null ? meta.getVersion() : "unknown")
                .description(meta != null ? meta.getDescription() : null)
                .createdBy(meta != null ? meta.getCreatedBy() : null)
                .createdAt(meta != null ? meta.getCreatedAt() : null)
                .riskLevelBoundaries(meta != null ? meta.getRiskLevelBoundaries() : null)
                .restructureThreshold(meta != null ? meta.getRestructureThreshold() : null)
                .build();
    }
}
