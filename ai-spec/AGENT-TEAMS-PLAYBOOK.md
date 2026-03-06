# 优化家 MVP — 原生 Agent Teams 执行手册

> **使用说明**：每个 Step 的 prompt 可以直接复制粘贴到 Claude Code 终端中执行。
> 前置条件：已配置 `.claude/settings.json` 启用 Agent Teams。

---

## 前置配置（只需执行一次）

```bash
# 1. 确保 .claude/settings.json 已配置
mkdir -p .claude
cat > .claude/settings.json << 'EOF'
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  },
  "permissions": {
    "allow": [
      "Bash(mvn *)",
      "Bash(npm *)",
      "Bash(flutter *)",
      "Bash(dart *)",
      "Bash(git *)"
    ]
  }
}
EOF

# 2. 启动 tmux（分屏可视化每个 teammate）
tmux new -s youhuajia

# 3. 进入项目目录，启动 Claude Code
cd /path/to/youhuajia
claude
```

---

## Step 1 — Maven 骨架 + 包结构

> 简单任务，2 个 teammate 串行即可。

```
创建 agent team，使用 delegate mode。

Teammate "architect" (用 Opus):
  读取 ai-spec/CLAUDE.md 和 ai-spec/domain/evolution.md。
  你是架构师，只输出文字规范，绝对不写代码、不创建文件。
  输出以下规范：
  1. pom.xml 完整依赖清单（所有 starter + 版本号）
  2. 包结构设计（按 evolution.md 中的 6 个模块拆分）
     com.youhua.{common,debt,profile,engine,ai,infra}/
  3. application.yml + application-dev.yml + application-prod.yml 的配置项
  4. .gitignore 内容
  规范写完后通知 "coder" 开始实现。

Teammate "coder" (用 Sonnet):
  等 architect 的规范完成后，严格按规范创建所有文件。
  - 生成 pom.xml
  - 创建所有包目录和 package-info.java
  - 生成配置文件
  - 生成 .gitignore
  每个文件创建后执行 mvn compile 验证。
  全部完成后执行 mvn compile -q 确认零错误。
```

---

## Step 2 — JPA Entity + Flyway 迁移

> 3 个 teammate：规范、实体实现、迁移脚本可部分并行。

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 ai-spec/domain/entities.md 和 ai-spec/domain/state-machines.yaml。
  你是架构师，只输出文字规范，绝对不写代码。
  为以下 7 个 Entity 各输出详细规范：
  - User, Debt, Income, FinanceProfile, OcrTask, Report, ActionPlan
  每个 Entity 规范必须包含：
    - 完整 Java 类路径（如 com.youhua.debt.entity.Debt）
    - 每个字段的：Java 类型、DB 列名、JPA 注解、是否 nullable
    - 金额字段必须标注 BigDecimal + @Column(precision=19, scale=4)
    - 时间字段用 xxxTime 后缀（createTime, updateTime）
    - 布尔字段不加 is 前缀
    - 状态字段标注枚举值（来自 state-machines.yaml）
    - 索引定义
    - 预留字段（加 @Column 注释标注"预留"）
  同时输出 Flyway V1__init.sql 的完整建表 DDL 规范。
  规范完成后通知 "entity-coder" 和 "migration-coder"。

Teammate "entity-coder" (用 Sonnet):
  等 spec 完成后，按规范生成 7 个 JPA Entity 类 + 7 个 Repository 接口。
  技术约束：
  - @Entity + @Table(name = "xxx")
  - 主键用 @GeneratedValue(strategy = IDENTITY)
  - 金额字段用 BigDecimal，精度 (19,4)
  - 每个 Entity 写完后 mvn compile 验证
  全部完成后通知 team lead。

Teammate "migration-coder" (用 Sonnet):
  等 spec 完成后，按规范生成 Flyway 迁移脚本。
  - src/main/resources/db/migration/V1__init_schema.sql
  - 包含所有 7 张表的 CREATE TABLE
  - 包含所有索引的 CREATE INDEX
  - 字段类型严格对应 Entity 定义
  写完后通知 team lead。
  注意：你不需要等 entity-coder，你和它并行工作，都基于 spec 的规范。
```

---

## Step 3 — Controller + DTO + 异常处理

> 3 个 teammate 并行：ErrorCode、DTO、Controller 分层实现。

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 ai-spec/contracts/openapi.yaml 和 ai-spec/contracts/error-codes.md。
  你是架构师，只输出文字规范，绝对不写代码。
  输出以下规范：
  1. ErrorCode 枚举：将 error-codes.md 中的 8 个通用码 + 所有业务码
     映射为 Java enum，每个值包含 httpStatus + businessCode + message
  2. DTO 清单：为 openapi.yaml 中每个 schema 输出对应的
     XxxRequest / XxxResponse 类，标注每个字段的类型、校验注解、是否必填
  3. Controller 清单：按 tag 分 7 个 Controller，
     列出每个方法的签名（路径、HTTP 方法、参数、返回类型）
  4. GlobalExceptionHandler 的异常映射规则
  规范完成后通知所有 coder。

Teammate "error-coder" (用 Sonnet):
  等 spec 完成后，实现：
  - ErrorCode 枚举（com.youhua.common.error.ErrorCode）
  - BusinessException 异常类
  - GlobalExceptionHandler（@RestControllerAdvice）
  - ErrorResponse DTO
  写完后 mvn compile 验证，然后通知 team lead。

Teammate "dto-coder" (用 Sonnet):
  等 spec 完成后，实现所有 Request/Response DTO 类。
  按模块分包：com.youhua.{debt,profile,engine,ai}.dto.*
  - 每个 DTO 加 @Valid 校验注解
  - 金额字段用 BigDecimal
  - 分页响应用 pageToken + nextPageToken 模式
  写完后 mvn compile 验证，通知 team lead。

Teammate "controller-coder" (用 Sonnet):
  等 error-coder 和 dto-coder 都完成后再开始。
  实现 7 个 Controller：
  - AuthController, DebtController, IncomeController
  - FinanceProfileController, OcrTaskController
  - ReportController, EngineController
  每个 Controller 只写方法签名 + @RequestMapping 注解 + 参数校验，
  Service 层调用先写 TODO 占位。
  写完后 mvn compile 验证。
  最后启动应用确认 Swagger UI 可访问。
```

---

## Step 4 — 状态机

> 2 个 teammate：规范 + 实现（含测试）。

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 ai-spec/domain/state-machines.yaml。
  你是架构师，只输出文字规范，绝对不写代码。
  为以下 4 个状态机各输出详细规范：
  1. DebtStatus (PENDING → ACTIVE → OPTIMIZING → SETTLED / WRITTEN_OFF)
  2. OcrTaskStatus (CREATED → PROCESSING → COMPLETED / FAILED)
  3. ReportStatus (GENERATING → READY → EXPIRED)
  4. ActionPlanStatus (DRAFT → IN_PROGRESS → COMPLETED / ABANDONED)
  每个状态机规范包含：
  - Java 枚举类路径
  - 所有合法转换（from → to）
  - 每个转换的 guard 条件（什么时候允许）
  - 每个转换的 action（转换时触发什么）
  - 非法转换抛什么异常（DEBT_STATE_INVALID 等）
  - 需要覆盖的测试用例列表
  规范完成后通知 "coder"。

Teammate "coder" (用 Sonnet):
  等 spec 完成后，实现：
  - 4 个状态枚举类，每个枚举带 transitionTo(targetStatus) 方法
  - 每个转换方法内含 guard 条件校验
  - 非法转换抛 BusinessException(DEBT_STATE_INVALID)
  - 为每个状态机写单元测试，覆盖：
    - 所有合法转换 → 成功
    - 所有非法转换 → 抛异常
    - 边界条件（null 输入等）
  实现完成后执行 mvn test，所有测试必须通过。
```

---

## Step 5 — 计算引擎（最关键的 Step）

> 4 个 teammate：规范 1 个 + 实现 2 个并行 + 集成 1 个串行。
> 这是最复杂的 Step，APR 精度要求 < 0.01%，必须严格按规范执行。

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取以下文件，输出三个引擎的详细实现规范：
  - ai-spec/engine/apr-calc.md（APR 计算公式 + 牛顿迭代法）
  - ai-spec/engine/scoring-model.md（五维评分模型）
  - ai-spec/engine/rules.md（规则引擎优先级）
  - ai-spec/domain/user-journey.md 第五章（评分<60 的特殊路径）

  你是架构师，绝对不写代码，只输出文字规范。

  规范 5.1 — AprCalculator：
  - 类路径：com.youhua.engine.calculator.AprCalculator
  - 方法签名：BigDecimal calculateApr(BigDecimal principal, BigDecimal totalRepayment, int loanDays)
  - 算法：牛顿迭代法，列出每一步的数学公式
  - BigDecimal scale 策略：中间步骤 scale=10，最终结果 scale=4
  - 最大迭代次数=100，收敛阈值=1e-10
  - 边界条件：principal<=0, totalRepayment<=principal, loanDays<=0, loanDays>3650
  - 20 个测试用例的输入和精确期望值（从 apr-calc.md 提取）

  规范 5.2 — ScoringEngine：
  - 类路径：com.youhua.engine.scoring.ScoringEngine
  - 五维权重配置化方案（不硬编码）
  - 每个维度的计算公式和边界
  - ⚠️ 关键约束：scoring-model.md 的"重组建议映射"必须遵循 user-journey.md
    评分<60 不输出 URGENT_ATTENTION，改为 CREDIT_BUILDING
    所有 message 以正面表达开头

  规范 5.3 — RuleEngine：
  - 类路径：com.youhua.engine.rule.RuleEngine
  - 规则优先级排序
  - 每条规则的触发条件和输出

  规范完成后通知 "apr-coder" 和 "scoring-coder" 同时开始。

Teammate "apr-coder" (用 Sonnet):
  等 spec 完成后，严格按规范 5.1 实现 AprCalculator。
  - 实现 calculateApr 方法（牛顿迭代法）
  - 所有金额用 BigDecimal，禁止 float/double
  - 除法必须用 divide(x, scale, RoundingMode.HALF_UP)
  - 写 AprCalculatorTest，覆盖 spec 中全部 20 个测试用例
  - 执行 mvn test -Dtest=AprCalculatorTest
  - 所有用例必须通过，APR 精度 < 0.01%
  完成后通知 team lead。
  注意：你和 scoring-coder 并行工作，各自操作不同的包，不会冲突。

Teammate "scoring-coder" (用 Sonnet):
  等 spec 完成后，严格按规范 5.2 和 5.3 实现 ScoringEngine + RuleEngine。
  - ScoringEngine：五维权重从配置读取（application.yml 或 @ConfigurationProperties）
  - RuleEngine：规则优先级排序 + 触发逻辑
  - ⚠️ 评分<60 的结果必须是 CREDIT_BUILDING，不是 URGENT_ATTENTION
  - 写 ScoringEngineTest + RuleEngineTest
  - 执行 mvn test
  完成后通知 team lead。

Teammate "integrator" (用 Sonnet):
  等 apr-coder 和 scoring-coder 都完成后再开始。
  写集成测试 EngineIntegrationTest：
  - 验证 AprCalculator + ScoringEngine + RuleEngine 三个引擎协作
  - 用 mock-data.md 中的 5 类用户画像作为测试输入
  - 每类用户验证：APR 计算 → 评分 → 规则触发 → 最终建议
  - 特别验证：评分<60 的用户走 CREDIT_BUILDING 路径
  执行 mvn test 全部通过。
```

---

## Step 6 — AI 服务（Spring AI + DeepSeek）

> 3 个 teammate：规范 + OCR 服务 + 建议生成服务并行。

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取以下文件：
  - ai-spec/prompts/ocr-extract.md（OCR 提取 Prompt + 结构化输出）
  - ai-spec/prompts/suggestion-gen.md（五段式建议生成 Prompt）
  你是架构师，绝对不写代码，只输出文字规范。

  规范 6.1 — OcrExtractService：
  - 类路径：com.youhua.ai.service.OcrExtractService
  - Spring AI ChatClient 调用方式（model="deepseek-chat"）
  - Prompt 模板（从 ocr-extract.md 提取完整 prompt）
  - 结构化输出解析：JSON → OcrResult DTO 的字段映射
  - 容错策略：JSON 解析失败时的 fallback 逻辑
  - 超时配置：30s timeout + 1 次重试
  - Mock 测试的输入输出示例

  规范 6.2 — SuggestionGenService：
  - 类路径：com.youhua.ai.service.SuggestionGenService
  - 五段式 Prompt 模板（从 suggestion-gen.md 提取）
  - 输入参数：FinanceProfile + List<Debt> + ScoringResult
  - 输出结构：五段文字 + 优先处理的债权机构
  - ⚠️ 约束：AI 只用于文案生成，APR/评分/规则绝不经过 AI

  规范完成后通知 "ocr-coder" 和 "suggestion-coder"。

Teammate "ocr-coder" (用 Sonnet):
  等 spec 完成后，实现 OcrExtractService：
  - 使用 Spring AI 的 ChatClient
  - Prompt 模板用 @Value 注入或 PromptTemplate
  - JSON 结构化输出解析（ObjectMapper）
  - 异常处理：AI 返回非 JSON 时的 fallback
  - 写 OcrExtractServiceTest（Mock ChatClient）
  - mvn compile + mvn test 验证
  完成后通知 team lead。

Teammate "suggestion-coder" (用 Sonnet):
  等 spec 完成后，实现 SuggestionGenService：
  - 五段式 Prompt 组装逻辑
  - 禁止词检查（来自 user-journey.md 第六章禁止表达列表）
  - AI 生成内容的后处理：检查是否违反文案约束
  - 写 SuggestionGenServiceTest（Mock ChatClient）
  - mvn compile + mvn test 验证
  完成后通知 team lead。
```

---

## Step 7 — 报告服务

> 3 个 teammate：规范 + 报告组装 + PDF 导出并行。

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取以下文件：
  - ai-spec/prompts/explainability.md（三层可解释性）
  - ai-spec/domain/user-journey.md 第三章 Page 4 的数据结构
  你是架构师，绝对不写代码，只输出文字规范。

  规范 7.1 — ReportAssembler：
  - 类路径：com.youhua.report.service.ReportAssembler
  - 三层可解释性的组装逻辑：
    第一层：数字摘要（加权APR、三年多付利息、月供占比）
    第二层：逐笔债务分析（每笔的 APR 和贡献占比）
    第三层：AI 建议（调用 SuggestionGenService）
  - threeYearExtraInterest 的计算公式（写出数学表达式）
  - "相当于 XX 个月房租" 的类比计算逻辑

  规范 7.2 — LossVisualization：
  - 可视化数据结构（给前端的 JSON 格式）
  - 饼图数据：各债务利息占比
  - 对比条数据：当前APR vs 市场均值

  规范 7.3 — PdfExportService：
  - PDF 模板结构（A4，分几个 section）
  - 使用什么库（iText / Apache PDFBox）

  规范完成后通知 "report-coder" 和 "pdf-coder"。

Teammate "report-coder" (用 Sonnet):
  等 spec 完成后，实现 ReportAssembler + ReportService：
  - ReportAssembler：三层数据组装
  - ReportService：调用 AprCalculator + ScoringEngine + SuggestionGenService
  - threeYearExtraInterest 计算（BigDecimal）
  - 写 ReportAssemblerTest
  - mvn compile + mvn test 验证

Teammate "pdf-coder" (用 Sonnet):
  等 spec 完成后，实现 PdfExportService：
  - 生成 A4 PDF 报告
  - 包含：数字摘要区、债务明细表、AI 建议区
  - 写 PdfExportServiceTest（验证 PDF 可生成且非空）
  - mvn compile + mvn test 验证
```

---

## Step 8 — 前端 9 页漏斗（Flutter）

> 4 个 teammate：架构规范 + 3 组页面并行。
> 这是前端最大的 Step，三组页面操作不同的 features/ 子目录，完全并行。

```
创建 agent team，使用 delegate mode。

Teammate "flutter-arch" (用 Opus):
  读取 ai-spec/client-spec.md 和 ai-spec/domain/user-journey.md。
  你是前端架构师，绝对不写代码，只输出文字规范。

  输出以下规范：
  1. pubspec.yaml 完整依赖清单（版本号）
  2. lib/core/api/api_client.dart 的类结构（Dio 配置 + 拦截器）
  3. lib/core/api/api_endpoints.dart 的所有 URL 常量
  4. lib/models/ 下所有 DTO 的 Dart 类定义（字段 + 类型）
  5. GoRouter 路由配置（9 个路由 + 评分<60 的重定向逻辑）
  6. lib/core/theme/app_colors.dart 色值定义
  7. 每个 Page 的 Widget 树结构（组件名 + 嵌套关系）
  8. CTA 按钮文案（严格遵循 user-journey.md 第四章）：
     Page 1-3: "继续" / "查看结果"
     Page 4: "看看我的优化空间"（绝不是"立即申请"）
     Page 5: "模拟一下效果"
     Page 6: "了解风险"
     Page 7: "开始准备"
     Page 8: 分层按钮
     Page 9: "查看进度" / "设置提醒"

  规范完成后通知三个 coder 同时开始。

Teammate "page-basic" (用 Sonnet):
  等 flutter-arch 完成后，实现以下页面（操作 lib/features/ 下的子目录）：

  Page 1（lib/features/page1_entry/）：
  - 纯展示页，品牌 Logo + 主文案 + CTA
  - 文案："看看你是否正在多付利息"
  - 不出现"债务""负债""重组"
  - 无需登录，无 API 调用

  Page 2（lib/features/page2_pressure/）：
  - 滑块 1：月总还款（Slider，范围 1000~50000，步进 500）
  - 滑块 2：月收入（区间选择）
  - 压力仪表盘（CustomPainter 环形图）
  - 输入方式必须是 Slider，不是 TextField
  - 即时反馈，无"提交"按钮
  - 文案用"偏高""较重"，不用"危险""严重"

  Page 7（lib/features/page7_risk/）：
  - Q&A 手风琴列表（ExpansionTile）
  - 4 个问答：征信查询、信用评分、失败风险、费用
  - 纯信息展示，不要求任何输入
  - 每个答案具体不含糊

  每页完成后 flutter analyze。
  你只操作 page1_entry/, page2_pressure/, page7_risk/ 三个目录。

Teammate "page-core" (用 Sonnet):
  等 flutter-arch 完成后，实现以下页面（操作 lib/features/ 下的子目录）：

  Page 3（lib/features/page3_debts/）：
  - 三种录入：📷 拍照 OCR / 📋 快速模板 / ✏️ 手动表单
  - 已录入统计栏："已录入 X 笔，已发现 ¥Y 潜在节省"
  - 债务列表（可左滑删除、点击编辑）
  - 每录一笔实时更新
  - 此页触发登录（短信验证码弹窗）
  - API: POST /debts, POST /ocr-tasks, POST /engine:calculateApr

  Page 4（lib/features/page4_report/）：
  - 三年多付利息总额（大字体 + 数字从 0 滚动到最终值的动画，1.5秒）
  - 加权 APR 对比条（当前 vs 市场均值）
  - 月供占收入比（与 40% 健康线对比）
  - 类比模块："相当于 XX 个月房租"
  - fl_chart 饼图：各债务利息占比
  - ⚠️ 不用红色，用蓝/橙信息风格
  - ⚠️ CTA 只能是 "看看我的优化空间 →"
  - API: POST /reports, GET /reports/{id}

  Page 6（lib/features/page6_simulator/）：
  - 利率滑块（左端=当前APR，右端=可能优化APR）
  - 实时联动三个数字（带 TweenAnimation 过渡）：
    月供变化、三年节省总额、月供占收入比
  - 滑块必须有阻尼感（SliderThemeData 自定义）
  - 不预设目标利率
  - 底部免责声明小字
  - API: POST /engine:simulateRate（防抖 300ms）

  每页完成后 flutter analyze。
  你只操作 page3_debts/, page4_report/, page6_simulator/ 三个目录。

Teammate "page-action" (用 Sonnet):
  等 flutter-arch 完成后，实现以下页面（操作 lib/features/ 下的子目录）：

  Page 5（lib/features/page5_opportunity/）：
  - 第一句必须正面："好消息是，你有优化空间。"
  - 成功概率卡片、月供对比柱状图、分阶段路径预览
  - ⚠️ 评分<60 的分支：
    不显示"重组推荐"，改为"当前更适合优化信用结构"
    CTA 改为"查看改善计划 →" → 直接跳 Page 9
  - API: GET /reports/{id}

  Page 8（lib/features/page8_action/）：
  - 四层 Stepper：资料清单 → 申请材料 → 模拟审批 → 正式提交(V2.0灰色)
  - 每层有"暂不继续"出口
  - 进度条 1/4 → 2/4 → 3/4
  - 不自动跳下一层，用户主动点击
  - 已完成层可回看

  Page 9（lib/features/page9_companion/）：
  - 优化进度 Timeline
  - 30/60/90 天 Checklist（可勾选）
  - 下一检查点提醒卡片
  - 正面强化："你已经迈出了第一步"
  - 评分<60 的用户：信用修复路线图 + 30 天改善计划

  每页完成后 flutter analyze。
  你只操作 page5_opportunity/, page8_action/, page9_companion/ 三个目录。
```

---

## Step 9 — 集成测试 + Seed 数据

> 3 个 teammate 并行：后端集成测试、Seed 脚本、前后端联调测试。

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 ai-spec/test/test-matrix.md 和 ai-spec/test/mock-data.md。
  你是测试架构师，绝对不写代码，只输出文字规范。

  规范 9.1 — 集成测试：
  - 测试类命名 + 方法列表（每个方法对应 test-matrix.md 的一个场景）
  - 5 类用户画像的完整测试数据（来自 mock-data.md）
  - 每个测试的：输入、调用的 API、期望响应、断言条件

  规范 9.2 — Seed 脚本：
  - 5 类用户的 INSERT SQL（User + Debt + Income + FinanceProfile）
  - 每类用户 3~5 笔 Debt 数据
  - 金额必须用精确的 BigDecimal 值

  规范 9.3 — 并发/安全测试：
  - 并发场景：同一用户同时创建多笔 Debt 的竞态条件
  - 安全场景：用户 A 尝试访问用户 B 的数据（必须 403）
  - SQL 注入测试：creditor 字段注入 SQL 片段

  规范完成后通知三个 coder。

Teammate "integration-coder" (用 Sonnet):
  等 spec 完成后，实现后端集成测试：
  - 使用 @SpringBootTest + TestRestTemplate
  - 覆盖 test-matrix.md 中的所有场景
  - 特别关注：
    - 评分<60 用户不返回 URGENT_ATTENTION
    - APR 精度 < 0.01%
    - 状态机非法转换返回正确错误码
  - mvn test 全部通过

Teammate "seed-coder" (用 Sonnet):
  等 spec 完成后，生成：
  - src/test/resources/seed/V999__test_seed.sql
  - 5 类用户画像的完整 Seed 数据
  - 每类 3~5 笔 Debt，覆盖所有 debtType 枚举值
  - 同时生成 data.sql（开发环境自动加载）

Teammate "security-coder" (用 Sonnet):
  等 spec 完成后，实现安全和并发测试：
  - SecurityTest：跨用户访问（userId 隔离）
  - ConcurrencyTest：@RepeatedTest + CountDownLatch 并发创建 Debt
  - InjectionTest：SQL 注入 / XSS 注入
  - mvn test 全部通过
```

---

## 全流程并行执行总览

```
Week 1:
  Day 1: Step 1（骨架）→ Step 2（Entity）       串行，约 2-3 小时
  Day 2: Step 3（Controller + DTO）              约 2-3 小时
  Day 3: Step 4（状态机）                         约 1-2 小时
  Day 4-5: Step 5（计算引擎）⬅ 最重要，给两天     约 4-6 小时

Week 2:
  Day 6: Step 6（AI 服务）                        约 2-3 小时
  Day 7: Step 7（报告服务）                        约 2-3 小时
  Day 8-10: Step 8（前端 9 页）⬅ 并行最大收益      约 6-8 小时

Week 3:
  Day 11-12: 前后端联调
  Day 13: Step 9（测试 + Seed）                    约 3-4 小时
  Day 14-15: Bug 修复 + UI 打磨

每个 Step 内部的并行策略：
  Step 1: 串行（太简单）
  Step 2: spec → (entity + migration) 并行
  Step 3: spec → (error + dto) 并行 → controller 串行
  Step 4: 串行（依赖紧密）
  Step 5: spec → (apr + scoring) 并行 → integrator 串行    ⬅ 关键
  Step 6: spec → (ocr + suggestion) 并行
  Step 7: spec → (report + pdf) 并行
  Step 8: arch → (basic + core + action) 三路并行          ⬅ 最大并行
  Step 9: spec → (integration + seed + security) 三路并行
```

---

## 注意事项

1. **每个 Step 用一个新的 Agent Team**。Step 完成后 team lead 自动清理，
   下一个 Step 重新创建 team。不要把 9 个 Step 塞进一个 team。

2. **spec teammate 用 Opus，coder teammate 用 Sonnet**。
   Opus 推理强但贵，用在规范制定上值得；Sonnet 写代码性价比更高。
   在 spawn prompt 中指定：`用 Sonnet 模型`。

3. **delegate mode 很重要**。如果不开 delegate mode，team lead 会自己动手
   写代码而不是协调 teammate——这就是 ccteam 遇到的 Leader 直接写代码的问题。

4. **文件隔离是并行的基础**。每个 coder teammate 只操作自己负责的目录/包，
   在 spawn prompt 里明确写清楚"你只操作 xxx 目录"，避免文件冲突。

5. **mvn compile / flutter analyze 是每个 coder 的验收门槛**。
   不通过不算完成。team lead 可以设置 quality gate hook 自动检查。
