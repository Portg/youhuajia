# 优化家 AI-Spec — AI 协作开发规范文件集

> Vibe Coding 场景下的"AI 可消费协议"
> 将产品文档转化为 AI 可直接消费的结构化输入

---

## 四层架构全景

| 层级 | 解决的核心问题 | 核心文件 |
|------|---------------|----------|
| **工程层** | AI 生成代码有结构可循 | CLAUDE.md / openapi.yaml / entities.md |
| **意图层** | AI 理解「为什么」后再生成代码 | intent.md / VIBE-CHECKLIST / rules.md |
| **对话层** | SPEC 由对话生成，上下文最小化，跨会话不失忆 | SPEC-DISCOVERY / CONTEXT-SLICE / SCRATCHPAD |
| **质量层** | 代码可测试可重构，Prompt 可版本化可审计 | TEST-SPEC / PROMPT-VERSIONS / PROMPT-GOLDEN-TESTS |

---

## 目录结构

```
ai-spec/
│
├── CLAUDE.md                      ← 🔴 AI 行为总纲（每次生成必读，链接到项目根目录）
├── README.md                      ← 本文件
├── WORKFLOW-KNOWHOW.md            ← 小迭代 vs 大规模迭代、何时先改 spec
│
├── ─── 意图层 ──────────────────────
├── SPEC-DISCOVERY.md              ← 🟢 SPEC 四阶段对话生成流程
├── VIBE-CHECKLIST.md              ← 🟢 AI 输出六关验收门禁
├── CONTEXT-SLICE.md               ← 🟢 任务上下文切片规则（8种切片）
│
├── ─── 对话层 ──────────────────────
├── SCRATCHPAD.md                  ← 🟢 跨会话连续性记录（含推理链）
│
├── ─── 质量层 ──────────────────────
├── TEST-SPEC.md                   ← 🟢 测试规范 + @AiGenerated 注解规范
├── AGENT-PROTOCOL.md              ← 🟢 Agent 交接契约（含 ADR 守护）
├── PROMPT-LIBRARY.md              ← 🟢 Prompt 索引（含版本号+通过率）
├── PROMPT-GOLDEN-TESTS.md         ← 🟢 Prompt 效果基准测试集
│
├── PROMPT-VERSIONS/               ← 🟢 Prompt 版本管理目录
│   ├── generate-api/
│   │   ├── v1.0.md
│   │   └── CHANGELOG.md
│   ├── generate-entity/
│   │   ├── v1.0.md
│   │   └── CHANGELOG.md
│   ├── generate-test/
│   │   ├── v1.0.md
│   │   └── CHANGELOG.md
│   └── code-review/
│       ├── v1.0.md
│       └── CHANGELOG.md
│
├── ─── 工程层（原有） ─────────────
├── contracts/                     ← 接口契约层
│   ├── openapi.yaml               ← OpenAPI 3.0 完整接口规范
│   └── error-codes.md             ← 统一错误码定义
│
├── domain/                        ← 领域模型层
│   ├── intent.md                  ← 🟢 🔴 业务意图 + Anti-Goals（最高优先级）
│   ├── entities.md                ← 所有实体定义（字段+约束+索引）
│   ├── state-machines.yaml        ← 状态机形式化定义
│   ├── evolution.md               ← MVP → 2.0 演进约束
│   └── user-journey.md            ← 🟡 用户心理路径（前端/文案生成必读）
│
├── client-spec.md                 ← 🟡 Vue/uni-app 客户端规范
│
├── engine/                        ← 计算引擎层
│   ├── apr-calc.md                ← APR 计算公式+边界+测试矩阵
│   ├── scoring-model.md           ← 评分模型（五维+权重+规则）
│   ├── engine-impl-spec.md        ← 四大引擎详细实现规范
│   └── rules.md                   ← 业务规则引擎定义
│
├── prompts/                       ← AI Prompt 模板层
│   ├── ocr-extract.md             ← OCR 字段抽取 Prompt
│   ├── suggestion-gen.md          ← 建议生成 Prompt（含心理五段式）
│   └── explainability.md          ← 可解释性输出规范
│
├── test/                          ← 测试规范层
│   ├── test-matrix.md             ← 测试用例矩阵总表
│   └── mock-data.md               ← Mock 数据定义（5类用户画像）
│
├── ─── 团队协作指南（参考） ───────
├── AGENT-TEAMS-PLAYBOOK.md        ← Agent Teams 九步实操手册
├── AGENT-TEAMS-TEMPLATES.md       ← Agent Teams Prompt 模板
├── PROMPT-PATTERNS.md             ← Prompt 编写方法论
├── CCTEAM-GUIDE.md                ← ccteam 操作指南（已废弃，参考用）
└── MVP-IMPLEMENTATION-GUIDE.md    ← 三层 Claude 工具策略
```

🟢 = 本次新增文件（源自《AI 原生项目开发规范》）

---

## 使用方式

### Step 1 → 项目骨架

```
输入给 AI：CLAUDE.md + domain/evolution.md
让 AI 生成：Maven 多模块结构 + 包结构 + 基础配置
```

### Step 2 → 数据库

```
输入给 AI：CLAUDE.md + domain/entities.md + domain/evolution.md
使用 Prompt 版本：PROMPT-VERSIONS/generate-entity/v1.0.md
让 AI 生成：Flyway 迁移脚本 + Entity 类 + Mapper
```

### Step 3 → 接口层

```
输入给 AI：CLAUDE.md + contracts/openapi.yaml + contracts/error-codes.md
使用 Prompt 版本：PROMPT-VERSIONS/generate-api/v1.0.md
让 AI 生成：Controller + DTO(Request/Response) + 全局异常处理 + Swagger 注解 + 测试骨架
```

### Step 4 → 状态机

```
输入给 AI：CLAUDE.md + domain/state-machines.yaml
让 AI 生成：状态枚举 + 状态机配置 + 状态流转 Service
```

### Step 5 → 计算引擎

```
输入给 AI：CLAUDE.md + engine/apr-calc.md + engine/scoring-model.md + engine/rules.md
让 AI 生成：APR 计算类 + 评分引擎 + 规则引擎 + 全部单元测试
```

### Step 6 → AI 能力

```
输入给 AI：CLAUDE.md + prompts/ocr-extract.md + prompts/suggestion-gen.md
让 AI 生成：OCR Service + 建议生成 Service + Prompt 管理 + 后处理逻辑
```

### Step 7 → 报告与可解释性

```
输入给 AI：CLAUDE.md + prompts/explainability.md
让 AI 生成：报告生成 Service + PDF 导出 + 前端展示数据结构
```

### Step 8 → 前端

```
输入给 AI：CLAUDE.md + contracts/openapi.yaml + domain/user-journey.md + client-spec.md
让 AI 生成：API 调用层 + 9 页渐进式漏斗组件 + 利率模拟器

⚠️ user-journey.md 是前端生成的核心约束：
  - 页面顺序不可调整
  - 每页的 CTA 按钮文案严格遵循心理路径
  - 评分 < 60 用户走独立分支（信用修复路线）
  - 文案禁止表达列表必须内置前端文案校验
```

### Step 9 → 测试补全

```
输入给 AI：CLAUDE.md + TEST-SPEC.md + test/test-matrix.md + test/mock-data.md
使用 Prompt 版本：PROMPT-VERSIONS/generate-test/v1.0.md
让 AI 生成：集成测试 + 并发测试 + 安全测试 + Anti-Goals 测试 + Seed 脚本
```

---

## 迭代方式（Vibe Coding）

- **小功能迭代**：直接改代码为主，对话里按需 @ 相关 spec；仅当改动涉及接口/表/流程/规则时，先改对应 spec 再实现。
- **大规模迭代**：先更新 ai-spec 中相关文件（openapi、entities、state-machines、user-journey、engine 等），再按 spec 实现或重构。
- 详见 **WORKFLOW-KNOWHOW.md**。

---

## 关键原则

1. **每次生成先读 CLAUDE.md** — 这是铁律
2. **intent.md 是业务守门员** — Anti-Goals 必须有对应测试
3. **分步喂文档，不要一次性全丢** — 每步只给相关文件（参考 CONTEXT-SLICE.md）
4. **每步都要编译+测试通过** — 生成 → compile → test → 修复 → 下一步
5. **金融计算绝不用大模型** — APR/评分/规则全部确定性算法
6. **接口契约是前后端的合同** — openapi.yaml 不可随意修改
7. **为 2.0 预留但不实现** — 预留字段/路径/枚举，不写业务代码
8. **用户心理路径是体验层铁律** — 每一步只推动"半步"，不恐吓、不催促、不否定
9. **测试与代码同步生成** — Coding Agent 生成任务必须包含测试文件
10. **Prompt 修改必须跑 Golden Test** — 没有回归测试的 Prompt 修改等于盲目飞行

---

## 文件速查表

| 文件 | 核心作用 | 使用时机 |
|------|----------|----------|
| `domain/intent.md` | 业务意图 + Anti-Goals（最高优先级） | 每次任务第一优先读取 |
| `VIBE-CHECKLIST.md` | AI 输出六关验收门禁 | 每次 AI 生成代码后 |
| `AGENT-PROTOCOL.md` | Agent 交接契约（含 ADR 守护） | 每次阶段交接 |
| `SPEC-DISCOVERY.md` | SPEC 四阶段对话生成流程 | 创建/修改 intent.md 前 |
| `CONTEXT-SLICE.md` | 任务上下文切片规则（8种切片） | 每次任务启动时查表 |
| `SCRATCHPAD.md` | 跨会话记录（含推理链） | 每次会话结束时填写 |
| `TEST-SPEC.md` | 测试规范 + @AiGenerated 注解规范 | Coding Agent 生成任务时 |
| `PROMPT-VERSIONS/` | Prompt 版本历史 + CHANGELOG | 修改/使用 Prompt 时 |
| `PROMPT-GOLDEN-TESTS.md` | Prompt 效果基准测试集 | 每次修改 Prompt 后 |
| `PROMPT-LIBRARY.md` | Prompt 索引（含版本号+通过率） | 按任务类型选用 |
