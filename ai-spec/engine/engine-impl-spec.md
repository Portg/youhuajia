# engine-impl-spec.md -- 四大引擎详细实现规范

> 本文件由架构师根据 apr-calc.md、scoring-model.md、rules.md、user-journey.md 综合输出。
> 实现者必须严格遵循本规范，不得自行调整公式、阈值或建议文案。

---

## 5.1 AprCalculator -- APR 计算引擎

### 类定义

- **包路径**：`com.youhua.engine.apr`
- **类名**：`AprCalculator`
- **注解**：`@Component`
- **依赖注入**：从 `application.yml` 读取告警阈值配置

### 配置项（application.yml）

```yaml
youhua:
  engine:
    apr:
      warning-threshold: 36.0
      danger-threshold: 100.0
      abnormal-threshold: 1000.0
      max-allowed: 10000.0
```

用 `@ConfigurationProperties(prefix = "youhua.engine.apr")` 绑定到配置类 `AprConfig`，字段类型全部为 `BigDecimal`。

### 方法签名

#### 5.1.1 calculateApr -- 单笔 APR 计算

```
public BigDecimal calculateApr(BigDecimal principal, BigDecimal totalRepayment, int loanDays)
```

**算法（简化 APR，非牛顿迭代法）**：

> apr-calc.md 明确 MVP 阶段使用简化 APR 公式，不使用牛顿迭代法。
> 牛顿迭代法留给 V2.0 的 IRR 计算。

数学公式逐步分解：

```
Step 1: interest = totalRepayment - principal
Step 2: interestRate = interest / principal          (scale=10, HALF_UP)
Step 3: annualized = interestRate * (365 / loanDays) (scale=10, HALF_UP)
Step 4: aprPercent = annualized * 100                (scale=10, HALF_UP)
Step 5: result = aprPercent.setScale(6, HALF_UP)     (最终结果 scale=6)
```

**精度策略**：
- 所有中间计算步骤：`scale = 10`，`RoundingMode.HALF_UP`
- 最终返回值：`scale = 6`，`RoundingMode.HALF_UP`
- 除法运算：`principal.divide(xxx, 10, RoundingMode.HALF_UP)`
- 禁止在中间步骤做截断

**参数校验（按顺序检查，不满足立即抛异常）**：

| 序号 | 条件 | 抛出 |
|------|------|------|
| 1 | principal == null | `BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE)` |
| 2 | totalRepayment == null | `BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE)` |
| 3 | principal <= 0 | `BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE)` |
| 4 | loanDays <= 0 | `BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE)` |
| 5 | totalRepayment < principal | `BizException(ErrorCode.ENGINE_APR_PARAMS_INCOMPLETE)` |

**计算后校验**：

| 条件 | 处理 |
|------|------|
| aprPercent > maxAllowed (默认10000) | `BizException(ErrorCode.ENGINE_APR_RESULT_ABNORMAL)` |

**日志要求**：
- DEBUG 级别记录每个中间步骤的值：`interest={}, interestRate={}, annualized={}, aprPercent={}`
- 不得在日志中输出任何用户身份信息

#### 5.1.2 calculateWeightedApr -- 加权 APR

```
public BigDecimal calculateWeightedApr(List<DebtAprItem> debts)
```

其中 `DebtAprItem` 是一个简单的 record 或 DTO，包含 `BigDecimal principal` 和 `BigDecimal apr` 两个字段。

**算法**：

```
weightedApr = SUM(debt_i.apr * debt_i.principal) / SUM(debt_i.principal)
```

- 分子：逐笔累加 `apr * principal`（中间 scale=10）
- 分母：逐笔累加 `principal`（中间 scale=10）
- 最终除法：`scale=6, HALF_UP`

**边界条件**：

| 条件 | 处理 |
|------|------|
| debts 为 null 或空 | 返回 `BigDecimal.ZERO`，记录 WARN 日志 |
| 只有 1 笔 | 直接返回该笔的 apr（scale=6） |
| 总本金为 0（不应出现） | 返回 `BigDecimal.ZERO`，记录 WARN 日志 |

#### 5.1.3 getAprLevel -- APR 告警级别

```
public AprLevel getAprLevel(BigDecimal apr)
```

返回枚举 `AprLevel`：

| 条件 | 返回值 |
|------|--------|
| apr <= warningThreshold (36) | `NORMAL` |
| apr <= dangerThreshold (100) | `WARNING` |
| apr <= abnormalThreshold (1000) | `DANGER` |
| apr <= maxAllowed (10000) | `ABNORMAL` |
| apr > maxAllowed | 不会到达此处（calculateApr 已拦截） |

`AprLevel` 枚举定义在 `com.youhua.engine.apr.AprLevel`，包含 `NORMAL`, `WARNING`, `DANGER`, `ABNORMAL` 四个值。

### 测试用例（共 20 个）

所有测试位于 `com.youhua.engine.apr.AprCalculatorTest`。

#### 正常场景（6 个）

| 用例ID | 方法名 | principal | totalRepayment | loanDays | 预期APR(%) |
|--------|--------|-----------|----------------|----------|------------|
| APR-N01 | `should_return_60_833333_when_short_term_small_loan` | 10000.00 | 10500.00 | 30 | 60.833333 |
| APR-N02 | `should_return_20_000000_when_standard_annual` | 100000.00 | 120000.00 | 365 | 20.000000 |
| APR-N03 | `should_return_8_111111_when_medium_term` | 50000.00 | 52000.00 | 180 | 8.111111 |
| APR-N04 | `should_return_20_000000_when_two_year_loan` | 200000.00 | 280000.00 | 730 | 20.000000 |
| APR-N05 | `should_return_104_285714_when_ultra_short_high_interest` | 5000.00 | 5100.00 | 7 | 104.285714 |
| APR-N06 | `should_return_5_000000_when_large_low_interest` | 1000000.00 | 1050000.00 | 365 | 5.000000 |

**验证公式示例（APR-N01）**：
```
interest = 10500.00 - 10000.00 = 500.00
interestRate = 500.00 / 10000.00 = 0.0500000000
annualized = 0.0500000000 * (365 / 30) = 0.0500000000 * 12.1666666667 = 0.6083333333
aprPercent = 0.6083333333 * 100 = 60.8333333300
result = 60.833333 (scale=6, HALF_UP)
```

#### 边界场景（3 个）

| 用例ID | 方法名 | principal | totalRepayment | loanDays | 预期结果 |
|--------|--------|-----------|----------------|----------|----------|
| APR-B01 | `should_return_zero_when_no_interest` | 50000.00 | 50000.00 | 90 | 0.000000 |
| APR-B02 | `should_return_36500_when_minimum_principal_and_one_day` | 0.01 | 0.02 | 1 | 36500.000000 |
| APR-B03 | `should_return_near_zero_when_maximum_principal` | 9999999999999.9999 | 10000000000000.0000 | 365 | 接近 0.000001（用 `isCloseTo` 断言，delta=0.000001） |

#### 异常场景（7 个）

| 用例ID | 方法名 | principal | totalRepayment | loanDays | 预期异常 |
|--------|--------|-----------|----------------|----------|----------|
| APR-E01 | `should_throw_when_principal_zero` | 0 | 500.00 | 30 | `BizException(404001)` |
| APR-E02 | `should_throw_when_principal_negative` | -5000.00 | 10000.00 | 30 | `BizException(404001)` |
| APR-E03 | `should_throw_when_loan_days_zero` | 10000.00 | 10500.00 | 0 | `BizException(404001)` |
| APR-E04 | `should_throw_when_loan_days_negative` | 10000.00 | 10500.00 | -30 | `BizException(404001)` |
| APR-E05 | `should_throw_when_repayment_less_than_principal` | 10000.00 | 9000.00 | 30 | `BizException(404001)` |
| APR-E06 | `should_throw_when_principal_null` | null | 10500.00 | 30 | `BizException(404001)` |
| APR-E07 | `should_throw_when_apr_exceeds_max` | 1000.00 | 200000.00 | 3 | `BizException(404002)` |

**APR-E07 验证**：
```
interest = 200000.00 - 1000.00 = 199000.00
interestRate = 199000.00 / 1000.00 = 199.0
annualized = 199.0 * (365 / 3) = 199.0 * 121.6666... = 24211.666...
aprPercent = 2421166.666... > 10000 => BizException(404002)
```

#### 加权 APR 测试（4 个）

| 用例ID | 方法名 | 债务集合 | 预期加权APR(%) |
|--------|--------|----------|----------------|
| WAPR-01 | `should_return_25_333333_when_two_debts` | [{p:100000, apr:20}, {p:50000, apr:36}] | 25.333333 |
| WAPR-02 | `should_return_same_apr_when_single_debt` | [{p:100000, apr:20}] | 20.000000 |
| WAPR-03 | `should_return_average_when_equal_principals` | [{p:10000, apr:10}, {p:10000, apr:20}, {p:10000, apr:30}] | 20.000000 |
| WAPR-04 | `should_return_zero_with_warn_when_empty` | [] | 0.000000 + WARN日志 |

**WAPR-01 验证**：
```
分子 = 100000 * 20 + 50000 * 36 = 2000000 + 1800000 = 3800000
分母 = 100000 + 50000 = 150000
加权APR = 3800000 / 150000 = 25.333333 (scale=6)
```

---

## 5.2 ScoringEngine -- 重组可行性评分引擎

### 类定义

- **包路径**：`com.youhua.engine.scoring`
- **类名**：`ScoringEngine`
- **注解**：`@Component`
- **依赖注入**：`PmmlStrategyRegistry`、`PmmlScorecardEvaluator`、`UserSegmentMatcher`

### 架构说明

评分引擎已迁移至 PMML Scorecard 架构：
- **主路径**：PMML 模型评分（通过 `PmmlStrategyRegistry` 加载的 jpmml-evaluator 执行）
- **降级路径**：Java 硬编码评分（PMML 不可用时的 fallback）
- **权重和阈值**：定义在 PMML 模型文件和 `.meta.yml` 元数据中，不在 application.yml

### 配置项（application.yml）

```yaml
youhua:
  engine:
    scoring:
      strategy-dir: ""  # 空值从 classpath:strategies/ 加载；设文件系统路径（如 /opt/youhua/strategies）支持热加载
```

> 注意：`ScoringWeightsProperties` 已删除，权重/阈值配置段已从 yml 移除。
> Fallback 常量以 `private static final` 内联在 `ScoringEngine` 中。

### 核心方法

#### 5.2.1 evaluate -- 综合评分

```
public ScoreResult evaluate(ScoreInput input)
```

**ScoreInput 字段**：

| 字段 | 类型 | 说明 | 可空 |
|------|------|------|------|
| monthlyPayment | BigDecimal | 月供总额 | 否 |
| monthlyIncome | BigDecimal | 月收入 | 是（null 表示未填写） |
| weightedApr | BigDecimal | 加权 APR（百分比形式，如 21.4） | 否 |
| overdueCount | int | 逾期笔数 | 否 |
| maxOverdueDays | int | 最长逾期天数 | 否 |
| debtCount | int | 债务笔数 | 否 |
| avgLoanDays | int | 平均借款天数 | 否 |

**ScoreResult 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| finalScore | BigDecimal (scale=2) | 总分 0-100 |
| riskLevel | RiskLevel 枚举 | LOW/MEDIUM/HIGH/CRITICAL |
| recommendation | Recommendation 枚举 | RESTRUCTURE_RECOMMENDED / OPTIMIZE_FIRST / CREDIT_BUILDING |
| message | String | 展示给用户的建议文案 |
| nextPage | String | 建议的下一页路径 |
| dimensions | List&lt;DimensionDetail&gt; | 五维明细 |
| calculatedAt | LocalDateTime | 计算时间 |
| segment | UserSegment | 命中的用户分群 |
| strategyName | String | 使用的策略名称（来自 .meta.yml） |
| strategyVersion | String | 策略版本（来自 .meta.yml） |
| reasonCodes | List&lt;String&gt; | PMML 输出的 ReasonCode（如 ["DIR", "APR"]） |

**DimensionDetail 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| name | String | 维度标识符（camelCase） |
| label | String | 中文名称 |
| inputValue | BigDecimal | 输入值 |
| score | BigDecimal | 维度原始分（0-100） |
| weight | BigDecimal | 权重 |
| weightedScore | BigDecimal (scale=2) | 加权得分 = score * weight |
| explanation | String | 中文解读（来自 .meta.yml 的 explain_template，可空） |
| improvementTip | String | 改善建议（来自 .meta.yml 的 improvement_tip，可空） |

### 五维计算逻辑

#### 维度 1：负债收入比（debtIncomeRatio，权重 0.30）

```
输入计算：ratio = monthlyPayment / monthlyIncome

特殊情况：
  IF monthlyIncome == null OR monthlyIncome == 0:
    score = 20
    insight = "收入数据缺失，建议补充以获得更精准评估"
    （不抛异常，给低分但不阻断）

正常分段：
  IF ratio <= 0.30:   score = 90,  insight = "月供占收入{ratio*100}%，财务状况健康"
  IF ratio <= 0.50:   score = 70,  insight = "月供占收入{ratio*100}%，处于合理范围"
  IF ratio <= 0.70:   score = 50,  insight = "月供占收入{ratio*100}%，压力偏大"
  IF ratio <= 0.90:   score = 30,  insight = "月供占收入{ratio*100}%，财务压力较重"
  IF ratio > 0.90:    score = 10,  insight = "月供占收入{ratio*100}%，建议优化月供结构"
```

**ratio 的中间计算精度**：scale=10, HALF_UP

#### 维度 2：加权 APR（weightedApr，权重 0.25）

```
输入：weightedApr（百分比形式，如 21.4 表示 21.4%）

  IF weightedApr <= 10.0:   score = 90,  insight = "综合年化{apr}%，利率水平健康"
  IF weightedApr <= 18.0:   score = 75,  insight = "综合年化{apr}%，利率处于中等水平"
  IF weightedApr <= 24.0:   score = 55,  insight = "综合年化{apr}%，利率中等偏高"
  IF weightedApr <= 36.0:   score = 35,  insight = "综合年化{apr}%，属于高息范围"
  IF weightedApr > 36.0:    score = 15,  insight = "综合年化{apr}%，利率偏高，有较大优化空间"
```

#### 维度 3：资产流动性（liquidity，权重 0.15）

```
MVP 阶段简化逻辑（无独立资产模块）：

  IF monthlyIncome != null AND monthlyIncome > monthlyPayment:
    score = 60,  insight = "月收入可覆盖月供，有一定缓冲空间"
  ELSE IF monthlyIncome != null AND monthlyIncome <= monthlyPayment:
    score = 30,  insight = "月收入不足以覆盖月供，建议调整还款计划"
  ELSE (monthlyIncome == null):
    score = 40,  insight = "资产信息不足，建议补充收入数据"
```

#### 维度 4：逾期情况（overdue，权重 0.20）

```
输入：overdueCount, maxOverdueDays

  IF overdueCount == 0:
    score = 95,  insight = "无逾期记录，信用状况良好"
  ELSE IF overdueCount == 1 AND maxOverdueDays <= 30:
    score = 70,  insight = "有1笔短期逾期，影响较小"
  ELSE IF overdueCount <= 2 AND maxOverdueDays <= 60:
    score = 50,  insight = "有少量逾期记录，建议尽快处理"
  ELSE IF overdueCount <= 3 AND maxOverdueDays <= 90:
    score = 30,  insight = "逾期情况需要关注，建议优先处理"
  ELSE:
    score = 10,  insight = "逾期记录较多，建议制定还款计划逐步改善"
```

#### 维度 5：信用稳定度（creditStability，权重 0.10）

```
输入：debtCount, avgLoanDays

  IF debtCount <= 2 AND avgLoanDays >= 180:
    score = 80,  insight = "借贷笔数少、周期长，信用结构稳定"
  ELSE IF debtCount <= 4:
    score = 60,  insight = "借贷结构中等，有优化空间"
  ELSE IF debtCount <= 6:
    score = 40,  insight = "借贷笔数较多，建议整合优化"
  ELSE:
    score = 20,  insight = "借贷笔数偏多，建议逐步归并"
```

### 总分计算

```
finalScore = SUM(dimension_i.score * dimension_i.weight)
finalScore = finalScore.setScale(2, HALF_UP)
```

所有中间乘法用 scale=10 计算，最终结果 scale=2。

### 风险等级映射

```
IF finalScore >= 80:    riskLevel = LOW
IF finalScore >= 60:    riskLevel = MEDIUM
IF finalScore >= 40:    riskLevel = HIGH
IF finalScore < 40:     riskLevel = CRITICAL
```

`RiskLevel` 枚举定义在 `com.youhua.engine.scoring.RiskLevel`。

### 重组建议映射（严格遵循 user-journey.md 心理路径）

```
IF finalScore >= 60:
    recommendation = "RESTRUCTURE_RECOMMENDED"
    message = "好消息是，你有优化空间。通过调整债务结构，可以有效降低利息支出"

ELSE IF finalScore >= 40:
    recommendation = "OPTIMIZE_FIRST"
    message = "当前更适合优化信用结构。我们为你准备了 30 天改善路径"

ELSE (finalScore < 40):
    recommendation = "CREDIT_BUILDING"
    message = "你的财务结构有提升空间。先从小步骤开始，30 天后重新评估会有明显变化"
```

**关键约束（违反即为 Bug）**：
1. 评分 < 60 绝不输出 `RESTRUCTURE_RECOMMENDED`
2. 评分 < 60 绝不输出 `URGENT_ATTENTION` 或任何带"紧急"含义的代码
3. 所有 message 必须以正面表达开头（"好消息""你可以""当前更适合""你的财务结构有提升空间"）
4. 禁止使用"问题严重""赶紧行动""最后机会""申请失败""不符合条件"等表达
5. 动词用"优化/调整/改善"，不用"解决/修复"

### PMML 集成层

| 类 | 包路径 | 用途 |
|------|--------|------|
| `PmmlStrategyRegistry` | `engine.scoring.pmml` | 管理已加载的 PMML Evaluator，Map&lt;UserSegment, Evaluator&gt;。@Scheduled 60s 检测文件 MD5 变更，热加载。ReentrantReadWriteLock 线程安全 |
| `PmmlScorecardEvaluator` | `engine.scoring.pmml` | 封装 jpmml-evaluator 调用：ScoreInput → Map&lt;String, FieldValue&gt; → evaluate → 提取 finalScore + partialScores + reasonCodes |
| `StrategyMetadata` | `engine.scoring.pmml` | .meta.yml 对应的 POJO |
| `StrategyMetadataLoader` | `engine.scoring.pmml` | 加载解析 .meta.yml |
| `UserSegment` | `engine.scoring.pmml` | 枚举：DEFAULT / HIGH_DEBT / MORTGAGE_HEAVY / YOUNG_BORROWER |
| `UserSegmentMatcher` | `engine.scoring.pmml` | 分群匹配逻辑 |

### 评分记录（效果追踪）

| 类 | 包路径 | 用途 |
|------|--------|------|
| `ScoreRecord` | `engine.scoring.record` | Entity，对应 `t_score_record` |
| `ScoreRecordMapper` | `engine.scoring.record` | Mapper |
| `ScoreRecordService` | `engine.scoring.record` | 记录评分 + 查历史 + 算 delta |

### 测试用例

所有测试位于 `com.youhua.engine.scoring.ScoringEngineTest`。

> 注意：以下测试用例使用 DEFAULT 分群的 fallback 逻辑验证。debtCount 设为 3-4 避免命中 YOUNG_BORROWER（≤2）或 HIGH_DEBT（≥5）分群。

| 用例ID | 方法名 | 负债收入比 | 加权APR | 逾期笔数 | 最长逾期天数 | 债务笔数 | avgLoanDays | 月收入>月供 | 预期总分 | 预期等级 | 预期recommendation |
|--------|--------|-----------|---------|----------|-------------|---------|-------------|-----------|---------|---------|-------------------|
| SC-01 | `should_score_79_75_when_healthy_profile` | 0.25 | 12.0 | 0 | 0 | 3 | 200 | Y | 79.75 | MEDIUM | RESTRUCTURE_RECOMMENDED |
| SC-02 | `should_score_52_25_when_medium_pressure` | 0.62 | 21.4 | 0 | 0 | 4 | 150 | N | 52.25 | MEDIUM | OPTIMIZE_FIRST |
| SC-03 | `should_score_31_25_when_high_risk` | 0.85 | 38.0 | 2 | 45 | 4 | 120 | N | 31.25 | HIGH | CREDIT_BUILDING |
| SC-04 | `should_score_17_25_when_critical` | 0.95 | 45.0 | 4 | 120 | 4 | 60 | N | 17.25 | CRITICAL | CREDIT_BUILDING |
| SC-05 | `should_score_61_75_when_borderline` | 0.50 | 24.0 | 1 | 25 | 3 | 180 | Y | 61.75 | MEDIUM | RESTRUCTURE_RECOMMENDED |
| SC-06 | `should_score_51_75_when_income_missing` | null | 15.0 | 0 | 0 | 3 | 365 | -- | 51.75 | MEDIUM | OPTIMIZE_FIRST |

分群路由测试：

| 用例ID | 说明 | 预期分群 | 预期策略名 |
|--------|------|---------|-----------|
| SC-S01 | debtCount=5 → HIGH_DEBT | HIGH_DEBT | 高负债优化策略 |
| SC-S02 | debtCount=2, avgLoanDays=200 → YOUNG_BORROWER | YOUNG_BORROWER | 新手成长策略 |
| SC-S03 | debtCount=3, avgLoanDays=400 → DEFAULT | DEFAULT | 稳健策略 |

**SC-01 验算（debtCount=3, avgLoanDays=200）**：
```
负债收入比: ratio=0.25 => score=90, weighted=27.00
加权APR: 12.0 => score=75, weighted=18.75
流动性: 月收入>月供 => score=60, weighted=9.00
逾期: 0笔 => score=95, weighted=19.00
信用稳定: 3笔 (<=4) => score=60, weighted=6.00
finalScore = 27.00 + 18.75 + 9.00 + 19.00 + 6.00 = 79.75
```

补充测试（边界和约束验证）：

| 用例ID | 方法名 | 说明 |
|--------|--------|------|
| SC-07 | `should_return_CREDIT_BUILDING_when_score_below_40` | 验证 finalScore < 40 时 recommendation 为 CREDIT_BUILDING |
| SC-08 | `should_never_return_URGENT_ATTENTION` | 遍历所有低分场景，确认永远不出现 URGENT_ATTENTION |
| SC-09 | `should_message_start_with_positive_expression` | 所有三种 recommendation 的 message 均以正面词汇开头 |

---

## 5.3 RuleEngine -- 业务规则引擎

### 类定义

- **包路径**：`com.youhua.engine.rules`
- **类名**：`RuleEngine`
- **注解**：`@Component`
- **依赖注入**：`RuleConfig`（规则阈值配置）

### 配置项（application.yml）

```yaml
youhua:
  rules:
    data:
      min-confirmed-debts: 1
    value:
      apr-max-allowed: 10000.0
      apr-warning-threshold: 36.0
      extreme-debt-ratio: 0.9
      high-total-debt: 10000000
    fraud:
      max-debt-create-per-hour: 20
      max-profile-calc-per-hour: 10
```

用 `@ConfigurationProperties(prefix = "youhua.rules")` 绑定到 `RuleConfig` 类。

### 核心方法

```
public RuleCheckResult check(RuleInput input)
```

**RuleInput 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| confirmedDebts | List&lt;DebtSnapshot&gt; | 已确认的债务快照列表 |
| monthlyIncome | BigDecimal | 月收入（可空） |
| monthlyPayment | BigDecimal | 月供总额（可空） |
| debtCreateCountInLastHour | int | 最近1小时创建债务笔数 |
| profileCalcCountInLastHour | int | 最近1小时画像计算次数 |

**DebtSnapshot 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| debtId | Long | 债务ID |
| creditor | String | 债权机构名称 |
| principal | BigDecimal | 本金 |
| totalRepayment | BigDecimal | 总还款额 |
| loanDays | int | 借款天数 |
| apr | BigDecimal | 已计算的 APR |
| overdueStatus | String | 逾期状态枚举值 |
| sourceType | String | 来源类型（MANUAL/OCR） |
| confidenceScore | Integer | OCR 置信度（仅 OCR 来源有值） |

**RuleCheckResult 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| ruleResults | List&lt;RuleResult&gt; | 每条规则的结果 |
| blocked | boolean | 是否有 BLOCK 级别规则触发 |
| blockReason | String | 第一条 BLOCK 规则的 message（用于返回给前端） |
| blockErrorCode | ErrorCode | 第一条 BLOCK 规则的错误码 |
| warnings | List&lt;String&gt; | 所有 WARN 标签列表 |
| canProceed | boolean | !blocked |

**RuleResult 字段**：

| 字段 | 类型 | 说明 |
|------|------|------|
| ruleId | String | 规则ID（如 DATA_001） |
| result | RuleResultType 枚举 | PASS / WARN / BLOCK |
| tag | String | 标签（可空） |
| message | String | 提示信息（可空） |
| relatedDebtId | Long | 关联债务ID（可空） |

### 规则执行顺序（严格按序，遇 BLOCK 立即终止）

#### 第一优先级：数据完整性规则（BLOCK 级）

**RULE DATA_001**：至少一笔已确认债务
```
IF confirmedDebts == null OR confirmedDebts.isEmpty():
  BLOCK, errorCode = PROFILE_NO_CONFIRMED_DEBT (403001)
  message = "暂无已确认的债务数据，无法生成画像"
```

**RULE DATA_002**：本金大于零
```
FOR EACH debt IN confirmedDebts:
  IF debt.principal == null OR debt.principal <= 0:
    BLOCK, errorCode = DEBT_PRINCIPAL_INVALID (402002)
    message = "存在本金为零或负数的债务记录"
    relatedDebtId = debt.debtId
```

**RULE DATA_003**：借款天数大于零
```
FOR EACH debt IN confirmedDebts:
  IF debt.loanDays <= 0:
    BLOCK, errorCode = DEBT_LOAN_DAYS_INVALID (402004)
    message = "存在借款天数为零或负数的债务记录"
    relatedDebtId = debt.debtId
```

**RULE DATA_004**：总还款额不小于本金
```
FOR EACH debt IN confirmedDebts:
  IF debt.totalRepayment == null OR debt.totalRepayment.compareTo(debt.principal) < 0:
    BLOCK, errorCode = DEBT_REPAYMENT_INVALID (402003)
    message = "存在总还款额小于本金的债务记录"
    relatedDebtId = debt.debtId
```

#### 第二优先级：数值合理性规则（BLOCK / WARN 级）

**RULE VALUE_001**：APR 超上限检查
```
FOR EACH debt IN confirmedDebts:
  IF debt.apr != null AND debt.apr > config.value.aprMaxAllowed:
    BLOCK, errorCode = ENGINE_APR_RESULT_ABNORMAL (404002)
    message = "APR 计算结果异常，请核实债务数据"
    relatedDebtId = debt.debtId
```

**RULE VALUE_002**：APR 高息预警
```
FOR EACH debt IN confirmedDebts:
  IF debt.apr != null AND debt.apr > config.value.aprWarningThreshold:
    WARN, tag = "HIGH_INTEREST"
    message = "债务 {debt.creditor} 实际年化 {debt.apr}%，属于高息债务"
    relatedDebtId = debt.debtId
```

**RULE VALUE_003**：月供超收入预警
```
IF monthlyIncome != null AND monthlyPayment != null AND monthlyPayment > monthlyIncome:
  WARN, tag = "PAYMENT_EXCEED_INCOME"
  message = "月供总额已超过月收入，财务压力较大"
```

**RULE VALUE_004**：负债收入比超标预警
```
IF monthlyIncome != null AND monthlyIncome > 0 AND monthlyPayment != null:
  debtIncomeRatio = monthlyPayment / monthlyIncome
  IF debtIncomeRatio > config.value.extremeDebtRatio:
    WARN, tag = "EXTREME_DEBT_RATIO"
    message = "负债收入比超过90%，财务状况需紧急关注"
```

**RULE VALUE_005**：总负债异常检查
```
totalDebt = SUM(debt.principal for debt in confirmedDebts)
IF totalDebt > config.value.highTotalDebt:
  WARN, tag = "HIGH_TOTAL_DEBT"
  message = "总负债金额较大，请确认数据准确性"
```

#### 第三优先级：业务逻辑规则（WARN 级）

**RULE BIZ_001**：重组评分阈值
> 此规则不在 RuleEngine 中执行。评分阈值判断由 ScoringEngine 负责。
> RuleEngine 只做前置校验，不做评分后的业务判断。

**RULE BIZ_002**：优先处理顺序
> 此规则为排序逻辑，不在 RuleEngine 的 check 方法中体现。
> 实现为独立工具方法：`List<DebtSnapshot> sortByPriority(List<DebtSnapshot> debts)`
> 排序规则：
>   1. overdueStatus DESC（逾期优先）
>   2. apr DESC（利率高的优先）
>   3. principal ASC（余额小的优先清偿）

**RULE BIZ_003**：逾期债务紧急标记
```
FOR EACH debt IN confirmedDebts:
  IF debt.overdueStatus IN ("OVERDUE_60", "OVERDUE_90_PLUS"):
    WARN, tag = "URGENT_OVERDUE"
    message = "存在严重逾期债务，建议立即处理"
    relatedDebtId = debt.debtId
```

**RULE BIZ_004**：OCR 低置信度提醒
```
FOR EACH debt IN confirmedDebts:
  IF debt.sourceType == "OCR" AND debt.confidenceScore != null AND debt.confidenceScore < 70:
    WARN, tag = "LOW_CONFIDENCE"
    message = "债务 {debt.creditor} 的OCR识别置信度较低（{debt.confidenceScore}%），建议人工核对"
    relatedDebtId = debt.debtId
```

#### 第四优先级：反欺诈规则（BLOCK 级）

**RULE FRAUD_001**：短时间大量录入
```
IF debtCreateCountInLastHour > config.fraud.maxDebtCreatePerHour:
  BLOCK, errorCode = ENGINE_RULE_FAILED (404004)
  message = "操作过于频繁，请稍后再试"
  LOG WARN: "用户触发反欺诈规则 FRAUD_001"
```

**RULE FRAUD_002**：重复债务检测
> 此规则需要查询数据库，不在纯计算引擎中实现。
> 由上层 Service 在创建债务时检测，不在 RuleEngine.check() 中处理。

**RULE FRAUD_003**：画像频繁计算
```
IF profileCalcCountInLastHour > config.fraud.maxProfileCalcPerHour:
  BLOCK, errorCode = PROFILE_CALCULATING (403003)
  message = "画像正在计算中，请稍后"
```

### 测试用例

所有测试位于 `com.youhua.engine.rules.RuleEngineTest`。

| 用例ID | 方法名 | 说明 | 预期 |
|--------|--------|------|------|
| RULE-01 | `should_pass_when_all_data_valid` | 所有数据合法 | canProceed=true, blocked=false |
| RULE-02 | `should_block_when_no_confirmed_debts` | 空债务列表 | BLOCK, 403001 |
| RULE-03 | `should_block_when_principal_zero` | 某笔 principal=0 | BLOCK, 402002 |
| RULE-04 | `should_block_when_loan_days_negative` | 某笔 loanDays=-1 | BLOCK, 402004 |
| RULE-05 | `should_block_when_repayment_less_than_principal` | 某笔 totalRepayment < principal | BLOCK, 402003 |
| RULE-06 | `should_block_when_apr_exceeds_max` | 某笔 apr=15000 | BLOCK, 404002 |
| RULE-07 | `should_warn_high_interest` | 某笔 apr=42.5 | WARN, tag=HIGH_INTEREST |
| RULE-08 | `should_warn_payment_exceed_income` | 月供>月收入 | WARN, tag=PAYMENT_EXCEED_INCOME |
| RULE-09 | `should_warn_extreme_debt_ratio` | 负债收入比>0.9 | WARN, tag=EXTREME_DEBT_RATIO |
| RULE-10 | `should_warn_high_total_debt` | 总负债>千万 | WARN, tag=HIGH_TOTAL_DEBT |
| RULE-11 | `should_warn_urgent_overdue` | 存在 OVERDUE_90_PLUS | WARN, tag=URGENT_OVERDUE |
| RULE-12 | `should_warn_low_confidence` | OCR 置信度=50 | WARN, tag=LOW_CONFIDENCE |
| RULE-13 | `should_block_when_fraud_batch_create` | 1小时创建21笔 | BLOCK, 404004 |
| RULE-14 | `should_block_when_fraud_frequent_calc` | 1小时计算11次 | BLOCK, 403003 |
| RULE-15 | `should_stop_at_first_block` | DATA_002 和 FRAUD_001 同时触发 | 只报 DATA_002（先执行） |
| RULE-16 | `should_accumulate_multiple_warnings` | 多条 WARN 同时触发 | warnings 包含所有 tag |
| RULE-17 | `should_skip_value_rules_when_data_blocked` | DATA_001 触发 BLOCK | VALUE_* 规则未执行 |

---

## 5.4 PreAuditEngine -- 预审通过概率估算引擎

### 类定义

- **包路径**：`com.youhua.engine.scoring`
- **类名**：`PreAuditEngine`
- **注解**：`@Component`
- **依赖注入**：无外部依赖，通过 `@PostConstruct` 从 classpath 加载 YAML 配置

### 定位

PreAuditEngine 是第四个引擎，对应 Page 8 第三层"模拟审批结果"。基于用户的评分、债务结构、收入情况等维度，按经验策略估算预审通过概率。所有计算确定性可复现，不调用大模型（F-02）。

前端在 `page8-action-layers/preaudit.js` 中实现了相同逻辑的 JavaScript 版本，与后端使用完全一致的规则配置，支持离线即时计算。

### 配置文件（preaudit.meta.yml）

配置文件位于 `classpath:strategies/preaudit.meta.yml`，POJO 映射类为 `PreAuditMetadata`。

```yaml
strategy_name: "预审通过概率估算"
description: "基于用户评分、债务结构、收入情况等维度，按经验策略估算预审通过概率"
version: "1.0"

# 基准概率（0-100）
base_probability: 50

# 概率上下限（避免给极端值误导用户）
min_probability: 35
max_probability: 92

# 各维度加减分规则
dimensions:
  SCORE:       # 优化评分 — 从高到低匹配 min 阈值，命中即停
  DIR:         # 负债收入比 — 从低到高匹配 max 阈值
  OVD:         # 逾期情况 — 二元判断（有/无逾期）
  CST:         # 债务笔数 — 从低到高匹配 max 阈值
  APR_HIGH:    # 高利率债务占比（APR > 24%）— 从低到高匹配 max 阈值

# 建议规则：按顺序匹配，最多取前 3 条
suggestions:
  - condition: "HAS_OVERDUE" / "HIGH_APR_RATIO_GT_30" / "DIR_GT_70" / ...
    text: "..."

# 兜底建议
fallback_suggestion: "准备详细的收入证明和还款记录可提高通过率"
```

### PreAuditMetadata POJO

```
com.youhua.engine.scoring.PreAuditMetadata
```

| 字段 | 类型 | 说明 |
|------|------|------|
| strategyName | String | 策略名称 |
| description | String | 策略描述 |
| version | String | 版本号 |
| baseProbability | int | 基准概率（默认 50） |
| minProbability | int | 概率下限（默认 35） |
| maxProbability | int | 概率上限（默认 92） |
| dimensions | Map<String, Dimension> | 维度配置，key 为维度标识 |
| suggestions | List<SuggestionRule> | 建议规则列表 |
| fallbackSuggestion | String | 兜底建议文案 |

**Dimension 内嵌类**：

| 字段 | 类型 | 说明 |
|------|------|------|
| label | String | 中文名称 |
| thresholds | List<Threshold> | 阈值列表（SCORE/DIR/CST/APR_HIGH 使用） |
| noOverdueDelta | Integer | 无逾期加分（OVD 专用） |
| hasOverdueDelta | Integer | 有逾期减分（OVD 专用） |
| highAprThreshold | BigDecimal | 高利率定义阈值（APR_HIGH 专用，默认 24） |

**Threshold 内嵌类**：

| 字段 | 类型 | 说明 |
|------|------|------|
| min | BigDecimal | 最小阈值（SCORE 使用，>=min 即命中） |
| max | BigDecimal | 最大阈值（DIR/CST/APR_HIGH 使用，<=max 即命中） |
| delta | int | 命中后的概率加减值 |

**SuggestionRule 内嵌类**：

| 字段 | 类型 | 说明 |
|------|------|------|
| condition | String | 条件标识 |
| text | String | 建议文案 |

### 核心方法

#### 5.4.1 estimate -- 预审概率估算

```
public PreAuditResult estimate(PreAuditInput input)
```

**PreAuditInput（record）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| score | BigDecimal | 优化评分（0-100） |
| debtIncomeRatio | BigDecimal | 负债收入比（0-1+） |
| hasOverdue | boolean | 是否有逾期 |
| debtCount | int | 债务笔数 |
| highAprRatio | BigDecimal | 高利率债务占比（0-1，APR > 24% 的笔数 / 总笔数） |

**PreAuditResult（record）**：

| 字段 | 类型 | 说明 |
|------|------|------|
| probability | int | 预审通过概率（35-92） |
| suggestions | List<String> | 个性化建议（最多 3 条） |

**算法步骤**：

```
Step 1: probability = baseProbability (50)

Step 2: SCORE 维度
  从高到低遍历 thresholds，找到第一个 score >= threshold.min 的条目
  probability += delta

Step 3: DIR 维度
  从低到高遍历 thresholds，找到第一个 debtIncomeRatio <= threshold.max 的条目
  probability += delta

Step 4: OVD 维度
  IF hasOverdue: probability += hasOverdueDelta (-12)
  ELSE:          probability += noOverdueDelta (+8)

Step 5: CST 维度
  从低到高遍历 thresholds，找到第一个 debtCount <= threshold.max 的条目
  probability += delta

Step 6: APR_HIGH 维度
  从低到高遍历 thresholds，找到第一个 highAprRatio <= threshold.max 的条目
  probability += delta

Step 7: Clamp
  probability = max(minProbability, min(maxProbability, probability))
```

**各维度默认阈值**：

| 维度 | 阈值 | Delta |
|------|------|-------|
| SCORE | >= 80 | +20 |
| SCORE | >= 60 | +10 |
| SCORE | >= 40 | 0 |
| SCORE | >= 0 | -10 |
| DIR | <= 0.30 | +10 |
| DIR | <= 0.50 | +5 |
| DIR | <= 0.70 | 0 |
| DIR | <= 1.00 | -5 |
| DIR | <= 999 | -10 |
| OVD | 无逾期 | +8 |
| OVD | 有逾期 | -12 |
| CST | <= 2 | +5 |
| CST | <= 4 | 0 |
| CST | <= 6 | -5 |
| CST | <= 999 | -10 |
| APR_HIGH | <= 0.00 | +5 |
| APR_HIGH | <= 0.30 | 0 |
| APR_HIGH | <= 0.60 | -3 |
| APR_HIGH | <= 1.00 | -8 |

### 建议生成

按顺序匹配条件，取前 3 条命中的建议。所有条件均不命中时使用 `fallbackSuggestion`。

**支持的条件标识**：

| condition 值 | 匹配逻辑 |
|-------------|---------|
| `HAS_OVERDUE` | hasOverdue == true |
| `HIGH_APR_RATIO_GT_30` | highAprRatio > 0.30 |
| `DIR_GT_70` | debtIncomeRatio > 0.70 |
| `DEBT_COUNT_GT_4` | debtCount > 4 |
| `SCORE_GE_70` | score >= 70 |
| `LOW_DIR_NO_OVERDUE` | debtIncomeRatio <= 0.50 AND !hasOverdue |
| `SCORE_LT_50` | score < 50 |

### PreAuditResponse（API 响应 DTO）

```
com.youhua.engine.dto.response.PreAuditResponse
```

| 字段 | 类型 | 说明 |
|------|------|------|
| probability | int | 预审通过概率百分比 |
| suggestions | List<String> | 个性化建议列表 |

使用 `@Builder` 构建，由 EngineController 调用 PreAuditEngine 后组装返回。

### 降级策略

配置文件加载失败时（`config == null`），返回默认结果：
- probability = 50
- suggestions = ["准备详细的收入证明和还款记录可提高通过率"]

### 日志要求

- DEBUG 级别记录每次估算的输入和输出：`score={}, dir={}, overdue={}, debtCount={}, highAprRatio={} → probability={}`
- WARN 级别记录配置加载失败
- 不得在日志中输出任何用户身份信息

### 前端对等实现

`youhuajia-app/src/pages/page8-action-layers/preaudit.js` 导出 `estimatePreAudit()` 函数，使用与后端完全相同的规则配置常量。前端版本用于 Page 8 第三层即时展示预审结果，无需等待网络请求。

```javascript
export function estimatePreAudit({ score, monthlyPayment, monthlyIncome, debts })
// 返回 { probability: number, suggestions: string[] }
```

前端额外负责从 debts 列表中派生 `hasOverdue`、`debtIncomeRatio`、`highAprRatio` 等输入参数。

---

## 附录：四引擎协作流程

```
用户触发画像计算
  |
  v
RuleEngine.check(input)
  |-- BLOCK? --> 返回错误，终止流程
  |-- PASS/WARN -->
  v
AprCalculator.calculateApr() -- 逐笔计算 APR
AprCalculator.calculateWeightedApr() -- 计算加权 APR
  |
  v
ScoringEngine.score(input)
  |-- UserSegmentMatcher → 确定分群
  |-- PmmlStrategyRegistry → 获取对应 PMML Evaluator
  |-- PmmlScorecardEvaluator → 执行评分
  |-- StrategyMetadata → 填充风险等级、解读、建议
  |-- （PMML 不可用）→ doScore() fallback
  |
  v
ScoreRecordService.recordScore() -- 持久化评分记录 + 计算 delta
  |
  v
返回 {ruleWarnings, aprResults, scoreResult} 给上层 Service

---

用户进入 Page 8 第三层（预审）
  |
  v
PreAuditEngine.estimate(input)
  |-- 从 scoreResult + financeProfile 构造 PreAuditInput
  |-- 加载 preaudit.meta.yml 规则
  |-- 五维度加减分 → 概率
  |-- 条件匹配 → 个性化建议
  |
  v
返回 PreAuditResponse { probability, suggestions }
```

上层 Service（非本规范范围）负责：
1. 从数据库获取债务数据构造 RuleInput
2. 调用 RuleEngine.check()
3. 若通过，逐笔调用 AprCalculator.calculateApr()
4. 调用 AprCalculator.calculateWeightedApr()
5. 构造 ScoreInput 调用 ScoringEngine.score()
6. 调用 ScoreRecordService.recordScore() 记录评分
7. 组装最终画像结果并持久化
8. Page 8 第三层：构造 PreAuditInput 调用 PreAuditEngine.estimate() 返回预审结果
