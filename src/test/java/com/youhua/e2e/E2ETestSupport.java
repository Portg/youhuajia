package com.youhua.e2e;

import com.youhua.ai.ocr.service.OcrTaskService;
import com.youhua.ai.service.SuggestionGenService;
import com.youhua.auth.service.AuthService;
import com.youhua.debt.service.DebtService;
import com.youhua.debt.statemachine.DebtEntryAction;
import com.youhua.debt.statemachine.DebtEntryGuard;
import com.youhua.debt.statemachine.DebtEntryTimeoutHandler;
import com.youhua.engine.service.EngineService;
import com.youhua.profile.service.FinanceProfileService;
import com.youhua.profile.service.IncomeService;
import com.youhua.profile.service.ReportService;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * Shared @MockBean declarations for @SpringBootTest e2e tests.
 *
 * Service implementations do not exist yet (only interfaces), so we provide
 * Mockito stubs to satisfy the Spring context. Each concrete test class configures
 * its own Mockito behaviour via when(...).thenReturn(...) in @BeforeEach.
 */
public abstract class E2ETestSupport {

    // Service layer — no implementations exist yet
    @MockBean
    protected AuthService authService;

    @MockBean
    protected DebtService debtService;

    @MockBean
    protected OcrTaskService ocrTaskService;

    @MockBean
    protected EngineService engineService;

    @MockBean
    protected FinanceProfileService financeProfileService;

    @MockBean
    protected IncomeService incomeService;

    @MockBean
    protected ReportService reportService;

    @MockBean
    protected SuggestionGenService suggestionGenService;

    // State machine components that have no-arg dependencies on mappers
    @MockBean
    protected DebtEntryGuard debtEntryGuard;

    @MockBean
    protected DebtEntryAction debtEntryAction;

    @MockBean
    protected DebtEntryTimeoutHandler debtEntryTimeoutHandler;
}
