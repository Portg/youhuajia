/**
 * 客户端评分模拟器
 *
 * 复用 scoring-model.md 第五节 fallback 逻辑（if/else + 加权求和），
 * 不依赖后端，支持离线诊断和 What-if 模拟。
 * 支持 4 种用户分群（DEFAULT / HIGH_DEBT / MORTGAGE_HEAVY / YOUNG_BORROWER），
 * 每种分群有独立权重和评分规则。
 *
 * 输入来源：profileStore.profile + funnelStore + debtStore
 */

// ---- 四组分群权重 ----
// 同步自 ai-spec/engine/strategies/*.meta.yml v1.0
// 修改权重时须同步更新此处，或后续改为构建时从 meta.yml 自动生成

const SEGMENT_WEIGHTS = {
  DEFAULT: {
    debtIncomeRatio: 0.30,
    weightedApr: 0.25,
    liquidity: 0.15,
    overdue: 0.20,
    creditStability: 0.10,
  },
  HIGH_DEBT: {
    debtIncomeRatio: 0.35,
    weightedApr: 0.20,
    liquidity: 0.15,
    overdue: 0.20,
    creditStability: 0.10,
  },
  MORTGAGE_HEAVY: {
    debtIncomeRatio: 0.25,
    weightedApr: 0.20,
    liquidity: 0.20,
    overdue: 0.20,
    creditStability: 0.15,
  },
  YOUNG_BORROWER: {
    debtIncomeRatio: 0.30,
    weightedApr: 0.20,
    liquidity: 0.15,
    overdue: 0.20,
    creditStability: 0.15,
  },
}

// ---- 各维度评分规则 ----

// DEFAULT DIR 评分（5 档）
function scoreDIR(ratio) {
  if (ratio == null || ratio <= 0) return 20
  if (ratio <= 0.30) return 90
  if (ratio <= 0.50) return 70
  if (ratio <= 0.70) return 50
  if (ratio <= 0.90) return 30
  return 10
}

// HIGH_DEBT DIR 评分（7 档细粒度，对应 high-debt.pmml）
function scoreDIR_HIGH_DEBT(ratio) {
  if (ratio == null || ratio <= 0) return 20
  if (ratio <= 0.20) return 95
  if (ratio <= 0.35) return 85
  if (ratio <= 0.50) return 65
  if (ratio <= 0.65) return 45
  if (ratio <= 0.80) return 30
  if (ratio <= 0.95) return 15
  return 5
}

function scoreAPR(apr) {
  if (apr <= 10.0) return 90
  if (apr <= 18.0) return 75
  if (apr <= 24.0) return 55
  if (apr <= 36.0) return 35
  return 15
}

function scoreLIQ(monthlyIncome, monthlyPayment) {
  if (monthlyIncome > 0 && monthlyIncome > monthlyPayment) return 60
  if (monthlyIncome > 0) return 30
  return 40
}

function scoreOVD(overdueCount, maxOverdueDays) {
  if (!overdueCount || overdueCount === 0) return 95
  if (overdueCount === 1 && maxOverdueDays <= 30) return 70
  if (overdueCount <= 2 && maxOverdueDays <= 60) return 50
  if (overdueCount <= 3 && maxOverdueDays <= 90) return 30
  return 10
}

function scoreCST(debtCount, avgLoanDays) {
  if (debtCount <= 2 && avgLoanDays >= 180) return 80
  if (debtCount <= 4) return 60
  if (debtCount <= 6) return 40
  return 20
}

// ---- 维度元数据（label + 分级描述 + 改善提示） ----

const DIMENSION_META = {
  debtIncomeRatio: {
    key: 'debtIncomeRatio',
    label: '月供压力',
    levels: [
      { max: 0.30, desc: '财务状况健康' },
      { max: 0.50, desc: '有一定压力但可控' },
      { max: 0.70, desc: '压力偏大，建议控制新增借贷' },
      { max: 0.90, desc: '月供压力较重，需优先缓解' },
      { max: Infinity, desc: '月供负担较重，建议优先降低支出' },
    ],
    tip: '减少月供或增加收入可提升此项',
  },
  weightedApr: {
    key: 'weightedApr',
    label: '综合利率',
    levels: [
      { max: 10, desc: '利率水平健康' },
      { max: 18, desc: '利率中等' },
      { max: 24, desc: '利率中等偏高' },
      { max: 36, desc: '利率偏高' },
      { max: Infinity, desc: '利率过高，建议优先处理' },
    ],
    tip: '优先偿还高利率负债，探索更低利率方案',
  },
  liquidity: {
    key: 'liquidity',
    label: '资金流动性',
    levels: [
      { condition: 'surplus', desc: '收入覆盖月供，有余力' },
      { condition: 'tight', desc: '月供压力较大，余力不足' },
      { condition: 'unknown', desc: '缺少收入数据' },
    ],
    tip: '增加收入或减少非必要支出可改善此项',
  },
  overdue: {
    key: 'overdue',
    label: '还款记录',
    levels: [
      { max: 0, desc: '无逾期记录' },
      { max: 1, desc: '轻微逾期，及时补齐即可' },
      { max: 2, desc: '有逾期记录，建议尽快补齐' },
      { max: 3, desc: '逾期较多，需优先处理' },
      { max: Infinity, desc: '逾期情况需要重点关注' },
    ],
    tip: '补齐所有逾期款项，保持连续按时还款',
  },
  creditStability: {
    key: 'creditStability',
    label: '信用稳定度',
    levels: [
      { max: 2, desc: '负债少且稳定' },
      { max: 4, desc: '负债笔数适中' },
      { max: 6, desc: '负债笔数偏多' },
      { max: Infinity, desc: '负债笔数过多，建议整合' },
    ],
    tip: '减少负债笔数（优先还清小额账户）可提升稳定度',
  },
}

/**
 * 获取维度的分级描述
 */
function getLevelDesc(dimKey, inputValue) {
  const meta = DIMENSION_META[dimKey]
  if (!meta) return ''

  if (dimKey === 'liquidity') {
    if (inputValue === 'surplus') return meta.levels[0].desc
    if (inputValue === 'tight') return meta.levels[1].desc
    return meta.levels[2].desc
  }

  for (const level of meta.levels) {
    if (inputValue <= level.max) return level.desc
  }
  return meta.levels[meta.levels.length - 1].desc
}

/**
 * 匹配用户分群
 *
 * 优先级：HIGH_DEBT > MORTGAGE_HEAVY > YOUNG_BORROWER > DEFAULT
 * 对应 scoring-model.md 第三节的分群规则。
 *
 * @param {Object} input - 评分输入（含 mortgageCount 用于判断房贷占比）
 * @returns {string} 分群标识
 */
export function matchSegment(input) {
  const debtCount = input.debtCount || 1

  // HIGH_DEBT: debtIncomeRatio > 0.70 OR debtCount >= 5
  if ((input.debtIncomeRatio > 0.70) || (debtCount >= 5)) {
    return 'HIGH_DEBT'
  }

  // MORTGAGE_HEAVY: 房贷笔数占总负债 > 50%
  if (input.mortgageCount > 0 && debtCount > 0 && (input.mortgageCount / debtCount) > 0.50) {
    return 'MORTGAGE_HEAVY'
  }

  // YOUNG_BORROWER: debtCount <= 2 AND avgLoanDays < 365
  if (debtCount <= 2 && (input.avgLoanDays || 365) < 365) {
    return 'YOUNG_BORROWER'
  }

  return 'DEFAULT'
}

/**
 * 主评分函数
 *
 * @param {Object} input
 * @param {number} input.debtIncomeRatio - 负债收入比（0-1+）
 * @param {number} input.weightedApr - 加权年化利率（%）
 * @param {number} input.monthlyIncome - 月收入
 * @param {number} input.monthlyPayment - 月供
 * @param {number} [input.overdueCount=0] - 逾期笔数
 * @param {number} [input.maxOverdueDays=0] - 最大逾期天数
 * @param {number} [input.debtCount=1] - 负债笔数
 * @param {number} [input.avgLoanDays=365] - 平均借贷天数
 * @param {number} [input.mortgageCount=0] - 房贷笔数（用于分群匹配）
 * @param {string} [segment] - 指定分群，不传则自动匹配
 * @returns {{ finalScore, segment, dimensions, weakDimensions }}
 */
export function calculateScore(input, segment) {
  const seg = segment || matchSegment(input)
  const weights = SEGMENT_WEIGHTS[seg] || SEGMENT_WEIGHTS.DEFAULT

  // HIGH_DEBT 使用 7 档细粒度 DIR，其他分群使用 DEFAULT 5 档
  const dir = seg === 'HIGH_DEBT'
    ? scoreDIR_HIGH_DEBT(input.debtIncomeRatio)
    : scoreDIR(input.debtIncomeRatio)
  const apr = scoreAPR(input.weightedApr || 0)
  const liq = scoreLIQ(input.monthlyIncome || 0, input.monthlyPayment || 0)
  const ovd = scoreOVD(input.overdueCount || 0, input.maxOverdueDays || 0)
  const cst = scoreCST(input.debtCount || 1, input.avgLoanDays || 365)

  const finalScore = Math.round(
    dir * weights.debtIncomeRatio +
    apr * weights.weightedApr +
    liq * weights.liquidity +
    ovd * weights.overdue +
    cst * weights.creditStability
  )

  // 流动性的输入值标签
  let liqCondition = 'unknown'
  if (input.monthlyIncome > 0 && input.monthlyIncome > input.monthlyPayment) liqCondition = 'surplus'
  else if (input.monthlyIncome > 0) liqCondition = 'tight'

  const dimensions = [
    {
      ...DIMENSION_META.debtIncomeRatio,
      score: dir,
      weight: weights.debtIncomeRatio,
      weightedScore: Math.round(dir * weights.debtIncomeRatio * 100) / 100,
      inputValue: input.debtIncomeRatio,
      levelDesc: getLevelDesc('debtIncomeRatio', input.debtIncomeRatio),
    },
    {
      ...DIMENSION_META.weightedApr,
      score: apr,
      weight: weights.weightedApr,
      weightedScore: Math.round(apr * weights.weightedApr * 100) / 100,
      inputValue: input.weightedApr,
      levelDesc: getLevelDesc('weightedApr', input.weightedApr),
    },
    {
      ...DIMENSION_META.liquidity,
      score: liq,
      weight: weights.liquidity,
      weightedScore: Math.round(liq * weights.liquidity * 100) / 100,
      inputValue: liqCondition,
      levelDesc: getLevelDesc('liquidity', liqCondition),
    },
    {
      ...DIMENSION_META.overdue,
      score: ovd,
      weight: weights.overdue,
      weightedScore: Math.round(ovd * weights.overdue * 100) / 100,
      inputValue: input.overdueCount || 0,
      levelDesc: getLevelDesc('overdue', input.overdueCount || 0),
    },
    {
      ...DIMENSION_META.creditStability,
      score: cst,
      weight: weights.creditStability,
      weightedScore: Math.round(cst * weights.creditStability * 100) / 100,
      inputValue: input.debtCount || 1,
      levelDesc: getLevelDesc('creditStability', input.debtCount || 1),
    },
  ]

  // 弱项维度：原始分 < 70 的，按分数从低到高
  const weakDimensions = dimensions
    .filter(d => d.score < 70)
    .sort((a, b) => a.score - b.score)

  return { finalScore, segment: seg, dimensions, weakDimensions }
}

/**
 * What-if 模拟：模拟用户执行改善操作后的评分变化
 *
 * @param {Object} input - 同 calculateScore 的 input
 * @param {Array<{type: string}>} improvements - 改善操作列表
 * @returns {{ current, simulated, scoreDelta, dimChanges }}
 */
export function simulateImprovement(input, improvements = []) {
  const current = calculateScore(input)
  const modified = { ...input }

  for (const imp of improvements) {
    switch (imp.type) {
      case 'CATCH_UP_PAYMENTS':
        // 补齐逾期
        modified.overdueCount = 0
        modified.maxOverdueDays = 0
        break
      case 'REDUCE_UTILIZATION':
        // 降低使用率 → 负债收入比改善约 15%
        modified.debtIncomeRatio = Math.max(0.1, (modified.debtIncomeRatio || 0) * 0.85)
        break
      case 'PAY_OFF_SMALLEST':
        // 还清最小额账户 → 减少负债笔数
        modified.debtCount = Math.max(1, (modified.debtCount || 1) - 1)
        break
      case 'REDUCE_APR':
        // 置换高利率负债 → APR 改善约 20%
        modified.weightedApr = Math.max(6, (modified.weightedApr || 24) * 0.80)
        break
    }
  }

  // 固定使用当前分群评估模拟结果，避免改善操作导致分群切换时
  // dimChanges 混入权重变化（如 debtCount 从 5→4 导致 HIGH_DEBT→DEFAULT）
  const simulated = calculateScore(modified, current.segment)

  // 每个维度的变化量
  const dimChanges = simulated.dimensions.map((dim, i) => ({
    label: dim.label,
    before: current.dimensions[i].score,
    after: dim.score,
    delta: dim.score - current.dimensions[i].score,
  })).filter(d => d.delta !== 0)

  return {
    current,
    simulated,
    scoreDelta: simulated.finalScore - current.finalScore,
    dimChanges,
  }
}

/**
 * 从 store 数据构造评分输入
 *
 * @param {Object} profileStore - useProfileStore()
 * @param {Object} funnelStore - useFunnelStore()
 * @param {Object} debtStore - useDebtStore()
 * @returns {Object} calculateScore 所需的 input（含 mortgageCount 用于分群匹配）
 */
export function buildScoreInput(profileStore, funnelStore, debtStore) {
  const profile = profileStore?.profile || funnelStore?.financeProfile || {}
  const debts = debtStore?.debts || []

  const monthlyPayment = profile.monthlyPayment || funnelStore?.monthlyPayment || 5000
  const monthlyIncome = profile.monthlyIncome || funnelStore?.monthlyIncome || 7500

  // 统计房贷笔数（用于 MORTGAGE_HEAVY 分群匹配）
  const mortgageCount = debts.filter(d =>
    d.productType === 'MORTGAGE' || d.debtType === 'MORTGAGE'
  ).length || profile.mortgageCount || 0

  return {
    debtIncomeRatio: profile.debtIncomeRatio || (monthlyPayment / monthlyIncome),
    weightedApr: profile.weightedApr || 24,
    monthlyIncome,
    monthlyPayment,
    overdueCount: profile.overdueCount || 0,
    maxOverdueDays: profile.maxOverdueDays || 0,
    debtCount: debts.length || profile.debtCount || 1,
    avgLoanDays: profile.avgLoanDays || 365,
    mortgageCount,
  }
}
