# API 前后端契约一致性检查清单

> 对比来源：
> - 前端：`youhuajia-app/src/api/*.js`
> - 后端：`ai-spec/contracts/openapi.yaml` + 实际 Controller 映射
> - 最后更新：2026-03-04

---

## 一、认证模块 `auth.js`

| # | 前端调用 | HTTP方法 | 请求体字段 | 后端实际路径 | 后端接收字段 | 状态 |
|---|----------|----------|-----------|-------------|-------------|------|
| A1 | `/auth/sms:send` | POST | `{ phone }` | `/api/v1/auth/sms:send` | `{ phone }` | ✅ 一致 |
| A2 | `/auth/sessions` | POST | `{ phone, code }` | `/api/v1/auth/sessions` | `{ phone, smsCode }` | ❌ **不一致** |
| A3 | `/auth/sessions:refresh` | POST | `{ refreshToken }` | `/api/v1/auth/sessions:refresh` | `(无 body，读 SecurityContext)` | ⚠️ 待确认 |
| A4 | `/auth/sessions:revoke` | POST | `(空)` | `/api/v1/auth/sessions:revoke` | `(无 body)` | ✅ 一致 |

### 不一致详情

**A2 — 登录请求字段名不匹配（高优先级）**

| 项 | 前端 (`auth.js`) | 后端 (`LoginRequest.java`) |
|----|-----------------|--------------------------|
| 验证码字段名 | `code` | `smsCode` |
| 影响 | 前端发送 `{phone, code}`，后端收到 `smsCode` 为 null，JSR 380 校验失败，返回 400 | |

**修复方案（二选一）：**
1. 后端：将 `LoginRequest.smsCode` 重命名为 `LoginRequest.code`
2. 前端：将 `auth.js` 中 `createSession` 改为发送 `{ phone, smsCode: code }`

---

## 二、债务模块 `debt.js`

| # | 前端调用 | HTTP方法 | 请求体字段 | 后端实际路径 | 后端接收字段 | 状态 |
|---|----------|----------|-----------|-------------|-------------|------|
| D1 | `/debts` | GET | `pageSize` (query) | `/api/v1/debts` | `pageSize` (query) | ✅ 一致 |
| D2 | `/debts` | POST | `{ requestId, debt }` | `/api/v1/debts` | `{ requestId, debt }` | ✅ 一致 |
| D3 | `/debts/{id}` | PATCH | `{ debt, updateMask }` | `/api/v1/debts/{debtId}` | `{ debt, updateMask }` | ✅ 一致 |
| D4 | `/debts/{id}` | DELETE | `(空)` | `/api/v1/debts/{debtId}` | `(无 body)` | ✅ 一致 |
| D5 | `/debts/{id}:confirm` | POST | `{}` | `/api/v1/debts/{debtId}:confirm` | `(无 body)` | ✅ 一致 |
| D6 | `/ocr-tasks` | POST (multipart) | `file` | `/api/v1/ocr-tasks` | `file` (MultipartFile) | ✅ 一致 |
| D7 | `/ocr-tasks/{id}` | GET | `(空)` | `/api/v1/ocr-tasks/{taskId}` | `(无 body)` | ✅ 一致 |
| D8 | `/ocr-tasks/{id}:confirm` | POST | `{ debt }` | `/api/v1/ocr-tasks/{taskId}:confirm` | `{ debt }` | ✅ 一致 |

---

## 三、计算引擎 `engine.js`

| # | 前端调用 | HTTP方法 | 请求体字段 | 后端实际路径 | 后端接收字段 | 状态 |
|---|----------|----------|-----------|-------------|-------------|------|
| E1 | `/engine/pressure:assess` | POST | `{ monthlyPayment, monthlyIncome }` | `/api/v1/engine/pressure:assess` | `{ monthlyPayment, monthlyIncome }` | ✅ 一致 |
| E2 | `/engine/apr:calculate` | POST | `{ principal, totalRepayment, loanDays }` | `/api/v1/engine/apr:calculate` | `{ principal, totalRepayment, loanDays }` | ✅ 一致 |
| E3 | `/engine/rate:simulate` | POST | `{ currentWeightedApr, targetApr, totalPrincipal, avgLoanDays, monthlyIncome }` | `/api/v1/engine/rate:simulate` | 同上 | ✅ 一致 |

---

## 四、财务画像 `profile.js`

| # | 前端调用 | HTTP方法 | 后端实际路径 | 状态 |
|---|----------|----------|-------------|------|
| P1 | `/finance-profiles/mine` | GET | `/api/v1/finance-profiles/mine` | ❌ **不一致** |
| P2 | `/finance-profiles/mine:calculate` | POST | `/api/v1/finance-profiles/mine:calculate` | ❌ **不一致** |

### 不一致详情

**P1/P2 — 路径后缀 `mine` vs `me`（高优先级）**

| 项 | 前端 (`profile.js`) | 后端 (`FinanceProfileController.java`) |
|----|--------------------|-----------------------------------------|
| 获取画像路径 | `/finance-profiles/mine` | `/finance-profiles/mine` |
| 触发计算路径 | `/finance-profiles/mine:calculate` | `/finance-profiles/mine:calculate` |
| 影响 | 前端调用返回 404 | |

**修复方案（二选一）：**
1. 后端：将 `@GetMapping("/finance-profiles/mine")` 改为 `/finance-profiles/mine`
2. 前端：将 `profile.js` 中 `mine` 改为 `me`

---

## 五、报告模块 `report.js`

| # | 前端调用 | HTTP方法 | 后端实际路径 | 状态 |
|---|----------|----------|-------------|------|
| R1 | `/reports:generate` | POST | `/api/v1/reports:generate` | ✅ 一致 |
| R2 | `/reports/{id}` | GET | `/api/v1/reports/{reportId}` | ✅ 一致 |
| R3 | `/reports/{id}:export` | POST | `/api/v1/reports/{reportId}:export` (`GET` in backend) | ⚠️ HTTP方法不一致 |

### 不一致详情

**R3 — 导出报告 HTTP 方法不一致（低优先级，功能不影响，幂等操作）**

| 项 | 前端 (`report.js`) | 后端 (`ReportController.java`) |
|----|--------------------|-------------------------------|
| 导出方法 | `POST /reports/{id}:export` | `GET /reports/{reportId}:export` |
| 影响 | 调用返回 405 Method Not Allowed | |

**修复方案（二选一）：**
1. 后端：将 `@GetMapping` 改为 `@PostMapping` (自定义方法应用 POST，符合 AIP-136)
2. 前端：将 `exportReport` 改为 GET 请求

---

## 六、响应体结构验证

| 响应类型 | openapi.yaml 定义字段 | 后端 DTO 字段 | 前端消费字段 | 状态 |
|----------|----------------------|-------------|-------------|------|
| `LoginResponse` | `accessToken, refreshToken, expiresIn, userId, newUser` | 同上 | `accessToken, refreshToken` | ✅ |
| `DebtResponse` | `name, creditor, debtType, principal, totalRepayment, apr, status, ...` | 同上 | `name` | ✅ |
| `FinanceProfileResponse` | `name, totalDebt, weightedApr, restructureScore, riskLevel, ...` | 同上 | `restructureScore, riskLevel, weightedApr` | ✅ |
| `AssessPressureResponse` | `pressureIndex, level, ratio, hint` | 同上 | `level, pressureIndex, ratio` | ✅ |
| `SimulateRateResponse` | `currentMonthlyPayment, targetMonthlyPayment, monthlySaving, ...` | 同上 | `monthlySaving` | ✅ |
| `ReportResponse` | `name, profileSnapshot, priorityList, actionPlan, aiSummary, riskWarnings` | 同上 | `name, actionPlan` | ✅ |

---

## 七、不一致项汇总

| 优先级 | 编号 | 描述 | 影响 | 修复难度 |
|--------|------|------|------|---------|
| 🔴 高 | A2 | 登录字段 `code` vs `smsCode` | 登录功能完全不可用 | 低（改一个字段名） |
| 🔴 高 | P1 | 画像路径 `mine` vs `me` | 画像页面 404 | 低（改路径） |
| 🔴 高 | P2 | 画像计算路径 `mine:calculate` vs `me:calculate` | 画像计算 404 | 低（改路径） |
| 🟡 中 | R3 | 导出 POST vs GET | 导出功能不可用 | 低（改 HTTP 方法） |
| 🟢 低 | A3 | refreshToken 请求体字段需确认 | 刷新 Token 可能异常 | 待确认 |

---

## 八、验证方法

### 自动化（Java）

```bash
# 运行 API 契约测试（Spring Boot 集成测试）
mvn test -Dtest="ApiContractTest" -Dsurefire.failIfNoSpecifiedTests=false
```

### 手动（Shell）

```bash
# 需先启动后端服务
./scripts/integration-test.sh http://localhost:8080
```

### 预期结果

修复以上4个不一致项后，`ApiContractTest` 中的 MISMATCH 测试应由"预期失败"转为"通过"。
具体来说，`should_detect_MISMATCH_*` 测试中的 `assertThat(...).isEqualTo(404)` 断言应改为 `isNotEqualTo(404)`。
