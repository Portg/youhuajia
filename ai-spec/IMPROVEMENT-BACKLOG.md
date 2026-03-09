# 优化家系统改善清单（合并版）

> 合并自代码扫描分析 + 人工改善点梳理，去重后按优先级排列。

---

## 高优（MVP 阻塞）

### 1. 种子用户登录后同步 currentStep

- **问题**：`currentStep` 仅存前端 store，换设备/重装后漏斗归零，种子用户需从 Page 1 重走
- **方案**：登录后拉 profile/债务/报告/咨询，按后端数据推算 currentStep 并写入 funnel store
- **涉及文件**：`stores/funnel.js`、`stores/auth.js`、登录流程
- **SPEC 决策**（SPEC-DISCOVERY 2026-03-09）：
  - **粒度**：细粒度推算，映射规则：无数据=1, 有债务=3, 有画像+score>0=5, 有报告=8, 有咨询=9
  - **降级**：数据不一致时向下降级，取最高「完整」锚点（AG-15）
  - **恢复方式**：首页展示「继续上次分析」卡片，点击后跳转推算页
  - **导航**：推算 step 仅影响首页入口，不阻止用户页内自由返回（AG-14）
  - **新增 Anti-Goals**：AG-14, AG-15

### 2. Page 4→5 前确保拉画像更新 score

- **问题**：低分用户（如 13800000003）进 Page 5 前若 score 未加载，误走正常路径
- **方案**：Page 4 点击「看看我的优化空间」前调用 `profileStore.loadProfile()` / `triggerCalculation()`，确保 `funnelStore.score` 已更新
- **涉及文件**：`pages/page4-loss-report/index.vue`、`stores/profile.js`

### 3. DebtServiceImpl summary 聚合 SQL

- **问题**：`DebtServiceImpl:94` 先分页查 list 再全量查一次做 summary（COUNT/SUM），双倍查询开销
- **方案**：用一条聚合 SQL（`COUNT`、`SUM(principal)`、`SUM(monthly_payment)`、confirmed 计数），避免第二次全表查询
- **涉及文件**：`DebtServiceImpl.java`、`DebtMapper.java`

### 4. PreAuditEngine 补单元测试

- **问题**：核心业务引擎，零测试覆盖
- **方案**：创建 `PreAuditEngineTest`，覆盖维度匹配、概率 clamping、建议生成等场景
- **涉及文件**：`engine/scoring/PreAuditEngine.java`

### 5. 前端补 store / 工具函数单测

- **问题**：仅有 `scoreSimulator.test.js` 一个测试文件，store 逻辑和工具函数无测试
- **方案**：优先补测 funnel store（步进逻辑、低分分支判断）、`formatters.js`（金额/百分比格式化）、debt store（APR 计算触发）
- **涉及文件**：`stores/__tests__/`、`utils/__tests__/`

---

## 中优（上线前）

### 6. 登录 / 发短信限流覆盖

- **问题**：`@RateLimit` 注解已实现但未覆盖敏感端点，存在短信轰炸和暴力登录风险
- **方案**：对 `sendSms`、`createSession` 做按手机号 + IP 的限流（注解或配置）
- **涉及文件**：`AuthController.java`、`RateLimitConfig.java`
- **SPEC 决策**（SPEC-DISCOVERY 2026-03-09）：
  - **短信**：按手机号限流，1 分钟 1 次
  - **登录**：按 IP 限流，5 分钟 5 次

### 7. 隐私协议首次强制展示 + 记录同意

- **问题**：隐私政策/用户协议页面已有（`privacy/index.vue`、`terms/index.vue`），但缺「首次强制展示 + 勾选同意」逻辑，无同意时间/版本记录
- **方案**：登录页底部勾选框 + 后端强制校验 + User 表记录
- **涉及文件**：`pages/auth/login.vue`、`AuthController.java`、`AuthServiceImpl.java`、User 表迁移
- **SPEC 决策**（SPEC-DISCOVERY 2026-03-09）：
  - **展示时机**：登录页底部「我已阅读并同意《隐私政策》《用户协议》」勾选框，未勾选不可登录
  - **后端校验**：`createSession` 接口新增 `consentVersion` 必填参数，未同意返回 `CONSENT_REQUIRED`（AG-13）
  - **存储**：User 表新增 `consent_time DATETIME(3)` + `consent_version VARCHAR(16)` 字段
  - **版本更新**：MVP 不处理协议版本升级重新同意，留字段不写逻辑（2.0 再加）
  - **新增 Anti-Goal**：AG-13
  - **新增敏感字段**：consentTime（低敏）、consentVersion（低敏）

### 8. Prompt Golden Tests 补实际用例

- **问题**：`PROMPT-GOLDEN-TESTS.md` 骨架已建但内容为空，4 个 Prompt 版本缺乏回归基准
- **方案**：为 OCR 抽取、建议生成、可解释性输出、代码审查 4 个 Prompt 各补 3-5 个 golden test case
- **涉及文件**：`ai-spec/PROMPT-GOLDEN-TESTS.md`

---

## 低优（上线后迭代）

### 9. 改善计划进度持久化至后端 ✅ 已实现（2026-03-09）

- **问题**：`actionLayers`（改善计划完成状态）仅存 unistorage（本地 App 缓存），清除缓存或换设备后丢失；多账号共用同一设备时存在数据混用风险
- **实现**：
  - `V11__create_user_improvement_plan.sql`：`t_user_improvement_plan` 表，`uk_improvement_plan_user_id`，乐观锁
  - `com.youhua.plan.*`：Entity / Mapper / DTO / Service / Controller（GET|PATCH|DELETE `/api/v1/improvement-plans/mine`）
  - `funnel.js`：`completeLayer1/2/3` fire-and-forget PATCH，`inferStep()` 并行拉取，`reset()` fire-and-forget DELETE
  - `src/api/improvementPlan.js`：前端 API 模块
  - `ImprovementPlanServiceImplTest`：7 用例，全通过

### 10. 埋点与漏斗指标定义

- **问题**：无统一埋点规范，无法统计 Page 1→2→…→9 转化与流失
- **方案**：定义关键事件（`step_view`、`step_complete`、`login`、`report_export`），前端统一封装上报；在产品文档中写「漏斗指标定义」
- **涉及文件**：前端埋点工具类（新建）、产品文档

### 10. revokeSession 实现或文档化

- **问题**：`AuthServiceImpl:178` 为空实现（`// TODO MVP 暂不实现`），用户无法主动登出
- **方案**：实现 Redis 删除 SESSION_KEY；若暂不实现则在 openapi / auth 文档中明确「MVP 登出仅客户端清除 token」
- **涉及文件**：`AuthServiceImpl.java`、`openapi.yaml`

### 11. 通用脱敏工具提取

- **问题**：手机号脱敏逻辑分散在各 Service 中，未集中管理
- **方案**：提取为 `SensitiveDataUtil`（maskPhone / maskIdCard），可选增加 JSON 自动脱敏序列化器
- **涉及文件**：`common/util/SensitiveDataUtil.java`（新建）

---

## 已排除项（延期至 2.0 或无需处理）

| 项 | 排除理由 |
|---|---------|
| 未登录数据与登录合并 | MVP 用户路径为先登录再填，匿名草稿合并增加大量复杂度，先在 spec 约定即可 |
| 客服/反馈入口 | 「我的」页已有 feedback.vue，MVP 够用 |
| 敏感操作二次确认 | MVP 无批量删除接口，导出非破坏性操作，风险极低 |
| 前端 BASE_URL 环境化 | request.js 已通过 #ifdef 区分平台，多环境通过构建变量解决 |
| 日志 traceId 异步透传 | 已有 TraceIdFilter + MDC，MVP 无 MQ，异步场景极少 |
| 健康与依赖检查 | 已有 DeepSeekHealthIndicator，Spring Boot 自带 Redis/DB health |
| E2E 低分路径覆盖 | 补前端单测（funnel store 低分判断）性价比更高 |
| 种子用户流程文档 | mock-data.md 已有 5 个用户画像，优先级最低 |
| OpenAPI 与实现一致性 | 维护性工作，非改善点，日常开发中保持即可 |
| 前端硬编码色值迁入变量 | 仅 page9 下 3 处，影响面极小 |
| 报告模块规范不完整 | 实现已完成，规范补充为文档工作 |
| 文档清理（AGENT-TEAMS） | 不影响功能，有空顺手清理即可 |
