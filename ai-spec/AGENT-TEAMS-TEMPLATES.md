# Agent Teams Prompt 模板 — 填空即用

> **使用方法**：
> 1. 选择适合你任务的模式模板
> 2. 把 `___` 和 `{提示}` 替换成你的项目内容
> 3. 整段复制粘贴到 Claude Code 终端执行
>
> `___` = 必填项
> `{提示文字}` = 按提示填写
> `[可选]` = 可删除的可选项

---
---

## 模板 0：前置配置（每个项目只配一次）

```bash
mkdir -p .claude
cat > .claude/settings.json << 'EOF'
{
  "env": {
    "CLAUDE_CODE_EXPERIMENTAL_AGENT_TEAMS": "1"
  },
  "permissions": {
    "allow": [
      "Bash({构建工具} *)",
      "Bash(git *)"
    ]
  }
}
EOF

#  ↑ 把 {构建工具} 替换为你的工具，例如：
#    Java 项目   → "Bash(mvn *)"
#    Node 项目   → "Bash(npm *)"
#    Flutter 项目 → "Bash(flutter *)", "Bash(dart *)"
#    Python 项目  → "Bash(pip *)", "Bash(pytest *)"
#    Go 项目     → "Bash(go *)"
```

---
---

## 模板 1：双人组（Spec + Coder）

**适用场景**：简单任务、单模块开发、初始化骨架

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 ___。
  # ↑ 填入规范文件路径，如：docs/api-spec.md 和 docs/data-model.md

  你是___架构师，只输出文字规范，绝对不写代码、不创建文件、不执行命令。
  # ↑ 填入领域，如：后端、前端、数据库、算法

  输出以下规范：
  1. ___
  2. ___
  3. ___
  # ↑ 填入 Spec 需要产出的规范项，如：
  #   "类路径 + 方法签名 + 参数类型"
  #   "数据库表结构 DDL"
  #   "组件树 + Props 定义"

  [输出格式要求：___]
  # ↑ 可选。如："每个类必须包含：包路径、字段列表、方法签名、边界条件"

  规范完成后通知 "coder"。

Teammate "coder" (用 Sonnet):
  等 spec 完成后，严格按规范写代码。
  不自行做架构决策，有疑问发消息问 spec。

  技术约束：
  - ___
  - ___
  # ↑ 填入项目特定约束，如：
  #   "金额字段用 BigDecimal，禁止 float/double"
  #   "所有组件用 TypeScript，不用 any"
  #   "SQL 用参数化查询，禁止字符串拼接"

  每个文件完成后执行 ___ 验证。
  # ↑ 填入验收命令，如：mvn compile / npm run build / flutter analyze / go build ./...

  全部完成后执行 ___ 确认零错误。
  # ↑ 填入最终验证命令，如：mvn test / npm test / flutter test
```

### 填写示例（Go 项目）

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取 docs/api.yaml 和 docs/domain.md。
  你是后端架构师，只输出文字规范，绝对不写代码、不创建文件、不执行命令。
  输出以下规范：
  1. 每个 handler 的函数签名（路径、方法、参数、返回）
  2. 每个 struct 的字段定义（类型、json tag、validate tag）
  3. 错误码枚举（HTTP status + 业务码 + message）
  输出格式要求：每个 handler 必须包含路由路径、请求/响应 struct 名、中间件列表
  规范完成后通知 "coder"。

Teammate "coder" (用 Sonnet):
  等 spec 完成后，严格按规范写代码。
  不自行做架构决策，有疑问发消息问 spec。
  技术约束：
  - 用 gin 框架，路由分组按 /api/v1 前缀
  - 数据库用 GORM，模型放 internal/model/
  每个文件完成后执行 go build ./... 验证。
  全部完成后执行 go test ./... 确认零错误。
```

---
---

## 模板 2：Spec + 多 Coder 扇出并行

**适用场景**：多模块并行开发（最常用）

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取以下文件：
  - ___
  - ___
  - ___
  # ↑ 填入所有需要的规范文件路径

  你是___架构师，只输出文字规范，绝对不写代码、不创建文件、不执行命令。

  为以下 ___ 个模块各输出详细规范：
  # ↑ 填入模块数量

  模块 A —— ___：
  # ↑ 填入模块名
    - ___
    - ___
    # ↑ 填入该模块需要的规范项

  模块 B —— ___：
    - ___
    - ___

  [模块 C —— ___：]
    [- ___]
    [- ___]
  # ↑ 按需增减模块

  规范完成后通知所有 coder 同时开始。

Teammate "coder-a" (用 Sonnet):
  等 spec 完成后，实现模块 A。
  你只操作 ___ 目录。
  # ↑ 填入该 coder 独占的目录路径，如：src/main/java/com/xxx/auth/
  技术约束：
  - ___
  完成后执行 ___ 验证。

Teammate "coder-b" (用 Sonnet):
  等 spec 完成后，实现模块 B。
  你只操作 ___ 目录。
  技术约束：
  - ___
  完成后执行 ___ 验证。

[Teammate "coder-c" (用 Sonnet):]
  [等 spec 完成后，实现模块 C。]
  [你只操作 ___ 目录。]
  [完成后执行 ___ 验证。]

coder-a 和 coder-b [和 coder-c] 并行，各自操作不同目录，不会冲突。
```

### 填写示例（React 项目，3 个页面并行）

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取以下文件：
  - docs/wireframes.md
  - docs/api-spec.yaml
  - docs/design-tokens.json
  你是前端架构师，只输出文字规范，绝对不写代码、不创建文件、不执行命令。
  为以下 3 个页面各输出详细规范：
  模块 A —— Dashboard 页：
    - 组件树（父子嵌套关系）
    - 每个组件的 Props interface 定义
    - 调用哪些 API endpoint
    - 状态管理：哪些用 local state，哪些用 zustand store
  模块 B —— Settings 页：
    - 表单字段列表 + 校验规则（zod schema）
    - API 调用的请求/响应类型
    - 乐观更新策略
  模块 C —— Analytics 页：
    - 图表类型 + 数据结构（recharts 配置）
    - 筛选器状态管理
    - 数据刷新策略（轮询 or WebSocket）
  规范完成后通知所有 coder 同时开始。

Teammate "coder-a" (用 Sonnet):
  等 spec 完成后，实现 Dashboard 页。
  你只操作 src/pages/dashboard/ 目录。
  技术约束：
  - 用 React + TypeScript，禁止 any
  - 样式用 Tailwind CSS，不写内联 style
  完成后执行 npm run build 验证。

Teammate "coder-b" (用 Sonnet):
  等 spec 完成后，实现 Settings 页。
  你只操作 src/pages/settings/ 目录。
  技术约束：
  - 表单用 react-hook-form + zod
  - 提交后乐观更新 UI
  完成后执行 npm run build 验证。

Teammate "coder-c" (用 Sonnet):
  等 spec 完成后，实现 Analytics 页。
  你只操作 src/pages/analytics/ 目录。
  技术约束：
  - 图表用 recharts
  - 大数据量用虚拟滚动
  完成后执行 npm run build 验证。

coder-a 和 coder-b 和 coder-c 并行，各自操作不同目录，不会冲突。
```

---
---

## 模板 3：Spec + 多 Coder + Integrator 管道

**适用场景**：多模块需要集成验证的复杂任务

```
创建 agent team，使用 delegate mode。

Teammate "spec" (用 Opus):
  读取以下文件：
  - ___
  - ___

  你是___架构师，只输出文字规范，绝对不写代码、不创建文件、不执行命令。

  规范 A —— ___：
  # ↑ 填入模块 A 名称
    - 类/函数路径：___
    - 方法签名：___
    - 算法/逻辑描述：___
    - 边界条件：___
    - 测试用例（输入→期望输出）：
      1. ___
      2. ___

  规范 B —— ___：
    - 类/函数路径：___
    - 方法签名：___
    - [与模块 A 的交互接口：___]

  规范完成后通知 "coder-a" 和 "coder-b" 同时开始。

Teammate "coder-a" (用 Sonnet):
  等 spec 完成后，严格按规范 A 实现。
  你只操作 ___ 目录。
  技术约束：
  - ___
  写单元测试覆盖 spec 中的所有测试用例。
  执行 ___ 验证，所有测试通过后通知 team lead。

Teammate "coder-b" (用 Sonnet):
  等 spec 完成后，严格按规范 B 实现。
  你只操作 ___ 目录。
  技术约束：
  - ___
  写单元测试。
  执行 ___ 验证，所有测试通过后通知 team lead。
  注意：你和 coder-a 并行工作，各自操作不同目录。

Teammate "integrator" (用 Sonnet):
  等 coder-a 和 coder-b 都完成后再开始。
  写集成测试验证模块 A + B 的协作：
  - 测试场景 1：___
  - 测试场景 2：___
  - 测试场景 3：___
  # ↑ 填入端到端的集成测试场景
  执行 ___ 全部通过。
```

---
---

## 模板 4：Code + Review 循环

**适用场景**：精度敏感（金融计算）、安全敏感

```
创建 agent team，使用 delegate mode。

Teammate "coder" (用 Sonnet):
  读取 ___。
  # ↑ 填入规范文件

  实现___。
  # ↑ 填入功能描述

  技术约束：
  - ___
  - ___

  完成后通知 reviewer 审查。
  如果 reviewer 发回修复清单，逐项修复后再次通知 reviewer。

Teammate "reviewer" (用 Sonnet):
  等 coder 完成后，逐项审查以下规则：

  □ ___
  □ ___
  □ ___
  □ ___
  □ ___
  # ↑ 填入审查清单（5-10 项），如：
  #   "□ 金额计算是否用 BigDecimal（没有 float/double）"
  #   "□ 所有 SQL 是否用参数化查询"
  #   "□ 所有 API 是否校验 userId 权限"
  #   "□ 密码是否用 bcrypt 哈希"
  #   "□ 错误响应是否不泄露堆栈信息"

  发现问题时：
  - 列出问题清单（文件:行号 + 问题描述 + 修复建议）
  - 通知 coder 修复
  - 不要自己修改代码

  coder 修复后重新审查，直到全部通过。
  全部通过后通知 team lead。
```

---
---

## 模板 5：技术选型对抗辩论

**适用场景**：方案选择、框架对比、架构决策

```
创建 agent team，使用 delegate mode。

背景：___
# ↑ 填入决策背景，如："我们需要选择消息队列，在 Kafka 和 RabbitMQ 之间犹豫"

Teammate "advocate-a" (用 Opus):
  你是 ___ 的倡导者。
  # ↑ 填入方案 A 名称
  从以下维度论证方案 A 的优势：
  - ___
  - ___
  - ___
  # ↑ 填入评估维度，如：性能、成本、运维复杂度、团队学习曲线、生态
  同时诚实列出方案 A 的劣势和风险。
  给出具体的数据或案例支撑。

Teammate "advocate-b" (用 Opus):
  你是 ___ 的倡导者。
  # ↑ 填入方案 B 名称
  从同样的维度论证方案 B 的优势：
  - ___
  - ___
  - ___
  同时诚实列出方案 B 的劣势和风险。
  给出具体的数据或案例支撑。

Teammate "judge" (用 Opus):
  等两个 advocate 完成后，综合评估。
  考虑我们的实际情况：
  - 团队规模：___
  - 技术栈：___
  - 预算：___
  - 时间要求：___
  # ↑ 填入团队实际约束
  输出：最终推荐 + 理由 + 迁移/实施计划。
```

---
---

## 模板 6：三路并行审查

**适用场景**：PR 审查、上线前检查、代码质量巡检

```
创建 agent team，使用 delegate mode。

审查范围：___
# ↑ 填入要审查的代码目录，如：src/

Teammate "security" (用 Sonnet):
  审查 ___ 的安全问题：
  # ↑ 填入代码目录
  □ ___
  □ ___
  □ ___
  # ↑ 填入安全审查项，如：
  #   "□ SQL 注入（字符串拼接 SQL）"
  #   "□ XSS（未转义的用户输入渲染到 HTML）"
  #   "□ 硬编码密钥/密码"
  #   "□ 权限校验缺失（越权访问）"
  #   "□ 敏感数据日志泄露"
  输出格式：文件:行号 | 风险等级(高/中/低) | 问题描述 | 修复建议

Teammate "performance" (用 Sonnet):
  审查 ___ 的性能问题：
  □ ___
  □ ___
  □ ___
  # ↑ 填入性能审查项，如：
  #   "□ N+1 查询"
  #   "□ 缺少数据库索引"
  #   "□ 大对象未分页"
  #   "□ 循环内重复创建对象"
  #   "□ 未使用连接池"
  输出格式同上。

Teammate "quality" (用 Sonnet):
  审查 ___ 的代码质量：
  □ ___
  □ ___
  □ ___
  # ↑ 填入质量审查项，如：
  #   "□ 测试覆盖率是否达标"
  #   "□ 命名规范一致性"
  #   "□ 错误处理是否完整（不吞异常）"
  #   "□ 注释是否充分"
  #   "□ 代码重复（DRY 原则）"
  输出格式同上。

三个 reviewer 并行审查，team lead 最后汇总为统一报告。
```

---
---

## 模板 7：渐进式多页面构建

**适用场景**：前端多页面应用

```
创建 agent team，使用 delegate mode。

Teammate "arch" (用 Opus):
  读取以下文件：
  - ___
  - ___
  # ↑ 填入设计稿/原型/需求文档路径

  你是前端架构师，只输出文字规范，绝对不写代码、不创建文件。
  输出以下规范：
  1. 依赖清单（___）
     # ↑ 填入包管理文件名：pubspec.yaml / package.json / requirements.txt
  2. 路由配置（所有页面路径 + 参数 + 守卫条件）
  3. 全局状态定义（哪些数据需要跨页面共享）
  4. 共享组件清单（哪些组件被多页面复用）
  5. 每个页面的组件树（组件名 + 嵌套关系 + 数据流）
  6. API 调用层的方法签名
  7. [色彩/主题规范]

  按难度将所有页面分为 3 组：
  - 简单组（纯展示 / 简单交互）：___
  - 核心组（复杂交互 / 图表 / 动画）：___
  - 业务组（分支逻辑 / 状态管理重）：___
  # ↑ 预填入你心中的分组，如：
  #   简单组：Landing, About, FAQ
  #   核心组：Dashboard, Editor, Chart
  #   业务组：Checkout, Settings, Profile

  规范完成后通知三个 coder 同时开始。

Teammate "group-simple" (用 Sonnet):
  等 arch 完成后，实现简单组页面：
  - ___（___目录）
  - ___（___目录）
  - ___（___目录）
  # ↑ 填入页面名和目录，如：
  #   Landing（src/pages/landing/）
  #   FAQ（src/pages/faq/）

  你只操作以上目录。
  技术约束：
  - ___
  每页完成后执行 ___ 验证。

Teammate "group-core" (用 Sonnet):
  等 arch 完成后，实现核心组页面：
  - ___（___目录）
  - ___（___目录）
  - ___（___目录）

  你只操作以上目录。
  技术约束：
  - ___
  - ___
  每页完成后执行 ___ 验证。

Teammate "group-business" (用 Sonnet):
  等 arch 完成后，实现业务组页面：
  - ___（___目录）
  - ___（___目录）
  - ___（___目录）

  你只操作以上目录。
  技术约束：
  - ___
  - ___
  每页完成后执行 ___ 验证。

三组并行，按目录隔离，互不冲突。
```

---
---

## 快速选择指南

```
我的任务是什么？
  │
  ├─ 单模块开发（一件事）
  │    └→ 模板 1：双人组
  │
  ├─ 多模块并行（几件独立的事）
  │    │
  │    ├─ 不需要集成验证 → 模板 2：扇出并行
  │    └─ 需要集成验证   → 模板 3：管道（加 integrator）
  │
  ├─ 精度/安全敏感
  │    └→ 模板 4：Code + Review 循环
  │
  ├─ 技术选型/方案比较
  │    └→ 模板 5：对抗辩论
  │
  ├─ 代码审查/质量检查
  │    └→ 模板 6：三路并行审查
  │
  └─ 前端多页面
       └→ 模板 7：渐进式构建
```

---
---

## 填空规则速查

| 填空符 | 含义 | 示例 |
|--------|------|------|
| `___` | 必填，替换为具体内容 | `读取 ___` → `读取 docs/api.yaml` |
| `{提示}` | 按提示填写 | `{构建工具}` → `mvn` |
| `[可选]` | 可整行删除 | `[输出格式要求：___]` → 删掉或填写 |
| `# ↑` | 填写说明（删掉） | 最终 prompt 中不保留 |

### 提交前检查

```
□ 所有 ___ 都已替换为具体内容
□ 所有 # ↑ 注释行已删除
□ 所有 [可选] 项已决定保留或删除
□ 每个 coder 都有独立的目录
□ 验收命令填写正确
□ 第一行有 "delegate mode"
```
