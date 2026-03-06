# ai-service-impl-spec.md -- AI 服务模块实现规范

> 本文件由架构师 spec 根据 ocr-extract.md、suggestion-gen.md、user-journey.md、CLAUDE.md 综合输出。
> 用于指导 ocr-coder 和 suggestion-coder 的编码实现。

---

## 6.1 OcrExtractService 实现规范

### 6.1.1 类定义

- **接口路径**：`com.youhua.ai.ocr.service.OcrExtractService`
- **实现类路径**：`com.youhua.ai.ocr.service.impl.OcrExtractServiceImpl`
- **职责**：调用 DeepSeek 大模型，从用户上传的合同/账单/短信截图中提取结构化债务字段
- **Bean 注册**：`@Service`

### 6.1.2 接口定义

```java
public interface OcrExtractService {
    /**
     * 从图片中提取结构化债务字段。
     *
     * @param imageBase64 图片的 Base64 编码字符串
     * @param fileType    文件类型（CONTRACT / BILL / SMS_SCREENSHOT）
     * @return OCR 提取结果
     */
    OcrExtractResult extract(String imageBase64, OcrFileType fileType);
}
```

### 6.1.3 DTO 定义 -- OcrExtractResult

路径：`com.youhua.ai.ocr.dto.OcrExtractResult`

```
OcrExtractResult:
  success        : boolean           -- 是否提取成功
  overallConfidence : BigDecimal     -- 整体置信度 (0-100, scale=2)
  lowConfidence  : boolean           -- overallConfidence < 60 时为 true
  fields         : OcrExtractedFields -- 提取的结构化字段
  errorCode      : String (nullable) -- 失败时的错误码 (OCR_FAILED / OCR_PARSE_ERROR / OCR_TIMEOUT)
  errorMessage   : String (nullable) -- 失败时的可读错误信息
```

路径：`com.youhua.ai.ocr.dto.OcrExtractedFields`

```
OcrExtractedFields:
  creditor       : OcrField<String>      -- 债权机构名称
  principal      : OcrField<BigDecimal>  -- 借款本金（元）
  totalRepayment : OcrField<BigDecimal>  -- 总还款额（元）
  nominalRate    : OcrField<BigDecimal>  -- 名义利率（小数形式，如 0.045）
  loanDays       : OcrField<Integer>     -- 借款天数
  startDate      : OcrField<LocalDate>   -- 起始日期
  endDate        : OcrField<LocalDate>   -- 结束日期
  monthlyPayment : OcrField<BigDecimal>  -- 月供（元）
  totalPeriods   : OcrField<Integer>     -- 总期数
  fees           : OcrField<BigDecimal>  -- 费用总额（元）
  penaltyRate    : OcrField<BigDecimal>  -- 罚息利率（小数形式）
```

路径：`com.youhua.ai.ocr.dto.OcrField<T>`

```
OcrField<T>:
  value      : T (nullable)    -- 提取值，null 表示未识别
  confidence : BigDecimal      -- 置信度 (0.0-1.0, scale=2)
```

**所有金额/利率字段必须使用 BigDecimal（F-01）。**

### 6.1.4 Spring AI ChatClient 调用方式

- 使用 Spring AI 的 `ChatClient`，配置 model = `deepseek-chat`
- 配置来源：`application.yml` 中的 `youhua.ai` 段

**调用流程**：

1. 构建 System Message（见 6.1.5）
2. 根据 `fileType` 选择对应的补充指令，拼接到 User Message
3. 将 `imageBase64` 作为图片内容附加到 User Message
4. 调用 ChatClient，设置超时 30s（从 `youhua.ai.timeout-seconds` 读取）
5. 解析返回的 JSON 为 OcrExtractedFields
6. 执行后处理逻辑
7. 返回 OcrExtractResult

**超时与重试配置**：

- 单次调用超时：30 秒（从 `youhua.ai.timeout-seconds` 配置读取）
- 最大重试次数：1 次（总共最多 2 次调用）
- 重试条件：AI 返回非 JSON **或** AI 调用超时
- 重试间隔：无需等待，立即重试
- 重试后仍失败：返回 `success=false`，`errorCode=OCR_FAILED`

### 6.1.5 Prompt 模板

**System Message（完整内容，直接硬编码为 Java 常量字符串）**：

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

**User Message 模板**：

```
请从以下{fileType}图片中提取债务信息。

文件类型：{fileType}
  - CONTRACT: 借款合同
  - BILL: 账单/还款计划
  - SMS_SCREENSHOT: 短信截图/APP截图

注意事项：
- 如果图片中包含多笔借款信息，只提取主要的一笔（金额最大的）
- 如果利率标注为"年化"则直接使用，如果标注为"月利率"需要x12转换
- 如果没有明确的总还款额，但有期数和月供，请计算：totalRepayment = monthlyPayment x totalPeriods
- fees 包括手续费、服务费、管理费等所有非利息费用的总和

[图片内容]
```

**文件类型补充指令**（根据 `fileType` 追加到 User Message 末尾）：

- **CONTRACT**：重点关注合同首页的借款金额和利率条款、借款期限、费用明细、提前还款违约金；等额本息时 totalRepayment = monthlyPayment x totalPeriods，先息后本时 totalRepayment = principal + (principal x nominalRate x loanDays/365) + fees
- **BILL**：重点关注账单周期和应还金额、分期手续费/利息明细、逾期费用；信用卡账单的"应还金额"是本期应还非总还款额；单期账单 confidence 设低(<0.6)
- **SMS_SCREENSHOT**：重点关注放款通知金额和期限、还款提醒金额和日期、逾期通知金额和罚息；短信信息通常不完整，confidence 应整体偏低

### 6.1.6 JSON 解析与后处理逻辑

**步骤 1 -- JSON 解析容错**：

1. 尝试直接 `JSON.parse` AI 返回内容
2. 如果失败，尝试提取返回内容中被 ``` 包裹的 JSON 块
3. 如果仍失败，尝试提取第一个 `{` 到最后一个 `}` 之间的内容
4. 如果三步都失败，标记为解析错误，触发重试

**步骤 2 -- 字段校验**：

| 规则 | 说明 |
|------|------|
| principal 校验 | `principal.value <= 0` 时视为无效，设为 null |
| 低置信度过滤 | `confidence < 0.3` 的字段，`value` 强制设为 null |
| loanDays 自动计算 | 如果 `loanDays` 为 null 但 `startDate` 和 `endDate` 都有值，自动计算天数差 |
| totalRepayment 自动计算 | 如果 `totalRepayment` 为 null 但 `monthlyPayment` 和 `totalPeriods` 都有值，自动计算 `monthlyPayment * totalPeriods` |

**步骤 3 -- 整体置信度计算**：

```
overallConfidence = creditor.confidence * 0.2
                  + principal.confidence * 0.4
                  + loanDays.confidence  * 0.2
                  + totalRepayment.confidence * 0.2

overallConfidence 转为百分制 (x100)，保留 2 位小数
```

**步骤 4 -- 低置信度标记**：

- `overallConfidence < 60` 时，`OcrExtractResult.lowConfidence = true`

### 6.1.7 异常处理矩阵

| 场景 | 处理方式 | errorCode |
|------|----------|-----------|
| AI 返回非 JSON | 重试 1 次，仍失败返回 `success=false` | `OCR_PARSE_ERROR` |
| AI 调用超时 (>30s) | 重试 1 次，仍失败返回 `success=false` | `OCR_TIMEOUT` |
| AI 返回空结果 | 直接返回 `success=false`，提示图片可能不清晰 | `OCR_FAILED` |
| JSON 解析成功但所有核心字段 null | 返回 `success=false` | `OCR_FAILED` |
| AI 返回多余字段 | 忽略多余字段，只取预定义字段 | 无 |
| AI 服务不可用 | 抛出 `BizException(ErrorCode.AI_UNAVAILABLE)` | -- |

**注意**：重试次数上限从 `youhua.ai.max-retries` 读取（当前配置为 3），但 OCR 提取场景仅重试 1 次（即最多调用 2 次 AI）。

### 6.1.8 日志要求

- DEBUG 级别：记录 AI 返回的原始 JSON（截断到 2000 字符）
- DEBUG 级别：记录后处理每个步骤的中间结果
- INFO 级别：记录提取结果摘要（success/fail、overallConfidence、耗时ms）
- WARN 级别：记录低置信度结果
- ERROR 级别：记录 AI 调用异常
- **禁止**：日志中不得输出图片 Base64 内容（合规 F-04，防止泄露用户文档）

### 6.1.9 Mock 测试用例（5 组）

**用例 1 -- 标准借款合同（高置信度）**

```json
// AI 返回
{
  "creditor": { "value": "招商银行", "confidence": 0.95 },
  "principal": { "value": 50000.00, "confidence": 0.92 },
  "totalRepayment": { "value": 62400.00, "confidence": 0.88 },
  "nominalRate": { "value": 0.086, "confidence": 0.90 },
  "loanDays": { "value": 365, "confidence": 0.85 },
  "startDate": { "value": "2025-01-15", "confidence": 0.90 },
  "endDate": { "value": "2026-01-15", "confidence": 0.90 },
  "monthlyPayment": { "value": 5200.00, "confidence": 0.88 },
  "totalPeriods": { "value": 12, "confidence": 0.92 },
  "fees": { "value": 500.00, "confidence": 0.75 },
  "penaltyRate": { "value": 0.0005, "confidence": 0.60 }
}
// 期望结果
// success=true, overallConfidence=90.20, lowConfidence=false
// 所有字段正常映射
```

**用例 2 -- 短信截图（低置信度，部分字段缺失）**

```json
// AI 返回
{
  "creditor": { "value": "某消费金融", "confidence": 0.60 },
  "principal": { "value": 20000.00, "confidence": 0.55 },
  "totalRepayment": { "value": null, "confidence": 0 },
  "nominalRate": { "value": null, "confidence": 0 },
  "loanDays": { "value": null, "confidence": 0 },
  "startDate": { "value": null, "confidence": 0 },
  "endDate": { "value": null, "confidence": 0 },
  "monthlyPayment": { "value": 2100.00, "confidence": 0.50 },
  "totalPeriods": { "value": 12, "confidence": 0.45 },
  "fees": { "value": null, "confidence": 0 },
  "penaltyRate": { "value": null, "confidence": 0 }
}
// 期望结果
// success=true, overallConfidence=34.00, lowConfidence=true
// totalRepayment 自动计算：2100 * 12 = 25200
// 但注意 monthlyPayment.confidence=0.50 和 totalPeriods.confidence=0.45 都 > 0.3，字段有效
```

**用例 3 -- AI 返回非 JSON（重试后成功）**

```
// 第一次 AI 返回
"抱歉，我无法识别这张图片的内容..."

// 第二次 AI 返回（重试）
{
  "creditor": { "value": "微粒贷", "confidence": 0.80 },
  "principal": { "value": 30000.00, "confidence": 0.85 },
  ...（正常 JSON）
}
// 期望结果
// success=true，使用第二次返回的结果
```

**用例 4 -- 置信度极低字段被过滤**

```json
// AI 返回
{
  "creditor": { "value": "某某公司", "confidence": 0.25 },
  "principal": { "value": 10000.00, "confidence": 0.88 },
  "totalRepayment": { "value": 12000.00, "confidence": 0.20 },
  "nominalRate": { "value": 0.10, "confidence": 0.15 },
  "loanDays": { "value": 180, "confidence": 0.70 },
  "startDate": { "value": "2025-06-01", "confidence": 0.10 },
  "endDate": { "value": "2025-12-01", "confidence": 0.10 },
  "monthlyPayment": { "value": 2000.00, "confidence": 0.60 },
  "totalPeriods": { "value": 6, "confidence": 0.70 },
  "fees": { "value": 200.00, "confidence": 0.05 },
  "penaltyRate": { "value": null, "confidence": 0 }
}
// 期望结果
// creditor.value -> null（confidence 0.25 < 0.3）
// totalRepayment.value -> null（confidence 0.20 < 0.3）
// nominalRate.value -> null（confidence 0.15 < 0.3）
// startDate.value -> null, endDate.value -> null（confidence < 0.3）
// fees.value -> null（confidence 0.05 < 0.3）
// 自动计算：totalRepayment = 2000 * 6 = 12000（因为 monthlyPayment 和 totalPeriods 有效）
// overallConfidence = 0 * 0.2 + 0.88 * 0.4 + 0.70 * 0.2 + (重算后 totalRepayment confidence) * 0.2
// 注意：自动计算的 totalRepayment 的 confidence 取 min(monthlyPayment.confidence, totalPeriods.confidence) = 0.60
// overallConfidence = (0 * 0.2 + 0.88 * 0.4 + 0.70 * 0.2 + 0.60 * 0.2) * 100 = 61.20
// lowConfidence=false (61.20 >= 60)
```

**用例 5 -- 完全无法识别（所有核心字段 null）**

```json
// AI 返回
{
  "creditor": { "value": null, "confidence": 0 },
  "principal": { "value": null, "confidence": 0 },
  "totalRepayment": { "value": null, "confidence": 0 },
  "nominalRate": { "value": null, "confidence": 0 },
  "loanDays": { "value": null, "confidence": 0 },
  "startDate": { "value": null, "confidence": 0 },
  "endDate": { "value": null, "confidence": 0 },
  "monthlyPayment": { "value": null, "confidence": 0 },
  "totalPeriods": { "value": null, "confidence": 0 },
  "fees": { "value": null, "confidence": 0 },
  "penaltyRate": { "value": null, "confidence": 0 }
}
// 期望结果
// success=false, errorCode=OCR_FAILED
// errorMessage="图片内容无法识别，建议手动录入"
```

### 6.1.10 与 OcrTaskService 的集成方式

`OcrExtractService` 是纯粹的 AI 调用封装，不涉及数据库操作。`OcrTaskService`（已存在）负责：
1. 创建 OcrTask 记录（状态 PENDING）
2. 调用 `OcrExtractService.extract()`
3. 将结果写入 `OcrTask.rawResultJson` 和 `OcrTask.extractedFieldsJson`
4. 更新 `OcrTask.status` 和 `OcrTask.confidenceScore`

实现者只需关注 `OcrExtractService` 本身的 AI 调用和后处理逻辑。

---

## 6.2 SuggestionGenService 实现规范

### 6.2.1 类定义

- **接口路径**：`com.youhua.ai.suggestion.service.SuggestionGenService`
- **实现类路径**：`com.youhua.ai.suggestion.service.impl.SuggestionGenServiceImpl`
- **职责**：基于用户财务画像和规则引擎输出，调用 DeepSeek 生成个性化优化建议文案
- **Bean 注册**：`@Service`

### 6.2.2 核心约束（红线）

| 编号 | 约束 | 来源 |
|------|------|------|
| C-01 | AI 只负责文案生成，所有数值来自 Java 侧计算结果 | F-02 |
| C-02 | AI 不得进行 APR/评分/加权利率计算 | F-02 |
| C-03 | AI 不得推荐具体金融产品、APP、平台 | suggestion-gen.md |
| C-04 | AI 不得建议"以贷还贷""借新还旧" | suggestion-gen.md |
| C-05 | AI 不得使用恐慌性表达 | F-11 |
| C-06 | 评分<60 不展示"申请失败""不符合条件" | F-13 |
| C-07 | 所有金额使用 BigDecimal | F-01 |

### 6.2.3 接口定义

```java
public interface SuggestionGenService {
    /**
     * 基于用户财务画像生成个性化优化建议。
     *
     * @param input 建议生成所需的输入数据（来自画像 + 评分 + 规则引擎）
     * @return AI 生成的建议结果
     */
    SuggestionResult generate(SuggestionInput input);
}
```

### 6.2.4 DTO 定义 -- 输入

路径：`com.youhua.ai.suggestion.dto.SuggestionInput`

```
SuggestionInput:
  -- 来自 FinanceProfile
  totalDebt           : BigDecimal        -- 总负债（元）
  debtCount           : int               -- 债务笔数
  weightedApr         : BigDecimal        -- 加权年化利率（百分比形式，如 21.4）
  monthlyPayment      : BigDecimal        -- 月供总额（元）
  monthlyIncome       : BigDecimal (nullable) -- 月收入（元），可能为 null
  debtIncomeRatio     : BigDecimal (nullable) -- 负债收入比（小数形式，如 0.65）
  restructureScore    : BigDecimal        -- 重组评分（0-100）
  riskLevel           : RiskLevel         -- 风险等级（LOW/MEDIUM/HIGH/CRITICAL）

  -- 来自 List<Debt>（按 APR 降序排列）
  debts               : List<DebtSummary> -- 债务明细摘要列表

  -- 来自 ScoringEngine.ScoreResult
  topFactor1          : String            -- 影响最大的评分维度名称（如"综合利率"）
  topFactor2          : String            -- 第二影响评分维度名称

  -- 来自规则引擎
  recommendation      : String            -- 推荐策略（RESTRUCTURE_RECOMMENDED / OPTIMIZE_FIRST / CREDIT_BUILDING）
  priorityDebtIds     : List<Long>        -- 优先处理的债务 ID（前 3 笔）
  hasOverdue          : boolean           -- 是否有逾期
  paymentExceedIncome : boolean           -- 月供是否超过收入
```

路径：`com.youhua.ai.suggestion.dto.DebtSummary`

```
DebtSummary:
  debtId         : Long
  creditor       : String       -- 债权机构
  principal      : BigDecimal   -- 本金（元）
  apr            : BigDecimal   -- 年化利率（百分比形式）
  monthlyPayment : BigDecimal   -- 月供（元）
  overdueStatus  : String       -- 逾期状态描述（正常/逾期30天内/...）
```

### 6.2.5 DTO 定义 -- 输出

路径：`com.youhua.ai.suggestion.dto.SuggestionResult`

```
SuggestionResult:
  success        : boolean
  summary        : String            -- 财务状况总结（50-200 字）
  keyFindings    : List<String>      -- 关键发现列表（3 条）
  actionPlan     : ActionPlan        -- 90 天行动计划（三阶段）
  warnings       : List<String>      -- 风险提示列表
  encouragement  : String            -- 鼓励语
  errorCode      : String (nullable) -- 失败时错误码
  errorMessage   : String (nullable) -- 失败时错误信息
```

路径：`com.youhua.ai.suggestion.dto.ActionPlan`

```
ActionPlan:
  phase1 : Phase   -- 第 1-30 天
  phase2 : Phase   -- 第 31-60 天
  phase3 : Phase   -- 第 61-90 天
```

路径：`com.youhua.ai.suggestion.dto.Phase`

```
Phase:
  title   : String         -- 阶段标题（如"第1-30天：稳定现状"）
  actions : List<Action>   -- 行动项列表
```

路径：`com.youhua.ai.suggestion.dto.Action`

```
Action:
  priority        : int     -- 优先级（1 最高）
  action          : String  -- 具体行动描述（20-200 字）
  reason          : String  -- 原因说明
  expectedBenefit : String  -- 预期效果（引用输入数据）
```

### 6.2.6 Spring AI ChatClient 调用方式

- 使用 Spring AI 的 `ChatClient`，配置 model = `deepseek-chat`
- 超时配置：30 秒（从 `youhua.ai.timeout-seconds` 读取）
- 不重试：建议生成失败时直接返回 `success=false`，报告中不含 AI 建议即可（对应 ErrorCode `AI_SUGGESTION_FAILED`）

**调用流程**：

1. 构建 System Message（见 6.2.7）
2. 根据 `riskLevel` 追加对应的风险等级补充指令
3. 填充 User Message 模板变量（见 6.2.8）
4. 调用 ChatClient
5. 解析返回的 JSON
6. 执行后处理（合规过滤 + 数值一致性校验 + 长度校验）
7. 返回 SuggestionResult

### 6.2.7 Prompt 模板 -- System Message

```
你是一名专业的财务优化顾问。你的任务是根据用户的财务数据分析结果，生成通俗易懂、具有可操作性的债务优化建议。

## 严格约束
1. 你不是在提供金融产品推荐或借贷服务
2. 不得建议用户"以贷还贷""借新还旧"
3. 不得推荐任何具体金融产品、APP、平台
4. 不得暗示用户可以通过你或平台获得贷款
5. 所有数值直接引用输入数据中的 calculatedData，不得自行计算或推测
6. 建议必须具体、可执行，不要说空话
7. 语气专业但温和，不制造焦虑
8. 如果用户有逾期，优先建议处理逾期

## 输出格式
你必须严格按以下 JSON 结构输出，不要输出任何其他文字：

{
  "summary": "一段话总结用户财务状况（50-100字）",
  "keyFindings": [
    "发现1（具体引用数据）",
    "发现2",
    "发现3"
  ],
  "actionPlan": {
    "phase1": {
      "title": "第1-30天：{主题}",
      "actions": [
        {
          "priority": 1,
          "action": "具体行动描述",
          "reason": "为什么要这样做",
          "expectedBenefit": "预期效果（引用数据）"
        }
      ]
    },
    "phase2": {
      "title": "第31-60天：{主题}",
      "actions": []
    },
    "phase3": {
      "title": "第61-90天：{主题}",
      "actions": []
    }
  },
  "warnings": ["风险提示1", "风险提示2"],
  "encouragement": "一句鼓励的话"
}
```

### 6.2.8 Prompt 模板 -- User Message

模板中的变量由 Java 侧填充，AI 不得自行计算任何数值。

```
请根据以下用户的财务数据分析结果，生成个性化的债务优化建议。

## 用户财务画像
- 总负债：{totalDebt} 元
- 债务笔数：{debtCount} 笔
- 加权年化利率：{weightedAPR}%
- 月供总额：{monthlyPayment} 元
- 月收入：{monthlyIncome} 元
- 负债收入比：{debtIncomeRatio}（{debtIncomeRatioPercent}%）
- 重组评分：{restructureScore} 分
- 风险等级：{riskLevel}

## 债务明细（按APR从高到低排序）
{debtList}

## 评分维度分析
- 影响最大的因素：{topFactor1}
- 第二影响因素：{topFactor2}

## 系统建议方向（基于规则引擎输出）
- 推荐策略：{recommendation}
- 优先处理的债务ID：{priorityDebtIds}
- 是否有逾期：{hasOverdue}
- 是否月供超收入：{paymentExceedIncome}

请基于以上数据生成优化建议。注意：所有数值直接引用上面的数据，不要自己计算。
```

### 6.2.9 变量填充规则（Java 侧）

| 模板变量 | 数据来源 | 格式化规则 |
|----------|----------|------------|
| `{totalDebt}` | `input.totalDebt` | 千分位格式（如 150,000.00） |
| `{debtCount}` | `input.debtCount` | 整数 |
| `{weightedAPR}` | `input.weightedApr` | 保留 2 位小数 |
| `{monthlyPayment}` | `input.monthlyPayment` | 千分位格式 |
| `{monthlyIncome}` | `input.monthlyIncome` | 如果 null 则写"未提供"，否则千分位格式 |
| `{debtIncomeRatio}` | `input.debtIncomeRatio` | 保留 4 位小数（如 0.6500） |
| `{debtIncomeRatioPercent}` | `debtIncomeRatio * 100` | 保留 1 位小数（如 65.0） |
| `{restructureScore}` | `input.restructureScore` | 保留 2 位小数 |
| `{riskLevel}` | `input.riskLevel` | 映射中文：LOW->低, MEDIUM->中, HIGH->高, CRITICAL->极高 |
| `{debtList}` | `input.debts` | 每笔一行，格式："1. {creditor}，本金{principal}元，年化{apr}%，月供{monthlyPayment}元，{overdueStatus}" |
| `{topFactor1}` | `input.topFactor1` | 直接使用 |
| `{topFactor2}` | `input.topFactor2` | 直接使用 |
| `{recommendation}` | `input.recommendation` | 直接使用枚举值字符串 |
| `{priorityDebtIds}` | `input.priorityDebtIds` | 逗号分隔的 ID 列表 |
| `{hasOverdue}` | `input.hasOverdue` | "是" / "否" |
| `{paymentExceedIncome}` | `input.paymentExceedIncome` | "是" / "否" |

### 6.2.10 风险等级补充指令

根据 `riskLevel` 在 System Message 末尾追加对应指令：

**LOW**：
```
用户财务状况较好。建议侧重：如何进一步降低利息支出、是否有提前还款的机会、如何保持良好的信用记录。语气轻松乐观。共情段可以简短。
```

**MEDIUM**：
```
用户财务有一定压力但可控。建议侧重：高息债务的优先处理顺序、如何优化月供压力、可以考虑进一步评估优化方案。语气专业、务实。第三段（正面转换）要加强。
```

**HIGH**：
```
用户财务压力较大。建议侧重：逾期债务的处理顺序、如何与债权人沟通协商、控制新增借贷、开源节流的具体方法。语气温和但坚定。第五段（安全兜底）要加强。注意：不制造焦虑，用"需要关注"替代"高风险"。
```

**CRITICAL**：
```
用户财务状况紧急。建议侧重：先稳定现状不追求一步到位、逾期债务按影响程度排序、建议咨询专业顾问（但不是"你必须去找律师"）、心理支持（财务困难是暂时的，可以改善的）。语气温暖、有力量感。第一段共情要充分，第五段安全感要最强。绝不推荐"以贷还贷"的任何变体。不展示"重组申请"入口，改为"30天改善计划"。
```

### 6.2.11 五段式心理路径结构（强制约束）

AI 生成的每份建议必须遵循以下五段结构。这是 user-journey.md 的强制要求：

| 段落 | 名称 | 内容要求 | 长度 |
|------|------|----------|------|
| 第一段 | 确认感受（共情） | "管理多笔债务确实需要花费精力..." 禁止直接用数字开头制造焦虑 | 1-2 句 |
| 第二段 | 量化损失 | "按当前结构，未来3年将多支付约 XX 元利息" 唯一允许讲"损失"的段落 | 仅 1 句 |
| 第三段 | 立即转正面 | "好消息是，通过调整优先级，你可以..." 必须紧跟第二段 | 1-2 句 |
| 第四段 | 具体行动 | 2-4 个小步骤，每个步骤可独立执行 | 对应 actionPlan |
| 第五段 | 安全兜底 | "这些调整不影响你的信用记录，随时可以调整节奏" 永远以安全感结尾 | 1 句 |

**禁止**：
- 连续两段都在讲"损失/风险/问题"
- 用"必须""一定""否则"等强制性词汇
- 与其他用户做比较（"别人都在..."）
- 使用"赶紧""立刻""最后机会"等紧迫性词汇

### 6.2.12 后处理逻辑（Java 侧）

**步骤 1 -- JSON 解析容错**：

与 OcrExtractService 相同的三步容错策略。

**步骤 2 -- 合规过滤（禁止词扫描）**：

扫描 AI 返回文本中的所有字符串字段，执行以下过滤：

| 扫描目标 | 处理方式 |
|----------|----------|
| 具体金融产品名称（如"花呗""借呗""微粒贷""京东白条"等） | 替换为"相关金融产品" |
| "借新还旧""以贷还贷"及其变体（"用新贷款还旧贷款"等） | 删除包含该表述的整条建议 |
| 具体 APP/平台名称 | 替换为"相关平台" |
| "问题严重""问题很严重" | 替换为"有优化空间" |
| "你需要立即处理""必须立即" | 替换为"越早调整，节省越多" |
| "高风险" | 替换为"需要关注" |
| "负债过高" | 替换为"月供压力偏大" |
| "申请失败""不符合条件" | 替换为"暂时不适合，建议先优化" |
| "你的征信可能有问题" | 替换为"信用状况有提升空间" |
| "赶紧行动""赶紧""立刻行动" | 替换为"你可以从这一步开始" |
| "最后机会" | 删除 |
| "必须""一定""否则" | 替换为"建议""可以考虑""如果不调整" |

**禁止词列表来源**：user-journey.md 第六章"文案语气约束"表格 + CLAUDE.md F-11/F-13。

实现建议：将禁止词列表配置在 `application.yml`（`youhua.ai.banned-words`），便于运营期动态调整。

**步骤 3 -- 数值一致性校验**：

扫描建议文本中出现的数字，与输入数据交叉比对。如果 AI 输出中引用的数值与输入不一致（误差超过 1%），用输入数据覆盖。

**步骤 4 -- 长度校验**：

| 字段 | 最小长度 | 最大长度 | 超长处理 | 过短处理 |
|------|----------|----------|----------|----------|
| summary | 50 字 | 200 字 | 截断到 200 字 | 标记异常但不阻断 |
| 每条 action | 20 字 | 200 字 | 截断到 200 字 | 标记异常但不阻断 |
| encouragement | 5 字 | 100 字 | 截断到 100 字 | 标记异常但不阻断 |

### 6.2.13 异常处理

| 场景 | 处理方式 |
|------|----------|
| AI 调用超时 | 返回 `success=false`，`errorCode=AI_SUGGESTION_FAILED` |
| AI 返回非 JSON | 返回 `success=false`，`errorCode=AI_SUGGESTION_FAILED` |
| AI 服务不可用 | 返回 `success=false`，`errorCode=AI_SUGGESTION_FAILED` |
| 后处理后建议为空 | 返回 `success=false`，`errorCode=AI_SUGGESTION_FAILED` |

**注意**：建议生成失败不应阻断报告生成流程。报告中 AI 建议为 optional 部分，失败时报告仍可生成，只是缺少 AI 建议段落。

### 6.2.14 日志要求

- DEBUG 级别：记录填充后的完整 User Message 模板（脱敏：隐藏具体金额数字）
- DEBUG 级别：记录 AI 返回的原始 JSON（截断到 3000 字符）
- DEBUG 级别：记录后处理每个步骤的变更（被替换的禁止词、被覆盖的数值）
- INFO 级别：记录生成结果摘要（success/fail、riskLevel、耗时ms）
- WARN 级别：记录合规过滤命中情况（命中了哪些禁止词）
- ERROR 级别：记录 AI 调用异常

### 6.2.15 Mock 测试用例（5 组）

**用例 1 -- LOW 风险用户（正常路径）**

```
输入：
  totalDebt=80000, debtCount=2, weightedApr=12.50,
  monthlyPayment=4200, monthlyIncome=15000,
  debtIncomeRatio=0.28, restructureScore=82.00,
  riskLevel=LOW, hasOverdue=false, paymentExceedIncome=false,
  recommendation=RESTRUCTURE_RECOMMENDED

期望：
  success=true
  summary 包含共情 + 正面表述
  actionPlan 侧重降低利息支出和提前还款
  encouragement 语气轻松乐观
  无禁止词命中
```

**用例 2 -- HIGH 风险用户（有逾期）**

```
输入：
  totalDebt=350000, debtCount=6, weightedApr=28.50,
  monthlyPayment=18000, monthlyIncome=12000,
  debtIncomeRatio=1.50, restructureScore=35.00,
  riskLevel=HIGH, hasOverdue=true, paymentExceedIncome=true,
  recommendation=CREDIT_BUILDING

期望：
  success=true
  summary 用"需要关注"而非"高风险"
  actionPlan 第一阶段优先处理逾期
  不出现"申请失败""不符合条件"
  encouragement 语气温暖有力量
```

**用例 3 -- AI 返回含禁止词（后处理过滤）**

```
输入：MEDIUM 风险用户

AI 返回 JSON 中 summary 包含 "你的债务问题很严重，需要立即处理"
AI 返回 JSON 中某条 action 包含 "建议下载借呗APP进行转贷"

期望：
  success=true
  summary 中 "你的债务问题很严重" -> "你的财务结构有优化空间"
  summary 中 "需要立即处理" -> "越早调整，节省越多"
  包含"借呗"的 action 被整条删除（因为含"转贷"属于以贷还贷变体）
  WARN 日志记录命中禁止词
```

**用例 4 -- AI 返回数值与输入不一致**

```
输入：totalDebt=150000, weightedApr=21.40

AI 返回 summary 中写 "总负债 180,000 元"（与输入 150,000 不一致）

期望：
  后处理将 180,000 替换为 150,000
  DEBUG 日志记录数值覆盖
```

**用例 5 -- AI 调用超时**

```
输入：任意有效输入
AI 调用超时（超过 30 秒）

期望：
  success=false
  errorCode=AI_SUGGESTION_FAILED
  errorMessage 为用户友好提示
  不抛异常，不阻断上层报告生成
```

---

## 附录 A -- 技术栈要求

| 依赖 | 用途 |
|------|------|
| Spring AI (`spring-ai-openai-spring-boot-starter`) | ChatClient 调用 DeepSeek |
| Jackson (`ObjectMapper`) | JSON 解析 |
| Lombok | DTO 简化 |
| SLF4J | 日志 |
| JUnit 5 + Mockito + AssertJ | 测试 |

## 附录 B -- 配置参考

```yaml
# application.yml 中已有的相关配置
youhua:
  ai:
    timeout-seconds: 30
    max-retries: 3
```

实现者如需新增配置项（如 `banned-words` 列表），请在 `application.yml` 的 `youhua.ai` 段下添加。

## 附录 C -- 包结构

```
com.youhua.ai
  /enums
    OcrFileType.java          (已存在)
    OcrTaskStatus.java        (已存在)
  /ocr
    /dto
      OcrExtractResult.java   (新建)
      OcrExtractedFields.java (新建)
      OcrField.java           (新建)
      /request                (已存在)
      /response               (已存在)
    /service
      OcrExtractService.java  (新建 - Task #2)
      OcrTaskService.java     (已存在)
      /impl
        OcrExtractServiceImpl.java (新建 - Task #2)
  /suggestion
    /dto
      SuggestionInput.java    (新建)
      SuggestionResult.java   (新建)
      ActionPlan.java         (新建)
      Phase.java              (新建)
      Action.java             (新建)
      DebtSummary.java        (新建)
    /service
      SuggestionGenService.java      (新建 - Task #3)
      /impl
        SuggestionGenServiceImpl.java (新建 - Task #3)
  /prompt                     (已存在, 可放 Prompt 常量类)
```
