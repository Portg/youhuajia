# 优化家 AI-Spec — AI 协作开发规范文件集

> Vibe Coding 场景下的"AI 可消费协议"
> 将产品文档转化为 AI 可直接消费的结构化输入

---

## 目录结构

```
ai-spec/
│
├── CLAUDE.md                      ← 🔴 AI 行为总纲（每次生成必读）
├── README.md                      ← 本文件
├── WORKFLOW-KNOWHOW.md            ← 小迭代 vs 大规模迭代、何时先改 spec（见下方）
│
├── contracts/                     ← 接口契约层
│   ├── openapi.yaml               ← OpenAPI 3.0 完整接口规范
│   └── error-codes.md             ← 统一错误码定义
│
├── domain/                        ← 领域模型层
│   ├── entities.md                ← 所有实体定义（字段+约束+索引）
│   ├── state-machines.yaml        ← 状态机形式化定义
│   ├── evolution.md               ← MVP → 2.0 演进约束
│   └── user-journey.md            ← 🟡 用户心理路径（前端/文案生成必读）
├── client-spec.md                    ← 🟡 Flutter 客户端规范（移动端代码生成必读）
│
├── engine/                        ← 计算引擎层
│   ├── apr-calc.md                ← APR 计算公式+边界+测试矩阵
│   ├── scoring-model.md           ← 评分模型（五维+权重+规则）
│   └── rules.md                   ← 业务规则引擎定义
│
├── prompts/                       ← AI Prompt 模板层
│   ├── ocr-extract.md             ← OCR 字段抽取 Prompt
│   ├── suggestion-gen.md          ← 建议生成 Prompt（含心理五段式）
│   └── explainability.md          ← 可解释性输出规范
│
└── test/                          ← 测试规范层
    ├── test-matrix.md             ← 测试用例矩阵总表
    └── mock-data.md               ← Mock 数据定义（5类用户画像）
```

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
让 AI 生成：Flyway 迁移脚本 + Entity 类 + Repository
```

### Step 3 → 接口层

```
输入给 AI：CLAUDE.md + contracts/openapi.yaml + contracts/error-codes.md
让 AI 生成：Controller + DTO(Request/Response) + 全局异常处理 + Swagger 注解
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
输入给 AI：CLAUDE.md + contracts/openapi.yaml + domain/user-journey.md + prompts/explainability.md
让 AI 生成：API 调用层 + 9 页渐进式漏斗组件 + 利率模拟器 + Mock Server

⚠️ user-journey.md 是前端生成的核心约束：
  - 页面顺序不可调整
  - 每页的 CTA 按钮文案严格遵循心理路径
  - 评分 < 60 用户走独立分支（信用修复路线）
  - 文案禁止表达列表必须内置前端文案校验
```

### Step 9 → 测试补全

```
输入给 AI：CLAUDE.md + test/test-matrix.md + test/mock-data.md
让 AI 生成：集成测试 + 并发测试 + 安全测试 + Seed 脚本
```

---

## 迭代方式（Vibe Coding）

- **小功能迭代**：直接改代码为主，对话里按需 @ 相关 spec；仅当改动涉及接口/表/流程/规则时，先改对应 spec 再实现。
- **大规模迭代**：先更新 ai-spec 中相关文件（openapi、entities、state-machines、user-journey、engine 等），再按 spec 实现或重构。
- 详见 **WORKFLOW-KNOWHOW.md**。

---

## 关键原则

1. **每次生成先读 CLAUDE.md** — 这是铁律
2. **分步喂文档，不要一次性全丢** — 每步只给相关文件
3. **每步都要编译+测试通过** — 生成 → compile → test → 修复 → 下一步
4. **金融计算绝不用大模型** — APR/评分/规则全部确定性算法
5. **接口契约是前后端的合同** — openapi.yaml 不可随意修改
6. **为 2.0 预留但不实现** — 预留字段/路径/枚举，不写业务代码
7. **用户心理路径是体验层铁律** — 每一步只推动"半步"，不恐吓、不催促、不否定
