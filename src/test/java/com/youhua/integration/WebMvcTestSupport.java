package com.youhua.integration;

import com.youhua.ai.ocr.mapper.OcrTaskMapper;
import com.youhua.auth.mapper.UserMapper;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.debt.statemachine.DebtEntryAction;
import com.youhua.debt.statemachine.DebtEntryGuard;
import com.youhua.debt.statemachine.DebtEntryTimeoutHandler;
import com.youhua.engine.service.EngineService;
import com.youhua.infra.log.mapper.OperationLogMapper;
import com.youhua.profile.mapper.FinanceProfileMapper;
import com.youhua.profile.mapper.IncomeRecordMapper;
import com.youhua.engine.scoring.mapper.ScoreRecordMapper;
import com.youhua.infra.mapper.FailedEventMapper;
import com.youhua.profile.mapper.ConsultationRequestMapper;
import com.youhua.profile.mapper.OptimizationReportMapper;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Base support for @WebMvcTest integration tests.
 * Provides @MockBean declarations for all infrastructure beans
 * that Spring components depend on.
 *
 * Subclasses should declare their own service-level @MockBean
 * in addition to extending this class.
 */
public abstract class WebMvcTestSupport {

    @MockBean
    protected UserMapper userMapper;

    @MockBean
    protected DebtMapper debtMapper;

    @MockBean
    protected OcrTaskMapper ocrTaskMapper;

    @MockBean
    protected OperationLogMapper operationLogMapper;

    @MockBean
    protected FinanceProfileMapper financeProfileMapper;

    @MockBean
    protected IncomeRecordMapper incomeRecordMapper;

    @MockBean
    protected OptimizationReportMapper optimizationReportMapper;

    @MockBean
    protected EngineService engineService;

    @MockBean
    protected DebtEntryGuard debtEntryGuard;

    @MockBean
    protected DebtEntryAction debtEntryAction;

    @MockBean
    protected DebtEntryTimeoutHandler debtEntryTimeoutHandler;

    @MockBean
    protected StringRedisTemplate stringRedisTemplate;

    @MockBean
    protected ScoreRecordMapper scoreRecordMapper;

    @MockBean
    protected ConsultationRequestMapper consultationRequestMapper;

    @MockBean
    protected FailedEventMapper failedEventMapper;
}
