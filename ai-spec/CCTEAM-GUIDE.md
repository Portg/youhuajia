# 优化家 MVP — ccteam / Agent Teams 精细化分工操作手册

## ⚠️ 前置说明：ccteam vs 原生 Agent Teams

| 维度 | ccteam (v0.9.0) | Claude Code Agent Teams (原生) |
|------|-----------------|-------------------------------|
| 状态 | 已归档 (2025-11-30)，停止更新 | 2026-02-05 发布，持续迭代中 |
| 安装 | `npx ccteam@latest start` | 内置，设置环境变量即可启用 |
| 角色模型 | 固定三角色：Manager → Leader → Worker | 灵活：Team Lead + N 个 Teammates |
| 通信机制 | 通过 `npx ccteam agent:*` 命令传递 | 原生 Mailbox 消息系统 |
| 自定义 | ccteam.yml 配置模型/权限 | 自然语言描述角色和任务 |
| 推荐 | 概念清晰，适合理解分工模式 | 生产使用推荐，功能更完整 |

**本文档两套方案都给，标注 [ccteam] 和 [原生] 方便选择。**

---

## 一、核心思路：ai-spec 三层映射到三角色

ccteam 的精髓是把"想清楚"和"写代码"分成三个独立上下文：

```
┌─────────────────────────────────────────────────────────────┐
│                                                             │
│  Manager（接需求，拆任务）                                    │
│  ➜ 读取：CLAUDE.md + README.md + user-journey.md            │
│  ➜ 输出：任务清单 + 依赖关系 + 优先级                         │
│  ➜ 类比：产品经理 / Tech Lead                                │
│                                                             │
│          │ 任务列表 + 上下文                                  │
│          ▼                                                   │
│                                                             │
│  Leader（写规范，审代码）                                      │
│  ➜ 读取：Manager 的任务 + 对应的 spec 文件                    │
│  ➜ 输出：详细实现规范 + 验收标准 + 文件清单                    │
│  ➜ 类比：架构师 / Senior Developer                           │
│                                                             │
│          │ 实现规范 + 验收标准                                 │
│          ▼                                                   │
│                                                             │
│  Worker（写代码，跑测试）                                      │
│  ➜ 读取：Leader 的规范 + 具体的 spec 文件                     │
│  ➜ 输出：代码 + 测试 + mvn compile/test 结果                  │
│  ➜ 类比：Developer                                           │
│                                                             │
└─────────────────────────────────────────────────────────────┘
```

**关键洞察**：每个角色有独立的上下文窗口（200K tokens），
不会互相"污染"。Manager 不需要看代码细节，Worker 不需要看产品全貌。
ai-spec 的 16 个文件按角色精确分配，避免上下文浪费。

---

## 二、环境准备

### 2.1 项目结构

```bash
youhuajia-mvp/
├── ai-spec/                    ← 我们的规范包（16 个文件）
│   ├── CLAUDE.md
│   ├── README.md
│   ├── contracts/
│   ├── domain/
│   ├── engine/
│   ├── prompts/
│   └── test/
├── CLAUDE.md                   ← 项目根目录的约束（从 ai-spec 复制）
├── ccteam.yml                  ← [ccteam] 角色配置
├── .claude/
│   ├── settings.json           ← 权限 + Agent Teams 启用
│   └── agents/                 ← [原生] 角色定义文件
│       ├── manager.md
│       ├── leader.md
│       └── worker.md
├── src/                        ← Java 代码（Worker 输出）
├── frontend/                   ← React 代码（Worker 输出）
└── pom.xml
```

### 2.2 [ccteam] 安装和配置

```bash
# 前置条件
# 1. Claude Code 已安装
# 2. tmux 已安装

# 初始化配置
npx ccteam@latest init
```

编辑 `ccteam.yml`：

```yaml
# ccteam.yml — 优化家 MVP 角色配置
roles:
  manager:
    model: "opus"              # Manager 需要强推理能力，用 Opus
    skipPermissions: false      # Manager 只做拆解不执行命令，保持安全

  leader:
    model: "opus"              # Leader 要写详细规范，用 Opus
    skipPermissions: false

  worker:
    model: "sonnet"            # Worker 写代码用 Sonnet 性价比更高
    skipPermissions: true       # Worker 需要频繁执行 mvn compile/test
```

配置 `.claude/settings.json`：

```json
{
  "permissions": {
    "allow": [
      "Bash(npx ccteam@latest agent:*)",
      "Bash(mvn *)",
      "Bash(npm *)",
      "Bash(git *)"
    ]
  }
}
```

### 2.3 [原生 Agent Teams] 启用和配置

```json
// .claude/settings.json
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  },
  "permissions": {
    "allow": [
      "Bash(mvn *)",
      "Bash(npm *)",
      "Bash(git *)"
    ]
  }
}
```

创建角色定义文件（原生 Agent Teams 的等效方式）：

```markdown
<!-- .claude/agents/manager.md -->
# Manager Agent — 任务拆解与协调

你是优化家 MVP 项目的 Manager。你的职责是：
1. 接收用户需求，拆解为可执行的任务单元
2. 为每个任务指定要读取的 ai-spec 文件
3. 确定任务依赖关系和执行顺序
4. 不写代码，不执行命令，只做规划和协调

你必须先读取：
- ai-spec/CLAUDE.md（全局约束）
- ai-spec/README.md（9-Step 工作流）

拆解任务时，输出格式：
- 任务名称
- 输入文件（ai-spec 中的哪些文件）
- 输出物（代码/测试/配置）
- 依赖关系（哪些任务必须先完成）
- 验收标准（怎么判断完成）
```

```markdown
<!-- .claude/agents/leader.md -->
# Leader Agent — 实现规范制定

你是优化家 MVP 项目的 Leader。你的职责是：
1. 接收 Manager 拆解的任务
2. 读取指定的 ai-spec 文件
3. 输出详细的实现规范（类名、方法签名、字段映射、异常处理）
4. 制定验收测试标准
5. 代码审查 Worker 的输出

你的输出必须具体到：
- Java 类的完整路径
- 方法签名（参数类型、返回类型）
- 数据库字段和 Java 字段的映射
- 异常场景和错误码
- 需要通过的测试用例
```

```markdown
<!-- .claude/agents/worker.md -->
# Worker Agent — 代码实现

你是优化家 MVP 项目的 Worker。你的职责是：
1. 严格按照 Leader 的实现规范写代码
2. 每写完一个模块执行 mvn compile 验证
3. 写完测试执行 mvn test 验证
4. 不自行决定架构或接口设计，有疑问向 Leader 确认

技术约束（来自 CLAUDE.md）：
- Java 21 + Spring Boot 3.x
- 金额用 BigDecimal，禁止 float/double
- 时间字段用 xxxTime 后缀
- 布尔字段不加 is 前缀
```

---

## 三、9-Step Workflow × 三角色精细分工

### Step 1：Maven 骨架 + 包结构

#### [ccteam] 操作

```bash
# 启动 ccteam
npx ccteam@latest start

# 连接到 tmux 会话
tmux attach -t ccteam-XXXXX

# 在 Manager 面板输入：
```

**给 Manager 的指令：**

```
请读取 ai-spec/CLAUDE.md 和 ai-spec/domain/evolution.md。

任务：创建 Spring Boot 3.x + Java 21 的 Maven 项目骨架。

拆解为子任务并分配给 Leader：
1. pom.xml 依赖清单（需要的所有 starter）
2. 包结构设计（按 evolution.md 中的模块拆分）
3. 基础配置文件（application.yml 分环境）
4. .gitignore 和项目元数据

每个子任务标注依赖关系。
```

**Manager 输出示例（自动传递给 Leader）：**

```
任务 1.1: pom.xml
  输入文件: CLAUDE.md (技术选型表)
  输出: pom.xml
  依赖: 无
  验收: mvn compile 通过

任务 1.2: 包结构
  输入文件: evolution.md (模块拆分), entities.md (实体归属)
  输出: src/main/java/com/youhua/{common,debt,profile,engine,ai,infra}/
  依赖: 1.1
  验收: 每个包有 package-info.java

任务 1.3: 配置文件
  输入文件: CLAUDE.md (数据库/Redis配置)
  输出: application.yml, application-dev.yml, application-prod.yml
  依赖: 1.1
  验收: Spring Boot 启动不报错
```

**Leader 接收后，输出详细规范给 Worker：**

```
实现规范 1.1 — pom.xml

parent: spring-boot-starter-parent:3.4.x
java.version: 21

依赖清单:
  - spring-boot-starter-web
  - spring-boot-starter-data-jpa
  - spring-boot-starter-data-redis
  - spring-boot-starter-validation
  - spring-ai-starter-model-deepseek
  - mysql-connector-j (runtime)
  - flyway-core
  - flyway-mysql
  - resilience4j-spring-boot3
  - springdoc-openapi-starter-webmvc-ui
  - lombok (provided)
  - spring-boot-starter-test (test)

禁止引入:
  - 任何 spring-cloud 依赖
  - 任何 mybatis 依赖（用 JPA）
  
验收命令: mvn compile -q && echo "PASS"
```

**Worker 接收后写代码，执行 mvn compile。**

#### [原生 Agent Teams] 等效操作

```bash
# 启动 Claude Code
cd youhuajia-mvp
claude

# 在 Claude Code 中输入：
> 创建一个 agent team 来搭建 Maven 项目骨架。
>
> 团队结构：
> - Teammate "planner": 读取 ai-spec/CLAUDE.md 和 ai-spec/domain/evolution.md，
>   输出 pom.xml 依赖清单 + 包结构设计 + 配置文件规范。
>   用 Opus 模型。不写代码，只输出规范。
>
> - Teammate "coder": 等 planner 完成后，按规范生成代码。
>   用 Sonnet 模型。每个文件生成后执行 mvn compile 验证。
>
> - Teammate "reviewer": 等 coder 完成后，检查代码是否符合
>   CLAUDE.md 的禁止项和命名规范。
>
> 用 delegate mode，team lead 只协调不写代码。
```

---

### Step 2-9：完整任务分配表

下表是 9 个 Step 中每个角色的精确职责和输入文件：

```
╔══════════════════════════════════════════════════════════════════════════╗
║  Step 2: Entity + Flyway                                               ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: entities.md, evolution.md║ 拆解:                     ║
║            ║                                ║  2.1 JPA Entity (7个)     ║
║            ║                                ║  2.2 Flyway V1__init.sql  ║
║            ║                                ║  2.3 Repository 接口      ║
║            ║                                ║ 依赖: 2.1→2.2→2.3        ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: entities.md 字段详情      ║ 输出每个 Entity 的:       ║
║            ║ + state-machines.yaml          ║  - 完整字段列表+类型       ║
║            ║                                ║  - 索引定义                ║
║            ║                                ║  - 预留字段(加注释)        ║
║            ║                                ║  - JPA 注解规范            ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Worker    ║ 按 Leader 规范写代码            ║ 验收:                     ║
║            ║                                ║  mvn compile              ║
║            ║                                ║  Flyway migrate 不报错     ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════╗
║  Step 3: Controller + DTO + 异常处理                                    ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: openapi.yaml             ║ 拆解:                     ║
║            ║                                ║  3.1 DTO (Request/Resp)   ║
║            ║                                ║  3.2 ErrorResponse 体系   ║
║            ║                                ║  3.3 Controller (按tag分)  ║
║            ║                                ║ 依赖: 3.1,3.2→3.3        ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: openapi.yaml 每个 path   ║ 输出:                     ║
║            ║ + error-codes.md               ║  - 每个 DTO 的字段映射     ║
║            ║                                ║  - updateMask 实现方式     ║
║            ║                                ║  - pageToken 编解码逻辑    ║
║            ║                                ║  - ErrorResponse 结构      ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Worker    ║ 按 Leader 规范写代码            ║ 验收:                     ║
║            ║                                ║  mvn compile              ║
║            ║                                ║  Swagger UI 可访问         ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════╗
║  Step 4: 状态机                                                         ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: state-machines.yaml      ║ 拆解:                     ║
║            ║                                ║  4.1 状态枚举 (4个)       ║
║            ║                                ║  4.2 Guard 条件实现       ║
║            ║                                ║  4.3 Action 触发逻辑      ║
║            ║                                ║  4.4 超时处理             ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: state-machines.yaml      ║ 输出:                     ║
║            ║ 逐个状态转换的 guard/action     ║  - 状态转换表(枚举方法)    ║
║            ║                                ║  - 每个 guard 的参数校验   ║
║            ║                                ║  - 非法转换的异常处理      ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Worker    ║ 实现 + 单元测试                 ║ 验收:                     ║
║            ║                                ║  mvn test (状态机测试全过) ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════╗
║  Step 5: 计算引擎（最关键的 Step）                                       ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: apr-calc.md,             ║ 拆解:                     ║
║            ║ scoring-model.md, rules.md     ║  5.1 AprCalculator        ║
║            ║                                ║  5.2 ScoringEngine        ║
║            ║                                ║  5.3 RuleEngine           ║
║            ║                                ║  5.4 单元测试(20+用例)    ║
║            ║                                ║ 依赖: 5.1,5.2→5.3→5.4    ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: apr-calc.md 全部公式      ║ 输出:                     ║
║            ║ + scoring-model.md 五维模型     ║  - BigDecimal scale 策略  ║˛
║            ║ + rules.md 规则优先级           ║  - 牛顿迭代法边界条件      ║
║            ║                                ║  - 五维权重配置化方案       ║
║            ║                                ║  - 20个测试用例的期望值    ║
║            ║                                ║                           ║
║            ║ ⚠️ 特别指令:                    ║                           ║
║            ║ scoring-model.md 的建议映射      ║                           ║
║            ║ 必须遵循 user-journey.md        ║                           ║
║            ║ (评分<60 不输出否定结论)          ║                           ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Worker    ║ 实现 + 全量测试                 ║ 验收:                     ║
║            ║                                ║  mvn test 20个用例全过     ║
║            ║                                ║  APR 精度 < 0.01%         ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════╗
║  Step 6: AI 服务（Spring AI + DeepSeek）                                ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: ocr-extract.md,          ║ 拆解:                     ║
║            ║ suggestion-gen.md              ║  6.1 OcrExtractService    ║
║            ║                                ║  6.2 SuggestionGenService ║
║            ║                                ║  6.3 Prompt 模板管理      ║
║            ║                                ║  6.4 超时重试配置          ║
║            ║                                ║                           ║
║            ║ ⚠️ 特别约束:                    ║                           ║
║            ║ AI 只用于 OCR 和文案生成         ║                           ║
║            ║ APR/评分/规则绝不经过 AI          ║                           ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: ocr-extract.md Prompt    ║ 输出:                     ║
║            ║ + suggestion-gen.md 五段式      ║  - ChatClient 调用方式    ║
║            ║                                ║  - 结构化输出解析逻辑      ║
║            ║                                ║  - 容错策略(JSON解析失败)  ║
║            ║                                ║  - 五段式 Prompt 模板     ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Worker    ║ 实现 + Mock 测试                ║ 验收:                     ║
║            ║                                ║  mvn compile              ║
║            ║                                ║  Mock DeepSeek 测试通过   ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════╗
║  Step 7: 报告服务                                                       ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: explainability.md,       ║ 拆解:                     ║
║            ║ user-journey.md (Page 4 数据)  ║  7.1 ReportAssembler      ║
║            ║                                ║  7.2 LossVisualization 计算║
║            ║                                ║  7.3 PDF 导出             ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: explainability.md 三层   ║ 输出:                     ║
║            ║ + user-journey.md 损失数据结构  ║  - 三层可解释性组装逻辑    ║
║            ║                                ║  - threeYearExtraInterest  ║
║            ║                                ║    的计算公式              ║
║            ║                                ║  - PDF 模板规范            ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════╗
║  Step 8: 前端 9 页漏斗                                                  ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: user-journey.md,         ║ 拆解:                     ║
║            ║ openapi.yaml                   ║  8.1 API 调用层           ║
║            ║                                ║  8.2 Page 1-3 (录入流)    ║
║            ║                                ║  8.3 Page 4-5 (分析流)    ║
║            ║                                ║  8.4 Page 6-7 (模拟/风险) ║
║            ║                                ║  8.5 Page 8-9 (行动/陪伴) ║
║            ║                                ║ 可并行: 8.1, 8.2~8.5     ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: user-journey.md 每页约束  ║ 输出:                     ║
║            ║                                ║  - 每页组件树              ║
║            ║                                ║  - CTA 按钮文案(严格遵循)  ║
║            ║                                ║  - 评分<60 的分支路径      ║
║            ║                                ║  - 文案禁止表达列表        ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Worker    ║ React/TypeScript 实现           ║ 验收:                     ║
║            ║                                ║  npm run build 通过        ║
║            ║                                ║  9 页全部可渲染             ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝

╔══════════════════════════════════════════════════════════════════════════╗
║  Step 9: 集成测试 + Seed 数据                                           ║
╠════════════╦════════════════════════════════╦═══════════════════════════╣
║  Manager   ║ 读取: test-matrix.md,          ║ 拆解:                     ║
║            ║ mock-data.md                   ║  9.1 集成测试              ║
║            ║                                ║  9.2 Seed 脚本            ║
║            ║                                ║  9.3 并发/安全测试         ║
╠════════════╬════════════════════════════════╬═══════════════════════════╣
║  Leader    ║ 读取: test-matrix.md 全场景     ║ 输出:                     ║
║            ║ + mock-data.md 5类画像          ║  - 测试类命名+方法列表     ║
║            ║                                ║  - 5类用户的 Seed SQL      ║
║            ║                                ║  - 并发测试的竞态条件       ║
╚════════════╩════════════════════════════════╩═══════════════════════════╝
```

---

## 四、实战操作（逐步演示 Step 5 — 最复杂的计算引擎）

### 4.1 [ccteam] 方式

```bash
# 启动
npx ccteam@latest start
tmux attach -t ccteam-XXXXX
```

**在 Manager 面板输入：**

```
请读取以下文件并拆解"计算引擎"任务：
- ai-spec/CLAUDE.md（全局约束，特别注意 F-01~F-04）
- ai-spec/engine/apr-calc.md
- ai-spec/engine/scoring-model.md
- ai-spec/engine/rules.md
- ai-spec/domain/user-journey.md（第五章评分<60的特殊路径）

要求：
1. 拆成 4 个子任务（AprCalc, Scoring, Rules, Tests）
2. 标注每个子任务依赖哪些 spec 文件
3. 标注子任务间的依赖关系
4. 每个子任务要有明确的验收命令

⚠️ 关键约束提醒给 Leader：
- scoring-model.md 第五章"重组建议映射"已按 user-journey.md 改写
- 评分<60 不输出 URGENT_ATTENTION，改为 CREDIT_BUILDING
- 所有 message 以正面表达开头
```

**Manager 自动传递给 Leader，Leader 输出实现规范：**

```
实现规范 5.1 — AprCalculator

包路径: com.youhua.engine.calculator.AprCalculator

方法签名:
  BigDecimal calculateApr(BigDecimal principal, BigDecimal totalRepayment, int loanDays)

实现要求:
  1. 使用牛顿迭代法（参考 apr-calc.md 公式）
  2. 中间步骤 scale = 10，最终结果 scale = 4
  3. 最大迭代次数 = 100
  4. 收敛阈值 = 1e-10
  
边界处理:
  - principal <= 0 → throw INVALID_ARGUMENT
  - totalRepayment <= principal → APR = 0
  - loanDays <= 0 → throw INVALID_ARGUMENT
  - loanDays > 3650 → throw INVALID_ARGUMENT

测试用例（从 apr-calc.md 摘取）:
  | principal | totalRepayment | loanDays | expectedApr |
  | 10000     | 10500          | 30       | 219.7%      |
  | 10000     | 11000          | 365      | 10.0%       |
  | ...（完整 20 个）

验收命令:
  mvn test -Dtest=AprCalculatorTest -q && echo "PASS"
```

**Leader 传递给 Worker，Worker 写代码并跑测试。**

### 4.2 [原生 Agent Teams] 方式

```bash
claude

> 创建一个 agent team 来实现计算引擎模块。
>
> 使用 delegate mode。
>
> Team 结构：
> - Teammate "spec-writer" (用 Opus):
>   读取 ai-spec/engine/apr-calc.md, scoring-model.md, rules.md。
>   输出每个类的详细实现规范：包路径、方法签名、边界条件、测试用例期望值。
>   注意 scoring-model.md 第五章已改写，评分<60 用 CREDIT_BUILDING。
>   不写代码。
>
> - Teammate "apr-impl" (用 Sonnet):
>   等 spec-writer 完成后，实现 AprCalculator + 单元测试。
>   每个测试必须通过 mvn test 验证。
>
> - Teammate "scoring-impl" (用 Sonnet):
>   等 spec-writer 完成后，实现 ScoringEngine + RuleEngine + 单元测试。
>   与 apr-impl 并行，不依赖。
>
> - Teammate "integrator" (用 Sonnet):
>   等 apr-impl 和 scoring-impl 都完成后，
>   写集成测试，验证三个引擎协作。
>   运行 mvn test 全部通过。
>
> apr-impl 和 scoring-impl 操作不同文件，可以并行。
> integrator 必须等前两个完成。
```

---

## 五、并行加速策略

### 5.1 哪些 Step 可以并行

```
Step 1 (骨架)
  │
  ├→ Step 2 (Entity)  ──→ Step 3 (Controller)
  │                          │
  │                          ├→ Step 4 (状态机)
  │                          │
  │                          └→ Step 5 (计算引擎) ──→ Step 6 (AI服务)
  │                                                      │
  │                                                      └→ Step 7 (报告)
  │
  └→ Step 8.1 (前端API层) ──→ Step 8.2~8.5 (页面) ──→ Step 9 (测试)
```

**Step 5 内部也可以并行**：

```
spec-writer (Leader)
  │
  ├→ apr-impl (Worker A)        ← 并行
  ├→ scoring-impl (Worker B)    ← 并行
  │
  └→ integrator (Worker C)      ← 等 A+B 完成
```

### 5.2 [原生 Agent Teams] 并行执行示例

```bash
claude

> 创建一个 agent team，4 个 teammates 并行工作。
> 每个 teammate 用独立的 git worktree 避免文件冲突。
>
> Teammate "entity" (worktree: feature/entity):
>   读取 ai-spec/domain/entities.md + state-machines.yaml
>   生成 7 个 JPA Entity + Flyway 迁移 + Repository
>
> Teammate "engine" (worktree: feature/engine):
>   读取 ai-spec/engine/*.md
>   生成 AprCalculator + ScoringEngine + RuleEngine + 测试
>
> Teammate "controller" (worktree: feature/controller):
>   读取 ai-spec/contracts/openapi.yaml + error-codes.md
>   生成 Controller + DTO + 异常处理
>
> Teammate "ai-service" (worktree: feature/ai-service):
>   读取 ai-spec/prompts/*.md
>   生成 OcrExtractService + SuggestionGenService
>
> 完成后 team lead 负责合并 4 个分支并解决冲突。
```

---

## 六、质量门禁：Leader 的审查清单

每个 Worker 输出后，Leader（或原生 Agent Teams 的 reviewer teammate）必须检查：

```
□ BigDecimal 使用
  - 金额字段没有 float/double
  - 除法使用 divide(x, scale, RoundingMode)

□ 命名规范
  - DB 列: snake_case (create_time)
  - Java 字段: camelCase (createTime)
  - JSON: camelCase
  - URL: kebab-case (/finance-profiles)
  - 布尔无 is 前缀
  - 时间用 xxxTime 后缀

□ 状态机
  - 状态变更通过状态机方法，无直接 setStatus
  - 非法转换抛 DEBT_STATE_INVALID

□ 用户心理路径
  - AI 文案遵循五段式结构
  - 无禁止表达（"严重""赶紧""最后机会"）
  - 评分<60 无否定结论

□ 安全
  - 所有查询带 userId 过滤
  - 无 SQL 拼接
  - 密码/Token 不出现在日志

□ 编译/测试
  - mvn compile 通过
  - mvn test 通过
  - 无 warning
```

---

## 七、成本对比

| 方案 | Step 5 (计算引擎) 预估 token | 预估成本 |
|------|------|------|
| 单 Claude Code 顺序执行 | ~150K | ~$3 |
| ccteam 三角色 | ~250K (3个窗口) | ~$5 |
| 原生 Agent Teams (4并行) | ~300K | ~$6 |
| 原生 + worktree 全并行 | ~400K | ~$8 |

并行方案 token 更多但**时间缩短 50-70%**。
整个 MVP 9 个 Step 总成本约 $50-80（Agent Teams）。

---

## 八、推荐方案

```
如果你是一个人开发：
  → 用原生 Agent Teams，4 个 teammate 并行，效率最高

如果你有 2-3 人团队：
  → 原生 Agent Teams + Claude Projects
  → 每人负责不同 Step，Projects 共享知识库保持一致性

如果你想体验 ccteam 的三角色模式（学习目的）：
  → ccteam 的 Manager/Leader/Worker 分工概念非常清晰
  → 理解后迁移到原生 Agent Teams 用自然语言描述相同角色

核心原则不变：
  CLAUDE.md 是全局约束
  ai-spec 的 16 个文件按角色精确分配
  每个角色只看自己需要的文件，不浪费上下文
```
