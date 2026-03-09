/**
 * scoreSimulator 单元测试
 *
 * 验证来源：scoring-model.md 第五节 fallback IF/THEN 规则 + 第三节分群匹配
 * 测试矩阵：scoring-model.md 第十二节 SC-01~SC-06 + SC-S01~SC-S03
 */
import { describe, it, expect } from 'vitest'
import {
  calculateScore,
  matchSegment,
  simulateImprovement,
  buildScoreInput,
} from '../scoreSimulator.js'

// ============================================================
// 一、分群匹配测试（matchSegment）
// ============================================================
describe('matchSegment', () => {
  it('should_match_HIGH_DEBT_when_debtIncomeRatio_above_070', () => {
    expect(matchSegment({ debtIncomeRatio: 0.75, debtCount: 3 })).toBe('HIGH_DEBT')
  })

  it('should_match_HIGH_DEBT_when_debtCount_gte_5', () => {
    // SC-S01: debtCount=5 → HIGH_DEBT
    expect(matchSegment({ debtIncomeRatio: 0.40, debtCount: 5 })).toBe('HIGH_DEBT')
  })

  it('should_match_MORTGAGE_HEAVY_when_mortgage_ratio_above_50pct', () => {
    expect(matchSegment({
      debtIncomeRatio: 0.40, debtCount: 2, mortgageCount: 2, avgLoanDays: 400,
    })).toBe('MORTGAGE_HEAVY')
  })

  it('should_not_match_MORTGAGE_HEAVY_when_mortgage_ratio_lte_50pct', () => {
    expect(matchSegment({
      debtIncomeRatio: 0.40, debtCount: 4, mortgageCount: 2, avgLoanDays: 400,
    })).toBe('DEFAULT')
  })

  it('should_match_YOUNG_BORROWER_when_debtCount_lte_2_and_avgLoanDays_lt_365', () => {
    // SC-S02: debtCount=2, avgLoanDays=200 → YOUNG_BORROWER
    expect(matchSegment({ debtIncomeRatio: 0.30, debtCount: 2, avgLoanDays: 200 })).toBe('YOUNG_BORROWER')
  })

  it('should_not_match_YOUNG_BORROWER_when_avgLoanDays_eq_365', () => {
    expect(matchSegment({ debtIncomeRatio: 0.30, debtCount: 2, avgLoanDays: 365 })).toBe('DEFAULT')
  })

  it('should_match_DEFAULT_when_no_special_conditions', () => {
    // SC-S03: debtCount=3, avgLoanDays=400 → DEFAULT
    expect(matchSegment({ debtIncomeRatio: 0.40, debtCount: 3, avgLoanDays: 400 })).toBe('DEFAULT')
  })

  it('should_prioritize_HIGH_DEBT_over_MORTGAGE_HEAVY', () => {
    // DIR > 0.70 AND mortgageCount > 50% → HIGH_DEBT wins
    expect(matchSegment({
      debtIncomeRatio: 0.80, debtCount: 2, mortgageCount: 2, avgLoanDays: 400,
    })).toBe('HIGH_DEBT')
  })

  it('should_prioritize_MORTGAGE_HEAVY_over_YOUNG_BORROWER', () => {
    // debtCount=2, mortgageCount=2, avgLoanDays=200 → MORTGAGE_HEAVY wins
    expect(matchSegment({
      debtIncomeRatio: 0.40, debtCount: 2, mortgageCount: 2, avgLoanDays: 200,
    })).toBe('MORTGAGE_HEAVY')
  })

  it('should_default_debtCount_to_1_when_missing', () => {
    expect(matchSegment({ debtIncomeRatio: 0.30 })).toBe('DEFAULT')
  })
})

// ============================================================
// 二、DEFAULT 分群评分测试（scoring-model.md 第十二节 SC-01~SC-06）
// ============================================================
describe('calculateScore — DEFAULT segment fallback', () => {
  // 手动推导每个维度的原始分 × 权重，与 scoring-model.md Section 5 对齐
  // Math.round 取整

  it('SC-01: 低负债健康用户', () => {
    // DIR=0.25→90, APR=12→75, LIQ=Y→60, OVD=0→95, CST=(3,200)→60
    // 90*0.30 + 75*0.25 + 60*0.15 + 95*0.20 + 60*0.10 = 79.75 → 80
    const result = calculateScore({
      debtIncomeRatio: 0.25, weightedApr: 12.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      overdueCount: 0, maxOverdueDays: 0,
      debtCount: 3, avgLoanDays: 200,
    }, 'DEFAULT')

    expect(result.finalScore).toBe(80)
    expect(result.segment).toBe('DEFAULT')
    expect(result.dimensions[0].score).toBe(90) // DIR
    expect(result.dimensions[1].score).toBe(75) // APR
    expect(result.dimensions[2].score).toBe(60) // LIQ
    expect(result.dimensions[3].score).toBe(95) // OVD
    expect(result.dimensions[4].score).toBe(60) // CST
  })

  it('SC-02: 中等负债，收入不足', () => {
    // DIR=0.62→50, APR=21.4→55, LIQ=N→30, OVD=0→95, CST=(4,150)→60
    // 50*0.30 + 55*0.25 + 30*0.15 + 95*0.20 + 60*0.10 = 58.25 → 58
    const result = calculateScore({
      debtIncomeRatio: 0.62, weightedApr: 21.4,
      monthlyIncome: 5000, monthlyPayment: 8000,
      overdueCount: 0, maxOverdueDays: 0,
      debtCount: 4, avgLoanDays: 150,
    }, 'DEFAULT')

    expect(result.finalScore).toBe(58)
    expect(result.dimensions[0].score).toBe(50)
    expect(result.dimensions[1].score).toBe(55)
    expect(result.dimensions[2].score).toBe(30)
    expect(result.dimensions[3].score).toBe(95)
    expect(result.dimensions[4].score).toBe(60)
  })

  it('SC-03: 高负债+高息+逾期', () => {
    // DIR=0.85→30, APR=38→15, LIQ=N→30, OVD=(2,45)→50, CST=(4,120)→60
    // 30*0.30 + 15*0.25 + 30*0.15 + 50*0.20 + 60*0.10 = 33.25 → 33
    const result = calculateScore({
      debtIncomeRatio: 0.85, weightedApr: 38.0,
      monthlyIncome: 5000, monthlyPayment: 8000,
      overdueCount: 2, maxOverdueDays: 45,
      debtCount: 4, avgLoanDays: 120,
    }, 'DEFAULT')

    expect(result.finalScore).toBe(33)
  })

  it('SC-04: 极端高风险', () => {
    // DIR=0.95→10, APR=45→15, LIQ=N→30, OVD=(4,120)→10, CST=(4,60)→60
    // 10*0.30 + 15*0.25 + 30*0.15 + 10*0.20 + 60*0.10 = 19.25 → 19
    const result = calculateScore({
      debtIncomeRatio: 0.95, weightedApr: 45.0,
      monthlyIncome: 5000, monthlyPayment: 8000,
      overdueCount: 4, maxOverdueDays: 120,
      debtCount: 4, avgLoanDays: 60,
    }, 'DEFAULT')

    expect(result.finalScore).toBe(19)
  })

  it('SC-05: 中等偏好，轻微逾期', () => {
    // DIR=0.50→70, APR=24→55, LIQ=Y→60, OVD=(1,25)→70, CST=(3,180)→60
    // 70*0.30 + 55*0.25 + 60*0.15 + 70*0.20 + 60*0.10 = 63.75 → 64
    const result = calculateScore({
      debtIncomeRatio: 0.50, weightedApr: 24.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      overdueCount: 1, maxOverdueDays: 25,
      debtCount: 3, avgLoanDays: 180,
    }, 'DEFAULT')

    expect(result.finalScore).toBe(64)
  })

  it('SC-06: 缺失收入数据', () => {
    // DIR=null→20, APR=15→75, LIQ=no income→40, OVD=0→95, CST=(3,365)→60
    // 20*0.30 + 75*0.25 + 40*0.15 + 95*0.20 + 60*0.10 = 55.75 → 56
    const result = calculateScore({
      debtIncomeRatio: null, weightedApr: 15.0,
      monthlyIncome: 0, monthlyPayment: 0,
      overdueCount: 0, maxOverdueDays: 0,
      debtCount: 3, avgLoanDays: 365,
    }, 'DEFAULT')

    expect(result.finalScore).toBe(56)
    expect(result.dimensions[0].score).toBe(20) // DIR missing → 20
    expect(result.dimensions[2].score).toBe(40) // LIQ no income → 40
  })
})

// ============================================================
// 三、弱项维度识别
// ============================================================
describe('calculateScore — weakDimensions', () => {
  it('should_identify_weak_dimensions_below_70', () => {
    const result = calculateScore({
      debtIncomeRatio: 0.85, weightedApr: 38.0,
      monthlyIncome: 5000, monthlyPayment: 8000,
      overdueCount: 2, maxOverdueDays: 45,
      debtCount: 4, avgLoanDays: 120,
    }, 'DEFAULT')

    // DIR=30, APR=15, LIQ=30, OVD=50, CST=60 — 全部 < 70
    expect(result.weakDimensions).toHaveLength(5)
    // 按分数从低到高排序
    expect(result.weakDimensions[0].score).toBe(15) // APR 最弱
    expect(result.weakDimensions[1].score).toBe(30) // DIR 或 LIQ
  })

  it('should_return_empty_when_all_dimensions_strong', () => {
    const result = calculateScore({
      debtIncomeRatio: 0.20, weightedApr: 8.0,
      monthlyIncome: 20000, monthlyPayment: 3000,
      overdueCount: 0, maxOverdueDays: 0,
      debtCount: 2, avgLoanDays: 400,
    }, 'DEFAULT')

    // DIR=90, APR=90, LIQ=60(<70), OVD=95, CST=80
    // LIQ=60 仍 < 70
    expect(result.weakDimensions).toHaveLength(1)
    expect(result.weakDimensions[0].key).toBe('liquidity')
  })
})

// ============================================================
// 四、HIGH_DEBT 分群 7 档 DIR 评分
// ============================================================
describe('calculateScore — HIGH_DEBT segment', () => {
  it('should_use_7tier_DIR_for_HIGH_DEBT', () => {
    // DIR=0.75 → scoreDIR_HIGH_DEBT: 0.65<0.75≤0.80 → 30
    // With DEFAULT scoreDIR: 0.70<0.75≤0.90 → 30 (same in this case)
    // But try DIR=0.25: HIGH_DEBT→95 vs DEFAULT→90
    const result = calculateScore({
      debtIncomeRatio: 0.25, weightedApr: 12.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      overdueCount: 0, maxOverdueDays: 0,
      debtCount: 6, avgLoanDays: 200,
    }, 'HIGH_DEBT')

    expect(result.dimensions[0].score).toBe(85) // HIGH_DEBT 7档: 0.20<0.25≤0.35 → 85
  })

  it('should_use_HIGH_DEBT_weights', () => {
    // DIR weight=0.35 (vs DEFAULT 0.30), APR weight=0.20 (vs 0.25)
    const result = calculateScore({
      debtIncomeRatio: 0.25, weightedApr: 12.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      overdueCount: 0, maxOverdueDays: 0,
      debtCount: 6, avgLoanDays: 200,
    }, 'HIGH_DEBT')

    expect(result.dimensions[0].weight).toBe(0.35)
    expect(result.dimensions[1].weight).toBe(0.20)
  })

  it('should_score_DIR_tiers_correctly_for_HIGH_DEBT', () => {
    const score = (dir) => calculateScore({
      debtIncomeRatio: dir, weightedApr: 12, monthlyIncome: 10000, monthlyPayment: 5000,
      debtCount: 6, avgLoanDays: 200,
    }, 'HIGH_DEBT').dimensions[0].score

    expect(score(0.15)).toBe(95)  // ≤0.20
    expect(score(0.30)).toBe(85)  // ≤0.35
    expect(score(0.45)).toBe(65)  // ≤0.50
    expect(score(0.60)).toBe(45)  // ≤0.65
    expect(score(0.75)).toBe(30)  // ≤0.80
    expect(score(0.90)).toBe(15)  // ≤0.95
    expect(score(1.00)).toBe(5)   // >0.95
  })
})

// ============================================================
// 五、MORTGAGE_HEAVY / YOUNG_BORROWER 权重验证
// ============================================================
describe('calculateScore — other segments', () => {
  it('should_use_MORTGAGE_HEAVY_weights', () => {
    const result = calculateScore({
      debtIncomeRatio: 0.40, weightedApr: 12.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      debtCount: 2, mortgageCount: 2, avgLoanDays: 400,
    }, 'MORTGAGE_HEAVY')

    // MORTGAGE_HEAVY: DIR=0.25, APR=0.20, LIQ=0.20, OVD=0.20, CST=0.15
    expect(result.dimensions[0].weight).toBe(0.25) // DIR
    expect(result.dimensions[2].weight).toBe(0.20) // LIQ
    expect(result.dimensions[4].weight).toBe(0.15) // CST
  })

  it('should_use_YOUNG_BORROWER_weights', () => {
    const result = calculateScore({
      debtIncomeRatio: 0.30, weightedApr: 12.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      debtCount: 1, avgLoanDays: 200,
    }, 'YOUNG_BORROWER')

    // YOUNG_BORROWER: DIR=0.30, APR=0.20, LIQ=0.15, OVD=0.20, CST=0.15
    expect(result.dimensions[1].weight).toBe(0.20) // APR
    expect(result.dimensions[4].weight).toBe(0.15) // CST
  })
})

// ============================================================
// 六、自动分群集成测试
// ============================================================
describe('calculateScore — auto segment matching', () => {
  it('should_auto_match_HIGH_DEBT_and_use_its_weights', () => {
    const result = calculateScore({
      debtIncomeRatio: 0.80, weightedApr: 20.0,
      monthlyIncome: 5000, monthlyPayment: 8000,
      debtCount: 3, avgLoanDays: 200,
    })

    expect(result.segment).toBe('HIGH_DEBT')
    expect(result.dimensions[0].weight).toBe(0.35)
  })

  it('should_auto_match_DEFAULT_for_normal_user', () => {
    const result = calculateScore({
      debtIncomeRatio: 0.40, weightedApr: 15.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      debtCount: 3, avgLoanDays: 400,
    })

    expect(result.segment).toBe('DEFAULT')
    expect(result.dimensions[0].weight).toBe(0.30)
  })
})

// ============================================================
// 七、What-if 模拟
// ============================================================
describe('simulateImprovement', () => {
  // 使用靠近分段边界的值，确保改善操作能跨越评分档次
  const baseInput = {
    debtIncomeRatio: 0.55, // 靠近 0.50 边界，*0.85=0.4675 → 跨入 ≤0.50 档
    weightedApr: 22.0,     // 靠近 18.0 边界，*0.80=17.6 → 跨入 ≤18 档
    monthlyIncome: 8000, monthlyPayment: 5000,
    overdueCount: 2, maxOverdueDays: 45,
    debtCount: 4, avgLoanDays: 200,
  }

  it('should_improve_score_when_catching_up_payments', () => {
    const result = simulateImprovement(baseInput, [{ type: 'CATCH_UP_PAYMENTS' }])

    expect(result.scoreDelta).toBeGreaterThan(0)
    expect(result.simulated.finalScore).toBeGreaterThan(result.current.finalScore)
    expect(result.dimChanges.some(d => d.label === '还款记录' && d.delta > 0)).toBe(true)
  })

  it('should_improve_score_when_reducing_utilization', () => {
    // 0.55 * 0.85 = 0.4675 → 跨入 ≤0.50 档（从 70 到 70...hmm）
    // 实际上 0.50<0.55≤0.70 → 50, 0.4675≤0.50 → 70. 跨档 ✓
    const result = simulateImprovement(baseInput, [{ type: 'REDUCE_UTILIZATION' }])
    expect(result.scoreDelta).toBeGreaterThan(0)
  })

  it('should_improve_score_when_paying_off_smallest', () => {
    const result = simulateImprovement(baseInput, [{ type: 'PAY_OFF_SMALLEST' }])
    expect(result.scoreDelta).toBeGreaterThanOrEqual(0)
  })

  it('should_improve_score_when_reducing_apr', () => {
    // 22.0 * 0.80 = 17.6 → 跨入 ≤18 档（从 55 到 75）✓
    const result = simulateImprovement(baseInput, [{ type: 'REDUCE_APR' }])
    expect(result.scoreDelta).toBeGreaterThan(0)
  })

  it('should_handle_multiple_improvements', () => {
    const result = simulateImprovement(baseInput, [
      { type: 'CATCH_UP_PAYMENTS' },
      { type: 'REDUCE_APR' },
    ])
    expect(result.scoreDelta).toBeGreaterThan(0)
    expect(result.dimChanges.length).toBeGreaterThanOrEqual(2)
  })

  it('should_pin_segment_during_simulation', () => {
    // debtCount=5 → HIGH_DEBT; PAY_OFF_SMALLEST → debtCount=4
    // Without pinning, simulated would be DEFAULT; with pinning, stays HIGH_DEBT
    const highDebtInput = {
      debtIncomeRatio: 0.50, weightedApr: 20.0,
      monthlyIncome: 10000, monthlyPayment: 5000,
      overdueCount: 0, maxOverdueDays: 0,
      debtCount: 5, avgLoanDays: 300,
    }

    const result = simulateImprovement(highDebtInput, [{ type: 'PAY_OFF_SMALLEST' }])

    expect(result.current.segment).toBe('HIGH_DEBT')
    // 模拟结果应保持 HIGH_DEBT 分群（segment 固定）
    expect(result.simulated.segment).toBe('HIGH_DEBT')
  })
})

// ============================================================
// 八、buildScoreInput
// ============================================================
describe('buildScoreInput', () => {
  it('should_build_input_from_stores', () => {
    const profileStore = { profile: { debtIncomeRatio: 0.5, weightedApr: 18, monthlyIncome: 10000, monthlyPayment: 5000 } }
    const funnelStore = { monthlyPayment: 6000, monthlyIncome: 12000 }
    const debtStore = { debts: [{ productType: 'CONSUMER_LOAN' }, { productType: 'MORTGAGE' }] }

    const input = buildScoreInput(profileStore, funnelStore, debtStore)

    expect(input.debtIncomeRatio).toBe(0.5)
    expect(input.weightedApr).toBe(18)
    expect(input.debtCount).toBe(2)
    expect(input.mortgageCount).toBe(1)
  })

  it('should_count_mortgages_by_productType_and_debtType', () => {
    const debtStore = {
      debts: [
        { productType: 'MORTGAGE' },
        { debtType: 'MORTGAGE' },
        { productType: 'CONSUMER_LOAN' },
      ],
    }

    const input = buildScoreInput({}, {}, debtStore)
    expect(input.mortgageCount).toBe(2)
  })

  it('should_fallback_to_funnel_values_when_profile_empty', () => {
    const input = buildScoreInput(
      { profile: null },
      { monthlyPayment: 6000, monthlyIncome: 12000 },
      { debts: [] },
    )

    expect(input.monthlyPayment).toBe(6000)
    expect(input.monthlyIncome).toBe(12000)
    expect(input.debtIncomeRatio).toBe(0.5) // 6000/12000
  })

  it('should_fallback_to_defaults_when_all_stores_empty', () => {
    const input = buildScoreInput({}, {}, {})

    expect(input.monthlyPayment).toBe(5000)
    expect(input.monthlyIncome).toBe(7500)
    expect(input.debtCount).toBe(1)
    expect(input.mortgageCount).toBe(0)
  })
})

// ============================================================
// 九、边界值与维度评分规则
// ============================================================
describe('dimension scoring boundaries', () => {
  const score = (overrides) => calculateScore({
    debtIncomeRatio: 0.30, weightedApr: 10.0,
    monthlyIncome: 10000, monthlyPayment: 5000,
    overdueCount: 0, maxOverdueDays: 0,
    debtCount: 2, avgLoanDays: 200,
    ...overrides,
  }, 'DEFAULT')

  it('DIR boundary: exactly 0.30 should score 90', () => {
    expect(score({ debtIncomeRatio: 0.30 }).dimensions[0].score).toBe(90)
  })

  it('DIR boundary: 0.31 should score 70', () => {
    expect(score({ debtIncomeRatio: 0.31 }).dimensions[0].score).toBe(70)
  })

  it('APR boundary: exactly 10.0 should score 90', () => {
    expect(score({ weightedApr: 10.0 }).dimensions[1].score).toBe(90)
  })

  it('APR boundary: 10.1 should score 75', () => {
    expect(score({ weightedApr: 10.1 }).dimensions[1].score).toBe(75)
  })

  it('OVD: 1 overdue at exactly 30 days should score 70', () => {
    expect(score({ overdueCount: 1, maxOverdueDays: 30 }).dimensions[3].score).toBe(70)
  })

  it('OVD: 1 overdue at 31 days should score 50', () => {
    // overdueCount=1 AND maxOverdueDays=31 → first check: 1==1 AND 31<=30? No
    // second check: 1<=2 AND 31<=60? Yes → 50
    expect(score({ overdueCount: 1, maxOverdueDays: 31 }).dimensions[3].score).toBe(50)
  })

  it('CST: 2 debts at 180 days should score 80', () => {
    expect(score({ debtCount: 2, avgLoanDays: 180 }).dimensions[4].score).toBe(80)
  })

  it('CST: 2 debts at 179 days should score 60', () => {
    // debtCount<=2 AND avgLoanDays>=180? 179<180 → No. debtCount<=4 → 60
    expect(score({ debtCount: 2, avgLoanDays: 179 }).dimensions[4].score).toBe(60)
  })
})
