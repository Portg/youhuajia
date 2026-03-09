/**
 * formatters 工具函数单元测试
 * 覆盖：formatMoney / formatMoneyInteger / formatRate / formatMoneyShort / formatDate
 */
import { describe, it, expect } from 'vitest'
import {
  formatMoney,
  formatMoneyInteger,
  formatRate,
  formatMoneyShort,
  formatDate,
} from '../formatters.js'

// ============================================================
// formatMoney
// ============================================================
describe('formatMoney', () => {
  it('should_format_normal_amount', () => {
    expect(formatMoney(82400)).toBe('¥82,400.00')
  })

  it('should_format_zero', () => {
    expect(formatMoney(0)).toBe('¥0.00')
  })

  it('should_format_string_input', () => {
    expect(formatMoney('12345.6')).toBe('¥12,345.60')
  })

  it('should_return_default_when_NaN', () => {
    expect(formatMoney('abc')).toBe('¥0.00')
    expect(formatMoney(undefined)).toBe('¥0.00')
    expect(formatMoney(null)).toBe('¥0.00')
  })

  it('should_respect_custom_decimals', () => {
    expect(formatMoney(1234.567, 0)).toBe('¥1,235')
    expect(formatMoney(1234.567, 4)).toBe('¥1,234.5670')
  })

  it('should_format_negative_amount', () => {
    expect(formatMoney(-500)).toBe('¥-500.00')
  })
})

// ============================================================
// formatMoneyInteger
// ============================================================
describe('formatMoneyInteger', () => {
  it('should_format_without_decimals', () => {
    expect(formatMoneyInteger(82400.55)).toBe('¥82,401')
  })

  it('should_return_default_when_NaN', () => {
    expect(formatMoneyInteger('abc')).toBe('¥0')
  })

  it('should_round_correctly', () => {
    expect(formatMoneyInteger(99.4)).toBe('¥99')
    expect(formatMoneyInteger(99.5)).toBe('¥100')
  })
})

// ============================================================
// formatRate
// ============================================================
describe('formatRate', () => {
  it('should_format_percent_value', () => {
    expect(formatRate(24)).toBe('24.0%')
  })

  it('should_format_decimal_value', () => {
    expect(formatRate(0.24, false)).toBe('24.0%')
  })

  it('should_return_default_when_NaN', () => {
    expect(formatRate('abc')).toBe('0.0%')
  })

  it('should_handle_zero', () => {
    expect(formatRate(0)).toBe('0.0%')
  })

  it('should_handle_high_precision', () => {
    expect(formatRate(12.345)).toBe('12.3%')
  })
})

// ============================================================
// formatMoneyShort
// ============================================================
describe('formatMoneyShort', () => {
  it('should_convert_to_wan_when_gte_10000', () => {
    expect(formatMoneyShort(82400)).toBe('8.24万')
  })

  it('should_keep_number_when_lt_10000', () => {
    expect(formatMoneyShort(9999)).toBe('9,999')
  })

  it('should_return_default_when_NaN', () => {
    expect(formatMoneyShort('abc')).toBe('0')
  })

  it('should_handle_exactly_10000', () => {
    expect(formatMoneyShort(10000)).toBe('1.00万')
  })

  it('should_handle_negative_large_amount', () => {
    expect(formatMoneyShort(-50000)).toBe('-5.00万')
  })
})

// ============================================================
// formatDate
// ============================================================
describe('formatDate', () => {
  it('should_format_date_object', () => {
    expect(formatDate(new Date(2026, 2, 9))).toBe('2026-03-09')
  })

  it('should_format_date_string', () => {
    expect(formatDate('2026-01-15')).toBe('2026-01-15')
  })

  it('should_return_empty_for_null', () => {
    expect(formatDate(null)).toBe('')
    expect(formatDate(undefined)).toBe('')
    expect(formatDate('')).toBe('')
  })

  it('should_return_empty_for_invalid_date', () => {
    expect(formatDate('not-a-date')).toBe('')
  })

  it('should_pad_month_and_day', () => {
    expect(formatDate(new Date(2026, 0, 5))).toBe('2026-01-05')
  })
})
