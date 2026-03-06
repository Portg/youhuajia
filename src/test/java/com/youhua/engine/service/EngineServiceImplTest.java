package com.youhua.engine.service;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.engine.apr.AprCalculator;
import com.youhua.engine.apr.AprConfig;
import com.youhua.engine.dto.request.AssessPressureRequest;
import com.youhua.engine.dto.request.CalculateAprRequest;
import com.youhua.engine.dto.request.SimulateRateRequest;
import com.youhua.engine.dto.response.AssessPressureResponse;
import com.youhua.engine.dto.response.AssessPressureResponse.PressureLevel;
import com.youhua.engine.dto.response.CalculateAprResponse;
import com.youhua.engine.dto.response.SimulateRateResponse;
import com.youhua.engine.service.impl.EngineServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("EngineServiceImpl Tests")
@ExtendWith(MockitoExtension.class)
class EngineServiceImplTest {

    private EngineServiceImpl engineService;
    private AprCalculator aprCalculator;

    @BeforeEach
    void setUp() {
        AprConfig config = new AprConfig();
        config.setWarningThreshold(new BigDecimal("36.0"));
        config.setDangerThreshold(new BigDecimal("100.0"));
        config.setAbnormalThreshold(new BigDecimal("1000.0"));
        config.setMaxAllowed(new BigDecimal("10000.0"));
        aprCalculator = new AprCalculator(config);
        engineService = new EngineServiceImpl(aprCalculator, null, null, null, null, null, null);
    }

    // ===== calculateApr Tests =====

    @Test
    @DisplayName("should_return_apr_response_when_valid_params")
    void should_return_apr_response_when_valid_params() {
        CalculateAprRequest request = new CalculateAprRequest();
        request.setPrincipal(new BigDecimal("10000.00"));
        request.setTotalRepayment(new BigDecimal("10500.00"));
        request.setLoanDays(30);

        CalculateAprResponse response = engineService.calculateApr(request);

        assertThat(response).isNotNull();
        assertThat(response.getApr()).isNotNull();
        assertThat(response.getApr()).isEqualByComparingTo(new BigDecimal("60.833333"));
        assertThat(response.getDailyRate()).isNotNull();
        assertThat(response.getTotalInterest()).isEqualByComparingTo(new BigDecimal("500.00"));
    }

    @Test
    @DisplayName("should_return_zero_interest_when_repayment_equals_principal")
    void should_return_zero_interest_when_repayment_equals_principal() {
        CalculateAprRequest request = new CalculateAprRequest();
        request.setPrincipal(new BigDecimal("50000.00"));
        request.setTotalRepayment(new BigDecimal("50000.00"));
        request.setLoanDays(90);

        CalculateAprResponse response = engineService.calculateApr(request);

        assertThat(response.getApr()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getTotalInterest()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should_throw_biz_exception_when_principal_is_null")
    void should_throw_biz_exception_when_principal_is_null() {
        CalculateAprRequest request = new CalculateAprRequest();
        request.setPrincipal(null);
        request.setTotalRepayment(new BigDecimal("10500.00"));
        request.setLoanDays(30);

        assertThatThrownBy(() -> engineService.calculateApr(request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("should_throw_biz_exception_when_repayment_less_than_principal")
    void should_throw_biz_exception_when_repayment_less_than_principal() {
        CalculateAprRequest request = new CalculateAprRequest();
        request.setPrincipal(new BigDecimal("10000.00"));
        request.setTotalRepayment(new BigDecimal("9000.00"));
        request.setLoanDays(30);

        assertThatThrownBy(() -> engineService.calculateApr(request))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("should_calculate_correct_daily_rate_when_valid_params")
    void should_calculate_correct_daily_rate_when_valid_params() {
        CalculateAprRequest request = new CalculateAprRequest();
        request.setPrincipal(new BigDecimal("10000.00"));
        request.setTotalRepayment(new BigDecimal("10500.00"));
        request.setLoanDays(30);

        CalculateAprResponse response = engineService.calculateApr(request);

        // dailyRate = 500/10000/30*100 = 0.166667
        assertThat(response.getDailyRate()).isEqualByComparingTo(new BigDecimal("0.166667"));
    }

    // ===== assessPressure Tests =====

    @Test
    @DisplayName("should_return_healthy_when_ratio_below_0_3")
    void should_return_healthy_when_ratio_below_0_3() {
        AssessPressureRequest request = new AssessPressureRequest();
        request.setMonthlyPayment(new BigDecimal("2000"));
        request.setMonthlyIncome(new BigDecimal("10000"));

        AssessPressureResponse response = engineService.assessPressure(request);

        assertThat(response.getLevel()).isEqualTo(PressureLevel.HEALTHY);
        assertThat(response.getRatio()).isEqualByComparingTo(new BigDecimal("0.200000"));
        assertThat(response.getPressureIndex()).isEqualByComparingTo(new BigDecimal("20.000000"));
        assertThat(response.getHint()).contains("良好");
    }

    @Test
    @DisplayName("should_return_moderate_when_ratio_between_0_3_and_0_5")
    void should_return_moderate_when_ratio_between_0_3_and_0_5() {
        AssessPressureRequest request = new AssessPressureRequest();
        request.setMonthlyPayment(new BigDecimal("4000"));
        request.setMonthlyIncome(new BigDecimal("10000"));

        AssessPressureResponse response = engineService.assessPressure(request);

        assertThat(response.getLevel()).isEqualTo(PressureLevel.MODERATE);
        assertThat(response.getRatio()).isEqualByComparingTo(new BigDecimal("0.400000"));
    }

    @Test
    @DisplayName("should_return_heavy_when_ratio_between_0_5_and_0_7")
    void should_return_heavy_when_ratio_between_0_5_and_0_7() {
        AssessPressureRequest request = new AssessPressureRequest();
        request.setMonthlyPayment(new BigDecimal("6000"));
        request.setMonthlyIncome(new BigDecimal("10000"));

        AssessPressureResponse response = engineService.assessPressure(request);

        assertThat(response.getLevel()).isEqualTo(PressureLevel.HEAVY);
        assertThat(response.getRatio()).isEqualByComparingTo(new BigDecimal("0.600000"));
    }

    @Test
    @DisplayName("should_return_severe_when_ratio_above_0_7")
    void should_return_severe_when_ratio_above_0_7() {
        AssessPressureRequest request = new AssessPressureRequest();
        request.setMonthlyPayment(new BigDecimal("8000"));
        request.setMonthlyIncome(new BigDecimal("10000"));

        AssessPressureResponse response = engineService.assessPressure(request);

        assertThat(response.getLevel()).isEqualTo(PressureLevel.SEVERE);
        assertThat(response.getPressureIndex()).isEqualByComparingTo(new BigDecimal("80.000000"));
    }

    @Test
    @DisplayName("should_return_severe_with_100_pressure_when_income_is_zero")
    void should_return_severe_with_100_pressure_when_income_is_zero() {
        AssessPressureRequest request = new AssessPressureRequest();
        request.setMonthlyPayment(new BigDecimal("3000"));
        request.setMonthlyIncome(BigDecimal.ZERO);

        AssessPressureResponse response = engineService.assessPressure(request);

        assertThat(response.getLevel()).isEqualTo(PressureLevel.SEVERE);
        assertThat(response.getPressureIndex()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(response.getRatio()).isNull();
    }

    @Test
    @DisplayName("should_cap_pressure_index_at_100_when_ratio_exceeds_1")
    void should_cap_pressure_index_at_100_when_ratio_exceeds_1() {
        AssessPressureRequest request = new AssessPressureRequest();
        request.setMonthlyPayment(new BigDecimal("20000"));
        request.setMonthlyIncome(new BigDecimal("10000"));

        AssessPressureResponse response = engineService.assessPressure(request);

        assertThat(response.getPressureIndex()).isEqualByComparingTo(new BigDecimal("100"));
        assertThat(response.getLevel()).isEqualTo(PressureLevel.SEVERE);
    }

    @Test
    @DisplayName("should_return_exactly_healthy_when_ratio_exactly_0_3")
    void should_return_exactly_healthy_when_ratio_exactly_0_3() {
        AssessPressureRequest request = new AssessPressureRequest();
        request.setMonthlyPayment(new BigDecimal("3000"));
        request.setMonthlyIncome(new BigDecimal("10000"));

        AssessPressureResponse response = engineService.assessPressure(request);

        assertThat(response.getLevel()).isEqualTo(PressureLevel.HEALTHY);
    }

    // ===== simulateRate Tests =====

    @Test
    @DisplayName("should_return_positive_saving_when_target_apr_lower")
    void should_return_positive_saving_when_target_apr_lower() {
        SimulateRateRequest request = new SimulateRateRequest();
        request.setCurrentWeightedApr(new BigDecimal("20.000000"));
        request.setTargetApr(new BigDecimal("10.000000"));
        request.setTotalPrincipal(new BigDecimal("100000"));
        request.setAvgLoanDays(360);
        request.setMonthlyIncome(new BigDecimal("20000"));

        SimulateRateResponse response = engineService.simulateRate(request);

        assertThat(response.getMonthlySaving()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getThreeYearSaving()).isGreaterThan(BigDecimal.ZERO);
        assertThat(response.getCurrentIncomeRatio()).isNotNull();
        assertThat(response.getTargetIncomeRatio()).isNotNull();
        assertThat(response.getDisclaimer()).isNotBlank();
    }

    @Test
    @DisplayName("should_return_null_income_ratio_when_no_monthly_income")
    void should_return_null_income_ratio_when_no_monthly_income() {
        SimulateRateRequest request = new SimulateRateRequest();
        request.setCurrentWeightedApr(new BigDecimal("20.000000"));
        request.setTargetApr(new BigDecimal("10.000000"));
        request.setTotalPrincipal(new BigDecimal("100000"));
        request.setAvgLoanDays(360);
        request.setMonthlyIncome(null);

        SimulateRateResponse response = engineService.simulateRate(request);

        assertThat(response.getCurrentIncomeRatio()).isNull();
        assertThat(response.getTargetIncomeRatio()).isNull();
    }

    @Test
    @DisplayName("should_return_zero_saving_when_same_apr")
    void should_return_zero_saving_when_same_apr() {
        SimulateRateRequest request = new SimulateRateRequest();
        request.setCurrentWeightedApr(new BigDecimal("15.000000"));
        request.setTargetApr(new BigDecimal("15.000000"));
        request.setTotalPrincipal(new BigDecimal("100000"));
        request.setAvgLoanDays(365);

        SimulateRateResponse response = engineService.simulateRate(request);

        assertThat(response.getMonthlySaving()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(response.getThreeYearSaving()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("should_return_disclaimer_in_response")
    void should_return_disclaimer_in_response() {
        SimulateRateRequest request = new SimulateRateRequest();
        request.setCurrentWeightedApr(new BigDecimal("20.000000"));
        request.setTargetApr(new BigDecimal("10.000000"));
        request.setTotalPrincipal(new BigDecimal("50000"));
        request.setAvgLoanDays(180);

        SimulateRateResponse response = engineService.simulateRate(request);

        assertThat(response.getDisclaimer()).isEqualTo("以上为简化估算，实际月供因还款方式不同可能存在差异");
    }
}
