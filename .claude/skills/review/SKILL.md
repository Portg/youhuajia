---
name: review
description: "基于 AGENT-PROTOCOL 的代码审查。按 Anti-Goals + ADR 合规 + 测试完整性 + @AiGenerated 四维检查，输出三级问题报告。触发方式：/review <文件路径或 PR 编号>"
user_invocable: true
---

# /review — AI Code Review（AGENT-PROTOCOL 规范）

基于 `AGENT-PROTOCOL.md` 的 Review Agent 检查项对代码执行审查。

## 触发方式

```
/review <文件路径 | 目录 | PR编号 | --staged>
```

示例：
- `/review src/main/java/com/youhuajia/service/ScoringEngine.java`
- `/review src/main/java/com/youhuajia/controller/`
- `/review --staged` （审查 git 暂存区文件）

---

## 执行流程

### 第一步：确定审查范围

- **指定文件**：直接读取
- **指定目录**：Glob 搜索 `*.java` / `*.vue` 文件
- **`--staged`**：`git diff --cached --name-only` 获取暂存区文件
- **PR 编号**：`gh pr diff <编号>` 获取变更文件

### 第二步：加载上下文（CONTEXT-SLICE 切片 D）

```
1. ai-spec/domain/intent.md（Anti-Goals 逐条核对）
2. ai-spec/VIBE-CHECKLIST.md（六关门禁）
3. ai-spec/AGENT-PROTOCOL.md（Review Agent 检查项）
4. CLAUDE.md（禁止项 + 命名规范）
5. [被审查的代码文件]
6. [对应的测试文件]
```

### 第三步：四维检查

#### 维度一：Anti-Goals 合规性
逐条核对 `intent.md` 中 AG-01~AG-12：
- BigDecimal 使用（AG-03）
- 敏感数据日志脱敏（AG-04）
- 恐慌表达检测（AG-05）
- Controller 层无业务逻辑（AG-10）
- 无自造接口/字段（AG-11）
- 无专业术语面向用户（AG-12）

#### 维度二：ADR 合规性
- 依赖是否违反技术选型（MyBatis-Plus, not JPA）
- 代码结构是否符合分层架构（Controller→Service→Mapper）
- 异常处理是否使用 BizException(ErrorCode)
- 配置管理是否走 application.yml（非硬编码）

#### 维度三：测试完整性
- Anti-Goals 中每条规则是否有对应测试方法
- 测试方法名是否包含 @DisplayName 中文描述
- 没有空测试或 @Disabled
- 状态机非法跳跃是否有 assertThrows

#### 维度四：@AiGenerated 标注
- 复杂逻辑是否已标注
- reviewBy 日期是否合理
- 已到期的标注是否已人工复核

### 第四步：输出报告

```
## Code Review 报告

**审查范围**：[文件列表]
**使用规范**：AGENT-PROTOCOL.md v1.0

### 严重（阻断合并）
- [AG-xx] 具体问题 → 文件:行号
  违反规范：intent.md / CLAUDE.md F-xx

### 一般（限期修复）
- 具体问题 → 文件:行号

### 建议（下次迭代）
- 具体问题 → 文件:行号

**四维评分**：
- Anti-Goals 合规：__/12 通过
- ADR 合规：✓/✗
- 测试完整性：__% 覆盖
- @AiGenerated：__ 处待复核
```
