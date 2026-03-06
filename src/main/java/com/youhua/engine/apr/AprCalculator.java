package com.youhua.engine.apr;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * APR (Annual Percentage Rate) calculator using simplified formula.
 *
 * <p>Formula: APR = (totalRepayment - principal) / principal × (365 / loanDays) × 100
 *
 * <p>Precision rules:
 * <ul>
 *   <li>All intermediate steps: scale=10, RoundingMode.HALF_UP</li>
 *   <li>Final result: scale=6, RoundingMode.HALF_UP</li>
 *   <li>No truncation in intermediate steps</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AprCalculator {

    private static final int INTERMEDIATE_SCALE = 10;
    private static final int FINAL_SCALE = 6;
    private static final RoundingMode ROUNDING = RoundingMode.HALF_UP;
    private static final BigDecimal DAYS_IN_YEAR = new BigDecimal("365");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    /**
     * Hard cap for APR calculation result. Values exceeding this indicate
     * clearly erroneous data (e.g. 200x principal returned in 3 days = 2.4M%).
     * Normal extreme cases like 0.01 principal over 1 day = 36500% are valid.
     */
    private static final BigDecimal HARD_CAP = new BigDecimal("1000000");

    private final AprConfig aprConfig;

    /**
     * Calculates APR for a single debt.
     *
     * <p>Validation order (fail fast):
     * <ol>
     *   <li>principal == null → BizException(404001)</li>
     *   <li>totalRepayment == null → BizException(404001)</li>
     *   <li>principal &lt;= 0 → BizException(404001)</li>
     *   <li>loanDays &lt;= 0 → BizException(404001)</li>
     *   <li>totalRepayment &lt; principal → BizException(404001)</li>
     * </ol>
     *
     * <p>Post-calculation: APR &gt; 1,000,000% → BizException(404002)
     *
     * @param principal      loan principal, must be &gt; 0
     * @param totalRepayment total repayment (principal + all fees), must be &gt;= principal
     * @param loanDays       loan duration in days, must be &gt; 0
     * @return APR as percentage with scale=6, e.g. 20.000000 means 20%
     * @throws BizException ENGINE_APR_PARAMS_INCOMPLETE if parameters invalid
     * @throws BizException ENGINE_APR_RESULT_ABNORMAL if APR exceeds hard cap
     */
    public BigDecimal calculateApr(BigDecimal principal, BigDecimal totalRepayment, int loanDays) {
        validateParams(principal, totalRepayment, loanDays);

        // Step 1: interest = totalRepayment - principal
        BigDecimal interest = totalRepayment.subtract(principal);

        // Step 2: interestRate = interest / principal (scale=10, HALF_UP)
        BigDecimal interestRate = interest.divide(principal, INTERMEDIATE_SCALE, ROUNDING);

        // Step 3: annualized = interestRate × (365 / loanDays) (scale=10, HALF_UP)
        BigDecimal loanDaysBd = new BigDecimal(loanDays);
        BigDecimal annualized = interestRate.multiply(
                DAYS_IN_YEAR.divide(loanDaysBd, INTERMEDIATE_SCALE, ROUNDING)
        ).setScale(INTERMEDIATE_SCALE, ROUNDING);

        // Step 4: aprPercent = annualized × 100 (scale=10, HALF_UP)
        BigDecimal aprPercent = annualized.multiply(HUNDRED).setScale(INTERMEDIATE_SCALE, ROUNDING);

        // Step 5: result = aprPercent.setScale(6, HALF_UP)
        BigDecimal result = aprPercent.setScale(FINAL_SCALE, ROUNDING);

        log.debug("APR calc: interest={}, interestRate={}, annualized={}, aprPercent={}",
                interest, interestRate, annualized, result);

        if (result.compareTo(HARD_CAP) > 0) {
            log.warn("APR result {} exceeds hard cap {}%, data likely erroneous", result, HARD_CAP);
            throw new BizException(ErrorCode.ENGINE_APR_RESULT_ABNORMAL,
                    String.format("APR %.6f%% 超过合理范围，请核实债务数据", result));
        }

        if (result.compareTo(aprConfig.getWarningThreshold()) > 0) {
            log.warn("HIGH_INTEREST: APR={}% exceeds warning threshold {}%", result, aprConfig.getWarningThreshold());
        }

        return result;
    }

    /**
     * Calculates weighted APR across multiple debts.
     *
     * <p>Formula: weightedApr = Σ(apr_i × principal_i) / Σ(principal_i)
     *
     * @param debts list of debt APR entries; null or empty returns 0 with WARN log
     * @return weighted APR with scale=6
     */
    public BigDecimal calculateWeightedApr(List<DebtAprEntry> debts) {
        if (debts == null || debts.isEmpty()) {
            log.warn("calculateWeightedApr: empty debts list, returning 0");
            return BigDecimal.ZERO.setScale(FINAL_SCALE, ROUNDING);
        }

        if (debts.size() == 1) {
            return debts.get(0).apr().setScale(FINAL_SCALE, ROUNDING);
        }

        BigDecimal totalPrincipal = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;

        for (DebtAprEntry entry : debts) {
            totalPrincipal = totalPrincipal.add(entry.principal());
            weightedSum = weightedSum.add(
                    entry.apr().multiply(entry.principal()).setScale(INTERMEDIATE_SCALE, ROUNDING)
            );
        }

        if (totalPrincipal.compareTo(BigDecimal.ZERO) == 0) {
            log.warn("calculateWeightedApr: total principal is zero, returning 0");
            return BigDecimal.ZERO.setScale(FINAL_SCALE, ROUNDING);
        }

        return weightedSum.divide(totalPrincipal, FINAL_SCALE, ROUNDING);
    }

    /**
     * Returns the APR alert level based on configured thresholds.
     *
     * @param apr APR value as percentage (e.g. 20.0 for 20%)
     * @return AprLevel enum value
     */
    public AprLevel getAprLevel(BigDecimal apr) {
        if (apr.compareTo(aprConfig.getWarningThreshold()) <= 0) {
            return AprLevel.NORMAL;
        }
        if (apr.compareTo(aprConfig.getDangerThreshold()) <= 0) {
            return AprLevel.WARNING;
        }
        if (apr.compareTo(aprConfig.getAbnormalThreshold()) <= 0) {
            return AprLevel.DANGER;
        }
        return AprLevel.ABNORMAL;
    }

    /**
     * Calculates daily interest rate as a percentage.
     *
     * <p>Formula: dailyRate = (totalRepayment - principal) / principal / loanDays × 100
     */
    public BigDecimal calculateDailyRate(BigDecimal principal, BigDecimal totalRepayment, int loanDays) {
        validateParams(principal, totalRepayment, loanDays);
        BigDecimal interest = totalRepayment.subtract(principal);
        BigDecimal loanDaysBd = new BigDecimal(loanDays);
        return interest.divide(principal, INTERMEDIATE_SCALE, ROUNDING)
                .divide(loanDaysBd, INTERMEDIATE_SCALE, ROUNDING)
                .multiply(HUNDRED)
                .setScale(FINAL_SCALE, ROUNDING);
    }

    /**
     * Calculates total interest amount.
     */
    public BigDecimal calculateTotalInterest(BigDecimal principal, BigDecimal totalRepayment) {
        if (principal == null || totalRepayment == null) {
            throw new BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE, "APR 计算参数不完整");
        }
        return totalRepayment.subtract(principal);
    }

    private void validateParams(BigDecimal principal, BigDecimal totalRepayment, int loanDays) {
        if (principal == null) {
            throw new BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE, "本金不能为空");
        }
        if (totalRepayment == null) {
            throw new BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE, "总还款额不能为空");
        }
        if (principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE, "本金必须大于零");
        }
        if (loanDays <= 0) {
            throw new BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE, "借款天数必须大于零");
        }
        if (totalRepayment.compareTo(principal) < 0) {
            throw new BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE, "总还款额不能小于本金");
        }
    }

    /**
     * Immutable record for weighted APR calculation inputs.
     */
    public record DebtAprEntry(BigDecimal principal, BigDecimal apr) {}
}
