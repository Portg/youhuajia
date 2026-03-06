# rules.md — 业务规则引擎定义

> 规则引擎在评分模型之前运行，负责硬性校验和数据过滤。
> 规则结果为 PASS / BLOCK / WARN，BLOCK 直接终止流程。

---

## 一、规则执行顺序

```
1. 数据完整性规则（DATA_*）       → BLOCK 级
2. 数值合理性规则（VALUE_*）      → BLOCK / WARN 级
3. 业务逻辑规则（BIZ_*）         → WARN 级
4. 反欺诈规则（FRAUD_*）         → BLOCK 级
```

所有规则按序执行，遇到 BLOCK 立即终止，WARN 累计记录但不终止。

---

## 二、数据完整性规则

```
RULE DATA_001: 至少一笔已确认债务
  IF confirmed_debt_count == 0:
    RESULT = BLOCK
    MESSAGE = "暂无已确认的债务数据，无法生成画像"
    ERROR_CODE = 403001

RULE DATA_002: 本金大于零
  FOR EACH debt IN confirmed_debts:
    IF debt.principal <= 0:
      RESULT = BLOCK
      MESSAGE = "存在本金为零或负数的债务记录"
      ERROR_CODE = 402002

RULE DATA_003: 借款天数大于零
  FOR EACH debt IN confirmed_debts:
    IF debt.loanDays <= 0:
      RESULT = BLOCK
      MESSAGE = "存在借款天数为零或负数的债务记录"
      ERROR_CODE = 402004

RULE DATA_004: 总还款额不小于本金
  FOR EACH debt IN confirmed_debts:
    IF debt.totalRepayment < debt.principal:
      RESULT = BLOCK
      MESSAGE = "存在总还款额小于本金的债务记录"
      ERROR_CODE = 402003
```

---

## 三、数值合理性规则

```
RULE VALUE_001: APR 超上限检查
  FOR EACH debt IN confirmed_debts:
    IF debt.apr > config.apr.max-allowed (默认10000):
      RESULT = BLOCK
      MESSAGE = "APR 计算结果异常，请核实债务数据"
      ERROR_CODE = 404002

RULE VALUE_002: APR 高息预警
  FOR EACH debt IN confirmed_debts:
    IF debt.apr > config.apr.warning-threshold (默认36):
      RESULT = WARN
      TAG = "HIGH_INTEREST"
      MESSAGE = "债务 {creditor} 实际年化 {apr}%，属于高息债务"

RULE VALUE_003: 月供超收入预警
  IF monthlyIncome IS NOT NULL AND monthlyPayment > monthlyIncome:
    RESULT = WARN
    TAG = "PAYMENT_EXCEED_INCOME"
    MESSAGE = "月供总额已超过月收入，财务压力较大"

RULE VALUE_004: 负债收入比超标预警
  IF debtIncomeRatio > 0.9:
    RESULT = WARN
    TAG = "EXTREME_DEBT_RATIO"
    MESSAGE = "负债收入比超过90%，财务状况需紧急关注"

RULE VALUE_005: 总负债异常检查
  IF totalDebt > 10000000 (千万):
    RESULT = WARN
    TAG = "HIGH_TOTAL_DEBT"
    MESSAGE = "总负债金额较大，请确认数据准确性"
```

---

## 四、业务逻辑规则

```
RULE BIZ_001: 重组评分阈值
  IF restructureScore >= config.scoring.restructure-threshold (默认60):
    TAG = "RESTRUCTURE_RECOMMENDED"
  ELSE:
    TAG = "OPTIMIZE_FIRST"

RULE BIZ_002: 优先处理顺序
  优先级排序规则（用于生成优化建议的排序）：
    SORT debts BY:
      1. overdue_status DESC (逾期的优先)
      2. apr DESC (利率高的优先)
      3. remaining_principal ASC (余额小的优先清偿)

RULE BIZ_003: 逾期债务紧急标记
  FOR EACH debt IN confirmed_debts:
    IF debt.overdueStatus IN (OVERDUE_60, OVERDUE_90_PLUS):
      RESULT = WARN
      TAG = "URGENT_OVERDUE"
      MESSAGE = "存在严重逾期债务，建议立即处理"

RULE BIZ_004: OCR 低置信度提醒
  FOR EACH debt IN confirmed_debts:
    IF debt.sourceType == OCR AND debt.confidenceScore < 70:
      RESULT = WARN
      TAG = "LOW_CONFIDENCE"
      MESSAGE = "债务 {creditor} 的OCR识别置信度较低（{confidenceScore}%），建议人工核对"
```

---

## 五、反欺诈规则

```
RULE FRAUD_001: 短时间大量录入
  IF debt_create_count_in_last_hour > 20:
    RESULT = BLOCK
    MESSAGE = "操作过于频繁，请稍后再试"
    LOG_LEVEL = WARN
    TAG = "SUSPICIOUS_BATCH"

RULE FRAUD_002: 重复债务检测
  FOR EACH new_debt:
    IF EXISTS debt WHERE
      debt.userId == new_debt.userId AND
      debt.creditor == new_debt.creditor AND
      debt.principal == new_debt.principal AND
      debt.loanDays == new_debt.loanDays AND
      debt.status != DELETED:
    RESULT = WARN
    MESSAGE = "检测到可能重复的债务记录，请确认"
    TAG = "DUPLICATE_SUSPECT"

RULE FRAUD_003: 画像频繁计算
  IF profile_calculate_count_in_last_hour > 10:
    RESULT = BLOCK
    MESSAGE = "画像正在计算中，请稍后"
    ERROR_CODE = 403003
```

---

## 六、规则输出结构

```json
{
  "ruleResults": [
    {
      "ruleId": "DATA_001",
      "result": "PASS",
      "message": null
    },
    {
      "ruleId": "VALUE_002",
      "result": "WARN",
      "tag": "HIGH_INTEREST",
      "message": "债务 招商银行 实际年化 42.5%，属于高息债务",
      "relatedDebtId": 10001
    }
  ],
  "blocked": false,
  "warnings": ["HIGH_INTEREST", "PAYMENT_EXCEED_INCOME"],
  "canProceed": true
}
```

---

## 七、规则配置化

所有阈值从配置中心读取，不得硬编码：

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
