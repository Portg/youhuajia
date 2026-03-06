package com.youhua.engine.rules;

import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.enums.DebtSourceType;
import com.youhua.debt.enums.OverdueStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Business rule engine — validates data integrity, value reasonableness,
 * business logic, and anti-fraud rules.
 * Rules run in order: DATA → VALUE → BIZ → FRAUD.
 * Any BLOCK rule terminates processing immediately.
 */
@Slf4j
@Component
public class RuleEngine {

    /** APR 超过此值（%）触发 HIGH_INTEREST 预警 */
    @Value("${youhua.rules.value.apr-warning-threshold:36.0}")
    private BigDecimal aprWarningThreshold;

    /** APR 超过此值（%）触发 SUSPECTED_FRAUD 拦截 */
    @Value("${youhua.rules.value.apr-max-allowed:10000.0}")
    private BigDecimal aprMaxAllowed;

    /** 月供/月收入超过此值触发 EXTREME_DEBT_RATIO 预警 */
    @Value("${youhua.rules.value.extreme-debt-ratio:0.9}")
    private BigDecimal extremeDebtRatio;

    /** 总负债超过此值（元）触发 HIGH_TOTAL_DEBT 预警 */
    @Value("${youhua.rules.value.high-total-debt:10000000}")
    private BigDecimal highTotalDebt;

    /** OCR 置信度低于此值触发 LOW_CONFIDENCE 预警 */
    @Value("${youhua.rules.value.ocr-low-confidence:70}")
    private BigDecimal ocrLowConfidence;

    /**
     * Evaluate all applicable rules against the provided rule input.
     *
     * @param input rule evaluation input
     * @return rule evaluation result
     * @throws BizException if a BLOCK rule is triggered
     */
    public RuleResult evaluate(RuleInput input) {
        List<RuleResultItem> items = new ArrayList<>();

        // Phase 1: Data integrity rules (BLOCK)
        evaluateDataRules(input, items);
        checkBlocked(items);

        // Phase 2: Value reasonableness rules (BLOCK/WARN)
        evaluateValueRules(input, items);
        checkBlocked(items);

        // Phase 3: Business logic rules (WARN)
        evaluateBizRules(input, items);

        // Phase 4: No fraud rules in unit context (FRAUD rules need DB access)
        // Fraud rule evaluation skipped here — handled at service layer

        List<String> warnings = items.stream()
                .filter(r -> "WARN".equals(r.result()) && r.tag() != null)
                .map(RuleResultItem::tag)
                .toList();

        boolean blocked = items.stream().anyMatch(r -> "BLOCK".equals(r.result()));

        return new RuleResult(items, blocked, warnings, !blocked);
    }

    private void evaluateDataRules(RuleInput input, List<RuleResultItem> items) {
        // DATA_001: at least one confirmed debt
        if (input.confirmedDebtCount() == 0) {
            items.add(new RuleResultItem("DATA_001", "BLOCK", null,
                    "暂无已确认的债务数据，无法生成画像"));
            return;
        }
        items.add(new RuleResultItem("DATA_001", "PASS", null, null));

        // DATA_002: principal > 0
        boolean hasInvalidPrincipal = input.debts().stream()
                .anyMatch(d -> d.principal().compareTo(BigDecimal.ZERO) <= 0);
        if (hasInvalidPrincipal) {
            items.add(new RuleResultItem("DATA_002", "BLOCK", null,
                    "存在本金为零或负数的债务记录"));
            return;
        }
        items.add(new RuleResultItem("DATA_002", "PASS", null, null));

        // DATA_003: loanDays > 0
        boolean hasInvalidDays = input.debts().stream()
                .anyMatch(d -> d.loanDays() <= 0);
        if (hasInvalidDays) {
            items.add(new RuleResultItem("DATA_003", "BLOCK", null,
                    "存在借款天数为零或负数的债务记录"));
            return;
        }
        items.add(new RuleResultItem("DATA_003", "PASS", null, null));

        // DATA_004: totalRepayment >= principal
        boolean hasInvalidRepayment = input.debts().stream()
                .anyMatch(d -> d.totalRepayment().compareTo(d.principal()) < 0);
        if (hasInvalidRepayment) {
            items.add(new RuleResultItem("DATA_004", "BLOCK", null,
                    "存在总还款额小于本金的债务记录"));
            return;
        }
        items.add(new RuleResultItem("DATA_004", "PASS", null, null));
    }

    private void evaluateValueRules(RuleInput input, List<RuleResultItem> items) {
        // VALUE_001: APR max check (BLOCK)
        for (DebtRuleEntry debt : input.debts()) {
            if (debt.apr() != null && debt.apr().compareTo(aprMaxAllowed) > 0) {
                items.add(new RuleResultItem("VALUE_001", "BLOCK", null,
                        "APR 计算结果异常，请核实债务数据"));
                return;
            }
        }
        items.add(new RuleResultItem("VALUE_001", "PASS", null, null));

        // VALUE_002: APR high-interest warning (WARN)
        for (DebtRuleEntry debt : input.debts()) {
            if (debt.apr() != null && debt.apr().compareTo(aprWarningThreshold) > 0) {
                items.add(new RuleResultItem("VALUE_002", "WARN", "HIGH_INTEREST",
                        String.format("债务 %s 实际年化 %.2f%%，属于高息债务",
                                debt.creditor(), debt.apr())));
            }
        }

        // VALUE_003: monthly payment exceeds income
        if (input.monthlyIncome() != null && input.monthlyIncome().compareTo(BigDecimal.ZERO) > 0
                && input.monthlyPayment() != null
                && input.monthlyPayment().compareTo(input.monthlyIncome()) > 0) {
            items.add(new RuleResultItem("VALUE_003", "WARN", "PAYMENT_EXCEED_INCOME",
                    "月供总额已超过月收入，财务压力较大"));
        }

        // VALUE_004: debt income ratio extreme warning
        if (input.monthlyIncome() != null && input.monthlyIncome().compareTo(BigDecimal.ZERO) > 0
                && input.monthlyPayment() != null) {
            BigDecimal ratio = input.monthlyPayment().divide(input.monthlyIncome(), 10, RoundingMode.HALF_UP);
            if (ratio.compareTo(extremeDebtRatio) > 0) {
                items.add(new RuleResultItem("VALUE_004", "WARN", "EXTREME_DEBT_RATIO",
                        "负债收入比超过90%，财务状况需紧急关注"));
            }
        }

        // VALUE_005: high total debt warning
        if (input.totalDebt() != null && input.totalDebt().compareTo(highTotalDebt) > 0) {
            items.add(new RuleResultItem("VALUE_005", "WARN", "HIGH_TOTAL_DEBT",
                    "总负债金额较大，请确认数据准确性"));
        }
    }

    private void evaluateBizRules(RuleInput input, List<RuleResultItem> items) {
        // BIZ_003: urgent overdue
        for (DebtRuleEntry debt : input.debts()) {
            if (OverdueStatus.OVERDUE_60.equals(debt.overdueStatus())
                    || OverdueStatus.OVERDUE_90_PLUS.equals(debt.overdueStatus())) {
                items.add(new RuleResultItem("BIZ_003", "WARN", "URGENT_OVERDUE",
                        "存在严重逾期债务，建议立即处理"));
                break;
            }
        }

        // BIZ_004: OCR low confidence
        for (DebtRuleEntry debt : input.debts()) {
            if (DebtSourceType.OCR.equals(debt.sourceType())
                    && debt.confidenceScore() != null
                    && debt.confidenceScore().compareTo(ocrLowConfidence) < 0) {
                items.add(new RuleResultItem("BIZ_004", "WARN", "LOW_CONFIDENCE",
                        String.format("债务 %s 的OCR识别置信度较低（%.1f%%），建议人工核对",
                                debt.creditor(), debt.confidenceScore())));
            }
        }
    }

    private void checkBlocked(List<RuleResultItem> items) {
        boolean blocked = items.stream().anyMatch(r -> "BLOCK".equals(r.result()));
        if (blocked) {
            String message = items.stream()
                    .filter(r -> "BLOCK".equals(r.result()))
                    .map(RuleResultItem::message)
                    .findFirst()
                    .orElse("Rule engine blocked");
            throw new BizException(ErrorCode.ENGINE_RULE_FAILED, message);
        }
    }

    /**
     * Input for rule evaluation.
     */
    public record RuleInput(
            int confirmedDebtCount,
            List<DebtRuleEntry> debts,
            BigDecimal totalDebt,
            BigDecimal monthlyPayment,
            BigDecimal monthlyIncome
    ) {}

    /**
     * Debt entry for rule evaluation.
     */
    public record DebtRuleEntry(
            String creditor,
            BigDecimal principal,
            BigDecimal totalRepayment,
            int loanDays,
            OverdueStatus overdueStatus,
            DebtSourceType sourceType,
            BigDecimal confidenceScore,
            BigDecimal apr
    ) {}

    /**
     * Single rule result item.
     */
    public record RuleResultItem(
            String ruleId,
            String result,
            String tag,
            String message
    ) {}

    /**
     * Overall rule evaluation result.
     */
    public record RuleResult(
            List<RuleResultItem> ruleResults,
            boolean blocked,
            List<String> warnings,
            boolean canProceed
    ) {}
}
