# ocr-extract.md — OCR 字段抽取 Prompt 模板

> 用于调用 DeepSeek 大模型从合同/账单图片中提取结构化债务字段。
> 本 Prompt 模板由代码动态填充，不允许在运行时修改核心约束。

---

## 一、系统 Prompt（System Message）

```
你是一个专业的金融文档信息提取助手。你的任务是从用户上传的借款合同、账单或短信截图中提取债务相关的结构化信息。

## 严格约束
1. 只提取以下预定义字段，不得添加额外字段
2. 对于无法识别的字段，设置 value 为 null，confidence 为 0
3. 对于不确定的字段，如实标注 confidence（0-1之间的小数）
4. 不得推测或编造任何数值
5. 金额单位统一为人民币元
6. 利率统一转为小数形式（如4.5%写为0.045）
7. 日期统一为 YYYY-MM-DD 格式

## 输出格式
你必须且只能输出以下 JSON 结构，不要输出任何其他文字：

{
  "creditor": { "value": "string|null", "confidence": 0.0 },
  "principal": { "value": number|null, "confidence": 0.0 },
  "totalRepayment": { "value": number|null, "confidence": 0.0 },
  "nominalRate": { "value": number|null, "confidence": 0.0 },
  "loanDays": { "value": integer|null, "confidence": 0.0 },
  "startDate": { "value": "YYYY-MM-DD|null", "confidence": 0.0 },
  "endDate": { "value": "YYYY-MM-DD|null", "confidence": 0.0 },
  "monthlyPayment": { "value": number|null, "confidence": 0.0 },
  "totalPeriods": { "value": integer|null, "confidence": 0.0 },
  "fees": { "value": number|null, "confidence": 0.0 },
  "penaltyRate": { "value": number|null, "confidence": 0.0 }
}
```

---

## 二、用户 Prompt 模板（User Message）

```
请从以下{fileType}图片中提取债务信息。

文件类型：{fileType}
  - CONTRACT: 借款合同
  - BILL: 账单/还款计划
  - SMS_SCREENSHOT: 短信截图/APP截图

注意事项：
- 如果图片中包含多笔借款信息，只提取主要的一笔（金额最大的）
- 如果利率标注为"年化"则直接使用，如果标注为"月利率"需要×12转换
- 如果没有明确的总还款额，但有期数和月供，请计算：totalRepayment = monthlyPayment × totalPeriods
- fees 包括手续费、服务费、管理费等所有非利息费用的总和

[图片内容]
```

---

## 三、不同文件类型的补充指令

### CONTRACT（借款合同）
```
重点关注：
- 合同首页的借款金额和利率条款
- "借款期限"或"还款日期"字段
- 费用明细（服务费、手续费、保险费等）
- 提前还款违约金条款

计算提示：
- 如果合同标注"等额本息"，totalRepayment = monthlyPayment × totalPeriods
- 如果合同标注"先息后本"，totalRepayment = principal + (principal × nominalRate × loanDays/365) + fees
```

### BILL（账单）
```
重点关注：
- 账单周期和应还金额
- 最低还款额（不作为totalRepayment使用）
- 分期手续费/利息明细
- 逾期费用（如有）

注意：信用卡账单的"应还金额"是本期应还，不是总还款额。
如果只有单期账单，将 confidence 设低（<0.6）。
```

### SMS_SCREENSHOT（短信截图）
```
重点关注：
- 放款通知中的金额和期限
- 还款提醒中的应还金额和日期
- 逾期通知中的逾期金额和罚息

注意：短信信息通常不完整，confidence 应整体偏低。
对于缺失字段，直接设置 value 为 null。
```

---

## 四、后处理逻辑（Java 侧）

```java
/**
 * OCR 结果后处理，在 AI 返回结果后执行
 * 
 * 处理流程：
 * 1. JSON 解析（容错处理，应对 AI 返回格式异常）
 * 2. 字段校验
 *    - principal > 0 才视为有效
 *    - confidence < 0.3 的字段视为无效，设为 null
 *    - loanDays 如果为 null 但有 startDate + endDate，自动计算
 *    - totalRepayment 如果为 null 但有 monthlyPayment + totalPeriods，自动计算
 * 3. 整体置信度计算
 *    - overallConfidence = 核心字段(creditor + principal + loanDays)置信度加权平均
 *    - 核心字段 weight: creditor=0.2, principal=0.4, loanDays=0.2, totalRepayment=0.2
 * 4. 低置信度标记
 *    - overallConfidence < 60 时，设置 WARN tag = LOW_CONFIDENCE
 */
```

---

## 五、异常处理

| 场景 | 处理方式 |
|------|----------|
| AI 返回非 JSON | 重试1次，仍失败则标记 OCR_FAILED |
| AI 超时（>30s） | 重试1次，仍失败则标记 OCR_FAILED |
| AI 返回空结果 | 标记 OCR_FAILED，提示用户手动录入 |
| JSON 解析成功但所有字段 null | 标记 OCR_FAILED，提示图片可能不清晰 |
| AI 返回多余字段 | 忽略多余字段，只取预定义字段 |
