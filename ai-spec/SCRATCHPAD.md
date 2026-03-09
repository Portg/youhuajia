# SCRATCHPAD.md — 跨会话连续性记录

> 关键决策必须要求 AI 输出推理过程，而不只是结论。
> 推理链和结论一起存入 Scratchpad，方便日后审计「AI 是否误解了某条 Anti-Goal」。

---

## 使用规则

1. 每次 AI 会话结束前，复制下方模板让 AI 填写
2. 新记录插入文件顶部（最新在上）
3. 超过 10 条时压缩最老 7 条为「历史摘要」
4. 关键决策必须填写「AI 推理链」

---

## 记录模板 v4

```markdown
---
日期：____ 任务类型：____ 关联切片：____
使用的 Prompt 版本：[如 generate-api/v2.0]

### 本次完成了什么

### 关键决策
| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|

### AI 推理链（关键决策必填）
# 格式要求：每个关键决策，让 AI 按以下结构输出推理过程
#
# 决策：[决策名称]
# 1. 我读取了 [文件/规则]
# 2. 我注意到 [关键约束，引用原文]
# 3. 我考虑了 [方案A] 和 [方案B]
# 4. 方案A 的问题：[具体原因]
# 5. 因此我选择了 [方案B]，因为 [与约束的对应关系]

### 放弃的方案
| 方案描述 | 放弃原因 |
|----------|----------|

### 遗留问题（下次会话继续）
- [ ]

### Spec 变更建议
- 文件：____ 内容：____

### Prompt 效果反馈
# 使用后对当前 Prompt 版本的评价：
# 版本：____ 评价：[好用 / 有问题] 具体：____
# 如发现问题，在对应 PROMPT-VERSIONS/CHANGELOG.md 中记录
---
```

---

## 推理链示例

```
决策：债务录入幂等方案选择
1. 我读取了 intent.md：「防重复提交使用 requestId（UUID4）」
2. 我注意到 CLAUDE.md 3.2 节要求 Create 请求支持 requestId（AIP-155）
3. 我考虑了「userId+timestamp」和「客户端 UUID」两种方案
4. userId+timestamp 的问题：分布式环境中时钟漂移导致同一请求被计两次
5. 因此选择客户端 UUID，因为幂等判断转移到 UUID 唯一性，与时钟无关
```

---

## 会话记录（最新在上）

<!-- 新记录插入此处 -->

---
日期：2026-03-09 任务类型：Bug 修复 + 设计互斥守卫 关联切片：前端改善计划流程

### 本次完成了什么

**Bug 修复：layer1ReportId 字符串占位符 → Long 反序列化异常**
- 根因：低分路径 `completeLayer1('low-score-plan')` 和降级路径 `FALLBACK_LAYER1.reportId: 'local-demo'` 均传字符串，后端字段为 `Long`
- 修复：3 处字符串改为 `null`（`low-score/improvement-plan.vue`、`page8-action-layers/index-low-score.vue`、`page8-action-layers/index.vue`）

**设计修复：improvement plan 持久化改为仅低分用户生效**
- 问题1：`syncPlanToBackend` 无 isLowScore 判断 → 正常用户也写 `improvement_plan` 表（应由报告系统追踪）
- 问题2：`inferStep` plan 同步无 isLowScore 判断 → 正常用户从 improvement_plan 读 all-false，覆盖 unistorage 中已完成的 actionLayers 状态
- 修复：两处加 `isLowScore.value` 守卫；`reset()` DELETE 保持无条件（低分用户 reset 后 score=0，isLowScore=false，条件判断会漏删）

**代码清理 + 文档对齐（本次会话前半段）**
- `generateJwt(Long userId, String phone)` → `generateJwt(Long userId)`，移除冗余参数
- `auth.js` consentVersion 提取为 `export const CONSENT_VERSION = 'v1.0'`
- `entities.md`：新增 UserImprovementPlan 实体定义 + V10/V11 迁移记录
- 4 个语义化 commit 推送到 origin/master

### 关键决策
| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| improvement plan 读写是否区分用户类型 | 仅低分用户（isLowScore.value 守卫） | 正常用户进度由报告系统追踪，两表互斥；否则正常用户的 actionLayers 会被 all-false 覆盖 |
| reset() DELETE 是否加 isLowScore 守卫 | 不加（无条件） | 低分用户调 reset 时 score 已被清零（=0），isLowScore 为 false，条件判断会漏删 |
| 低分路径 reportId | null | 低分用户无真实报告，后端 Long 字段不接受字符串；null 在 upsert 中被正确处理（null-preserve）|

### AI 推理链（关键决策必填）

决策：improvement plan 读写仅低分用户生效
1. 我读取了 funnel.js 的 completeLayer1/2/3 和 inferStep 实现
2. 我注意到 syncPlanToBackend 和 inferStep plan 同步对所有用户生效
3. 用户指出：正常用户有报告表、低分用户有改善计划表，应互斥
4. 方案A（无守卫）的问题：正常用户写 improvement_plan 是冗余写；inferStep 读 all-false 会覆盖 unistorage 完成状态，导致 Page8 进度丢失
5. 因此加 `if (!isLowScore.value) return` 守卫，确保两个系统各自独立

决策：reset() DELETE 保持无条件
1. 我注意到 reset() 先清零 score（`score.value = 0`），再执行后续逻辑
2. isLowScore = `score.value > 0 && score.value < 60`，reset 后为 false
3. 如果在 reset 内判断 isLowScore，低分用户的 improvement_plan 记录永远不会被删除
4. 因此 DELETE 必须在 score 清零前执行，或保持无条件（后者更简单）
5. 对正常用户无影响（DELETE 是幂等空操作）

### 放弃的方案
| 方案描述 | 放弃原因 |
|----------|----------|
| reset() 中先读 isLowScore 再清零再 DELETE | 代码顺序依赖脆弱，无条件 DELETE 更简单且等价 |
| 保留字符串 reportId，后端改为接受 String | 违反数据模型设计，reportId 语义上是数字型外键 |

### 遗留问题（下次会话继续）
- （无）

### Spec 变更建议
- 文件：domain/entities.md 内容：已完成（UserImprovementPlan + V10/V11）
- 文件：client-spec.md 内容：已完成（funnel store 新增 improvement plan 互斥设计说明）
---

---
日期：2026-03-09 任务类型：BACKLOG #9 后端持久化 关联切片：全栈（后端新功能 + 前端 store）

### 本次完成了什么

**BACKLOG #9：改善计划后端持久化（421 后端单元测试 + 90 前端测试，全绿）**

1. **V11 Flyway 迁移** — `t_user_improvement_plan`（4 层字段 + `uk_improvement_plan_user_id` 唯一索引 + 乐观锁 version）
2. **后端 plan 包** — Entity / Mapper / DTO(Request+Response) / Service / ServiceImpl / Controller
   - GET `/api/v1/improvement-plans/mine` — 无记录返回 empty()（全 false），前端无需处理 404
   - PATCH `/api/v1/improvement-plans/mine` — upsert，null 字段保留现有值
   - DELETE `/api/v1/improvement-plans/mine` — 逻辑删除（重新评估时调用）
3. **前端 `src/api/improvementPlan.js`** — 三个 API 函数
4. **`funnel.js` 三处改动**：
   - `completeLayer1/2/3` → 调用 `syncPlanToBackend()`（fire-and-forget，失败不阻断前端）
   - `inferStep()` → 并行第 4 个请求拉取 plan，同步到 `actionLayers`
   - `reset()` → fire-and-forget `deleteImprovementPlan()`
5. **ImprovementPlanServiceImplTest** — 7 用例（insert/update/null-preserve/delete/response mapping）
6. **funnel.test.js** — 顶层 mock `improvementPlan.js`，23 用例全通过

**关闭遗留问题**：
- ✅ BACKLOG #9 完成
- ✅ MISMATCH #3 分析确认：测试逻辑正确，withSmsCodeField 通过 MockBean 返回 200（非 400），断言无误

### 关键决策
| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| GET 无记录返回策略 | 返回 empty()（全 false，200） | 前端 inferStep 用 requestSilent 无法区分"无记录 404"和"服务故障 500"；统一 200 让前端直接解构不需要特判 |
| completeLayer 同步方式 | fire-and-forget（不 await） | 完成动作是用户即时操作，不应等待后端响应；本地 unistorage 已持久化，后端持久化是强化保障 |
| reset DELETE 触发时机 | 在 reset() 中直接 fire-and-forget | reset() 的语义已是"重新开始"，统一在此处清空后端，无需每个调用方额外处理 |
| null 字段是否覆盖 | null=不更新，false/true=覆盖 | 防止多设备并发"部分更新"时误清已完成层；前端实际上总是发全量，null 保护是防御性设计 |

### AI 推理链（关键决策必填）

决策：GET 无记录返回 200+empty 而非 404
1. 我读取了 inferStep() 调用 requestSilent，错误被 Promise.allSettled 的 rejected 分支静默吞掉
2. 我注意到 requestSilent 设计为"所有异常不抛出"，前端用 `planRes.status === 'fulfilled'` 判断
3. 如果返回 404，requestSilent 会将其 reject，planRes.status 为 'rejected'，前端不会同步 plan — 逻辑正确但多了一个空请求的失败日志
4. 如果返回 200+empty，planRes.status 为 'fulfilled'，前端安全解构，no-op（因为所有字段都是 false，与默认状态一致）
5. 因此选择 200+empty，减少无用的失败日志，简化前端处理路径

### 放弃的方案
| 方案描述 | 放弃原因 |
|----------|----------|
| completeLayer 改为 async + await | 用户点击"完成"后需立即看到 UI 反馈，异步等待后端增加感知延迟 |
| 单独新建 ImprovementPlanController 在 profile 包 | plan 是独立业务域（不是 profile 的子资源），独立包职责更清晰 |
| checklist 也持久化到后端 | checklist 是 Page 9 UI 辅助项，无跨设备恢复价值；BACKLOG 原文只提 actionLayers |

### 遗留问题（下次会话继续）
- （无）

### Spec 变更建议
- 文件：domain/entities.md 内容：新增 `t_user_improvement_plan` 表定义（V11 迁移）
- 文件：contracts/openapi.yaml 内容：新增 GET/PATCH/DELETE `/improvement-plans/mine` 端点
---

---
日期：2026-03-09 任务类型：Bug 修复（安全/显示/状态）+ 架构对齐讨论 关联切片：后端认证安全 / 前端低分路径

### 本次完成了什么
- `sessions:revoke` 端点认证漏洞修复（JwtAuthFilter PUBLIC_PREFIXES 过宽）
- `JwtAuthFilterTest` 新增（15 用例，含 TC-01 回归防止同类问题）
- 改善计划预估分数倒退修复（`improvement-plan.vue` + `credit-optimization.vue`）
- 登出后改善计划被清空修复（`mine/index.vue` 移除 `funnelStore.reset()`）
- 高分用户"我的报告"排查（无问题，后端实时拉取）
- 改善计划后端持久化方案设计（两表结构 + 不保留历史，记入 BACKLOG #9）

### 关键决策
| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| JwtAuthFilter 公开路径策略 | 精确枚举 PUBLIC_EXACT_PATHS，移除宽泛前缀 | `/api/v1/auth/` 前缀匹配到 `revoke`，导致绕过 JWT 校验；精确枚举默认受保护，防止未来新增端点被误判为公开 |
| projectedScore 基准 | `funnelStore.score`（后端 PMML）+ 客户端 `scoreDelta` | 两个模型绝对分不可比；delta 反映改善幅度是正确语义，基准锚定后端分保持一致性 |
| 登出是否 reset funnelStore | 不 reset | 改善计划是用户数据，只应在重新评估时清空；登出时保留本地数据 |
| 改善计划是否保留历史 | 不保留（只保最新一条）| 计划是行动指令而非历史快照；报告才是评分结果记录；MVP 保持简单 |

### AI 推理链（关键决策必填）

决策：JwtAuthFilter 精确枚举公开路径
1. 我读取了 `JwtAuthFilter.PUBLIC_PREFIXES = {"/api/v1/auth/", ...}`
2. 我注意到 `/api/v1/auth/sessions:revoke` 匹配前缀，JWT 过滤器跳过，`userId` 未注入，`RequestContextUtil.getCurrentUserId()` 抛"用户未登录"
3. 我考虑了「在前缀集合中手动排除 revoke」和「改为精确枚举公开路径」
4. 手动排除的问题：未来新增 `/api/v1/auth/xxx` 端点默认公开，需每次记得排除，容易遗漏
5. 因此选择精确枚举 `PUBLIC_EXACT_PATHS`，默认受保护，只有明确列出的才是公开

决策：projectedScore = funnelStore.score + scoreDelta
1. 我读取了 `improvement-plan.vue`：当前评分展示 `funnelStore.score`（后端 PMML），预估展示 `simulation.value.simulated.finalScore`（客户端 JS）
2. 我注意到两个值来自不同模型，同样输入下后端分（51.5）≠ 客户端分（35），导致"改善后"反而低于当前
3. 我考虑了「统一用客户端分展示当前」和「用 scoreDelta 叠加后端基准分」
4. 统一用客户端分的问题：需改 `funnelStore.score` 来源，影响面大，且客户端分本身不如后端 PMML 准确
5. 因此用后端基准 + 客户端 delta，delta 是同一模型内的相对变化，方向正确且不依赖绝对精度

### 放弃的方案
| 方案描述 | 放弃原因 |
|----------|----------|
| 在 PUBLIC_PREFIXES 中手动排除 revoke | 防御性差，未来新增 auth 端点默认公开，易遗漏 |
| projectedScore 改为纯客户端绝对分 | 客户端模型与后端 PMML 精度不同，用户会看到"当前 35 分"与之前看到的"51.5 分"不一致 |
| 改善计划保留历史（对齐报告） | 计划是行动指令，历史无复看价值；报告是评分快照，有比较价值；两者性质不同 |

### 遗留问题（下次会话继续）
- （无）

### Spec 变更建议
- 文件：`client-spec.md` 内容：`scoreSimulator.js` 补充说明"projectedScore 必须用 `funnelStore.score + scoreDelta`，不得直接展示 `simulated.finalScore`"
---

---
日期：2026-03-09 任务类型：系统改善 #1-#11 全实现 + Code Review 修复 关联切片：全栈（后端认证/债务/引擎 + 前端漏斗/埋点）
使用的 Prompt 版本：无（IMPROVEMENT-BACKLOG 驱动）

### 本次完成了什么

**实现 IMPROVEMENT-BACKLOG 11 项改善（489 tests，全绿）**

1. **#1 inferStep**：登录后并行拉 debts/profile/reports，推算 step（1/3/5/8），静默写入 funnelStore
2. **#2 Page4→5 低分路由**：点击 CTA 前确保画像已计算，score≤0 先 triggerCalculation，isLowScore 分支路由
3. **#3 DebtMapper 聚合 SQL**：selectSummaryByUserId 一条 SQL 替代全量 selectList 二次查询
4. **#4 PreAuditEngineTest**：22 个测试，覆盖概率 clamping / 建议生成 / 配置降级
5. **#5 前端单测**：formatters.test.js(24) + funnel.test.js(18) 新增
6. **#6 限流注解**：@RateLimit(sms-send: 1/60s, login: 5/300s)
7. **#7 隐私协议**：V10 Flyway 迁移 + User 字段 + LoginRequest.consentVersion + AuthServiceImpl 记录
8. **#8 Golden Tests**：3 套 golden test 基准（generate-api/code-review/generate-test）
9. **#9 埋点**：tracking.js，9 个标准事件，H5/小程序双条件编译
10. **#10 revokeSession**：Redis 删除 session key，RequestContextUtil 取 userId
11. **#11 SensitiveDataUtil**：maskPhone/maskIdCard，AuthServiceImpl 委托

**Code Review 修复（6 项）**

- 严重1: 新增 `CONSENT_REQUIRED(401009)` 错误码，service 层显式校验替代 @NotBlank
- 严重2: 补 AG-13/14/15 对应 @Test（AuthServiceImplTest + funnel.test.js 6 个用例）
- 一般: DebtServiceImpl 聚合 Map null 防御 / @AiGenerated 标注 / @DisplayName / tracking.js 注释澄清

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| AG-13 校验位置 | Service 层显式抛 CONSENT_REQUIRED，移除 DTO @NotBlank | 前端可程序化识别专用错误码；@NotBlank 返回通用 400 无法区分"未同意"和"字段缺失" |
| CONSENT_REQUIRED 时 SMS code 是否删除 | 不删除 | 用户同意后可直接重试，无需重新获取验证码；5 分钟 TTL 内安全 |
| inferStep 推算粒度 | 粗粒度 4 档（1/3/5/8） | SPEC-DISCOVERY 决策：细粒度推算在数据不完整时产生错误锚点，粗粒度降级更安全 |
| DebtServiceImpl null 防御 | 逐 key 三元运算，默认 0/ZERO | 避免 NPE 同时保持返回结构完整，不影响正常路径 |

### AI 推理链（关键决策必填）

决策：CONSENT_REQUIRED 移除 @NotBlank
1. 我读取了 intent.md AG-13："未同意隐私协议返回 CONSENT_REQUIRED"
2. 我注意到 @NotBlank 校验走 Spring Validation，返回 VALIDATION_FAILED(500007) 通用 400
3. 我考虑了 "保留 @NotBlank + 加 ConstraintViolation 处理器" 和 "移除 @NotBlank + service 层抛专用 CONSENT_REQUIRED"
4. 前者问题：GlobalExceptionHandler 需新增 ConsentVersion 专属匹配，且前端仍需解析 details 字段，复杂度高
5. 因此移除 @NotBlank，service 层第一道校验抛 CONSENT_REQUIRED，前端可直接 switch error.code

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| @NotBlank + ConstraintViolationException 拦截 | 需定制 GlobalExceptionHandler 匹配 consentVersion 字段名，脆弱且可维护性差 |
| inferStep 细粒度推算（step 1-9 全档） | 数据不完整时产生假性高锚点，AG-15 要求降级到最高「完整」锚点 |
| generateJwt 移除 phone 参数 | 破坏 2 处调用 + 测试，代价 > 收益（参数只是冗余，非 bug） |

### 遗留问题（下次会话继续）

- [ ] localStorage phone 明文存储（MVP 可接受，2.0 阶段整体加密 storage 时处理）

### Spec 变更建议

- 文件：contracts/error-codes.md 内容：新增 CONSENT_REQUIRED(401009) 条目
- 文件：domain/intent.md 内容：AG-13 对应测试方法名已实现（should_reject_login_without_consent）
---

---

## 历史摘要（2026-03-09 早期会话，共 7 条压缩）

### 工程规范升级 + 技能包（最早）
- 新建 10 个 ai-spec 规范文件：intent.md / TEST-SPEC.md / VIBE-CHECKLIST.md / SCRATCHPAD.md / CONTEXT-SLICE.md / SPEC-DISCOVERY.md / AGENT-PROTOCOL.md / PROMPT-LIBRARY.md / PROMPT-GOLDEN-TESTS.md / PROMPT-VERSIONS/
- CLAUDE.md 文档索引拆分为 5.1 意图层+质量层 / 5.2 工程层
- 4 个项目级技能：/vibe-check、/review、/scratchpad、/spec-discover
- 低分流程排查：13800000001(82分)/13800000002(62分) 正常流程，13800000003(22分) 低分修复流程；发现 5 个实现问题（2严重/3一般）
- 关键决策：规范文件独立新建（四层架构各层职责不同，合并会模糊边界）；AG-xx 编号区别于 F-xx 技术禁令

### 低分用户流程差异修复
- Page 6/7 低分 redirect 守卫（`onMounted` 检测 isLowScore，重定向 credit-repair/risk-faq）
- 新建 `pages/low-score/risk-faq.vue`（4 个低分专属 FAQ）
- 低分流程步骤统一：5→6→7→8→9，credit-repair CTA 插入 risk-faq 消除跳号
- `funnel.js setFinanceProfile()` 检测低分↔正常切换时自动重置 actionLayers
- 首页 `continueAssessment()` 新增 lowScorePageMap

### 目录统一 + ai-spec 反向更新
- 确认 `src/` 为 uni-app inputDir（`UNI_INPUT_DIR` 默认 `path.resolve(root, 'src')`）
- 删除根目录冗余文件（pages/、pages.json、main.js、App.vue、manifest.json、uni.scss）
- 5 项低分修复合并到 src/ 正确位置
- `domain/user-journey.md` 低分流程完整页面定义

### ai-spec 反向更新（低分路径文档）
- `test/mock-data.md` 新增第二节：正常/低分双列路由对比表
- `client-spec.md` 5 处更新：src/ 说明 + risk-faq 路由 + Page 6/7 低分守卫说明

### 低分路径体验提升 + 客户端评分模拟 + 策略模型文件
- 新建 `src/utils/scoreSimulator.js`：`calculateScore`（五维评分+弱项识别）/ `simulateImprovement`（What-if 模拟）/ `buildScoreInput`（store 数据构造）
- 4 组策略文件（`ai-spec/engine/strategies/`）：DEFAULT / HIGH_DEBT / MORTGAGE_HEAVY / YOUNG_BORROWER（PMML + meta.yml 各一）
- `credit-optimization.vue` 个性化：弱项维度诊断卡片 + 30 天计划 + 预估分数卡片
- `improvement-plan.vue` Layer 1 增强：预估分数进度条 + 维度变化明细 + 后端降级为行为记录
- Page 9 低分适配：仪式弹窗 / 目标卡片（当前→60分）/ 时间轴 / Checklist 4 处条件分支
- 硬编码颜色修复：`confirmColor: '#1B6DB2'`（匹配 `$primary`）
- 关键决策：评分模拟前端本地计算（逻辑简单+离线友好）；projectedScore = `funnelStore.score + scoreDelta`（两模型绝对分不可比）

### scoreSimulator 分群支持 + client-spec 文档补全
- `matchSegment(input)`：4 种分群 HIGH_DEBT / MORTGAGE_HEAVY / YOUNG_BORROWER / DEFAULT（优先级匹配）
- `SEGMENT_WEIGHTS` 常量对齐 strategies/*.meta.yml；HIGH_DEBT 7 档 DIR
- `calculateScore` 升级：可选 segment 参数，向后兼容；返回值增加 segment 字段
- `buildScoreInput` 增加 `mortgageCount`
- `scoreSimulator.test.js` 43 用例全通过
- client-spec.md 补充 scoreSimulator 完整文档 + strategies/ 映射表

### 漏斗页退出机制 + 低分用户"我的"页面适配
- 新建 `src/components/FunnelNavBar.vue`：左侧返回箭头（navigateBack + fail 兜底 switchTab）+ 首页图标
- 13 个漏斗页集成 FunnelNavBar，pages.json 11 个页面切换 `navigationStyle: "custom"`
- `mine/index.vue` 条件文案：低分→"我的改善计划"，正常→"我的报告"
- `mine/reports.vue` 低分用户展示本地改善计划（30天计划+预估分数+checklist 进度）
- 关键决策：导航栏首页按钮（vs 全程 TabBar：底部空间冲突+降低漏斗完成率+技术成本高）

