package com.youhua.common.util;

import com.youhua.profile.entity.IncomeRecord;

import java.math.BigDecimal;
import java.util.List;

/**
 * Shared income calculation utility.
 * Uses primary income records if any exist, otherwise sums all records.
 */
public final class IncomeCalculator {

    private IncomeCalculator() {}

    /**
     * Calculate total monthly income from income records.
     * Prefers primary records; falls back to all records if no primary exists.
     *
     * @param records income records
     * @return total monthly income, or null if records is empty
     */
    public static BigDecimal sumMonthlyIncome(List<IncomeRecord> records) {
        if (records == null || records.isEmpty()) {
            return null;
        }

        List<IncomeRecord> primaryRecords = records.stream()
                .filter(r -> Boolean.TRUE.equals(r.getPrimary()))
                .toList();

        List<IncomeRecord> toSum = primaryRecords.isEmpty() ? records : primaryRecords;
        return toSum.stream()
                .filter(r -> r.getAmount() != null)
                .map(IncomeRecord::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
