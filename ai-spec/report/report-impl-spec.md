# report-impl-spec.md — 报告模块实现规范

> 本文件定义报告模块（com.youhua.report）三个核心类的详细实现规范。
> 所有实现必须遵守 CLAUDE.md 的绝对禁止和强制约束。

---

## 7.1 ReportAssembler — 报告数据组装器

### 类信息

- **包路径**：`com.youhua.report.service.ReportAssembler`
- **类型**：`@Component`
- **职责**：将 FinanceProfile、Debt 列表、ScoreResult、SuggestionResult 组装为三层可解释性报告数据结构
- **不持有状态**：纯组装逻辑，所有数据从参数传入

### 依赖注入

```
@RequiredArgsConstructor
- ReportConfigProperties reportConfig  // 配置化的市场均值、健康线等参数
```

### 方法签名

```java
/**
 * 组装完整报告数据。
 *
 * @param profile     用户财务画像（已计算完毕）
 * @param debts       用户的 IN_PROFILE 状态债务列表
 * @param scoreResult ScoringEngine 的评分结果
 * @param suggestion  SuggestionGenService 的 AI 建议结果（可能为 null，降级场景）
 * @return 完整的报告数据 DTO
 * @throws BizException REPORT_PROFILE_INCOMPLETE(406002) 当 profile 缺少必要字段时
 */
public ReportData assemble(FinanceProfile profile, List<Debt> debts,
                           ScoreResult scoreResult, SuggestionResult suggestion)
```

### 三层可解释性组装逻辑

#### 第一层：数字摘要（NumericSummary）

组装以下字段：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| totalDebt | BigDecimal | profile.totalDebt | 总负债 |
| debtCount | int | profile.debtCount | 债务笔数 |
| weightedApr | BigDecimal | profile.weightedApr | 加权年化利率（%） |
| monthlyPayment | BigDecimal | profile.monthlyPayment | 月供总额 |
| monthlyIncome | BigDecimal | profile.monthlyIncome | 月收入（可能 null） |
| debtIncomeRatio | BigDecimal | profile.debtIncomeRatio | 负债收入比（可能 null） |
| threeYearExtraInterest | BigDecimal | **计算** | 三年多付利息（详见公式） |
| threeYearExtraInterestAnalogy | String | **计算** | "相当于 X 个月房租" |
| restructureScore | BigDecimal | scoreResult.finalScore | 重组评分 |
| riskLevel | RiskLevel | scoreResult.riskLevel | 风险等级 |

**threeYearExtraInterest 计算公式**：

```
threeYearExtraInterest = Σ extraInterest_i

对于每笔债务 debt_i：
  totalInterest_i = debt_i.totalRepayment - debt_i.principal
  dailyInterest_i = totalInterest_i / debt_i.loanDays          // scale=10, HALF_UP
  extraInterest_i = dailyInterest_i × 1095                     // 1095 = 365 × 3

最终结果 scale=2, RoundingMode.HALF_UP
```

数学表达式：

```
threeYearExtraInterest = Σ( (debt_i.totalRepayment - debt_i.principal) / debt_i.loanDays × 1095 )
```

**注意**：
- 所有中间步骤 scale=10, RoundingMode.HALF_UP
- 最终结果 scale=2, RoundingMode.HALF_UP
- 如果 debt_i.loanDays == 0 或 debt_i.principal == null，跳过该笔并记 WARN 日志
- 该计算由 Java 引擎完成，绝不调用 AI（F-02）

**"相当于 XX 个月房租" 类比计算**：

```
months = threeYearExtraInterest / avgMonthlyRent    // scale=0, HALF_UP（取整）
analogy = "相当于 " + months + " 个月房租"
```

- `avgMonthlyRent` 从 `ReportConfigProperties.avgMonthlyRent` 读取，不硬编码（F-09）
- 如果 avgMonthlyRent 为 0 或未配置，不生成类比文案，analogy 设为 null

#### 第二层：逐笔债务分析（DebtAnalysis 列表）

对每笔债务生成一个 DebtAnalysisItem：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| debtId | Long | debt.id | 债务 ID |
| creditor | String | debt.creditor | 债权机构 |
| debtType | DebtType | debt.debtType | 债务类型 |
| principal | BigDecimal | debt.principal | 本金 |
| apr | BigDecimal | debt.apr | 年化利率 |
| monthlyPayment | BigDecimal | debt.monthlyPayment | 月供 |
| totalInterest | BigDecimal | 计算: totalRepayment - principal | 总利息 |
| interestContribution | BigDecimal | 计算 | 该笔利息占总利息的百分比（%） |
| aprLevel | AprLevel | AprCalculator.getAprLevel(apr) | 利率告警级别 |
| sourceType | DebtSourceType | debt.sourceType | 数据来源 |
| overdueStatus | OverdueStatus | debt.overdueStatus | 逾期状态 |

**interestContribution 计算**：

```
totalInterestAll = Σ (debt_i.totalRepayment - debt_i.principal)   // 所有债务利息总和
interestContribution_i = (debt_i_interest / totalInterestAll) × 100

结果 scale=2, HALF_UP，表示百分比（如 35.20 表示 35.20%）
如果 totalInterestAll == 0，所有 interestContribution 设为 0
```

排序规则：按 APR 从高到低排序。

#### 第三层：AI 建议（SuggestionLayer）

直接透传 SuggestionResult 的字段：

| 字段 | 类型 | 来源 | 说明 |
|------|------|------|------|
| empathy | String | suggestion.empathy | 共情段 |
| quantifiedLoss | String | suggestion.quantifiedLoss | 量化损失段 |
| positiveTurn | String | suggestion.positiveTurn | 正面转换段 |
| actionSteps | List\<String\> | suggestion.actionSteps | 行动步骤 |
| safetyClosure | String | suggestion.safetyClosure | 安全兜底句 |
| summary | String | suggestion.summary | 总结 |
| priorityCreditors | List\<String\> | suggestion.priorityCreditors | 优先处理机构 |
| aiGenerated | boolean | suggestion.aiGenerated | 是否 AI 生成 |

如果 suggestion 为 null（AI 降级），第三层整体设为 null，前端据此决定是否展示 AI 建议区块。

### 输出 DTO：ReportData

```
包路径：com.youhua.report.dto.ReportData

ReportData
├── numericSummary: NumericSummary           // 第一层
├── debtAnalyses: List<DebtAnalysisItem>     // 第二层
├── suggestion: SuggestionLayer              // 第三层（可 null）
├── lossVisualization: LossVisualizationData // 可视化数据（7.2）
├── metadata: ReportMetadata                 // 元信息
│   ├── generatedTime: LocalDateTime
│   ├── scoringModelVersion: String          // "v1.0"
│   ├── manualCount: int                     // 手动录入笔数
│   ├── ocrCount: int                        // OCR 识别笔数
│   └── incomeProvided: boolean              // 是否提供了收入
└── warnings: List<ReportWarning>            // 不确定性标注
    ├── type: WarningType                    // INCOME_MISSING / OCR_LOW_CONFIDENCE / DRAFT_PENDING
    └── message: String
```

### 不确定性标注规则

组装时自动检测以下情况并添加 warning：

1. **INCOME_MISSING**：profile.monthlyIncome == null
   - message: "您尚未填写收入信息，负债收入比和部分建议基于估算值"
2. **OCR_LOW_CONFIDENCE**：任意债务 source_type == OCR 且 confidence_score < 70
   - message: "部分债务数据由AI识别，置信度偏低，建议人工核对后再查看报告"
3. **DRAFT_PENDING**：存在 status == DRAFT 或 PENDING_CONFIRM 的债务（需额外查询或由调用方传入计数）
   - message: "您有 {count} 笔债务尚未确认，确认后报告将更准确"

### DTO 类路径清单

| 类 | 包路径 |
|----|--------|
| ReportData | com.youhua.report.dto.ReportData |
| NumericSummary | com.youhua.report.dto.NumericSummary |
| DebtAnalysisItem | com.youhua.report.dto.DebtAnalysisItem |
| SuggestionLayer | com.youhua.report.dto.SuggestionLayer |
| LossVisualizationData | com.youhua.report.dto.LossVisualizationData |
| ReportMetadata | com.youhua.report.dto.ReportMetadata |
| ReportWarning | com.youhua.report.dto.ReportWarning |
| WarningType | com.youhua.report.dto.WarningType (enum) |

所有 DTO 使用 Java record（不可变），金额字段 BigDecimal，时间字段 LocalDateTime。

---

## 7.1.1 ReportService — 报告编排服务

### 类信息

- **包路径**：`com.youhua.report.service.ReportService`
- **类型**：`@Service`
- **职责**：编排报告生成流程，调用各组件并持久化结果

### 依赖注入

```
- FinanceProfileService profileService   // 获取画像
- DebtService debtService                // 获取债务列表
- ScoringEngine scoringEngine            // 评分
- SuggestionGenService suggestionGenService  // AI 建议
- ReportAssembler reportAssembler        // 组装
- OptimizationReportMapper reportMapper  // 持久化
- ObjectMapper objectMapper              // JSON 序列化
```

### 核心方法

```java
/**
 * 生成用户优化报告。
 *
 * @param userId 用户 ID
 * @return 报告 ID
 * @throws BizException REPORT_PROFILE_INCOMPLETE(406002)
 */
public Long generateReport(Long userId)
```

**流程**：
1. 查询 FinanceProfile（必须存在且已计算）
2. 查询 IN_PROFILE 状态的债务列表
3. 构建 ScoreInput，调用 ScoringEngine.score()
4. 调用 SuggestionGenService.generate()（try-catch，失败时 suggestion=null，记 WARN 日志）
5. 调用 ReportAssembler.assemble() 组装 ReportData
6. 序列化为 JSON，持久化到 OptimizationReport 表
7. 返回报告 ID

```java
/**
 * 查询报告详情。
 *
 * @param reportId 报告 ID
 * @param userId   用户 ID（鉴权用，防止越权）
 * @return ReportData
 * @throws BizException REPORT_NOT_FOUND(406001)
 */
public ReportData getReport(Long reportId, Long userId)
```

---

## 7.2 LossVisualization — 损失可视化数据结构

### 说明

LossVisualizationData 是 ReportData 的子结构，由 ReportAssembler 在组装时一并生成。不是独立的 Service 类。

### 数据结构（给前端的 JSON 格式）

```json
{
  "lossVisualization": {
    "threeYearExtraInterest": {
      "value": 82400.00,
      "analogy": "相当于 13 个月房租",
      "displayFormat": "如果维持当前结构，3 年将多支付 {value} 元"
    },
    "currentVsHealthy": {
      "currentWeightedApr": 24.00,
      "marketAvgApr": 8.50,
      "gap": 15.50,
      "displayFormat": "你的综合利率 {current}%，市场均值 {avg}%"
    },
    "monthlyPressure": {
      "ratio": 0.65,
      "healthyLine": 0.40,
      "displayed": true,
      "displayFormat": "月供占收入 {ratio}%，健康线为 {healthyLine}% 以下"
    },
    "interestBreakdown": [
      {
        "debtId": 10001,
        "creditor": "平安普惠",
        "interestAmount": 45000.00,
        "percentage": 54.61
      },
      {
        "debtId": 10002,
        "creditor": "招商银行信用卡",
        "interestAmount": 37400.00,
        "percentage": 45.39
      }
    ]
  }
}
```

### DTO 定义：LossVisualizationData

```
LossVisualizationData (record)
├── threeYearExtraInterest: ThreeYearLoss
│   ├── value: BigDecimal            // 三年多付利息总额，scale=2
│   ├── analogy: String              // "相当于 X 个月房租"，可能 null
│   └── displayFormat: String        // 展示模板
├── currentVsHealthy: AprComparison
│   ├── currentWeightedApr: BigDecimal  // 用户加权 APR，scale=2
│   ├── marketAvgApr: BigDecimal        // 市场均值，从配置读取，scale=2
│   ├── gap: BigDecimal                 // currentWeightedApr - marketAvgApr，scale=2
│   └── displayFormat: String
├── monthlyPressure: MonthlyPressure
│   ├── ratio: BigDecimal            // 月供/收入，scale=2
│   ├── healthyLine: BigDecimal      // 健康线，从配置读取，scale=2
│   ├── displayed: boolean           // 是否展示（无收入数据时 false）
│   └── displayFormat: String        // 如果 displayed=false 则为 "填写收入获取更精确分析"
└── interestBreakdown: List<InterestBreakdownItem>
    ├── debtId: Long
    ├── creditor: String
    ├── interestAmount: BigDecimal   // 该笔利息，scale=2
    └── percentage: BigDecimal       // 占比(%)，scale=2
```

### 饼图数据：各债务利息占比

interestBreakdown 列表即为饼图数据源：

- 每项包含 debtId、creditor、interestAmount（该笔总利息）、percentage（占总利息百分比）
- 按 interestAmount 从大到小排序
- percentage 计算方式同 7.1 的 interestContribution
- 所有 percentage 之和应为 100.00（最后一项兜底差值）

### 对比条数据：当前 APR vs 市场均值

AprComparison 用于前端渲染对比条/柱状图：

- `currentWeightedApr`：用户的加权 APR，来自 profile.weightedApr，scale=2
- `marketAvgApr`：市场平均 APR，从 `ReportConfigProperties.marketAvgApr` 读取（F-09），scale=2
- `gap`：差值 = currentWeightedApr - marketAvgApr，scale=2
- 如果 gap <= 0（用户利率低于市场均值），仍正常展示，但 displayFormat 改为 "你的综合利率低于市场均值，表现良好"

### monthlyPressure 展示逻辑

- 如果 profile.monthlyIncome == null 或 == 0：`displayed = false`，displayFormat = "填写收入获取更精确分析"
- 否则：`ratio = profile.monthlyPayment / profile.monthlyIncome`（scale=2），`displayed = true`
- `healthyLine` 从 `ReportConfigProperties.healthyDebtIncomeRatio` 读取

### 配置项（ReportConfigProperties）

```yaml
youhua:
  report:
    market-avg-apr: 8.5                   # 市场平均 APR（%）
    avg-monthly-rent: 6000.00             # 城市平均月租金（元）
    healthy-debt-income-ratio: 0.40       # 健康线
    scoring-model-version: "v1.0"         # 评分模型版本号
```

对应 Java 配置类：

```
包路径：com.youhua.report.config.ReportConfigProperties
类型：@ConfigurationProperties(prefix = "youhua.report")

字段：
  marketAvgApr: BigDecimal
  avgMonthlyRent: BigDecimal
  healthyDebtIncomeRatio: BigDecimal
  scoringModelVersion: String
```

### Page 4 约束提醒（F-12）

损失可视化数据用于 Page 4（损失可视化报告），该页面 CTA 只能是"看看我的优化空间"，绝不能出现"申请"按钮。这是前端约束，后端 DTO 中不包含任何申请相关字段。

---

## 7.3 PdfExportService — PDF 导出服务

### 类信息

- **包路径**：`com.youhua.report.service.PdfExportService`
- **类型**：`@Service`
- **职责**：将 ReportData 渲染为 PDF 文件

### 依赖库

使用 **Apache PDFBox 3.x**（Apache License 2.0，适合商业项目）。

pom.xml 新增依赖：
```xml
<dependency>
    <groupId>org.apache.pdfbox</groupId>
    <artifactId>pdfbox</artifactId>
    <version>3.0.4</version>
</dependency>
```

### 中文字体支持方案

- 使用 **思源黑体（Noto Sans CJK SC）** 或 **文泉驿微米黑**，OFL 开源字体许可，可商用
- 字体文件放置路径：`src/main/resources/fonts/NotoSansSC-Regular.ttf`
- 通过 `PDType0Font.load(document, fontInputStream)` 加载
- 提供 Regular（正文）一种字重即可，标题通过字号区分
- 字体加载为实例变量缓存（PDFBox 的 PDDocument 生命周期内复用），避免每次都从磁盘读取

### 方法签名

```java
/**
 * 将报告数据导出为 PDF。
 *
 * @param reportData 组装好的报告数据
 * @return PDF 文件内容
 * @throws BizException REPORT_PDF_EXPORT_FAILED(406004) 导出失败时
 */
public byte[] export(ReportData reportData)
```

### PDF 模板结构（A4 竖版，210mm × 297mm）

PDF 分为 5 个 Section，从上到下排列，超出一页时自动分页：

#### Section 1：报告头部

```
┌──────────────────────────────────────────┐
│  优化家 — 债务优化分析报告                    │  字号：18pt，居中
│  报告生成时间：2026-03-04 14:30:00           │  字号：10pt，居中，灰色
│  评分模型版本：v1.0                          │  字号：10pt，居中，灰色
└──────────────────────────────────────────┘
```

#### Section 2：核心数据摘要

四列卡片布局（单行），每个卡片包含标签+数值：

```
┌──────────┬──────────┬──────────┬──────────┐
│ 总负债     │ 加权APR   │ 月供总额   │ 重组评分   │
│ 380,000元 │ 24.00%   │ 12,800元  │ 68.00分   │
└──────────┴──────────┴──────────┴──────────┘

三年多付利息：82,400.00 元（相当于 13 个月房租）     字号：14pt
```

- 金额格式：千分位 + 2 位小数
- 百分比格式：2 位小数 + %
- 评分格式：2 位小数 + 分

#### Section 3：逐笔债务分析表

表格形式：

| 序号 | 债权机构 | 类型 | 本金(元) | 年化利率(%) | 月供(元) | 利息占比(%) | 状态 |
|------|----------|------|----------|-------------|----------|-------------|------|

- 表头灰色背景
- 行交替色（白/浅灰），提高可读性
- APR 超过告警阈值的行，利率数字标注 `[!]` 后缀
- 最后一行为合计行（粗体）

#### Section 4：损失可视化

文字描述形式（PDF 中不画图表，图表由前端渲染）：

```
■ 利率对比
  你的综合利率：24.00%
  市场平均利率：8.50%
  差距：15.50 个百分点

■ 月供压力
  月供占收入比：65.00%
  健康线：40.00% 以下

■ 三年多付利息
  82,400.00 元
  相当于 13 个月房租
```

如果 monthlyPressure.displayed == false，月供压力段替换为："填写收入获取更精确分析"

#### Section 5：AI 优化建议

如果 suggestion 不为 null：

```
■ 分析总结
  {suggestion.summary}

■ 建议行动步骤
  1. {actionSteps[0]}
  2. {actionSteps[1]}
  3. {actionSteps[2]}

■ 优先处理的债务
  {priorityCreditors}
```

如果 suggestion 为 null：

```
■ 分析总结
  AI 建议暂时不可用，请在线查看报告获取完整建议。
```

#### 页脚（每页）

```
──────────────────────────────────────────
本报告基于您提供的债务和收入数据生成，仅供参考，不构成任何金融建议。
数据来源：手动录入 {manualCount} 笔、AI识别 {ocrCount} 笔
                                        优化家 · 第 {page}/{totalPages} 页
```

### 布局常量（不硬编码到方法中，抽为 private static final）

```
PAGE_WIDTH   = 595 pt (A4)
PAGE_HEIGHT  = 842 pt (A4)
MARGIN_LEFT  = 50 pt
MARGIN_RIGHT = 50 pt
MARGIN_TOP   = 60 pt
MARGIN_BOTTOM= 60 pt
CONTENT_WIDTH= PAGE_WIDTH - MARGIN_LEFT - MARGIN_RIGHT = 495 pt
TITLE_FONT_SIZE   = 18
HEADING_FONT_SIZE = 14
BODY_FONT_SIZE    = 10
SMALL_FONT_SIZE   = 8
LINE_SPACING      = 16 pt
```

### 输入参数

- `ReportData reportData`：由 ReportAssembler 组装好的完整报告数据

### 输出

- `byte[]`：PDF 文件字节数组
- 调用方（Controller）设置响应头：
  ```
  Content-Type: application/pdf
  Content-Disposition: attachment; filename="youhuajia-report-{reportId}.pdf"
  ```

### 异常处理

- 字体文件缺失：抛 BizException(REPORT_PDF_EXPORT_FAILED, "PDF 导出失败：字体文件缺失")
- PDFBox 渲染异常：捕获 IOException，包装为 BizException(REPORT_PDF_EXPORT_FAILED)
- 数据为 null 的防御：所有可能为 null 的字段使用 "—" 占位

### 安全约束

- PDF 中不输出用户手机号、身份证号等敏感信息（F-04）
- PDF 中不包含任何"申请"按钮或链接（F-12 延伸）
- PDF 中金额使用 BigDecimal 格式化，不使用 float/double（F-01）

---

## 附录：报告模块新增配置项汇总

在 application.yml 中添加：

```yaml
youhua:
  report:
    market-avg-apr: 8.5
    avg-monthly-rent: 6000.00
    healthy-debt-income-ratio: 0.40
    scoring-model-version: "v1.0"
```

---

## 附录：报告模块类清单

| 类 | 包路径 | 类型 |
|----|--------|------|
| ReportAssembler | com.youhua.report.service | @Component |
| ReportService | com.youhua.report.service | @Service |
| PdfExportService | com.youhua.report.service | @Service |
| ReportConfigProperties | com.youhua.report.config | @ConfigurationProperties |
| ReportData | com.youhua.report.dto | record |
| NumericSummary | com.youhua.report.dto | record |
| DebtAnalysisItem | com.youhua.report.dto | record |
| SuggestionLayer | com.youhua.report.dto | record |
| LossVisualizationData | com.youhua.report.dto | record |
| ThreeYearLoss | com.youhua.report.dto | record (内嵌于 LossVisualizationData 或独立) |
| AprComparison | com.youhua.report.dto | record |
| MonthlyPressure | com.youhua.report.dto | record |
| InterestBreakdownItem | com.youhua.report.dto | record |
| ReportMetadata | com.youhua.report.dto | record |
| ReportWarning | com.youhua.report.dto | record |
| WarningType | com.youhua.report.dto | enum |

---

## 附录：测试要求

每个 Service/Component 必须有对应的 Test 类（CLAUDE.md 3.4）。

### ReportAssemblerTest

- should_assemble_complete_report_when_all_data_present
- should_calculate_three_year_extra_interest_correctly
- should_calculate_interest_contribution_correctly
- should_generate_analogy_when_avg_rent_configured
- should_skip_analogy_when_avg_rent_zero
- should_add_income_missing_warning_when_no_income
- should_add_ocr_low_confidence_warning
- should_handle_null_suggestion_gracefully
- should_set_monthly_pressure_not_displayed_when_no_income
- should_calculate_gap_correctly_when_user_below_market_avg

### PdfExportServiceTest

- should_export_valid_pdf_bytes
- should_include_all_sections_in_pdf
- should_handle_null_suggestion_in_pdf
- should_handle_null_monthly_income_in_pdf
- should_throw_when_font_file_missing (需 mock 资源加载)
- should_format_amounts_with_thousands_separator
- should_not_contain_sensitive_info_in_pdf

### ReportServiceTest

- should_generate_report_successfully
- should_handle_suggestion_failure_gracefully
- should_throw_when_profile_not_found
- should_persist_report_to_database
