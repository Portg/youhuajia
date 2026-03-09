# TEST-SPEC.md — AI 生成代码测试规范

> 测试是生成任务的组成部分，不是事后补充。

---

## 强制原则

1. **测试与代码同步生成**：Coding Agent 收到任务时，测试是任务的一部分
2. **测试先于代码检视**：Review Agent 必须先看测试，再看实现
3. **Anti-Goals 必须有测试覆盖**：`domain/intent.md` 每条 Anti-Goal 对应至少一个 @Test

---

## 分层测试强制要求

| 代码层 | 测试类型 | 最低覆盖率 | 框架 | 特殊要求 |
|--------|----------|-----------|------|----------|
| Controller | MockMvc 集成测试 | 80% | Spring Test | 每个接口至少一个成功用例 + 一个参数校验失败用例 |
| Service | 单元测试 | 90% | JUnit5 + Mockito | Anti-Goals 中每条规则必须有对应测试用例 |
| Mapper | SQL 验证测试 | 核心查询 100% | MyBatis Test | 包含边界值：空结果 / 最大记录数 |
| 状态流转 | 状态边界值测试 | 100% | JUnit5 | 合法流转 + 所有非法跳跃（必须抛出明确异常） |
| 规则引擎 | 规则单元测试 | 100% | JUnit5 | `engine/rules.md` 每个 Rule Key 对应一个测试 |
| APR 计算 | 精度验证测试 | 95% | JUnit5 + AssertJ | BigDecimal 精度到 scale=6，边界值全覆盖 |

---

## 状态机测试规范（最严格）

### 必须覆盖的用例类型

**合法流转测试**（每条合法路径一个用例）：
```java
@Test void should_transit_from_DRAFT_to_SUBMITTED() { ... }
@Test void should_transit_from_SUBMITTED_to_OCR_PROCESSING() { ... }
```

**非法跳跃测试**（每条非法路径一个用例，必须抛出异常）：
```java
@Test void should_throw_when_transit_from_DRAFT_to_CONFIRMED() {
    assertThrows(IllegalStateTransitionException.class, () -> {
        debtService.updateStatus(debt, DebtStatus.CONFIRMED);
    });
}
```

**Anti-Goals 测试**（`intent.md` 每条 Anti-Goal 对应一个）：
```java
// Anti-Goal AG-03: 不使用 float/double 进行金额/利率计算
@Test void should_use_BigDecimal_for_all_financial_fields() { ... }

// Anti-Goal AG-09: 不硬编码评分权重
@Test void should_load_thresholds_from_config() { ... }
```

---

## @AiGenerated 注解规范

当 AI 生成的代码存在以下情况时，必须标注：
1. 逻辑复杂但未经充分人工审查
2. 性能未经过基准测试
3. 依赖了 Mock 数据的临时实现
4. AI 推理链记录显示「不确定」的部分

```java
@AiGenerated(
    reason = "评分权重映射逻辑待人工复核",
    generatedAt = "2026-03-09",
    reviewBy = "2026-Q2",
    scratchpadRef = "2026-03-09_评分引擎开发"
)
public class ScoringEngine { ... }
```

### 注解定义

```java
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
public @interface AiGenerated {
    String reason();
    String generatedAt();
    String reviewBy() default "";
    String scratchpadRef() default "";
}
```

---

## 测试命名规范

- 方法命名：`should_{结果}_when_{条件}`（与 CLAUDE.md 一致）
- 必须使用 `@DisplayName` 包含中文业务描述
- 禁止空测试方法或 `@Disabled` 注解

```java
@Test
@DisplayName("当用户有超过3笔债务时，应计算加权APR")
void should_calculate_weighted_apr_when_debts_exceed_3() { ... }
```

---

## 测试上下文加载（CONTEXT-SLICE 切片 G）

生成测试时加载：
1. `@ai-spec/domain/intent.md`（Anti-Goals 全文）
2. `@ai-spec/domain/state-machines.yaml`
3. `@ai-spec/engine/rules.md`
4. `@ai-spec/TEST-SPEC.md`（本文件）
5. [待测试的代码文件]

跳过：`openapi.yaml`、`user-journey.md`
