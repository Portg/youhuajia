package com.youhua.engine.scoring.pmml;

import com.youhua.engine.scoring.ScoringEngine.ScoreInput;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

/**
 * Matches a user's ScoreInput to a UserSegment.
 * Priority: HIGH_DEBT > MORTGAGE_HEAVY > YOUNG_BORROWER > DEFAULT.
 */
@Slf4j
@Component
public class UserSegmentMatcher {

    private static final BigDecimal HIGH_DEBT_RATIO_THRESHOLD = new BigDecimal("0.70");
    private static final int HIGH_DEBT_COUNT_THRESHOLD = 5;
    private static final int YOUNG_BORROWER_MAX_DEBT_COUNT = 2;
    private static final int YOUNG_BORROWER_MAX_AVG_LOAN_DAYS = 365;
    private static final BigDecimal MORTGAGE_HEAVY_RATIO_THRESHOLD = new BigDecimal("0.50");

    /**
     * Match the user to a segment based on ScoreInput.
     *
     * @param input scoring input
     * @param mortgageRatio ratio of MORTGAGE debts (0.0-1.0), null if unknown
     * @return matched segment
     */
    public UserSegment match(ScoreInput input, BigDecimal mortgageRatio) {
        // Priority 1: HIGH_DEBT
        if (isHighDebt(input)) {
            log.debug("Segment matched: HIGH_DEBT");
            return UserSegment.HIGH_DEBT;
        }

        // Priority 2: MORTGAGE_HEAVY
        if (mortgageRatio != null && mortgageRatio.compareTo(MORTGAGE_HEAVY_RATIO_THRESHOLD) > 0) {
            log.debug("Segment matched: MORTGAGE_HEAVY, mortgageRatio={}", mortgageRatio);
            return UserSegment.MORTGAGE_HEAVY;
        }

        // Priority 3: YOUNG_BORROWER
        if (isYoungBorrower(input)) {
            log.debug("Segment matched: YOUNG_BORROWER");
            return UserSegment.YOUNG_BORROWER;
        }

        log.debug("Segment matched: DEFAULT");
        return UserSegment.DEFAULT;
    }

    /**
     * Simplified match without mortgage ratio info.
     */
    public UserSegment match(ScoreInput input) {
        return match(input, null);
    }

    private boolean isHighDebt(ScoreInput input) {
        if (input.debtCount() >= HIGH_DEBT_COUNT_THRESHOLD) {
            return true;
        }
        if (input.monthlyIncome() != null && input.monthlyIncome().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal ratio = input.monthlyPayment()
                    .divide(input.monthlyIncome(), 10, RoundingMode.HALF_UP);
            return ratio.compareTo(HIGH_DEBT_RATIO_THRESHOLD) > 0;
        }
        return false;
    }

    private boolean isYoungBorrower(ScoreInput input) {
        return input.debtCount() <= YOUNG_BORROWER_MAX_DEBT_COUNT
                && input.avgLoanDays() < YOUNG_BORROWER_MAX_AVG_LOAN_DAYS;
    }
}
