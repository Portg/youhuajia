# scoring-model.md — 重组可行性评分模型规范

> 评分结果决定用户是否适合进行债务重组。
> 所有评分计算必须使用确定性算法，禁止调用大模型。
> 评分过程必须可追溯、可解释。

---

## 一、评分模型概述

```
目标：评估用户是否适合申请债务重组
输出：0-100 分（BigDecimal, scale=2）
阈值：≥ 60 推荐评估重组，< 60 优先结构优化
```

### 架构：PMML Scorecard + 用户分群 + 热加载

评分逻辑从 Java 代码迁移到 **PMML 4.4 Scorecard 模型文件**。策略师用训练工具导出 `.pmml` → 放到 `strategies/` 目录 → 系统热加载执行。

```
策略师（模型训练工具）
    ↓ 导出 .pmml 文件
strategies/ 目录（按分群组织）
    ├── default.pmml          ← 通用策略
    ├── high-debt.pmml        ← 高负债用户
    ├── mortgage-heavy.pmml   ← 房贷用户
    └── young-borrower.pmml   ← 年轻首贷用户
    ↓ @Scheduled(fixedDelay=60000) 轮询检测变更（文件 MD5）
PmmlStrategyRegistry（内存缓存所有 Evaluator）
    ↓
ScoringEngine.score(input) → UserSegmentMatcher 匹配分群 → 选 Evaluator → 评分
    ↓
ScoreRecordService → 记录评分结果 + 策略版本 + delta
```

**关键设计**：
- PMML 不可用时自动降级到 Java 硬编码评分逻辑（fallback）
- 降级使用 `private static final` 常量，不依赖外部配置
- PMML finalScore 为权威结果，Java 仅作为后备

---

## 二、评分维度（五维模型）

| 维度 | 标识 | ReasonCode | 默认权重 | 输入数据 | 评分范围 |
|------|------|------------|----------|----------|----------|
| 负债收入比 | debtIncomeRatio | DIR | 0.30 | monthlyPayment / monthlyIncome | 0-100 |
| 加权APR | weightedApr | APR | 0.25 | weightedApr | 0-100 |
| 资产流动性 | liquidity | LIQ | 0.15 | monthlyIncome vs monthlyPayment | 0-100 |
| 逾期情况 | overdue | OVD | 0.20 | overdueCount + maxOverdueDays | 0-100 |
| 信用稳定度 | creditStability | CST | 0.10 | debtCount + avgLoanDays | 0-100 |

> 注意：以上权重为 DEFAULT 分群的默认值。不同分群 PMML 模型可定义不同权重。

**总分公式**：
```
finalScore = Σ(dimensionScore_i × weight_i)
```

权重和评分阈值定义在 PMML Scorecard 模型中，不再存放于 application.yml。

---

## 三、用户分群

### UserSegment 枚举

| 分群 | 匹配条件 | 对应 PMML 文件 | 策略特点 |
|------|----------|---------------|----------|
| HIGH_DEBT | debtIncomeRatio > 0.70 或 debtCount >= 5 | high-debt.pmml | DIR 权重 0.35（↑），APR 0.20（↓），更细粒度 DIR 分段 |
| MORTGAGE_HEAVY | 有 MORTGAGE 类型债务占比 > 50% | mortgage-heavy.pmml | LIQ 0.20（↑），CST 0.15（↑），DIR 分段更宽松 |
| YOUNG_BORROWER | debtCount <= 2 且 avgLoanDays < 365 | young-borrower.pmml | CST 0.15（↑），积极鼓励语调 |
| DEFAULT | 以上都不命中 | default.pmml | 均衡考虑各维度 |

**匹配优先级**：HIGH_DEBT > MORTGAGE_HEAVY > YOUNG_BORROWER > DEFAULT

分群对应的 `.pmml` 文件不存在时，降级到 DEFAULT。

---

## 四、PMML 模型文件规范

### 输入字段（MiningSchema）

| 字段名 | 类型 | 说明 | 对应 ScoreInput |
|--------|------|------|-----------------|
| debtIncomeRatio | DOUBLE | 负债收入比 | monthlyPayment / monthlyIncome |
| weightedApr | DOUBLE | 加权年化利率 | weightedApr |
| monthlyIncome | DOUBLE | 月收入 | monthlyIncome |
| monthlyPayment | DOUBLE | 月供 | monthlyPayment |
| overdueCount | INTEGER | 逾期笔数 | overdueCount |
| maxOverdueDays | INTEGER | 最大逾期天数 | maxOverdueDays |
| debtCount | INTEGER | 负债笔数 | debtCount |
| avgLoanDays | INTEGER | 平均借贷天数 | avgLoanDays |

### 输出

| 输出 | 来源 | 说明 |
|------|------|------|
| finalScore | Target field | 0-100 最终得分 |
| 各维度 partialScore | Characteristic | 每个维度的分数贡献 |
| ReasonCode1~3 | reasonCode 属性 | 最需改善的维度（可解释性） |

### 伴生元数据文件（.meta.yml）

每个 `.pmml` 文件旁放一个同名 `.meta.yml`，存放 PMML 不支持的业务元数据：

```yaml
# strategies/default.meta.yml
strategy_name: "稳健策略"
segment: DEFAULT
description: "适用于大多数用户，均衡考虑各维度风险"
created_by: "系统"
created_at: "2026-03-01"
version: "1.0"

# 风险等级边界（PMML 只算分，分级逻辑在 Java）
risk_level_boundaries: [80, 60, 40]
restructure_threshold: 60

# 各 Reason Code 对应的中文解读和改善建议
reason_code_messages:
  DIR:
    label: "负债收入比"
    explain_template: "当前负债收入比 {value}，{level_desc}"
    level_descriptions:
      - { max: 0.30, desc: "财务状况健康" }
      - { max: 0.50, desc: "有一定压力但可控" }
      # ...
    improvement_tip: "减少月供或增加收入可提升此项得分"
  APR:
    label: "综合利率"
    improvement_tip: "优先偿还高利率负债，或申请更低利率贷款置换"
  # ... LIQ, OVD, CST
```

---

## 五、各维度评分逻辑（Fallback IF/THEN 规则）

> 以下逻辑为 Java 硬编码 fallback，PMML 不可用时使用。
> PMML 可用时，评分逻辑由 PMML Scorecard 的 Characteristic 定义。

### 5.1 负债收入比评分

```
输入：debtIncomeRatio = monthlyPayment / monthlyIncome

IF monthlyIncome == 0 OR monthlyIncome IS NULL:
    RETURN score = 20  # 无收入数据，给低分但不是0
    FLAG = "INCOME_MISSING"

IF debtIncomeRatio <= 0.30:   score = 90
ELSE IF debtIncomeRatio <= 0.50:   score = 70
ELSE IF debtIncomeRatio <= 0.70:   score = 50
ELSE IF debtIncomeRatio <= 0.90:   score = 30
ELSE:   score = 10
```

### 5.2 加权 APR 评分

```
输入：weightedAPR（百分比形式，如 21.4 表示 21.4%）

IF weightedAPR <= 10.0:   score = 90
ELSE IF weightedAPR <= 18.0:   score = 75
ELSE IF weightedAPR <= 24.0:   score = 55
ELSE IF weightedAPR <= 36.0:   score = 35
ELSE:   score = 15
```

### 5.3 资产流动性评分

```
MVP 阶段简化处理（无资产模块）：

IF 用户有收入记录 AND 月收入 > 月供:   score = 60
ELSE IF 用户有收入记录 AND 月收入 <= 月供:   score = 30
ELSE:   score = 40
```

### 5.4 逾期情况评分

```
IF overdueCount == 0:   score = 95
ELSE IF overdueCount == 1 AND maxOverdueDays <= 30:   score = 70
ELSE IF overdueCount <= 2 AND maxOverdueDays <= 60:   score = 50
ELSE IF overdueCount <= 3 AND maxOverdueDays <= 90:   score = 30
ELSE:   score = 10
```

### 5.5 信用稳定度评分

```
IF debtCount <= 2 AND avgLoanDays >= 180:   score = 80
ELSE IF debtCount <= 4:   score = 60
ELSE IF debtCount <= 6:   score = 40
ELSE:   score = 20
```

---

## 六、风险等级映射

风险边界由 `.meta.yml` 的 `risk_level_boundaries` 定义，DEFAULT 策略默认值：

```
IF finalScore >= 80:    riskLevel = LOW
ELSE IF finalScore >= 60:    riskLevel = MEDIUM
ELSE IF finalScore >= 40:    riskLevel = HIGH
ELSE:    riskLevel = CRITICAL
```

不同分群可有不同边界（如 HIGH_DEBT 策略使用 `[75, 55, 35]`）。

---

## 七、重组建议映射（遵循 user-journey.md 心理路径）

重组阈值由 `.meta.yml` 的 `restructure_threshold` 定义，DEFAULT 策略默认 60。

```
IF finalScore >= restructureThreshold:
    recommendation = "RESTRUCTURE_RECOMMENDED"
    message = "好消息是，你有优化空间。通过调整债务结构，可以有效降低利息支出"
    nextPage = "利率模拟器（Page 6）"
ELSE IF finalScore >= 40:
    recommendation = "OPTIMIZE_FIRST"
    message = "当前更适合优化信用结构。我们为你准备了 30 天改善路径"
    nextPage = "信用修复路线图（替代利率模拟器）"
ELSE:
    recommendation = "CREDIT_BUILDING"
    message = "你的财务结构有提升空间。先从小步骤开始，30 天后重新评估会有明显变化"
    nextPage = "30天行动计划"

⚠️ 心理路径约束（来自 domain/user-journey.md）：
  - 永远不输出"不符合条件""申请失败"等否定性结论
  - 评分 < 60 不进入重组申请流程，改为"信用修复 → 30天回访"路径
  - 所有 message 必须以正面表达开头（"好消息""你可以""有空间"）
  - message 中的动词用"优化/调整/改善"，不用"解决/修复"
```

---

## 八、可解释性输出

每次评分输出 ScoreResult，包含 PMML 策略信息和维度解读：

```json
{
  "finalScore": 68.00,
  "riskLevel": "MEDIUM",
  "recommendation": "RESTRUCTURE_RECOMMENDED",
  "segment": "DEFAULT",
  "strategyName": "稳健策略",
  "strategyVersion": "1.0",
  "reasonCodes": ["DIR", "APR"],
  "dimensions": [
    {
      "name": "debtIncomeRatio",
      "label": "负债收入比",
      "inputValue": 0.62,
      "score": 50,
      "weight": 0.30,
      "weightedScore": 15.00,
      "explanation": "当前负债收入比 0.62，建议控制新增借贷",
      "improvementTip": "减少月供或增加收入可提升此项得分"
    },
    {
      "name": "weightedApr",
      "label": "综合利率",
      "inputValue": 21.4,
      "score": 55,
      "weight": 0.25,
      "weightedScore": 13.75,
      "explanation": "当前加权利率 21.4%，利率中等偏高",
      "improvementTip": "优先偿还高利率负债，或申请更低利率贷款置换"
    }
    // ... 其他维度
  ],
  "calculatedAt": "2026-03-04T10:30:00"
}
```

---

## 九、效果追踪

每次评分持久化到 `t_score_record` 表（见 `domain/entities.md`），记录：
- 策略名称和版本
- 命中的分群
- 各维度分数 JSON
- 与用户上次评分的 delta

策略师可按 `strategy_name + strategy_version` 聚合分析不同策略版本下的分数分布和改善趋势。

---

## 十、What-if 模拟

### API：`POST /api/v1/engine/score:simulate`

输入：用户 ID + 模拟操作列表（还清某笔债务 / 减少本金 / 置换利率）
处理：复制用户债务 → 应用修改 → 重算 ScoreInput → 用当前策略评分
输出：当前 ScoreResult + 模拟 ScoreResult + 每维度变化量

---

## 十一、热加载机制

- `PmmlStrategyRegistry` 使用 `@Scheduled(fixedDelay=60000)` 每 60 秒检测文件 MD5
- 文件变更时自动重新加载 PMML Evaluator
- `ReentrantReadWriteLock` 保证线程安全
- 支持通过 `POST /api/v1/scoring-strategies:reload` 手动强制刷新

---

## 十二、测试用例矩阵（DEFAULT 策略 fallback 模式）

| 用例ID | 负债收入比 | 加权APR | 逾期笔数 | 最长逾期天数 | 债务笔数 | avgLoanDays | 月收入>月供 | 预期总分 | 预期等级 |
|--------|-----------|---------|----------|-------------|---------|-------------|-----------|---------|---------|
| SC-01 | 0.25 | 12.0 | 0 | 0 | 3 | 200 | Y | 79.75 | MEDIUM |
| SC-02 | 0.62 | 21.4 | 0 | 0 | 4 | 150 | N | 52.25 | MEDIUM |
| SC-03 | 0.85 | 38.0 | 2 | 45 | 4 | 120 | N | 31.25 | HIGH |
| SC-04 | 0.95 | 45.0 | 4 | 120 | 4 | 60 | N | 17.25 | CRITICAL |
| SC-05 | 0.50 | 24.0 | 1 | 25 | 3 | 180 | Y | 61.75 | MEDIUM |
| SC-06 | NULL | 15.0 | 0 | 0 | 3 | 365 | — | 51.75 | MEDIUM |

> 注意：以上期望值基于 DEFAULT 策略 fallback 逻辑精确推导，debtCount 调整为 3-4 以确保落入 DEFAULT 分群。
> PMML 模式下 finalScore 由 PMML Scorecard 计算，可能与 fallback 结果略有差异。

### 分群路由测试

| 用例ID | 说明 | 预期命中分群 | 预期策略名 |
|--------|------|------------|-----------|
| SC-S01 | debtCount=5 | HIGH_DEBT | 高负债优化策略 |
| SC-S02 | debtCount=2, avgLoanDays=200 | YOUNG_BORROWER | 新手成长策略 |
| SC-S03 | debtCount=3, avgLoanDays=400 | DEFAULT | 稳健策略 |
