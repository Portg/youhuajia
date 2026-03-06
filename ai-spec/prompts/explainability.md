# explainability.md — 可解释性输出规范

> 所有分析报告必须做到"数据有来源、评分有依据、建议有逻辑"。
> 本文件定义可解释性的展示规范和生成规则。

---

## 一、可解释性三层结构

```
Layer 1: 数据来源标识 — 用户看到的每个数据从哪里来
Layer 2: 计算过程透明 — 用户可以理解评分怎么算出来的
Layer 3: 建议逻辑说明 — 用户知道为什么给出这个建议
```

---

## 二、Layer 1 — 数据来源标识

### 补充：损失可视化数据（Page 4 专用，来自 user-journey.md）

```json
{
  "lossVisualization": {
    "threeYearExtraInterest": {
      "value": 82400.00,
      "formula": "Σ(debt_i.totalRepayment - debt_i.principal) × (3年/loanDays_i) 外推",
      "source": "CALCULATED",
      "displayFormat": "如果维持当前结构，3 年将多支付 {value} 元",
      "analogy": "相当于 {value/avgMonthlyRent} 个月房租",
      "visualStyle": "大字体 + 数字滚动动画 + 橙色强调"
    },
    "currentVsHealthy": {
      "currentWeightedApr": 24.0,
      "marketAvgApr": 8.5,
      "gap": 15.5,
      "displayFormat": "你的综合利率 {current}%，市场均值 {avg}%"
    },
    "monthlyPressure": {
      "ratio": 0.65,
      "healthyLine": 0.40,
      "displayFormat": "月供占收入 {ratio}%，健康线为 {healthyLine}% 以下"
    }
  }
}
```

**计算约束**：
- `threeYearExtraInterest` 由 Java 引擎计算，不由 AI 生成
- 类比数据（月房租等）从配置读取城市均值
- 如果用户未填收入，`monthlyPressure` 不展示，改为"填写收入获取更精确分析"

### 每个数据字段必须标注来源

```json
{
  "field": "principal",
  "value": 100000.00,
  "source": "MANUAL",           // MANUAL | OCR | BANK_API | CALCULATED
  "sourceDetail": "用户手动录入",  // 人类可读的来源说明
  "confidence": null,            // OCR 来源时显示置信度
  "lastUpdated": "2026-03-04T10:00:00"
}
```

### 来源类型说明

| source | 说明 | 前端展示 |
|--------|------|----------|
| MANUAL | 用户手动填写 | 📝 手动录入 |
| OCR | AI 识别（含置信度） | 🤖 AI识别 (置信度 85%) |
| BANK_API | 银行直连（V2.0） | 🏦 银行同步 |
| CALCULATED | 系统计算 | 🔢 系统计算 |

### OCR 来源的特殊展示

```
当 source == OCR 时：
  IF confidence >= 80:  显示绿色 ✅ "AI识别（置信度高）"
  IF confidence >= 60:  显示黄色 ⚠️ "AI识别（建议核对）"
  IF confidence < 60:   显示红色 ❗ "AI识别（置信度低，请确认）"
```

---

## 三、Layer 2 — 评分过程透明

### 3.1 评分结果展示结构

```json
{
  "title": "重组可行性评分",
  "finalScore": 68.00,
  "riskLevel": "MEDIUM",
  "riskLevelLabel": "中等风险",

  "dimensions": [
    {
      "name": "负债收入比",
      "icon": "📊",
      "weight": "30%",
      "rawValue": "62%",
      "score": 50,
      "weightedScore": 15.00,
      "impact": "NEGATIVE",
      "explanation": "您的月供占收入的62%，超过50%的警戒线，这是影响您评分的最大因素"
    },
    {
      "name": "综合利率",
      "icon": "📈",
      "weight": "25%",
      "rawValue": "21.4%",
      "score": 55,
      "weightedScore": 13.75,
      "impact": "NEGATIVE",
      "explanation": "您的加权年化利率为21.4%，处于中等偏高水平，存在优化空间"
    },
    {
      "name": "逾期情况",
      "icon": "⏰",
      "weight": "20%",
      "rawValue": "无逾期",
      "score": 95,
      "weightedScore": 19.00,
      "impact": "POSITIVE",
      "explanation": "您没有逾期记录，这是非常好的信用表现"
    },
    {
      "name": "资产流动性",
      "icon": "💰",
      "weight": "15%",
      "rawValue": "收入低于月供",
      "score": 30,
      "weightedScore": 4.50,
      "impact": "NEGATIVE",
      "explanation": "您的月收入暂时不能完全覆盖月供，建议关注现金流"
    },
    {
      "name": "信用稳定度",
      "icon": "🛡️",
      "weight": "10%",
      "rawValue": "4笔债务",
      "score": 60,
      "weightedScore": 6.00,
      "impact": "NEUTRAL",
      "explanation": "您有4笔在还债务，属于中等水平"
    }
  ],

  "scoreFormula": "15.00 + 13.75 + 19.00 + 4.50 + 6.00 = 58.25",
  
  "keyInsight": "影响您评分的前两个因素是「负债收入比偏高」和「综合利率偏高」"
}
```

### 3.2 impact 判定规则

```
IF dimensionScore >= 70: impact = "POSITIVE"
IF dimensionScore >= 40 AND < 70: impact = "NEUTRAL"
IF dimensionScore < 40: impact = "NEGATIVE"
```

### 3.3 explanation 生成

explanation 字段由 AI 生成，Prompt 如下：

```
请为以下评分维度生成一句通俗的解释（30-60字），让普通用户能理解：

维度名称：{dimensionName}
原始值：{rawValue}
评分：{score}/100
影响方向：{impact}

要求：
- 用"您"称呼用户
- 引用具体数值
- 如果是 NEGATIVE，给出改善方向但不制造焦虑
- 如果是 POSITIVE，给予肯定

只输出一句话，不要输出其他内容。
```

---

## 四、Layer 3 — 建议逻辑说明

### 每条建议必须包含 reason 字段

```json
{
  "action": "优先偿还平安普惠消费贷",
  "reason": "该笔债务年化利率 30% 为所有债务中最高，且本金仅3万元，全额清偿可每年节省约9000元利息",
  "dataReference": {
    "debtId": 10003,
    "apr": 30.0,
    "principal": 30000.00,
    "annualInterestSaved": 9000.00
  }
}
```

### reason 的生成约束

```
reason 必须满足：
1. 引用至少一个具体数值
2. 说明"为什么是这笔"（比较逻辑）
3. 说明"预期效果"（量化收益）
4. 长度 30-100 字
```

---

## 五、前端展示规范

### 5.1 报告页信息层级

```
第一层：核心数据卡片（总负债 + 加权APR + 月供 + 评分）
  ↓ 点击展开
第二层：评分五维雷达图 + 各维度得分
  ↓ 点击维度
第三层：该维度的详细解释 + 数据来源
```

### 5.2 数据来源提示

```
在报告底部固定展示：

"本报告基于您提供的债务和收入数据生成。
数据来源：手动录入 {manualCount} 笔、AI识别 {ocrCount} 笔。
评分模型版本：v1.0
报告生成时间：{generatedAt}
提示：评分仅供参考，不构成任何金融建议。"
```

### 5.3 不确定性标注

```
当报告中存在以下情况时，在对应位置显示提示：

1. 无收入数据：
   "⚠️ 您尚未填写收入信息，负债收入比和部分建议基于估算值。
    填写收入信息可获得更准确的分析。[去填写 →]"

2. OCR 低置信度（<70）：
   "⚠️ 部分债务数据由AI识别，置信度偏低，建议人工核对后再查看报告。
    [查看待核对数据 →]"

3. 债务不完整（有草稿未确认）：
   "ℹ️ 您有 {draftCount} 笔债务尚未确认，确认后报告将更准确。
    [去确认 →]"
```
