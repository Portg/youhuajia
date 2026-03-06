/**
 * 预审通过概率估算器
 * 基于用户评分、债务结构、收入情况等维度，按经验策略估算预审通过概率
 * 类似后端 ScoringEngine 的规则策略，纯前端计算，不调用大模型
 *
 * 概率范围：35% - 92%（不给极端值避免误导）
 * 基准概率 = 50，各维度加减分
 */

// ============ 规则配置（可运维调整） ============

const RULES = {
  // 基准概率
  baseProbability: 50,

  // 评分维度：优化评分越高，通过概率越高
  score: {
    thresholds: [
      { min: 80, delta: +20 },
      { min: 60, delta: +10 },
      { min: 40, delta: 0 },
      { min: 0, delta: -10 },
    ],
  },

  // 负债收入比维度
  debtIncomeRatio: {
    thresholds: [
      { max: 0.3, delta: +10 },
      { max: 0.5, delta: +5 },
      { max: 0.7, delta: 0 },
      { max: 1.0, delta: -5 },
      { max: Infinity, delta: -10 },
    ],
  },

  // 逾期情况
  overdue: {
    noOverdue: +8,
    hasOverdue: -12,
  },

  // 债务笔数
  debtCount: {
    thresholds: [
      { max: 2, delta: +5 },
      { max: 4, delta: 0 },
      { max: 6, delta: -5 },
      { max: Infinity, delta: -10 },
    ],
  },

  // 高利率债务占比（APR > 24%）
  highAprRatio: {
    thresholds: [
      { max: 0, delta: +5 },
      { max: 0.3, delta: 0 },
      { max: 0.6, delta: -3 },
      { max: 1.0, delta: -8 },
    ],
  },

  // 概率上下限
  minProbability: 35,
  maxProbability: 92,
}

// ============ 建议模板（按条件触发） ============

const SUGGESTION_RULES = [
  {
    condition: (ctx) => ctx.hasOverdue,
    text: '优先处理逾期债务，逾期记录是影响审批的关键因素',
  },
  {
    condition: (ctx) => ctx.highAprRatio > 0.3,
    text: '建议先偿还或协商高利率债务（年化>24%），降低综合负债成本',
  },
  {
    condition: (ctx) => ctx.debtIncomeRatio > 0.7,
    text: '月供占收入比例偏高，增加收入证明或降低月供可提高通过率',
  },
  {
    condition: (ctx) => ctx.debtCount > 4,
    text: '负债笔数较多，整合债务后再申请效果更好',
  },
  {
    condition: (ctx) => ctx.score >= 70,
    text: '你的优化评分较高，保持当前良好状态继续推进',
  },
  {
    condition: (ctx) => ctx.debtIncomeRatio <= 0.5 && !ctx.hasOverdue,
    text: '收入覆盖能力好且无逾期，准备好材料后通过率较高',
  },
  {
    condition: (ctx) => ctx.score < 50,
    text: '建议先完成 30 天改善计划，提升评分后再申请',
  },
]

// ============ 核心计算 ============

/**
 * 估算预审通过概率
 * @param {Object} params
 * @param {number} params.score - 优化评分 0-100
 * @param {number} params.monthlyPayment - 月供
 * @param {number} params.monthlyIncome - 月收入
 * @param {Array} params.debts - 债务列表
 * @returns {{ probability: number, suggestions: string[] }}
 */
export function estimatePreAudit({ score = 0, monthlyPayment = 0, monthlyIncome = 0, debts = [] }) {
  const activeDebts = debts.filter(d => d.status === 'CONFIRMED' || d.status === 'IN_PROFILE')
  const debtCount = activeDebts.length
  const hasOverdue = activeDebts.some(d => d.overdue || d.overdueAmount > 0 || d.overdueDays > 0)
  const debtIncomeRatio = monthlyIncome > 0 ? monthlyPayment / monthlyIncome : 1
  const highAprDebts = activeDebts.filter(d => Number(d.apr) > 24)
  const highAprRatio = debtCount > 0 ? highAprDebts.length / debtCount : 0

  let probability = RULES.baseProbability

  // 评分维度
  for (const t of RULES.score.thresholds) {
    if (score >= t.min) { probability += t.delta; break }
  }

  // 负债收入比
  for (const t of RULES.debtIncomeRatio.thresholds) {
    if (debtIncomeRatio <= t.max) { probability += t.delta; break }
  }

  // 逾期
  probability += hasOverdue ? RULES.overdue.hasOverdue : RULES.overdue.noOverdue

  // 债务笔数
  for (const t of RULES.debtCount.thresholds) {
    if (debtCount <= t.max) { probability += t.delta; break }
  }

  // 高利率占比
  for (const t of RULES.highAprRatio.thresholds) {
    if (highAprRatio <= t.max) { probability += t.delta; break }
  }

  // 限制范围
  probability = Math.max(RULES.minProbability, Math.min(RULES.maxProbability, probability))

  // 生成建议
  const ctx = { score, debtIncomeRatio, hasOverdue, debtCount, highAprRatio }
  const suggestions = SUGGESTION_RULES
    .filter(r => r.condition(ctx))
    .slice(0, 3) // 最多3条
    .map(r => r.text)

  // 兜底：至少给一条建议
  if (suggestions.length === 0) {
    suggestions.push('准备详细的收入证明和还款记录可提高通过率')
  }

  return { probability, suggestions }
}
