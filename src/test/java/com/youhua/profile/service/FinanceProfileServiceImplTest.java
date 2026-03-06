package com.youhua.profile.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.debt.mapper.DebtMapper;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.rules.RuleEngine;
import com.youhua.engine.scoring.ScoringEngine;
import com.youhua.infra.log.OperationLogService;
import com.youhua.profile.dto.response.FinanceProfileResponse;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.entity.IncomeRecord;
import com.youhua.profile.enums.ProfileGenerationStatus;
import com.youhua.profile.enums.RiskLevel;
import com.youhua.profile.mapper.FinanceProfileMapper;
import com.youhua.profile.mapper.IncomeRecordMapper;
import com.youhua.profile.service.impl.FinanceProfileServiceImpl;
import com.youhua.testutil.TestDataBuilder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("FinanceProfileServiceImpl Tests")
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FinanceProfileServiceImplTest {

    @Mock private FinanceProfileMapper financeProfileMapper;
    @Mock private DebtMapper debtMapper;
    @Mock private IncomeRecordMapper incomeRecordMapper;
    @Mock private AprCalculator aprCalculator;
    @Mock private ScoringEngine scoringEngine;
    @Mock private RuleEngine ruleEngine;
    @Mock private OperationLogService operationLogService;
    @Mock private com.youhua.engine.scoring.record.ScoreRecordService scoreRecordService;

    @Captor private ArgumentCaptor<FinanceProfile> profileCaptor;

    private FinanceProfileServiceImpl service;

    private static final Long TEST_USER_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new FinanceProfileServiceImpl(
                financeProfileMapper, debtMapper, incomeRecordMapper,
                aprCalculator, scoringEngine, ruleEngine, operationLogService,
                new ObjectMapper(), scoreRecordService
        );
        ReflectionTestUtils.setField(service, "marketBaseApr", new BigDecimal("18"));

        // Set up request context so getCurrentUserId() returns TEST_USER_ID
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute("userId", TEST_USER_ID);
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ===================== getFinanceProfile() tests =====================

    @Test
    @DisplayName("should_returnProfile_when_profileExists")
    void should_returnProfile_when_profileExists() {
        FinanceProfile profile = TestDataBuilder.aFinanceProfile(TEST_USER_ID)
                .withId(400001L)
                .build();
        when(financeProfileMapper.selectOne(any())).thenReturn(profile);

        FinanceProfileResponse response = service.getFinanceProfile();

        assertThat(response).isNotNull();
        assertThat(response.getName()).isEqualTo("users/" + TEST_USER_ID + "/finance-profile");
        assertThat(response.getTotalDebt()).isEqualByComparingTo(profile.getTotalDebt());
        assertThat(response.getRestructureScore()).isEqualByComparingTo(profile.getRestructureScore());
        assertThat(response.getRiskLevel()).isEqualTo(RiskLevel.LOW);
    }

    @Test
    @DisplayName("should_throwBizException_when_profileNotFound")
    void should_throwBizException_when_profileNotFound() {
        when(financeProfileMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> service.getFinanceProfile())
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NO_CONFIRMED_DEBT);
    }

    // ===================== calculateFinanceProfile() tests =====================

    @Test
    @DisplayName("should_calculateAndReturnProfile_when_debtsExistWithIncome")
    void should_calculateAndReturnProfile_when_debtsExistWithIncome() {
        Debt debt1 = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300001L)
                .withPrincipal(new BigDecimal("10000"))
                .withTotalRepayment(new BigDecimal("12000"))
                .withMonthlyPayment(new BigDecimal("1000"))
                .withApr(new BigDecimal("20.000000"))
                .withLoanDays(365)
                .build();

        Debt debt2 = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300002L)
                .withPrincipal(new BigDecimal("20000"))
                .withTotalRepayment(new BigDecimal("23000"))
                .withMonthlyPayment(new BigDecimal("1916"))
                .withApr(new BigDecimal("15.000000"))
                .withLoanDays(365)
                .build();

        IncomeRecord income = TestDataBuilder.anIncome(TEST_USER_ID)
                .withAmount(new BigDecimal("15000"))
                .withPrimary(true)
                .build();

        RuleEngine.RuleResult ruleResult = new RuleEngine.RuleResult(List.of(), false, List.of(), true);
        ScoringEngine.ScoreResult scoreResult = buildScoreResult(new BigDecimal("75.00"), RiskLevel.MEDIUM);

        when(debtMapper.selectList(any())).thenReturn(List.of(debt1, debt2));
        when(incomeRecordMapper.selectList(any())).thenReturn(List.of(income));
        when(ruleEngine.evaluate(any())).thenReturn(ruleResult);
        when(aprCalculator.calculateWeightedApr(any())).thenReturn(new BigDecimal("17.000000"));
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(financeProfileMapper.selectOne(any())).thenReturn(null);
        when(financeProfileMapper.insert((FinanceProfile) any(FinanceProfile.class))).thenReturn(1);

        FinanceProfileResponse response = service.calculateFinanceProfile();

        assertThat(response).isNotNull();
        assertThat(response.getTotalDebt()).isEqualByComparingTo(new BigDecimal("30000"));
        assertThat(response.getDebtCount()).isEqualTo(2);
        assertThat(response.getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("15000"));
        assertThat(response.getWeightedApr()).isEqualByComparingTo(new BigDecimal("17.000000"));

        verify(financeProfileMapper, times(1)).insert((FinanceProfile) profileCaptor.capture());
        FinanceProfile saved = profileCaptor.getValue();
        assertThat(saved.getGenerationStatus()).isEqualTo(ProfileGenerationStatus.COMPLETED);
        assertThat(saved.getLastCalculatedTime()).isNotNull();
        verify(operationLogService, times(1)).record(any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("should_throwBizException_when_noInProfileDebts")
    void should_throwBizException_when_noInProfileDebts() {
        when(debtMapper.selectList(any())).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> service.calculateFinanceProfile())
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.PROFILE_NO_CONFIRMED_DEBT);

        verify(ruleEngine, never()).evaluate(any());
        verify(scoringEngine, never()).score(any());
    }

    @Test
    @DisplayName("should_calculateWithNullIncome_when_noIncomeRecords")
    void should_calculateWithNullIncome_when_noIncomeRecords() {
        Debt debt = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300001L)
                .withPrincipal(new BigDecimal("10000"))
                .withTotalRepayment(new BigDecimal("12000"))
                .withMonthlyPayment(new BigDecimal("1000"))
                .withApr(new BigDecimal("20.000000"))
                .withLoanDays(365)
                .build();

        RuleEngine.RuleResult ruleResult = new RuleEngine.RuleResult(List.of(), false, List.of(), true);
        ScoringEngine.ScoreResult scoreResult = buildScoreResult(new BigDecimal("45.00"), RiskLevel.HIGH);

        when(debtMapper.selectList(any())).thenReturn(List.of(debt));
        when(incomeRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(ruleEngine.evaluate(any())).thenReturn(ruleResult);
        when(aprCalculator.calculateWeightedApr(any())).thenReturn(new BigDecimal("20.000000"));
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(financeProfileMapper.selectOne(any())).thenReturn(null);
        when(financeProfileMapper.insert((FinanceProfile) any(FinanceProfile.class))).thenReturn(1);

        FinanceProfileResponse response = service.calculateFinanceProfile();

        assertThat(response).isNotNull();
        assertThat(response.getMonthlyIncome()).isNull();
        assertThat(response.getDebtIncomeRatio()).isNull();
    }

    @Test
    @DisplayName("should_throwBizException_when_ruleEngineBlocks")
    void should_throwBizException_when_ruleEngineBlocks() {
        Debt debt = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300001L)
                .withPrincipal(BigDecimal.ZERO)
                .build();

        when(debtMapper.selectList(any())).thenReturn(List.of(debt));
        when(incomeRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(ruleEngine.evaluate(any())).thenThrow(new BizException(ErrorCode.ENGINE_RULE_FAILED, "存在本金为零的债务"));

        assertThatThrownBy(() -> service.calculateFinanceProfile())
                .isInstanceOf(BizException.class)
                .extracting(e -> ((BizException) e).getErrorCode())
                .isEqualTo(ErrorCode.ENGINE_RULE_FAILED);

        verify(scoringEngine, never()).score(any());
    }

    @Test
    @DisplayName("should_updateExistingProfile_when_profileAlreadyExists")
    void should_updateExistingProfile_when_profileAlreadyExists() {
        FinanceProfile existing = TestDataBuilder.aFinanceProfile(TEST_USER_ID)
                .withId(400001L)
                .withGenerationStatus(ProfileGenerationStatus.COMPLETED)
                .build();

        Debt debt = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300001L)
                .withPrincipal(new BigDecimal("10000"))
                .withTotalRepayment(new BigDecimal("12000"))
                .withMonthlyPayment(new BigDecimal("1000"))
                .withApr(new BigDecimal("20.000000"))
                .withLoanDays(365)
                .build();

        RuleEngine.RuleResult ruleResult = new RuleEngine.RuleResult(List.of(), false, List.of(), true);
        ScoringEngine.ScoreResult scoreResult = buildScoreResult(new BigDecimal("82.00"), RiskLevel.LOW);

        when(debtMapper.selectList(any())).thenReturn(List.of(debt));
        when(incomeRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(ruleEngine.evaluate(any())).thenReturn(ruleResult);
        when(aprCalculator.calculateWeightedApr(any())).thenReturn(new BigDecimal("20.000000"));
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(financeProfileMapper.selectOne(any())).thenReturn(existing);
        when(financeProfileMapper.updateById((FinanceProfile) any(FinanceProfile.class))).thenReturn(1);

        service.calculateFinanceProfile();

        verify(financeProfileMapper, never()).insert((FinanceProfile) any(FinanceProfile.class));
        verify(financeProfileMapper, times(1)).updateById((FinanceProfile) profileCaptor.capture());
        assertThat(profileCaptor.getValue().getId()).isEqualTo(400001L);
    }

    @Test
    @DisplayName("should_setHighestAprDebtId_when_multipleDebtsExist")
    void should_setHighestAprDebtId_when_multipleDebtsExist() {
        Debt debtLow = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300001L)
                .withPrincipal(new BigDecimal("10000"))
                .withTotalRepayment(new BigDecimal("12000"))
                .withMonthlyPayment(new BigDecimal("1000"))
                .withApr(new BigDecimal("15.000000"))
                .withLoanDays(365)
                .build();

        Debt debtHigh = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300002L)
                .withPrincipal(new BigDecimal("5000"))
                .withTotalRepayment(new BigDecimal("6800"))
                .withMonthlyPayment(new BigDecimal("566"))
                .withApr(new BigDecimal("36.000000"))
                .withLoanDays(365)
                .build();

        RuleEngine.RuleResult ruleResult = new RuleEngine.RuleResult(List.of(), false, List.of(), true);
        ScoringEngine.ScoreResult scoreResult = buildScoreResult(new BigDecimal("55.00"), RiskLevel.MEDIUM);

        when(debtMapper.selectList(any())).thenReturn(List.of(debtLow, debtHigh));
        when(incomeRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(ruleEngine.evaluate(any())).thenReturn(ruleResult);
        when(aprCalculator.calculateWeightedApr(any())).thenReturn(new BigDecimal("21.000000"));
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(financeProfileMapper.selectOne(any())).thenReturn(null);
        when(financeProfileMapper.insert((FinanceProfile) any(FinanceProfile.class))).thenReturn(1);

        service.calculateFinanceProfile();

        verify(financeProfileMapper).insert((FinanceProfile) profileCaptor.capture());
        assertThat(profileCaptor.getValue().getHighestAprDebtId()).isEqualTo(300002L);
    }

    @Test
    @DisplayName("should_countOverdueDebts_when_someDebtsAreOverdue")
    void should_countOverdueDebts_when_someDebtsAreOverdue() {
        Debt normalDebt = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300001L)
                .withPrincipal(new BigDecimal("10000"))
                .withTotalRepayment(new BigDecimal("12000"))
                .withMonthlyPayment(new BigDecimal("1000"))
                .withApr(new BigDecimal("20.000000"))
                .withLoanDays(365)
                .withOverdueStatus(OverdueStatus.NORMAL)
                .build();

        Debt overdueDebt = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300002L)
                .withPrincipal(new BigDecimal("5000"))
                .withTotalRepayment(new BigDecimal("6000"))
                .withMonthlyPayment(new BigDecimal("500"))
                .withApr(new BigDecimal("24.000000"))
                .withLoanDays(365)
                .withOverdueStatus(OverdueStatus.OVERDUE_30)
                .withOverdueDays(25)
                .build();

        RuleEngine.RuleResult ruleResult = new RuleEngine.RuleResult(List.of(), false, List.of(), true);
        ScoringEngine.ScoreResult scoreResult = buildScoreResult(new BigDecimal("60.00"), RiskLevel.MEDIUM);

        when(debtMapper.selectList(any())).thenReturn(List.of(normalDebt, overdueDebt));
        when(incomeRecordMapper.selectList(any())).thenReturn(Collections.emptyList());
        when(ruleEngine.evaluate(any())).thenReturn(ruleResult);
        when(aprCalculator.calculateWeightedApr(any())).thenReturn(new BigDecimal("21.000000"));
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(financeProfileMapper.selectOne(any())).thenReturn(null);
        when(financeProfileMapper.insert((FinanceProfile) any(FinanceProfile.class))).thenReturn(1);

        service.calculateFinanceProfile();

        verify(financeProfileMapper).insert((FinanceProfile) profileCaptor.capture());
        assertThat(profileCaptor.getValue().getOverdueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("should_useAllIncomeRecords_when_noPrimaryIncomeExists")
    void should_useAllIncomeRecords_when_noPrimaryIncomeExists() {
        Debt debt = TestDataBuilder.aDebt(TEST_USER_ID)
                .withId(300001L)
                .withPrincipal(new BigDecimal("10000"))
                .withTotalRepayment(new BigDecimal("12000"))
                .withMonthlyPayment(new BigDecimal("1000"))
                .withApr(new BigDecimal("20.000000"))
                .withLoanDays(365)
                .build();

        IncomeRecord inc1 = TestDataBuilder.anIncome(TEST_USER_ID)
                .withAmount(new BigDecimal("5000")).withPrimary(false).build();
        IncomeRecord inc2 = TestDataBuilder.anIncome(TEST_USER_ID)
                .withAmount(new BigDecimal("3000")).withPrimary(false).build();

        RuleEngine.RuleResult ruleResult = new RuleEngine.RuleResult(List.of(), false, List.of(), true);
        ScoringEngine.ScoreResult scoreResult = buildScoreResult(new BigDecimal("70.00"), RiskLevel.MEDIUM);

        when(debtMapper.selectList(any())).thenReturn(List.of(debt));
        when(incomeRecordMapper.selectList(any())).thenReturn(List.of(inc1, inc2));
        when(ruleEngine.evaluate(any())).thenReturn(ruleResult);
        when(aprCalculator.calculateWeightedApr(any())).thenReturn(new BigDecimal("20.000000"));
        when(scoringEngine.score(any())).thenReturn(scoreResult);
        when(financeProfileMapper.selectOne(any())).thenReturn(null);
        when(financeProfileMapper.insert((FinanceProfile) any(FinanceProfile.class))).thenReturn(1);

        service.calculateFinanceProfile();

        verify(financeProfileMapper).insert((FinanceProfile) profileCaptor.capture());
        // Should sum both non-primary records: 5000 + 3000 = 8000
        assertThat(profileCaptor.getValue().getMonthlyIncome()).isEqualByComparingTo(new BigDecimal("8000"));
    }

    // ===================== Helpers =====================

    private ScoringEngine.ScoreResult buildScoreResult(BigDecimal score, RiskLevel riskLevel) {
        return new ScoringEngine.ScoreResult(
                score, riskLevel,
                ScoringEngine.Recommendation.OPTIMIZE_FIRST,
                "当前更适合优化信用结构",
                "信用修复路线图",
                List.of(),
                LocalDateTime.now()
        );
    }
}
