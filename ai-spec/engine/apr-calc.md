# apr-calc.md — APR 计算引擎规范

> 本文件定义 APR（实际年化利率）和加权 APR 的计算公式、边界条件和实现约束。
> 所有计算必须使用 BigDecimal，RoundingMode.HALF_UP，精度 scale=6。

---

## 一、APR 计算公式

### 1.1 简化 APR（MVP 阶段使用）

```
APR = (totalRepayment - principal) / principal × (365 / loanDays) × 100

其中：
  totalRepayment = 总还款额（含所有利息、手续费、服务费）
  principal      = 借款本金
  loanDays       = 借款天数（从放款日到最后还款日）
  APR            = 百分比形式的实际年化利率
```

### 1.2 Java 实现规范

```java
/**
 * 计算 APR（实际年化利率）
 *
 * @param principal      本金，必须 > 0
 * @param totalRepayment 总还款额，必须 >= principal
 * @param loanDays       借款天数，必须 > 0
 * @return APR 百分比值，scale=6
 * @throws BizException 参数不合法时
 */
public BigDecimal calculateApr(BigDecimal principal, BigDecimal totalRepayment, int loanDays) {
    // 1. 参数校验
    // 2. 计算利息 = totalRepayment - principal
    // 3. 计算利息率 = 利息 / principal
    // 4. 年化 = 利息率 × (365 / loanDays)
    // 5. 转百分比 = × 100
    // 6. 返回 scale=6 的 BigDecimal
}
```

### 1.3 中间过程精度要求

```
所有中间计算步骤：scale = 10，RoundingMode.HALF_UP
最终结果：scale = 6，RoundingMode.HALF_UP
不得在中间步骤做截断
```

---

## 二、加权 APR 计算

### 2.1 公式

```
weightedAPR = Σ(debtAPR_i × principal_i) / Σ(principal_i)

即：每笔债务的 APR × 该笔债务本金占总本金的比例，求和。
```

### 2.2 约束

```
- 只统计 status = IN_PROFILE 的债务
- 如果只有1笔债务，加权APR = 该笔APR
- 如果总本金为0（不应出现），返回 0 并记录 WARN 日志
- 结果 scale = 6
```

---

## 三、附加指标计算

### 3.1 日利率

```
dailyRate = (totalRepayment - principal) / principal / loanDays × 100
```

### 3.2 总利息

```
totalInterest = totalRepayment - principal
```

### 3.3 月供估算（等额本息近似）

```
如果用户未填写 monthlyPayment：
  estimatedMonthlyPayment = totalRepayment / ceil(loanDays / 30)
```

---

## 四、告警规则

| 条件 | 告警级别 | 处理 |
|------|----------|------|
| APR > 36% | WARNING | 标记为"高息债务"，前端标红 |
| APR > 100% | DANGER | 强提示"极高息债务"，建议优先处理 |
| APR > 1000% | ABNORMAL | 提示用户"数据可能有误，请核实" |
| APR > 10000% | REJECT | 拒绝入库，返回错误码 404002 |

配置化（从 application.yml 读取）：
```yaml
youhua:
  engine:
    apr:
      warning-threshold: 36.0
      danger-threshold: 100.0
      abnormal-threshold: 1000.0
      max-allowed: 10000.0
```

---

## 五、测试用例矩阵

### 5.1 正常场景

| 用例ID | 本金 | 总还款额 | 借款天数 | 预期APR(%) | 说明 |
|--------|------|----------|----------|------------|------|
| APR-N01 | 10000.00 | 10500.00 | 30 | 60.833333 | 短期小额 |
| APR-N02 | 100000.00 | 120000.00 | 365 | 20.000000 | 标准年化 |
| APR-N03 | 50000.00 | 52000.00 | 180 | 8.111111 | 中期 |
| APR-N04 | 200000.00 | 280000.00 | 730 | 20.000000 | 两年期 |
| APR-N05 | 5000.00 | 5100.00 | 7 | 104.285714 | 超短期高息 |
| APR-N06 | 1000000.00 | 1050000.00 | 365 | 5.000000 | 大额低息 |

### 5.2 边界场景

| 用例ID | 本金 | 总还款额 | 借款天数 | 预期结果 | 说明 |
|--------|------|----------|----------|----------|------|
| APR-B01 | 50000.00 | 50000.00 | 90 | 0.000000 | 零利息 |
| APR-B02 | 0.01 | 0.02 | 1 | 36500.000000 | 最小本金+最短天数 |
| APR-B03 | 9999999999999.9999 | 10000000000000.0000 | 365 | ~0.000001 | 最大本金边界 |

### 5.3 异常场景

| 用例ID | 本金 | 总还款额 | 借款天数 | 预期结果 | 说明 |
|--------|------|----------|----------|----------|------|
| APR-E01 | 0 | 500.00 | 30 | BizException(404001) | 本金为零 |
| APR-E02 | -5000.00 | 10000.00 | 30 | BizException(404001) | 本金为负 |
| APR-E03 | 10000.00 | 10500.00 | 0 | BizException(404001) | 天数为零 |
| APR-E04 | 10000.00 | 10500.00 | -30 | BizException(404001) | 天数为负 |
| APR-E05 | 10000.00 | 9000.00 | 30 | BizException(404001) | 还款额 < 本金 |
| APR-E06 | null | 10500.00 | 30 | BizException(404001) | 参数为null |
| APR-E07 | 1000.00 | 200000.00 | 3 | BizException(404002) | APR超上限 |

### 5.4 加权 APR 测试

| 用例ID | 债务集合 | 预期加权APR(%) | 说明 |
|--------|----------|----------------|------|
| WAPR-01 | [{p:100000, apr:20%}, {p:50000, apr:36%}] | 25.333333 | 两笔加权 |
| WAPR-02 | [{p:100000, apr:20%}] | 20.000000 | 单笔 |
| WAPR-03 | [{p:10000, apr:10%}, {p:10000, apr:20%}, {p:10000, apr:30%}] | 20.000000 | 等额三笔 |
| WAPR-04 | [] | 0.000000 + WARN日志 | 无债务 |
