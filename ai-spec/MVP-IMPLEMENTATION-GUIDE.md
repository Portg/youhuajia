# 优化家 MVP 实施方案：Claude 全链路落地指南

## 一、工具矩阵：三层 Claude 各管什么

```
┌─────────────────────────────────────────────────────┐
│  Claude Projects (Team Plan)                         │
│  ➜ 团队共享知识库 + AI 约束统一                        │
│  ➜ 产品讨论 / 方案评审 / 文案生成                      │
│  角色：产品经理、设计师、业务方                          │
├─────────────────────────────────────────────────────┤
│  Claude Code (CLI / VS Code / Web)                   │
│  ➜ 代码生成 / 重构 / 测试 / Git 管理                   │
│  ➜ 读取 CLAUDE.md 作为约束，分步执行 9-Step Workflow    │
│  角色：开发者                                          │
├─────────────────────────────────────────────────────┤
│  Claude.ai 对话（当前界面）                             │
│  ➜ 规范文档生成 / 方案论证 / 技术选型讨论                │
│  ➜ 生成 ai-spec 包并持续迭代                           │
│  角色：架构师 / 技术负责人                               │
└─────────────────────────────────────────────────────┘
```

---

## 二、Phase 0 — 团队基建（第 1 天）

### 2.1 开通 Claude Team Plan

- 最低 5 席，$30/人/月 = $150/月
- 如果团队不足 5 人，可邀请外部顾问/兼职占席
- Team Plan 核心价值：共享 Projects + 200K 上下文 + 更高消息限额

### 2.2 创建 4 个 Claude Projects

每个 Project 是一个独立的 AI 工作空间，有自己的知识库和指令集。

```
Project 1: 📐 优化家-架构与规范
  ├── 知识库上传：ai-spec/ 全部 16 个文件
  ├── 自定义指令（见下方）
  ├── 权限：Tech Lead = Can Edit, 其他开发 = Can Use
  └── 用途：技术方案讨论、规范查询、接口设计评审

Project 2: 🎨 优化家-产品与体验
  ├── 知识库上传：
  │   ├── domain/user-journey.md（核心）
  │   ├── prompts/suggestion-gen.md
  │   ├── prompts/explainability.md
  │   └── 优化家_APP_MVP版_.md（原始产品文档）
  ├── 自定义指令（见下方）
  ├── 权限：产品经理 = Can Edit, 设计师 = Can Edit, 开发 = Can Use
  └── 用途：页面文案撰写、用户路径讨论、心理模型验证

Project 3: 🧪 优化家-测试与质量
  ├── 知识库上传：
  │   ├── test/test-matrix.md
  │   ├── test/mock-data.md
  │   ├── contracts/error-codes.md
  │   └── engine/*.md（计算引擎规范）
  ├── 权限：QA = Can Edit, 开发 = Can Use
  └── 用途：测试用例生成、边界条件讨论、Bug 分析

Project 4: 📊 优化家-商业与竞品
  ├── 知识库上传：资管星 2.0 文档 + 市场分析资料
  ├── 权限：业务方 = Can Edit
  └── 用途：竞品分析、商业模式验证、投标材料准备
```

### 2.3 Project 自定义指令（关键）

**Project 1 — 架构与规范 的自定义指令：**

```
你是优化家项目的技术架构师。请严格遵循以下约束：

1. 技术栈：Java 21 + Spring Boot 3.x + MySQL + Redis + Spring AI
2. 所有金额/利率计算必须使用 BigDecimal，禁止 float/double
3. 接口设计遵循 Google API Design Guide (AIP-121~161)
4. 状态变更必须通过状态机驱动，不允许直接 set
5. MVP 阶段是单体应用，不引入微服务框架
6. 为 V2.0 预留字段/接口/枚举，但不实现业务代码

回答技术问题时，先检索知识库中的对应规范文件再回答。
如果问题涉及多个文件，列出参考了哪些规范。
```

**Project 2 — 产品与体验 的自定义指令：**

```
你是优化家的产品体验设计师。请严格遵循 user-journey.md 中的心理路径设计。

核心原则：
1. 每一步只推动"半步"，不恐吓、不催促、不否定
2. 文案主语是"你的财务结构"，不是"你"
3. 动词用"优化/调整/改善"，不用"解决/修复"
4. "申请按钮"只能出现在 Page 8-9，绝不在 Page 4
5. 评分 < 60 的用户不展示否定结论，改为"信用修复路线图"

禁止的表达（写任何文案前检查）：
- "你的债务问题很严重" → "你的财务结构有优化空间"
- "高风险" → "需要关注"
- "赶紧行动" → "你可以从这一步开始"
- "最后机会" → 永远不使用
```

---

## 三、Phase 1 — Claude Code 搭建后端骨架（第 2-5 天）

### 3.1 环境准备

```bash
# 安装 Claude Code
curl -fsSL https://claude.ai/install-cli | sh

# 创建项目目录
mkdir youhuajia-mvp && cd youhuajia-mvp
git init

# 把 ai-spec 放进项目根目录
cp -r ~/ai-spec ./ai-spec

# 创建 CLAUDE.md（Claude Code 会自动读取项目根目录的 CLAUDE.md）
cp ai-spec/CLAUDE.md ./CLAUDE.md
```

**关键机制**：Claude Code 启动时会自动读取项目根目录的 `CLAUDE.md` 文件作为行为约束，
这意味着我们的 10+ 条禁止项、命名规范、技术栈限制会自动生效。

### 3.2 九步执行（每步一个 Claude Code 会话）

**Step 1 — Maven 骨架 + 包结构**

```bash
claude

# 在 Claude Code 中输入：
> 阅读 ai-spec/CLAUDE.md 和 ai-spec/domain/evolution.md，
> 生成 Spring Boot 3.x + Java 21 的 Maven 项目骨架。
> 包结构按 com.youhua/{common,debt,profile,engine,ai,infra} 组织。
> pom.xml 包含：Spring Boot 3.x, Spring AI (DeepSeek), MySQL, Redis,
> Flyway, Resilience4j, springdoc-openapi。
> 生成后执行 mvn compile 验证。
```

**Step 2 — Entity + Flyway 迁移**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/domain/entities.md + ai-spec/domain/evolution.md。
> 为 7 个核心实体生成：
> 1. JPA Entity 类（字段类型严格按 entities.md）
> 2. Flyway 迁移脚本 V1__init.sql
> 3. Repository 接口
> 注意：金额用 BigDecimal，时间字段用 xxxTime 后缀，
> 布尔不加 is 前缀，所有表包含 id/create_time/update_time/deleted/version。
> evolution.md 中的预留字段加注释但不建索引。
> 完成后执行 mvn compile。
```

**Step 3 — Controller + DTO + 异常处理**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/contracts/openapi.yaml + ai-spec/contracts/error-codes.md。
> 按 openapi.yaml 生成：
> 1. 所有 Controller（方法名对齐 operationId）
> 2. Request/Response DTO（遵循 Google AIP 命名）
> 3. 统一异常处理（ErrorResponse 对齐 google.rpc.Status）
> 4. 错误码枚举（含 httpStatus + status 映射）
> PATCH 接口必须支持 updateMask 参数。
> 分页使用 pageSize + pageToken。
> 完成后执行 mvn compile。
```

**Step 4 — 状态机**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/domain/state-machines.yaml。
> 生成 4 个状态机的实现：
> 1. DebtStatus 枚举 + 状态转换校验
> 2. 转换 guard 条件（principal > 0, loanDays > 0 等）
> 3. 转换 action（calculateApr, triggerProfileRecalculation）
> 4. 超时处理逻辑
> 每个非法转换必须抛 DEBT_STATE_INVALID 异常。
> 完成后执行 mvn compile + mvn test。
```

**Step 5 — 计算引擎 + 单元测试**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/engine/apr-calc.md +
> ai-spec/engine/scoring-model.md + ai-spec/engine/rules.md。
> 生成：
> 1. AprCalculator（BigDecimal，中间步骤 scale=10）
> 2. ScoringEngine（五维评分，权重可配置）
> 3. RuleEngine（4 类规则按顺序执行）
> 4. 对应的单元测试（覆盖 apr-calc.md 中全部 20 个测试用例）
> 所有阈值从 application.yml 读取，不硬编码。
> 完成后执行 mvn test，所有测试必须通过。
```

**Step 6 — AI 服务（Spring AI + DeepSeek）**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/prompts/ocr-extract.md +
> ai-spec/prompts/suggestion-gen.md。
> 使用 Spring AI 的 ChatClient 生成：
> 1. OcrExtractService（调用 DeepSeek，JSON 输出 + 容错解析）
> 2. SuggestionGenService（五段式心理结构 Prompt）
> 3. Prompt 模板管理（模板变量注入）
> 4. 超时重试（Spring AI 内置 retry，配置 1 次重试）
> 注意：AI 只用于 OCR 和文案，APR/评分/规则绝不经过 AI。
> spring.ai.deepseek.api-key 从环境变量读取。
> 完成后执行 mvn compile。
```

**Step 7 — 报告服务 + PDF**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/prompts/explainability.md。
> 生成：
> 1. ReportService（组装三层可解释性数据）
> 2. PDF 导出（损失可视化 + 评分雷达图）
> 3. lossVisualization 计算（三年累计、月供收入比等）
> 完成后执行 mvn compile。
```

**Step 8 — 前端 API 层 + 关键页面**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/contracts/openapi.yaml +
> ai-spec/domain/user-journey.md + ai-spec/prompts/explainability.md。
> 生成 React/TypeScript 前端：
> 1. API 调用层（基于 openapi.yaml 的类型安全封装）
> 2. 9 页渐进式漏斗的路由结构
> 3. Page 2 快速压力检测（滑块输入 + 实时仪表盘）
> 4. Page 4 损失可视化（大字体数字 + 动画）
> 5. Page 6 利率滑动模拟器
> 每页的 CTA 按钮文案严格遵循 user-journey.md。
> 评分 < 60 用户走独立分支。
```

**Step 9 — 集成测试 + Seed 数据**

```bash
claude

> 阅读 ai-spec/CLAUDE.md + ai-spec/test/test-matrix.md + ai-spec/test/mock-data.md。
> 生成：
> 1. 集成测试（覆盖 test-matrix.md 全部场景）
> 2. Seed 脚本（5 类用户画像的测试数据）
> 3. 并发测试（乐观锁冲突、画像计算竞争）
> 4. 安全测试（跨用户访问、SQL 注入、XSS）
> 完成后执行 mvn test，全部通过。
```

### 3.3 Claude Code 高级用法

**子代理并行**（大特性拆分）：

```bash
claude

> 用子代理并行完成以下任务：
> Agent 1: 生成 debt 模块的 Controller + Service + Repository
> Agent 2: 生成 engine 模块的 APR + Scoring + Rules
> Agent 3: 生成 Flyway 迁移脚本和 Seed 数据
> 每个 Agent 完成后运行 mvn compile 验证。
```

**Git Hook 自动化**：

```bash
# 在 .claude/settings.json 中配置 hooks
# 每次 Claude Code 提交前自动运行测试
{
  "hooks": {
    "PreCommit": {
      "command": "mvn test -q",
      "description": "提交前自动运行测试"
    }
  }
}
```

**CLAUDE.md 的魔力**：

```
Claude Code 在每个会话开始时自动读取项目根目录的 CLAUDE.md。
这意味着：
- F-01 (禁止 float/double) → 生成代码自动用 BigDecimal
- F-11 (禁止恐慌文案) → 生成的 AI Prompt 自动遵循心理路径
- 命名规范 → 自动 camelCase JSON, snake_case DB, kebab-case URL
- 不需要每次提醒，约束始终生效
```

---

## 四、Phase 2 — 团队协作工作流（持续）

### 4.1 产品经理在 Project 2 中工作

```
典型场景：写 Page 4 的损失报告文案

PM 在 Project 2 中对话：
> "帮我写 Page 4 损失可视化页面的文案，
>  用户有 3 笔债务，加权 APR 24%，三年多付 82400 元。
>  注意遵循 user-journey.md 的文案约束。"

Claude 会自动参考知识库中的 user-journey.md，
输出符合心理路径的文案，不会出现禁止表达。
```

### 4.2 设计师在 Project 2 中工作

```
典型场景：设计利率模拟器交互

设计师在 Project 2 中对话：
> "Page 6 的利率滑动模拟器，给我具体的交互规范，
>  包括滑块的阻尼感、数字动画、色彩建议。"

Claude 参考 user-journey.md 中 Page 6 的约束，
输出不预设目标利率、有过渡动画、底部带 disclaimer 的方案。
```

### 4.3 开发者在 Claude Code 中工作

```
典型场景：修复一个计算精度 bug

开发者在 Claude Code 中：
> "AprCalculator 在 principal=0.01 时结果异常，
>  参考 ai-spec/engine/apr-calc.md 的边界测试用例修复。"

Claude Code 读取 CLAUDE.md 约束 + apr-calc.md 规范，
自动用 BigDecimal 修复，补充边界测试，运行 mvn test。
```

### 4.4 QA 在 Project 3 中工作

```
典型场景：生成新场景的测试用例

QA 在 Project 3 中对话：
> "OCR 识别到 principal=0 的情况，
>  参考 test-matrix.md 补充测试用例。"

Claude 参考知识库中的规则引擎规范，
输出应该触发 DATA_001 规则的 BLOCK 结果。
```

---

## 五、协作流信息流转图

```
                    ┌──────────────────┐
                    │ Claude.ai 对话    │
                    │ (架构师/你)       │
                    │                  │
                    │ 输出 ai-spec 包   │
                    └────────┬─────────┘
                             │ 上传到
                             ▼
┌───────────────────────────────────────────────────┐
│              Claude Team Projects                  │
│                                                   │
│  ┌─────────┐  ┌─────────┐  ┌────────┐  ┌───────┐ │
│  │Project 1│  │Project 2│  │Project3│  │Proj 4 │ │
│  │架构规范  │  │产品体验  │  │测试质量 │  │商业   │ │
│  │         │  │         │  │        │  │       │ │
│  │开发查询  │  │PM 文案   │  │QA 用例  │  │BP 材料│ │
│  │接口评审  │  │设计交互  │  │Bug 分析 │  │竞品   │ │
│  └────┬────┘  └────┬────┘  └───┬────┘  └───────┘ │
│       │            │           │                   │
└───────┼────────────┼───────────┼───────────────────┘
        │            │           │
        ▼            ▼           ▼
┌─────────────────────────────────────────┐
│           Claude Code (开发者)            │
│                                         │
│  自动读取 CLAUDE.md 约束                  │
│  分步执行 9-Step Workflow                 │
│  子代理并行加速                            │
│  Hook 自动化测试                           │
│                                         │
│  输出 → Git 仓库 → CI/CD → 部署          │
└─────────────────────────────────────────┘
```

---

## 六、MVP 验证里程碑（4 周）

### Week 1：基础设施 + 核心引擎

| 天 | 任务 | 工具 | 产出 |
|----|------|------|------|
| D1 | 开通 Team Plan，建 4 个 Projects，上传知识库 | Claude.ai | 4 个 Project 就绪 |
| D2 | Step 1-2：Maven 骨架 + Entity + Flyway | Claude Code | 编译通过的骨架 |
| D3 | Step 3-4：Controller/DTO + 状态机 | Claude Code | API 层就绪 |
| D4 | Step 5：计算引擎 + 20 个单元测试 | Claude Code | mvn test 全绿 |
| D5 | Step 6：Spring AI + DeepSeek 集成 | Claude Code | OCR + 建议生成就绪 |

### Week 2：产品体验 + 前端

| 天 | 任务 | 工具 | 产出 |
|----|------|------|------|
| D6 | Step 7：报告服务 + PDF 导出 | Claude Code | 报告完整链路 |
| D7-8 | Step 8：前端 9 页漏斗 | Claude Code | 核心页面可交互 |
| D9 | Page 2 压力检测 + Page 6 模拟器 | Claude Code | 两个关键交互就绪 |
| D10 | PM 在 Project 2 产出全部页面文案 | Claude Projects | 文案定稿 |

### Week 3：联调 + 测试

| 天 | 任务 | 工具 | 产出 |
|----|------|------|------|
| D11-12 | 前后端联调 | Claude Code | 全链路跑通 |
| D13 | Step 9：集成测试 + Seed 数据 | Claude Code | 测试覆盖 |
| D14 | QA 在 Project 3 补充边界用例 | Claude Projects | 测试用例完善 |
| D15 | 安全测试 + 性能基准 | Claude Code | 安全报告 |

### Week 4：验证 + 迭代

| 天 | 任务 | 工具 | 产出 |
|----|------|------|------|
| D16-17 | 部署测试环境，内部试用 | — | 可访问的测试版 |
| D18-19 | 收集反馈，快速迭代 | Claude Code | 修复 + 优化 |
| D20 | MVP Demo + 验证报告 | Claude Projects | 可演示的 MVP |

---

## 七、成本估算

| 项目 | 月费 | 备注 |
|------|------|------|
| Claude Team Plan (5 席) | $150 | PM + 2 开发 + 设计 + QA |
| Claude Code (Max Plan) | $100-200 | 主力开发 1-2 人，按需 |
| DeepSeek API | ~$10 | MVP 阶段调用量小 |
| 服务器 (测试环境) | ~$50 | 轻量云主机 |
| **合计** | **~$400/月** | 4 周 MVP 总成本 ~$400 |

---

## 八、关键成功因素

### 8.1 CLAUDE.md 是核心杠杆

```
整个方案的杠杆点是 CLAUDE.md 这个文件。

它同时被三个工具读取：
  - Claude Projects → 通过知识库上传
  - Claude Code → 自动读取项目根目录
  - Claude.ai → 对话中引用

这意味着：一处定义，全链路生效。
改一次 CLAUDE.md，所有工具的行为同时更新。
```

### 8.2 Project 知识库保持同步

```
当 ai-spec 文件有更新时：
1. 在 Claude.ai 中修改并重新打包
2. 替换 Claude Projects 中对应的知识库文件
3. 更新项目根目录的副本（Claude Code 自动读取）

建议用 Git 管理 ai-spec/，每次修改都 commit。
```

### 8.3 不要跳步

```
9 个 Step 的顺序是精心设计的：
  先有 Entity 才能写 Repository
  先有 openapi.yaml 才能写 Controller
  先有状态机才能写业务逻辑
  先有引擎才能写报告

跳步 = 返工。严格按顺序来。
```
