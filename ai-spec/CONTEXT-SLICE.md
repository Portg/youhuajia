# CONTEXT-SLICE.md — 任务上下文切片规则

> 每次任务启动时查表，选最小上下文切片，避免 Token 浪费。

---

## 切片索引

### 切片 A：接口层开发

加载：
1. `@CLAUDE.md`（命名规范 + 接口层约束）
2. `@ai-spec/domain/intent.md`（Anti-Goals + 数据敏感级别）
3. `@ai-spec/contracts/openapi.yaml`（仅本次涉及路径）
4. `@ai-spec/contracts/error-codes.md`
5. `@ai-spec/domain/state-machines.yaml`

跳过：`engine/`、`prompts/`、`user-journey.md`

---

### 切片 B：数据层开发

加载：
1. `@CLAUDE.md`（数据层约束 3.1 节）
2. `@ai-spec/domain/entities.md`
3. `@ai-spec/domain/evolution.md`
4. `@ai-spec/domain/state-machines.yaml`

跳过：`contracts/`、`engine/`、`prompts/`、`user-journey.md`

---

### 切片 C：计算引擎开发

加载：
1. `@CLAUDE.md`（金融计算禁止项 F-01/F-02/F-09）
2. `@ai-spec/engine/apr-calc.md`
3. `@ai-spec/engine/scoring-model.md`
4. `@ai-spec/engine/rules.md`
5. `@ai-spec/engine/engine-impl-spec.md`
6. `@ai-spec/domain/entities.md`（字段类型参考）

跳过：`contracts/`、`prompts/`、`user-journey.md`

---

### 切片 D：代码审查（Review Agent）

加载：
1. `@ai-spec/domain/intent.md`（Anti-Goals 逐条核对）
2. `@ai-spec/VIBE-CHECKLIST.md`（六关门禁）
3. `@CLAUDE.md`（禁止项 + 命名规范）
4. `@ai-spec/SCRATCHPAD.md`（最近 3 条记录）
5. [被审查的代码文件]
6. [对应的测试文件]

跳过：`engine/` 算法细节、`prompts/`

---

### 切片 E：前端页面开发

加载：
1. `@CLAUDE.md`（前端层约束 3.5 节 + 命名规范）
2. `@ai-spec/domain/user-journey.md`（心理路径 + 页面约束）
3. `@ai-spec/client-spec.md`（Vue/uni-app 技术规范）
4. `@ai-spec/contracts/openapi.yaml`（本页涉及接口）
5. `@ai-spec/domain/intent.md`（AG-05/06/07/12 前端相关 Anti-Goals）

跳过：`engine/`、`domain/entities.md`、`prompts/`

---

### 切片 F：AI 能力开发（OCR / 建议生成）

加载：
1. `@CLAUDE.md`（AI 文案禁止项 F-11）
2. `@ai-spec/prompts/ocr-extract.md` 或 `suggestion-gen.md`
3. `@ai-spec/prompts/explainability.md`
4. `@ai-spec/domain/intent.md`（AG-02/05 产品推荐+恐慌禁止）

跳过：`engine/`、`contracts/`、`state-machines.yaml`

---

### 切片 G：测试生成

加载：
1. `@ai-spec/domain/intent.md`（Anti-Goals 全文）
2. `@ai-spec/domain/state-machines.yaml`
3. `@ai-spec/engine/rules.md`
4. `@ai-spec/TEST-SPEC.md`
5. [待测试的代码文件]

跳过：`openapi.yaml`、`user-journey.md`

---

### 切片 H：Prompt 版本审查

加载：
1. `@ai-spec/PROMPT-VERSIONS/[Prompt名]/CHANGELOG.md`
2. `@ai-spec/PROMPT-GOLDEN-TESTS.md`（对应测试用例）
3. [上次使用该 Prompt 生成的代码样本]

跳过：业务 spec 文件
