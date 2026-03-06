package com.youhua.engine.service;

import com.youhua.engine.dto.request.AssessPressureRequest;
import com.youhua.engine.dto.request.CalculateAprRequest;
import com.youhua.engine.dto.request.CompareStrategiesRequest;
import com.youhua.engine.dto.request.SimulateRateRequest;
import com.youhua.engine.dto.request.SimulateScoreRequest;
import com.youhua.engine.dto.response.AssessPressureResponse;
import com.youhua.engine.dto.response.CalculateAprResponse;
import com.youhua.engine.dto.response.CompareStrategiesResponse;
import com.youhua.engine.dto.response.SimulateRateResponse;
import com.youhua.engine.dto.response.SimulateScoreResponse;

public interface EngineService {

    CalculateAprResponse calculateApr(CalculateAprRequest request);

    AssessPressureResponse assessPressure(AssessPressureRequest request);

    SimulateRateResponse simulateRate(SimulateRateRequest request);

    SimulateScoreResponse simulateScore(SimulateScoreRequest request);

    CompareStrategiesResponse compareStrategies(CompareStrategiesRequest request);
}
