# PROMPT-GOLDEN-TESTS.md — Prompt 效果基准测试

> 每次修改 PROMPT-VERSIONS 中的任何 Prompt 后，运行对应 Golden Test。
> 没有 Golden Test 的 Prompt 修改，等于盲目飞行。

---

## 使用流程

1. 修改 `PROMPT-VERSIONS/` 中任何 Prompt 文件前，先记录当前版本号
2. 修改后，用下方对应的 Golden Test 输入跑一次
3. 对比输出特征是否全部通过
4. 将结果记入对应 `CHANGELOG.md`
5. 通过率低于上一版本时，禁止升级为当前默认版本

---

## Golden Test A：generate-api

### 标准输入（每次测试使用完全相同的输入）

intent.md 片段：
- Anti-Goals: 不允许 Controller 直接调用 Mapper
- 数据敏感级别: phone=高敏（日志脱敏）

openapi.yaml 片段：
- POST /api/v1/debts
- requestBody: { userId: string, amount: BigDecimal, debtName: string }

### 预期输出特征（逐条判断）
- [x] T-A-01: Controller 方法体不超过 10 行
- [x] T-A-02: 入参 DTO 包含 @NotNull @DecimalMin 等约束注解
- [x] T-A-03: 返回类型使用统一 Response 包装
- [x] T-A-04: amount 字段类型为 BigDecimal，非 double/float
- [x] T-A-05: phone 在日志语句中被脱敏处理
- [x] T-A-06: 包含《生成决策说明》且提到了 Anti-Goals
- [x] T-A-07: 同时生成了 Service 接口骨架
- [x] T-A-08: 测试骨架中包含针对 Anti-Goals 的测试方法名

### 通过率历史
| 版本 | 日期 | 通过数 | 总数 | 通过率 |
|------|------|--------|------|--------|
| v1.0 | 2026-03-09 | 8 | 8 | 100% |

---

## Golden Test B：code-review

### 标准输入

待 Review 代码：包含以下已知问题的样本代码
- double 类型的 debtAmount 字段（应为 BigDecimal）
- Controller 中包含 `if (score < 60)` 判断
- phone 直接出现在 `log.info()` 语句中
- 缺少 @Version 乐观锁注解

### 预期输出特征
- [x] T-B-01: 发现并标注 double 类型问题为「严重」级
- [x] T-B-02: 发现 Controller 业务逻辑问题为「严重」级
- [x] T-B-03: 发现 phone 日志泄露问题为「严重」级
- [x] T-B-04: 每个问题标注了违反的具体规范来源（CLAUDE.md F-xx / intent.md AG-xx）
- [x] T-B-05: 输出按「严重 / 一般 / 建议」三级分类

### 通过率历史
| 版本 | 日期 | 通过数 | 总数 | 通过率 |
|------|------|--------|------|--------|
| v1.0 | 2026-03-09 | 5 | 5 | 100% |

---

## Golden Test C：generate-test

### 标准输入

state-machines.yaml: DRAFT→SUBMITTED→OCR_PROCESSING→PENDING_CONFIRM→CONFIRMED→IN_PROFILE
intent.md Anti-Goals:
- AG-10: 不在 Controller 层直接操作 Mapper
- AG-03: 不使用 float/double 进行金额计算

### 预期输出特征
- [x] T-C-01: 包含至少 5 个合法流转测试方法
- [x] T-C-02: 包含「DRAFT→CONFIRMED」非法跳跃测试，使用 assertThrows
- [x] T-C-03: 包含 BigDecimal 强制验证的测试
- [x] T-C-04: 测试方法名使用中文 @DisplayName 描述业务语义
- [x] T-C-05: 没有空测试方法或 @Disabled 注解

### 通过率历史
| 版本 | 日期 | 通过数 | 总数 | 通过率 |
|------|------|--------|------|--------|
| v1.0 | 2026-03-09 | 5 | 5 | 100% |
