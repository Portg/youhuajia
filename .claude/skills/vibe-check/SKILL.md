---
name: vibe-check
description: "AI 输出六关验收门禁。对指定代码文件运行 VIBE-CHECKLIST 检查，输出三级问题报告。触发方式：/vibe-check <文件路径或模块名>"
user_invocable: true
---

# /vibe-check — AI 输出六关验收门禁

对指定的代码文件或模块执行 VIBE-CHECKLIST.md 六关验收检查。

## 触发方式

```
/vibe-check <文件路径 | 模块名 | 最近改动>
```

示例：
- `/vibe-check src/main/java/com/youhuajia/service/DebtService.java`
- `/vibe-check engine`
- `/vibe-check --recent` （检查最近 git 改动的文件）

---

## 执行流程

### 第一步：确定检查范围

根据用户输入确定检查文件：
- **指定文件路径**：直接读取该文件
- **模块名**：Glob 搜索相关文件
- **`--recent`**：`git diff --name-only HEAD~1` 获取最近改动文件

### 第二步：加载上下文（CONTEXT-SLICE 切片 D）

读取以下 ai-spec 文件：
1. `ai-spec/domain/intent.md` — Anti-Goals 清单
2. `ai-spec/VIBE-CHECKLIST.md` — 六关检查项
3. `CLAUDE.md` — 禁止项 + 命名规范
4. 被检查的代码文件
5. 对应的测试文件（如存在）

### 第三步：逐关检查

按 VIBE-CHECKLIST.md 的六关 + 附加测试关执行检查：

**【第一关】意图对齐** — 核对 intent.md Anti-Goals AG-01~AG-12
**【第二关】上下文遗漏** — 核对 state-machines.yaml 状态 + openapi.yaml 字段
**【第三关】AI 幻觉风险** — 检查是否有未定义的字段/接口/魔法数字
**【第四关】金融场景专项** — BigDecimal + 日志脱敏 + 乐观锁 + 幂等
**【第五关】代码质量底线** — printStackTrace / SuppressWarnings / 裸 RuntimeException
**【第六关】重构信号检测** — 单一职责 + 方法长度 + 嵌套深度 + @AiGenerated
**【附加】测试覆盖** — Anti-Goals 测试 + 状态机测试 + @DisplayName

### 第四步：输出报告

```
## VIBE-CHECK 报告

**检查文件**：[文件列表]
**检查时间**：[日期]

### 严重（阻断合并）
- [ ] [AG-xx] 具体问题描述 → 文件:行号

### 一般（限期修复）
- [ ] 具体问题描述 → 文件:行号

### 建议（下次迭代）
- [ ] 具体问题描述 → 文件:行号

**通过关卡**：第一关 ✓ | 第二关 ✓ | 第三关 ✗ | ...
**总计**：严重 __ / 一般 __ / 建议 __
```
