# Agent Teams 提示语模式手册

> 本文件是写 Agent Teams prompt 的方法论。
> 不是具体项目的 prompt，而是**造 prompt 的模具**。
> 掌握这些模式后，任何项目都能快速拆解为 Agent Teams 任务。

---

## 一、核心公式

每个 Agent Teams 的 prompt 由 **5 层结构** 组成：

```
┌─────────────────────────────────────────────────┐
│  第 1 层：Team 元指令（怎么协作）                  │
│  第 2 层：角色定义（每个 teammate 是谁）            │
│  第 3 层：输入约束（读什么文件）                    │
│  第 4 层：输出约束（产出什么、格式是什么）            │
│  第 5 层：协调约束（谁等谁、谁和谁并行）             │
└─────────────────────────────────────────────────┘
```

### 万能模板

```
创建 agent team，使用 delegate mode。             ← 第 1 层

Teammate "{角色名}" (用 {模型}):                   ← 第 2 层
  读取 {文件路径}。                                ← 第 3 层
  你是{角色定位}，{行为边界}。
  输出以下内容：                                   ← 第 4 层
    - {产出物 1}
    - {产出物 2}
  {格式约束}
  {完成后动作}。                                   ← 第 5 层

Teammate "{角色名}" (用 {模型}):
  等 {前置角色} 完成后，{任务描述}。                 ← 第 5 层
  {技术约束}。
  {验收命令}。

{角色A} 和 {角色B} 并行，各自操作不同目录。           ← 第 5 层
{角色C} 必须等 {角色A} 和 {角色B} 都完成。
```

---

## 二、5 层详解 + 反模式

### 第 1 层：Team 元指令

**作用**：告诉 Claude Code 如何管理这个 team。

| 指令 | 含义 | 什么时候用 |
|------|------|-----------|
| `使用 delegate mode` | team lead 只协调不写代码 | **几乎总是要用** |
| `使用 worktree 隔离` | 每个 teammate 独立 git 分支 | 多人改同一类文件时 |
| `使用 plan approval` | teammate 动手前先提交计划给 lead 审批 | 任务模糊或风险高时 |

```
✅ 好："创建 agent team，使用 delegate mode。"
❌ 坏："创建一个 team 帮我做这个任务。"      ← team lead 会自己动手
```

**反模式**：不写 `delegate mode`
→ team lead 既当指挥又写代码，跟 ccteam 的 Leader 直接写代码是同一个问题。

---

### 第 2 层：角色定义

**作用**：每个 teammate 启动时的"人格植入"。这是最关键的一层。

#### 模式 A：规范制定者（Spec Writer）

```
Teammate "spec" (用 Opus):
  你是{领域}架构师，只输出文字规范，绝对不写代码、不创建文件、不执行命令。
```

关键词：**"绝对不写代码"** — 必须明确写出禁止项，否则 Opus 会"好心"帮你写。

#### 模式 B：代码实现者（Coder）

```
Teammate "coder" (用 Sonnet):
  你是开发者，严格按照 spec 的规范写代码。
  不自行决定架构或接口设计，有疑问发消息给 spec 确认。
```

关键词：**"严格按照"** + **"不自行决定"** — 防止 Sonnet 自由发挥偏离规范。

#### 模式 C：审查者（Reviewer）

```
Teammate "reviewer" (用 Sonnet):
  你是代码审查员。等 coder 完成后检查以下规则：
  {规则清单}
  发现问题通知 coder 修复，不要自己修改代码。
```

关键词：**"不要自己修改代码"** — reviewer 的价值在于发现问题，不是修 bug。

#### 模型选择原则

```
Opus  → 需要深度推理的角色：规范制定、架构设计、复杂拆解
Sonnet → 需要大量执行的角色：写代码、写测试、跑命令
Haiku → 简单重复任务：格式转换、文件搬运（一般不用）
```

**反模式**：所有角色都用 Opus
→ 成本翻 3-5 倍，但 Coder 角色的质量提升有限（写代码 Sonnet 够用）。

---

### 第 3 层：输入约束

**作用**：精确控制每个 teammate 的上下文窗口内容。

#### 模式：精确文件路径

```
✅ 好："读取 ai-spec/engine/apr-calc.md 和 ai-spec/engine/scoring-model.md。"
❌ 坏："读取项目中相关的文件。"              ← teammate 会乱读，浪费上下文
❌ 坏："读取所有 ai-spec 文件。"             ← 200K 上下文被无关内容占满
```

#### 模式：分层读取（Spec 读多，Coder 读少）

```
Spec:   读取 3-5 个规范文件（需要全局视野）
Coder:  只读取 Spec 输出的规范（不需要原始文件）
Review: 只读取 Coder 输出的代码 + 约束检查清单
```

这就是三角色分离的核心价值——每个角色只看自己需要的东西。
200K 上下文不浪费在无关内容上，输出质量更高。

**反模式**：让 Coder 同时读取需求文档 + 架构文档 + API 规范 + 测试用例
→ 上下文被稀释，代码质量下降。

---

### 第 4 层：输出约束

**作用**：定义每个 teammate 的产出物格式。

#### 模式 A：Spec 的输出模板

```
输出以下规范：
  - 类路径：com.youhua.xxx.XxxClass
  - 方法签名：返回类型 方法名(参数类型 参数名)
  - 字段映射：DB 列名 → Java 字段名 → JSON 字段名
  - 边界条件：{条件} → {处理方式}
  - 测试用例：input(...) → expected = ...
  - 验收命令：mvn test -Dtest=XxxTest
```

为什么要这么具体？因为 Coder 拿到这个规范后可以**无歧义地**写代码。
如果 Spec 只写"实现 APR 计算"，Coder 有 100 种理解方式。

#### 模式 B：Coder 的完成报告

```
完成后发送消息给 team lead，包含：
  已创建文件：[文件列表]
  编译结果：mvn compile → PASS/FAIL
  测试结果：mvn test → X/Y PASS
  待审查点：[如有]
```

#### 模式 C：强制输出格式

```
✅ 好："输出格式：文件路径 + Dart 类签名 + 依赖关系"
❌ 坏："输出你觉得合适的规范"     ← 每次格式不一样，Coder 无法对接
```

**反模式**：不定义输出格式
→ Spec 第一次输出表格，第二次输出散文，Coder 每次都要重新理解。

---

### 第 5 层：协调约束

**作用**：定义 teammate 之间的依赖关系和并行策略。

#### 模式 A：串行链

```
spec → coder → reviewer
"等 spec 完成后..." → "等 coder 完成后..."
```

适用场景：后一个角色的输入完全依赖前一个角色的输出。

#### 模式 B：扇出并行

```
spec → coder-A (并行)
     → coder-B (并行)
     → coder-C (并行)

"spec 完成后通知三个 coder 同时开始。"
"coder-A, coder-B, coder-C 操作不同目录，并行不冲突。"
```

适用场景：多个 coder 操作不同文件/目录。这是**最常用也收益最大的并行模式**。

#### 模式 C：扇入汇合

```
coder-A ──┐
coder-B ──┼→ integrator
coder-C ──┘

"等 coder-A 和 coder-B 和 coder-C 都完成后再开始。"
```

适用场景：集成测试、合并验证、最终报告。

#### 模式 D：管道（最常用的组合）

```
spec → (coder-A + coder-B) 并行 → integrator

第一句："spec 完成后通知 coder-A 和 coder-B 同时开始。"
最后一句："integrator 等 coder-A 和 coder-B 都完成后再开始。"
```

#### 并行的前提：文件隔离

```
✅ 好："coder-A 只操作 src/main/java/com/youhua/engine/"
      "coder-B 只操作 src/main/java/com/youhua/ai/"

❌ 坏："coder-A 和 coder-B 都可以修改任何文件"
      → 必定出现文件冲突，两个 teammate 覆盖对方的代码
```

**反模式**：不写目录隔离
→ 两个 Sonnet 同时编辑 `pom.xml`，后写的覆盖先写的。

---

## 三、7 种实战 Prompt 模式

### 模式 1：Spec-Code 双人组（最基础）

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 {规范文件}。
  你是架构师，只输出文字规范，绝对不写代码。
  输出：{规范清单}
  规范完成后通知 "coder"。

Teammate "coder" (用 Sonnet):
  等 spec 完成后，按规范写代码。
  {技术约束}
  验收：{命令}
```

**适用场景**：简单任务（Step 1 骨架、Step 4 状态机）

---

### 模式 2：Spec + 多 Coder 扇出（最常用）

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 {多个规范文件}。
  输出 N 组规范。
  完成后通知所有 coder。

Teammate "coder-A" (用 Sonnet):
  等 spec 完成后，实现规范第 1 组。
  你只操作 {目录A}。

Teammate "coder-B" (用 Sonnet):
  等 spec 完成后，实现规范第 2 组。
  你只操作 {目录B}。

coder-A 和 coder-B 并行，操作不同目录。
```

**适用场景**：模块化开发（Step 2 Entity+迁移、Step 6 OCR+建议、Step 8 前端页面）

---

### 模式 3：Spec + 多 Coder + Integrator 管道（复杂任务）

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  输出详细规范。

Teammate "coder-A" (用 Sonnet):
  等 spec 完成后实现模块 A。

Teammate "coder-B" (用 Sonnet):
  等 spec 完成后实现模块 B。

Teammate "integrator" (用 Sonnet):
  等 coder-A 和 coder-B 都完成后，
  写集成测试验证 A + B 协作。

coder-A 和 coder-B 并行。
integrator 必须等两者都完成。
```

**适用场景**：有集成需求的复杂模块（Step 5 计算引擎、Step 9 集成测试）

---

### 模式 4：Code + Review 循环（质量敏感）

```
创建 agent team，使用 delegate mode。

Teammate "coder" (用 Sonnet):
  实现 {功能}。
  完成后通知 reviewer 审查。
  如果 reviewer 发回问题，修复后再次通知。

Teammate "reviewer" (用 Sonnet):
  等 coder 完成后，按以下清单审查：
  {审查规则}
  发现问题通知 coder 修复，不自己改代码。
  coder 修复后重新审查，直到全部通过。
```

**适用场景**：金融计算（APR 精度）、安全敏感代码

---

### 模式 5：Research + Debate 对抗（技术选型）

```
创建 agent team，使用 delegate mode。

Teammate "advocate-A" (用 Opus):
  论证方案 A 的优势和劣势。

Teammate "advocate-B" (用 Opus):
  论证方案 B 的优势和劣势。

Teammate "judge" (用 Opus):
  等两个 advocate 完成后，综合评估。
  输出最终推荐和理由。
```

**适用场景**：技术选型争论、架构方案比较

---

### 模式 6：三路并行巡检（代码审查/测试）

```
创建 agent team，使用 delegate mode。

Teammate "security" (用 Sonnet):
  审查 {代码目录} 的安全漏洞。

Teammate "performance" (用 Sonnet):
  审查 {代码目录} 的性能问题。

Teammate "coverage" (用 Sonnet):
  检查 {代码目录} 的测试覆盖率。

三个 reviewer 并行，最后 team lead 汇总报告。
```

**适用场景**：PR 审查、上线前检查

---

### 模式 7：渐进式构建（前端页面）

```
创建 agent team，使用 delegate mode。

Teammate "arch" (用 Opus):
  输出所有页面的组件树 + 路由配置。

Teammate "group-1" (用 Sonnet):
  实现简单页面（纯展示 + 简单交互）。
  只操作 {目录列表}。

Teammate "group-2" (用 Sonnet):
  实现核心交互页面（图表 + 动画 + API 调用）。
  只操作 {目录列表}。

Teammate "group-3" (用 Sonnet):
  实现业务逻辑页面（分支路由 + 状态管理）。
  只操作 {目录列表}。

三组并行，按目录隔离。
```

**适用场景**：多页面应用开发

---

## 四、约束语强度等级

同样的约束，措辞不同效果差异巨大：

| 强度 | 措辞 | 效果 |
|------|------|------|
| ❌ 弱 | "尽量不要写代码" | 大概率还是会写 |
| ⚠️ 中 | "不写代码" | 有时管用 |
| ✅ 强 | "绝对不写代码、不创建文件、不执行命令" | 基本可靠 |
| ✅✅ 最强 | "你只输出文字规范。如果你创建了任何 .java/.dart 文件，这次任务就失败了。" | 最可靠 |

**常用强约束短语**：

```
行为边界：
  "绝对不写代码、不创建文件、不执行命令"
  "严格按照规范，不自行做架构决策"
  "有疑问发消息确认，不要猜测"
  "不要自己修改代码，只报告问题"

格式约束：
  "规范格式必须包含：类路径、方法签名、字段映射、边界条件、测试用例"
  "每个文件创建后执行 {验收命令}"
  "全部完成后通知 team lead"

文件隔离：
  "你只操作 {目录}，不要修改其他目录的文件"
  "你和 {另一个 teammate} 并行工作，各自操作不同的目录"
```

---

## 五、常见问题诊断

| 现象 | 原因 | 修复 |
|------|------|------|
| Spec 直接写代码 | 缺少"绝对不写代码"约束 | 加强行为边界措辞 |
| Team lead 自己动手 | 没开 delegate mode | 加 `使用 delegate mode` |
| Coder 偏离规范自由发挥 | 缺少"严格按照"+"不自行决定" | 加上明确的服从约束 |
| 两个 Coder 文件冲突 | 缺少目录隔离 | 每个 Coder 指定独立目录 |
| Coder 写完不验证 | 缺少验收命令 | 加 `mvn compile` / `flutter analyze` |
| Reviewer 自己改代码 | 缺少"不自己修改"约束 | 明确写"只报告，不修改" |
| Integrator 提前开始 | 缺少等待约束 | 明确写"等 X 和 Y 都完成后" |
| 输出格式每次不同 | 缺少格式模板 | 给出具体的输出格式示例 |

---

## 六、Prompt 质量自检清单

写完一个 Agent Teams prompt 后，逐项检查：

```
□ 第 1 层：写了 delegate mode 吗？
□ 第 2 层：每个角色有明确的行为边界吗？
           Spec 写了"绝对不写代码"吗？
           Coder 写了"严格按照规范"吗？
□ 第 3 层：每个角色指定了具体的文件路径吗？
           Coder 是否不需要读原始规范文件？（应该只读 Spec 的输出）
□ 第 4 层：Spec 的输出有具体格式模板吗？
           Coder 有验收命令吗？
□ 第 5 层：并行的 Coder 有目录隔离吗？
           Integrator 有等待条件吗？
           完成后有通知动作吗？
□ 模型选择：Spec 用 Opus，Coder 用 Sonnet 吗？
□ 整体：一个 team 只做一个 Step 吗？
        （不把多个 Step 塞进一个 team）
```

---

## 七、从零写 Prompt 的 4 步法

遇到任何新任务，按这 4 步拆解：

### Step A：画依赖图

```
问自己：这个任务有哪些子任务？它们之间有依赖吗？

例：
  "实现用户认证模块"
  → 子任务：JWT 配置、Login API、Token 刷新、权限注解
  → 依赖：JWT 配置 → Login API → Token 刷新（串行）
  → 权限注解和 Login API 可以并行
```

### Step B：分配角色

```
问自己：哪些子任务需要"想清楚"？哪些需要"写代码"？

  需要想清楚的 → Spec (Opus)
  需要写代码的 → Coder (Sonnet)
  需要验证的   → Reviewer / Integrator (Sonnet)
```

### Step C：定义文件边界

```
问自己：每个 Coder 操作哪些文件/目录？有重叠吗？

  无重叠 → 可以并行
  有重叠 → 必须串行，或用 worktree 隔离
```

### Step D：填入模板

```
选择最匹配的模式（模式 1-7），填入具体内容：
  - 角色名
  - 文件路径
  - 输出格式
  - 验收命令
  - 并行/串行关系
```

---

## 八、不同项目类型的推荐模式

| 项目类型 | 推荐模式 | 原因 |
|---------|---------|------|
| Spring Boot 后端 | 模式 3（Spec + 多 Coder + Integrator）| 模块化清晰，按包隔离 |
| Flutter/React 前端 | 模式 7（渐进式构建）| 按页面/组件隔离 |
| API 开发 | 模式 2（Spec + 多 Coder 扇出）| Controller/DTO/Error 并行 |
| 数据库迁移 | 模式 1（双人组）| Entity 和 SQL 可部分并行 |
| 技术选型 | 模式 5（Research + Debate）| 对抗式思考更全面 |
| 代码审查 | 模式 6（三路并行巡检）| 安全/性能/覆盖率并行 |
| 复杂算法 | 模式 4（Code + Review 循环）| 精度要求高需要反复验证 |
| 全栈功能 | 模式 3 + 7 组合 | 后端模式 3，前端模式 7 |
