package com.youhua.engine.apr;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.engine.apr.AprCalculator.DebtAprEntry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for AprCalculator.
 *
 * <p>Covers: 6 normal + 3 boundary + 7 exception + 4 weighted APR = 20 test cases.
 */
@DisplayName("AprCalculator Tests")
class AprCalculatorTest {

    private AprCalculator aprCalculator;

    @BeforeEach
    void setUp() {
        AprConfig config = new AprConfig();
        config.setWarningThreshold(new BigDecimal("36.0"));
        config.setDangerThreshold(new BigDecimal("100.0"));
        config.setAbnormalThreshold(new BigDecimal("1000.0"));
        config.setMaxAllowed(new BigDecimal("10000.0"));
        aprCalculator = new AprCalculator(config);
    }

    // ===== Normal Scenarios (6) =====

    @Test
    @DisplayName("APR-N01: should_return_60_833333_when_short_term_small_loan")
    void should_return_60_833333_when_short_term_small_loan() {
        // interest=500, rate=0.05, annualized=0.05*(365/30)=0.6083333..., *100=60.833333
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("10000.00"),
                new BigDecimal("10500.00"),
                30
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("60.833333"));
    }

    @Test
    @DisplayName("APR-N02: should_return_20_000000_when_standard_annual")
    void should_return_20_000000_when_standard_annual() {
        // interest=20000, rate=0.2, annualized=0.2*(365/365)=0.2, *100=20.000000
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("100000.00"),
                new BigDecimal("120000.00"),
                365
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("20.000000"));
    }

    @Test
    @DisplayName("APR-N03: should_return_8_111111_when_medium_term")
    void should_return_8_111111_when_medium_term() {
        // interest=2000, rate=0.04, annualized=0.04*(365/180)=0.081111..., *100=8.111111
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("50000.00"),
                new BigDecimal("52000.00"),
                180
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("8.111111"));
    }

    @Test
    @DisplayName("APR-N04: should_return_20_000000_when_two_year_loan")
    void should_return_20_000000_when_two_year_loan() {
        // interest=80000, rate=0.4, annualized=0.4*(365/730)=0.2, *100=20.000000
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("200000.00"),
                new BigDecimal("280000.00"),
                730
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("20.000000"));
    }

    @Test
    @DisplayName("APR-N05: should_return_104_285714_when_ultra_short_high_interest")
    void should_return_104_285714_when_ultra_short_high_interest() {
        // interest=100, rate=0.02, annualized=0.02*(365/7)=1.0428571..., *100=104.285714
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("5000.00"),
                new BigDecimal("5100.00"),
                7
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("104.285714"));
    }

    @Test
    @DisplayName("APR-N06: should_return_5_000000_when_large_low_interest")
    void should_return_5_000000_when_large_low_interest() {
        // interest=50000, rate=0.05, annualized=0.05*(365/365)=0.05, *100=5.000000
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("1000000.00"),
                new BigDecimal("1050000.00"),
                365
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("5.000000"));
    }

    // ===== Boundary Scenarios (3) =====

    @Test
    @DisplayName("APR-B01: should_return_zero_when_no_interest")
    void should_return_zero_when_no_interest() {
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("50000.00"),
                new BigDecimal("50000.00"),
                90
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.000000"));
    }

    @Test
    @DisplayName("APR-B02: should_return_36500_when_minimum_principal_and_one_day")
    void should_return_36500_when_minimum_principal_and_one_day() {
        // interest=0.01, rate=1.0, annualized=1.0*(365/1)=365, *100=36500.000000
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("0.01"),
                new BigDecimal("0.02"),
                1
        );
        assertThat(result).isEqualByComparingTo(new BigDecimal("36500.000000"));
    }

    @Test
    @DisplayName("APR-B03: should_return_near_zero_when_maximum_principal")
    void should_return_near_zero_when_maximum_principal() {
        // Very large principal with tiny interest → APR rounds to 0.000000 at scale=6
        BigDecimal result = aprCalculator.calculateApr(
                new BigDecimal("9999999999999.9999"),
                new BigDecimal("10000000000000.0000"),
                365
        );
        // interest=0.0001, interestRate≈1e-17 → APR effectively 0 at scale=6
        assertThat(result).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ===== Exception Scenarios (7) =====

    @Test
    @DisplayName("APR-E01: should_throw_when_principal_zero")
    void should_throw_when_principal_zero() {
        assertThatThrownBy(() -> aprCalculator.calculateApr(
                BigDecimal.ZERO,
                new BigDecimal("500.00"),
                30
        ))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("APR-E02: should_throw_when_principal_negative")
    void should_throw_when_principal_negative() {
        assertThatThrownBy(() -> aprCalculator.calculateApr(
                new BigDecimal("-5000.00"),
                new BigDecimal("10000.00"),
                30
        ))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("APR-E03: should_throw_when_loan_days_zero")
    void should_throw_when_loan_days_zero() {
        assertThatThrownBy(() -> aprCalculator.calculateApr(
                new BigDecimal("10000.00"),
                new BigDecimal("10500.00"),
                0
        ))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("APR-E04: should_throw_when_loan_days_negative")
    void should_throw_when_loan_days_negative() {
        assertThatThrownBy(() -> aprCalculator.calculateApr(
                new BigDecimal("10000.00"),
                new BigDecimal("10500.00"),
                -30
        ))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("APR-E05: should_throw_when_repayment_less_than_principal")
    void should_throw_when_repayment_less_than_principal() {
        assertThatThrownBy(() -> aprCalculator.calculateApr(
                new BigDecimal("10000.00"),
                new BigDecimal("9000.00"),
                30
        ))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("APR-E06: should_throw_when_principal_null")
    void should_throw_when_principal_null() {
        assertThatThrownBy(() -> aprCalculator.calculateApr(
                null,
                new BigDecimal("10500.00"),
                30
        ))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE));
    }

    @Test
    @DisplayName("APR-E07: should_throw_when_apr_exceeds_max")
    void should_throw_when_apr_exceeds_max() {
        // principal=1000, total=200000, days=3
        // APR = (199000/1000) * (365/3) * 100 = 2,421,166.67% >> 1,000,000% hard cap
        assertThatThrownBy(() -> aprCalculator.calculateApr(
                new BigDecimal("1000.00"),
                new BigDecimal("200000.00"),
                3
        ))
                .isInstanceOf(BizException.class)
                .satisfies(e -> assertThat(((BizException) e).getErrorCode())
                        .isEqualTo(ErrorCode.ENGINE_APR_RESULT_ABNORMAL));
    }

    // ===== Weighted APR Scenarios (4) =====

    @Test
    @DisplayName("WAPR-01: should_return_25_333333_when_two_debts")
    void should_return_25_333333_when_two_debts() {
        // weighted = (100000*20 + 50000*36) / 150000 = 3800000/150000 = 25.333333
        List<DebtAprEntry> debts = List.of(
                new DebtAprEntry(new BigDecimal("100000"), new BigDecimal("20")),
                new DebtAprEntry(new BigDecimal("50000"), new BigDecimal("36"))
        );
        BigDecimal result = aprCalculator.calculateWeightedApr(debts);
        assertThat(result).isEqualByComparingTo(new BigDecimal("25.333333"));
    }

    @Test
    @DisplayName("WAPR-02: should_return_same_apr_when_single_debt")
    void should_return_same_apr_when_single_debt() {
        List<DebtAprEntry> debts = List.of(
                new DebtAprEntry(new BigDecimal("100000"), new BigDecimal("20"))
        );
        BigDecimal result = aprCalculator.calculateWeightedApr(debts);
        assertThat(result).isEqualByComparingTo(new BigDecimal("20.000000"));
    }

    @Test
    @DisplayName("WAPR-03: should_return_average_when_equal_principals")
    void should_return_average_when_equal_principals() {
        // (10000*10 + 10000*20 + 10000*30) / 30000 = 600000/30000 = 20.000000
        List<DebtAprEntry> debts = List.of(
                new DebtAprEntry(new BigDecimal("10000"), new BigDecimal("10")),
                new DebtAprEntry(new BigDecimal("10000"), new BigDecimal("20")),
                new DebtAprEntry(new BigDecimal("10000"), new BigDecimal("30"))
        );
        BigDecimal result = aprCalculator.calculateWeightedApr(debts);
        assertThat(result).isEqualByComparingTo(new BigDecimal("20.000000"));
    }

    @Test
    @DisplayName("WAPR-04: should_return_zero_with_warn_when_empty")
    void should_return_zero_with_warn_when_empty() {
        BigDecimal result = aprCalculator.calculateWeightedApr(List.of());
        assertThat(result).isEqualByComparingTo(new BigDecimal("0.000000"));
    }
}
