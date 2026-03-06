package com.youhua.engine.controller;

import com.youhua.engine.dto.request.AssessPressureRequest;
import com.youhua.engine.dto.request.CalculateAprRequest;
import com.youhua.engine.dto.request.SimulateRateRequest;
import com.youhua.engine.dto.request.SimulateScoreRequest;
import com.youhua.engine.dto.response.AssessPressureResponse;
import com.youhua.engine.dto.response.CalculateAprResponse;
import com.youhua.engine.dto.response.SimulateRateResponse;
import com.youhua.engine.dto.response.SimulateScoreResponse;
import com.youhua.engine.service.EngineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "engine", description = "计算引擎（无状态工具方法）")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EngineController {

    private final EngineService engineService;

    @Operation(summary = "单笔 APR 试算（不持久化）")
    @PostMapping("/engine/apr:calculate")
    public CalculateAprResponse calculateApr(@Valid @RequestBody CalculateAprRequest request) {
        return engineService.calculateApr(request);
    }

    @Operation(summary = "快速压力检测（Page 2，无需登录，不持久化）")
    @PostMapping("/engine/pressure:assess")
    public AssessPressureResponse assessPressure(@Valid @RequestBody AssessPressureRequest request) {
        return engineService.assessPressure(request);
    }

    @Operation(summary = "利率模拟器（Page 6，无需持久化）")
    @PostMapping("/engine/rate:simulate")
    public SimulateRateResponse simulateRate(@Valid @RequestBody SimulateRateRequest request) {
        return engineService.simulateRate(request);
    }

    @Operation(summary = "What-if 评分模拟（还清/减少本金/置换利率 → 分数变化）")
    @PostMapping("/engine/score:simulate")
    public SimulateScoreResponse simulateScore(@Valid @RequestBody SimulateScoreRequest request) {
        return engineService.simulateScore(request);
    }
}
