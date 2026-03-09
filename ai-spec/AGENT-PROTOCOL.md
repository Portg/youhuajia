# AGENT-PROTOCOL.md — Agent 交接契约

> 每个阶段的交接包必须完整，缺失项阻断合并。

---

## 交接要素总览

| 交接要素 | 要求 |
|----------|------|
| 输出物 | spec + 代码 + Scratchpad 条目 + 测试文件 + Prompt 版本号 |
| 自检报告 | VIBE-CHECKLIST 逐项 + 放弃方案记录 + @AiGenerated 标注确认 |
| 下游验收 | 读 spec 后开工 + 上下文恢复确认 + ADR 合规性确认 |
| 阻断条件 | 3项 spec 质量 + Scratchpad 缺失 + 测试缺失 + 推理链缺失 |
| Prompt 溯源 | 必须记录使用的 Prompt 版本 |

---

## 阶段一：需求 → Architect Agent

### 交接包内容
- `intent.md`（经 SPEC-DISCOVERY 生成，人工审定）
- SCRATCHPAD 初始条目（含 SPEC 探索推理链）

### Architect Agent 输出
- `openapi.yaml` 新增/修改的路径
- `state-machines.yaml` 新增/修改的状态机
- ADR（架构决策记录）
- SCRATCHPAD 条目（含推理链 + Prompt 版本号）

---

## 阶段二：Architect Agent → Coding Agent

### 交接包内容
- 上一阶段全部输出
- CONTEXT-SLICE 切片编号（A~H）

### Coding Agent 必须
1. 查 `CONTEXT-SLICE.md`，选最小上下文切片
2. 确认使用 `PROMPT-VERSIONS/` 中的当前推荐版本
3. 生成代码 + 测试文件（TEST-SPEC 规范） ← **强制**
4. 复杂逻辑标注 `@AiGenerated` ← **强制**
5. 过 `VIBE-CHECKLIST.md` 六关自检
6. 填写 SCRATCHPAD（含推理链 + Prompt 效果反馈）

---

## 阶段三：Coding Agent → Review Agent（最关键）

### 交接包内容
- 代码文件
- 测试文件（TEST-SPEC.md 规定的测试）
- 《生成决策说明》含推理链
- VIBE-CHECKLIST 检查记录（含第六关）
- SCRATCHPAD 条目（含推理链）
- 使用的 Prompt 版本号

### Review Agent 检查项

#### ADR 合规性检查
- [ ] 代码引入的依赖是否违反了 CLAUDE.md 中的技术选型约束？
- [ ] 代码结构是否符合分层架构决策（Controller→Service→Mapper）？
- [ ] 有没有 AI 绕过了某个重要决策的情况？

#### 测试质量检查
- [ ] Anti-Goals 中每条规则是否有对应测试方法？
- [ ] 测试方法名是否包含业务语义（@DisplayName）？
- [ ] 没有空测试或 @Disabled？

#### Prompt 可追溯性检查
- [ ] 交接包中是否包含 Prompt 版本号？
- [ ] 该 Prompt 版本是否是 `PROMPT-VERSIONS/` 中当前推荐版本？
- [ ] 如果 Prompt 版本过旧，建议升级并重新生成

#### @AiGenerated 标注检查
- [ ] 复杂逻辑是否已标注 @AiGenerated？
- [ ] @AiGenerated 的 reviewBy 日期是否合理？
- [ ] 已到期的 @AiGenerated 是否已人工复核并移除？

### 阻断条件
- 测试文件缺失 → **阻断合并**
- Anti-Goals 对应测试用例不完整 → **阻断合并**
- Prompt 版本号缺失 → 要求补充（不阻断，但需记录）
- 存在过期 @AiGenerated（reviewBy 已过）→ 标记为「一般」级问题

---

## 阶段四：Review Agent → 人工 Review + 部署

### 人工检查重点
1. 检查 @AiGenerated 标注，决定是否人工重构
2. 运行 PROMPT-GOLDEN-TESTS，更新 CHANGELOG
3. SCRATCHPAD 更新（记录上线决策）

---

## 输出问题分级

| 级别 | 定义 | 处理 |
|------|------|------|
| 严重 | 违反 Anti-Goals / BigDecimal 缺失 / 敏感数据泄露 | 阻断，必须修复 |
| 一般 | 命名不规范 / 过期 @AiGenerated / Prompt 版本过旧 | 记录，限期修复 |
| 建议 | 代码风格优化 / 重构信号 / 性能优化 | 记录，下次迭代处理 |
