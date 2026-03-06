# client-spec.md — 移动客户端技术规范

> 本文件是客户端的完整实现规范。
> 与 user-journey.md（心理路径）、openapi.yaml（接口契约）配合使用。

---

## 一、技术选型：Vue 3 + uni-app

### 选型理由

| 维度 | Vue 3 + uni-app | Flutter | 决策 |
|------|----------------|---------|------|
| 语言 | JavaScript/TypeScript，前端通用 | Dart（需学新语言） | Vue 团队更容易上手 |
| 跨平台 | H5 + 微信小程序 + App，一套代码 | iOS + Android + Web | uni-app 覆盖小程序生态 |
| 生态 | npm 生态成熟，组件丰富 | pub.dev 生态较小 | npm 生态更成熟 |
| 开发效率 | 热更新快，SFC 单文件组件 | 编译较慢 | Vue SFC 开发体验好 |
| 小程序支持 | 原生支持微信小程序编译 | 不支持 | 国内场景必须支持小程序 |

### MVP 技术栈

```
Vue 3.4 + uni-app 3.0 (alpha)
状态管理: Pinia 2.1 + pinia-plugin-unistorage（跨端持久化）
网络层: uni.request 统一封装（支持 H5/小程序双端）
路由: pages.json 声明式路由 + uni.navigateTo / uni.switchTab
本地存储: uni.getStorageSync / uni.setStorageSync（Token、漏斗状态）
图表: Canvas API（RadarChart 自绘）
动画: requestAnimationFrame + CSS transition
样式: SCSS + 设计变量系统（variables.scss + mixins.scss）
构建: Vite 5.0 + @dcloudio/vite-plugin-uni
```

---

## 二、项目结构

```
youhuajia-app/
├── package.json                           # 依赖管理
├── vite.config.js                         # Vite 构建配置 + H5 代理
│
└── src/
    ├── main.js                            # 入口：createSSRApp + Pinia 挂载
    ├── App.vue                            # 根组件：onLaunch 初始化 Auth
    ├── pages.json                         # 声明式路由 + TabBar + 全局样式
    │
    ├── pages/                             # 页面目录（每页独立文件夹）
    │   ├── home/index.vue                 # TabBar 首页（漏斗入口）
    │   ├── mine/                          # TabBar 我的
    │   │   ├── index.vue                  # 个人中心
    │   │   ├── reports.vue                # 我的报告列表
    │   │   ├── feedback.vue               # 意见反馈
    │   │   └── about.vue                  # 关于优化家
    │   ├── auth/login.vue                 # 短信验证码登录
    │   ├── onboarding/index.vue           # 新用户引导页
    │   ├── page1-safe-entry/index.vue     # Page 1: 安全进入
    │   ├── page2-pressure-check/index.vue # Page 2: 压力检测
    │   ├── page3-debt-input/index.vue     # Page 3: 债务录入
    │   ├── page4-loss-report/index.vue    # Page 4: 损失可视化
    │   ├── page5-optimization/index.vue   # Page 5: 优化空间
    │   ├── page6-rate-simulator/index.vue # Page 6: 利率模拟器
    │   ├── page7-risk-assessment/index.vue# Page 7: 风险评估
    │   ├── page8-action-layers/           # Page 8: 分层行动
    │   │   ├── index.vue                  # 正常路径（score >= 60）
    │   │   ├── index-low-score.vue        # 低分路径（score < 60）
    │   │   └── preaudit.js                # 预审逻辑辅助
    │   ├── page9-companion/               # Page 9: 持续陪伴
    │   │   ├── index.vue
    │   │   └── components/
    │   │       └── ConsultCard.vue        # 咨询意向卡片
    │   ├── low-score/                     # 评分 < 60 特殊路径
    │   │   ├── credit-optimization.vue    # 替代 Page 5
    │   │   ├── credit-repair.vue          # 替代 Page 6
    │   │   └── improvement-plan.vue       # 替代 Page 8
    │   ├── terms/index.vue                # 用户服务协议
    │   └── privacy/index.vue              # 隐私保护说明
    │
    ├── stores/                            # Pinia 状态管理
    │   ├── index.js                       # createPinia + unistorage 插件
    │   ├── auth.js                        # 认证状态（token、refreshToken、phone）
    │   ├── funnel.js                      # 漏斗进度（score、step、actionLayers、checklist、pressure）
    │   ├── profile.js                     # 财务画像（profile 加载、利率模拟、评分模拟）
    │   └── debt.js                        # 债务管理（CRUD、OCR、APR 试算）
    │
    ├── api/                               # API 接口层
    │   ├── request.js                     # 统一请求封装（Token 注入、401 自动刷新、错误 toast）
    │   ├── auth.js                        # sendSms, createSession, refreshSession, revokeSession
    │   ├── engine.js                      # assessPressure, calculateApr, simulateRate, simulateScore, estimatePreAudit
    │   ├── debt.js                        # listDebts, createDebt, updateDebt, deleteDebt, confirmDebt, OCR 三件套
    │   ├── profile.js                     # getFinanceProfile, calculateFinanceProfile
    │   ├── report.js                      # generateReport, getReport, exportReport, listReports
    │   ├── consultation.js                # createConsultation（Page 9 咨询意向）
    │   └── income.js                      # batchCreateIncomes
    │
    ├── components/                        # 共享组件
    │   ├── YouhuaButton.vue               # 统一 CTA 按钮（primary/secondary/text，含 loading 状态）
    │   ├── AnimatedNumber.vue             # 数字滚动动画（easeOutCubic，可配格式化/前后缀）
    │   ├── ProgressBar.vue                # 漏斗进度条（1/9 节点 + 填充动画）
    │   ├── RadarChart.vue                 # 五维雷达图（Canvas 自绘，网格+数据多边形+数据点）
    │   └── SafeAreaBottom.vue             # 底部安全区占位
    │
    ├── styles/                            # 设计系统
    │   ├── variables.scss                 # 色彩、间距、圆角、字号、阴影、动效、毛玻璃
    │   └── mixins.scss                    # glass-card, card, page-bg, press-effect, input-field 等
    │
    └── static/                            # 静态资源
        └── tab/                           # TabBar 图标
            ├── home.png
            ├── home-active.png
            ├── mine.png
            └── mine-active.png
```

---

## 三、路由设计（pages.json 声明式路由）

### 3.1 路由配置

uni-app 使用 `src/pages.json` 声明所有路由，不支持动态路由。路由跳转使用 uni API：

```javascript
// 漏斗页面之间：navigateTo（可返回）
uni.navigateTo({ url: '/pages/page2-pressure-check/index' })

// TabBar 页面：switchTab
uni.switchTab({ url: '/pages/home/index' })

// 替换当前页（不可返回）：redirectTo
uni.redirectTo({ url: '/pages/low-score/credit-optimization' })

// 返回上一页
uni.navigateBack()
```

### 3.2 完整路由表

```
╔════════════════════════════════════════╦═══════════════════╦═══════════════════╗
║  路由路径                               ║ 导航栏             ║ 说明              ║
╠════════════════════════════════════════╬═══════════════════╬═══════════════════╣
║  pages/home/index                      ║ 自定义（custom）   ║ TabBar 首页       ║
║  pages/mine/index                      ║ "我的"            ║ TabBar 个人中心    ║
║  pages/mine/reports                    ║ "我的报告"        ║ 报告列表           ║
║  pages/mine/feedback                   ║ "意见反馈"        ║ 反馈表单           ║
║  pages/mine/about                      ║ "关于优化家"      ║ 关于页             ║
╠════════════════════════════════════════╬═══════════════════╬═══════════════════╣
║  pages/auth/login                      ║ "登录"            ║ 短信验证码登录     ║
║  pages/onboarding/index                ║ 自定义（custom）   ║ 新用户引导         ║
╠════════════════════════════════════════╬═══════════════════╬═══════════════════╣
║  pages/page1-safe-entry/index          ║ 自定义（custom）   ║ Page 1 安全进入    ║
║  pages/page2-pressure-check/index      ║ "快速检查"        ║ Page 2 压力检测    ║
║  pages/page3-debt-input/index          ║ "债务录入"        ║ Page 3 债务录入    ║
║  pages/page4-loss-report/index         ║ 自定义（custom）   ║ Page 4 损失可视化  ║
║  pages/page5-optimization/index        ║ "优化空间"        ║ Page 5 优化空间    ║
║  pages/page6-rate-simulator/index      ║ 自定义（custom）   ║ Page 6 利率模拟    ║
║  pages/page7-risk-assessment/index     ║ "风险评估"        ║ Page 7 风险评估    ║
║  pages/page8-action-layers/index       ║ "行动计划"        ║ Page 8 分层行动    ║
║  pages/page8-action-layers/index-low-score ║ "改善行动"    ║ Page 8 低分路径    ║
║  pages/page9-companion/index           ║ "我的进度"        ║ Page 9 持续陪伴    ║
╠════════════════════════════════════════╬═══════════════════╬═══════════════════╣
║  pages/low-score/credit-optimization   ║ "信用优化"        ║ 替代 Page 5       ║
║  pages/low-score/credit-repair         ║ "信用修复"        ║ 替代 Page 6       ║
║  pages/low-score/improvement-plan      ║ "改善计划"        ║ 替代 Page 8       ║
╠════════════════════════════════════════╬═══════════════════╬═══════════════════╣
║  pages/terms/index                     ║ "用户服务协议"    ║ 协议页             ║
║  pages/privacy/index                   ║ "隐私保护说明"    ║ 隐私页             ║
╚════════════════════════════════════════╩═══════════════════╩═══════════════════╝
```

### 3.3 TabBar 配置

```json
{
  "tabBar": {
    "color": "#94A3B8",
    "selectedColor": "#1B6DB2",
    "backgroundColor": "#FFFFFF",
    "borderStyle": "white",
    "list": [
      { "pagePath": "pages/home/index", "text": "首页" },
      { "pagePath": "pages/mine/index", "text": "我的" }
    ]
  }
}
```

### 3.4 登录时机

```
Page 1-2：无需登录（assessPressure 接口无需认证）
Page 3  ：首次需要登录（创建债务需要 userId）
Page 4-9：已登录状态
```

跳转登录时通过 URL 参数传递来源页：
```javascript
uni.navigateTo({ url: '/pages/auth/login?redirect=/pages/page3-debt-input/index' })
```

---

## 四、状态管理（Pinia + unistorage）

### 4.1 Store 初始化

```javascript
// stores/index.js
import { createPinia } from 'pinia'
import { createUnistorage } from 'pinia-plugin-unistorage'

const pinia = createPinia()
pinia.use(createUnistorage())  // 自动持久化到 uni storage

export default pinia
```

在 `main.js` 中挂载：

```javascript
import { createSSRApp } from 'vue'
import App from './App.vue'
import pinia from './stores/index.js'

export function createApp() {
  const app = createSSRApp(App)
  app.use(pinia)
  return { app }
}
```

### 4.2 Auth Store（认证状态）

```
文件: stores/auth.js
持久化: 手动管理（uni.getStorageSync/setStorageSync），不走 unistorage

状态:
  - token: string           — JWT Access Token
  - refreshToken: string    — JWT Refresh Token
  - phone: string           — 脱敏手机号
  - loggedIn: boolean       — 登录标记

计算属性:
  - isLoggedIn              — !!token && loggedIn

方法:
  - login(phone, code)      — 调 createSession，存储 token 对
  - logout()                — 调 revokeSession，清除存储
  - refresh()               — 调 refreshSession，刷新 accessToken
  - loadFromStorage()       — noop（兼容接口，初始化已在 ref 中完成）

特殊设计:
  - token 通过 uni.getStorageSync 在 ref 初始化时一次性读取
  - 不使用 pinia-plugin-unistorage 持久化（手动控制更精确）
```

### 4.3 Funnel Store（漏斗进度）

```
文件: stores/funnel.js
持久化: unistorage，持久化字段 = [score, currentStep, pressureIndex, pressureLevel, actionLayers, checklist]

状态:
  - score: number                 — 评分（来自 financeProfile.restructureScore）
  - currentStep: number           — 当前漏斗步骤（1-9）
  - financeProfile: object|null   — 完整财务画像数据
  - monthlyPayment: number        — Page 2 月供（默认 5000）
  - monthlyIncome: number         — Page 2 月收入（默认 7500）
  - pressureIndex: number         — Page 2 压力指数
  - pressureLevel: string         — Page 2 压力等级（HEALTHY/MODERATE/HEAVY/SEVERE）
  - actionLayers: object          — Page 8 三层完成状态 + reportId
  - checklist: object             — Page 9 四项清单勾选状态

计算属性:
  - isLowScore                    — score > 0 && score < 60
  - completedLayerCount           — 已完成的 action layer 数量

方法:
  - setScore(newScore)
  - setFinanceProfile(profile)    — 同步更新 profile + score
  - advanceStep(step)
  - updatePressure(index, level)
  - completeLayer1(reportId) / completeLayer2() / completeLayer3()
  - toggleChecklistItem(key)
  - reset()                       — 重置所有漏斗状态
```

### 4.4 Profile Store（财务画像）

```
文件: stores/profile.js
持久化: 无

状态:
  - profile: object|null          — 完整画像数据
  - loading / error               — 加载状态
  - simulationResult: object|null — 利率模拟结果
  - simulationLoading / simulationError
  - scoreSimResult / scoreSimLoading — 评分模拟结果

计算属性:
  - threeYearExtraInterest        — 三年多付利息
  - weightedApr                   — 加权年化利率
  - debtIncomeRatio               — 债务收入比
  - score                         — 重组评分

方法:
  - loadProfile()                 — GET 画像（含请求去重，多页面同时调只发一次）
  - triggerCalculation()          — POST 触发计算（返回完整画像，兜底补查）
  - doSimulateRate(targetApr)     — 利率模拟（静默降级：已有结果时失败不报错）
  - doSimulateScore(actions)      — What-if 评分模拟

特殊设计:
  - _loadPromise 去重：多个页面同时挂载时只发一次 HTTP 请求
  - 模拟失败时静默降级：保留上次结果，不清空页面数据
```

### 4.5 Debt Store（债务管理）

```
文件: stores/debt.js
持久化: 无

状态:
  - debts: Array                  — 债务列表
  - loading / error               — 加载状态
  - ocrTask: object|null          — OCR 任务状态 { status, taskId, recognizedDebt }

计算属性:
  - confirmedCount                — 已确认债务数（CONFIRMED | IN_PROFILE）
  - totalCount                    — 总债务数
  - estimatedSaving               — 估算潜在节省（APR > 18% 的超额利息 * 3年）

方法:
  - loadDebts()                   — 获取债务列表（含请求去重）
  - addDebt(debtData)             — 创建债务 + 自动 APR 试算
  - removeDebt(debtId)            — 删除债务
  - doConfirmDebt(debtId)         — 确认债务（DRAFT -> CONFIRMED）
  - startOcr()                    — 调用相机 -> 上传 -> 轮询 OCR 结果
  - confirmOcr(taskId, debtData)  — 确认 OCR 结果并创建债务
  - cancelOcr()                   — 取消 OCR 轮询

OCR 轮询机制:
  - 每 2 秒轮询一次 getOcrTask
  - 最多 30 次（60 秒超时）
  - 状态流转: UPLOADING -> PROCESSING -> COMPLETED/FAILED

幂等保护:
  - createDebt 携带 requestId（UUID4），遵循 AIP-155
```

---

## 五、API 层实现规范

### 5.1 请求封装（request.js）

```javascript
// 核心设计:
// 1. BASE_URL 根据平台条件编译:
//    H5:     '/api/v1'              — 走 Vite proxy 代理
//    小程序:  'http://host/api/v1'  — 直连后端
//
// 2. 自动注入 Bearer Token（从 uni.getStorageSync 读取）
//
// 3. 401 自动刷新:
//    - 并发防抖: 多个请求同时 401 只触发一次 refresh
//    - refreshingPromise 单例保证不重复刷新
//    - 刷新成功后自动重试原请求（skipRefresh 防死循环）
//    - 刷新失败: 清除存储 + 跳转登录页
//
// 4. 两种请求模式:
//    - request()       — 非 401 错误自动弹 uni.showToast
//    - requestSilent() — 不弹 toast（适合利率模拟、APR 预览等后台请求）
//
// 5. 超时: 默认 8s，可自定义（如 report:generate 用 30s）
//
// 6. 错误响应对齐 google.rpc.Status:
//    { error: { code, message, status, details } }
```

### 5.2 条件编译（H5 / 小程序）

```javascript
// uni-app 条件编译语法:
// #ifdef H5
return '/api/v1'              // H5 开发: vite proxy 代理到 localhost:8080
// #endif
// #ifndef H5
return `${DEV_API_HOST}/api/v1`  // 小程序: 直连后端地址
// #endif
```

### 5.3 接口映射表

```
╔═══════════════════════════════════════════════════════════════════════════╗
║  auth.js                                                               ║
╠═══════════════════════════╦══════════════════════════╦═════════════════ ╣
║  JS 方法                   ║ HTTP                     ║ 对应页面         ║
╠═══════════════════════════╬══════════════════════════╬═════════════════ ╣
║  sendSms(phone)           ║ POST /auth/sms:send      ║ 登录页           ║
║  createSession(phone,code)║ POST /auth/sessions      ║ 登录页           ║
║  refreshSession(token)    ║ POST /auth/sessions      ║ request.js 自动  ║
║                           ║      :refresh            ║                  ║
║  revokeSession()          ║ POST /auth/sessions      ║ 我的-登出        ║
║                           ║      :revoke             ║                  ║
╚═══════════════════════════╩══════════════════════════╩═════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  engine.js（无状态计算，前两个无需认证）                                     ║
╠═══════════════════════════╦══════════════════════════╦═════════════════ ╣
║  assessPressure(          ║ POST /engine/            ║ Page 2 压力检测  ║
║    payment, income)       ║   pressure:assess        ║ （无需登录）      ║
╠═══════════════════════════╬══════════════════════════╬═════════════════ ╣
║  calculateApr(            ║ POST /engine/            ║ Page 3 单笔试算  ║
║    principal,             ║   apr:calculate          ║ （requestSilent）║
║    totalRepayment,        ║                          ║                  ║
║    loanDays)              ║                          ║                  ║
╠═══════════════════════════╬══════════════════════════╬═════════════════ ╣
║  simulateRate(params)     ║ POST /engine/            ║ Page 6 利率模拟  ║
║                           ║   rate:simulate          ║ （requestSilent）║
╠═══════════════════════════╬══════════════════════════╬═════════════════ ╣
║  simulateScore(actions)   ║ POST /engine/            ║ Page 5 评分模拟  ║
║                           ║   score:simulate         ║ （requestSilent）║
╠═══════════════════════════╬══════════════════════════╬═════════════════ ╣
║  estimatePreAudit()       ║ POST /engine/            ║ Page 8 预审概率  ║
║                           ║   preaudit:estimate      ║ （requestSilent）║
╚═══════════════════════════╩══════════════════════════╩═════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  debt.js                                                               ║
╠═══════════════════════════╦══════════════════════════╦═════════════════ ╣
║  listDebts(pageSize)      ║ GET /debts               ║ Page 3 债务列表  ║
║  createDebt(debt, reqId)  ║ POST /debts              ║ Page 3 手动添加  ║
║  updateDebt(id, debt,     ║ PATCH /debts/{id}        ║ Page 3 编辑      ║
║    updateMask)            ║                          ║                  ║
║  deleteDebt(id)           ║ DELETE /debts/{id}       ║ Page 3 删除      ║
║  confirmDebt(id)          ║ POST /debts/{id}:confirm ║ Page 3 确认      ║
╠═══════════════════════════╬══════════════════════════╬═════════════════ ╣
║  createOcrTask(filePath)  ║ POST /ocr-tasks          ║ Page 3 拍照识别  ║
║                           ║ （uni.uploadFile）        ║                  ║
║  getOcrTask(taskId)       ║ GET /ocr-tasks/{id}      ║ Page 3 轮询结果  ║
║  confirmOcrTask(id, debt) ║ POST /ocr-tasks/{id}     ║ Page 3 确认 OCR  ║
║                           ║   :confirm               ║                  ║
╚═══════════════════════════╩══════════════════════════╩═════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  profile.js                                                            ║
╠═══════════════════════════╦══════════════════════════╦═════════════════ ╣
║  getFinanceProfile()      ║ GET /finance-profiles    ║ Page 4-5 画像    ║
║                           ║   /mine                  ║                  ║
║  calculateFinanceProfile()║ POST /finance-profiles   ║ Page 3->4 计算   ║
║                           ║   /mine:calculate        ║                  ║
╚═══════════════════════════╩══════════════════════════╩═════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  report.js                                                             ║
╠═══════════════════════════╦══════════════════════════╦═════════════════ ╣
║  generateReport()         ║ POST /reports:generate   ║ Page 8 生成报告  ║
║                           ║ （timeout: 30s）          ║                  ║
║  getReport(id)            ║ GET /reports/{id}        ║ Page 8 查看报告  ║
║  exportReport(id)         ║ GET /reports/{id}:export ║ Page 8 导出      ║
║  listReports(pageSize,    ║ GET /reports             ║ 我的-报告列表    ║
║    pageToken)             ║                          ║                  ║
╚═══════════════════════════╩══════════════════════════╩═════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  consultation.js                                                       ║
╠═══════════════════════════╦══════════════════════════╦═════════════════ ╣
║  createConsultation(      ║ POST /consultations      ║ Page 9 咨询意向  ║
║    { phone, consultType,  ║                          ║                  ║
║      remark })            ║                          ║                  ║
╚═══════════════════════════╩══════════════════════════╩═════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  income.js                                                             ║
╠═══════════════════════════╦══════════════════════════╦═════════════════ ╣
║  batchCreateIncomes(      ║ POST /incomes            ║ Page 3 收入录入  ║
║    incomes)               ║   :batchCreate           ║ （requestSilent）║
╚═══════════════════════════╩══════════════════════════╩═════════════════ ╝
```

---

## 六、9 页实现规范

### Page 1 -- 安全进入

```
文件: pages/page1-safe-entry/index.vue
路由: pages/page1-safe-entry/index
导航: 自定义（custom），无系统导航栏
背景色: #F8FAFE

布局:
  - 顶部区域：温和插画/图标（财务健康主题）
  - 中部标题："看看你是否正在多付利息"
  - 副标题："1分钟检查，不需要提供个人信息"
  - 底部 CTA 按钮：YouhuaButton "开始检查"
    @click → uni.navigateTo({ url: '/pages/page2-pressure-check/index' })

禁止:
  - 不出现"债务""负债""重组"等词
  - 不用红色
  - 不要求登录
```

### Page 2 -- 压力检测

```
文件: pages/page2-pressure-check/index.vue
导航: "快速检查"
状态管理: useFunnelStore（monthlyPayment, monthlyIncome, pressureIndex, pressureLevel）

核心组件:
  1. 月供滑块
     - 组件: <slider> (uni-app 内置)
     - 范围: 0 ~ 50000, 步进 500
     - 初始: 5000
     - 标签: "每月大约还款 ¥{value}"

  2. 收入区间选择
     - 组件: 一组 <view> 按钮
     - 选项: ["5千以下", "5千-1万", "1万-2万", "2万-5万", "5万以上"]
     - 映射: [3000, 7500, 15000, 35000, 75000]

  3. 压力仪表盘（实时联动）
     - 输入: pressureIndex (0-100)
     - 颜色: 0-40 绿色, 40-70 橙色, 70-100 深橙（不用红色）
     - 标签: HEALTHY -> "健康", MODERATE -> "偏高",
             HEAVY -> "较重", SEVERE -> "需关注"

API 调用:
  滑块 onChange 时 debounce，调用:
    assessPressure(monthlyPayment, monthlyIncome)
  响应实时更新仪表盘。

CTA: "查看详细分析"
  @click → 如果未登录 → uni.navigateTo('/pages/auth/login?redirect=...')
           如果已登录 → uni.navigateTo('/pages/page3-debt-input/index')
```

### Page 3 -- 债务录入

```
文件: pages/page3-debt-input/index.vue
导航: "债务录入"
状态管理: useDebtStore（debts, ocrTask, estimatedSaving）

布局:
  - 顶部统计栏: "已录入 {n} 笔，已发现 ¥{saving} 潜在节省"
  - 债务卡片列表
  - 底部添加方式按钮:
      [拍照识别]  [手动添加]

三种录入交互:
  1. 手动添加 → uni.showModal 或页内表单
     字段: creditorName, productType, principal, totalRepayment, loanDays
     每笔自动调 calculateApr 试算显示 APR

  2. 拍照识别 → debtStore.startOcr()
     流程: uni.chooseImage(camera) -> createOcrTask -> pollOcr(2s/次, 最多30次)
          -> 成功后展示预填结果 -> confirmOcr -> 自动加入列表

关键约束:
  - 支持"先录一笔看效果"
  - 至少录入 1 笔才能进入 Page 4
  - createDebt 携带 requestId（UUID4 幂等键）

CTA: "查看分析报告"
  @click → profileStore.triggerCalculation()
         → uni.navigateTo('/pages/page4-loss-report/index')
```

### Page 4 -- 损失可视化

```
文件: pages/page4-loss-report/index.vue
导航: 自定义（custom）
背景色: #F8FAFE
状态管理: useProfileStore（profile, threeYearExtraInterest, weightedApr）

数据来源: profileStore.loadProfile() 或 triggerCalculation() 已加载

布局:
  1. 核心冲击数字（占屏幕 40%）
     "如果维持当前结构"
     "3 年将多支付"
     <AnimatedNumber :value="threeYearExtraInterest" prefix="¥" />
     字号大，颜色 $accent（橙色）
     "相当于 {n} 个月房租"

  2. 对比卡片
     卡片 A: "当前加权利率 / 市场均值"
     卡片 B: "月供占收入比 / 健康线"
     卡片 C: "债务笔数 / 高息笔数"

  3. 绝对没有"申请"按钮
     CTA 只有: "看看我的优化空间"
     @click → 根据 score 走不同路径:
       score >= 60 → '/pages/page5-optimization/index'
       score < 60  → '/pages/low-score/credit-optimization'

颜色约束:
  - 不用红色/警告图标
  - 冲击数字用 $accent（橙色）
  - 背景用浅蓝/浅灰
```

### Page 5 -- 优化空间

```
文件: pages/page5-optimization/index.vue
导航: "优化空间"
状态管理: useProfileStore + useFunnelStore

核心: 第一句话必须正面

布局:
  1. 顶部大字: "好消息是，你有优化空间。"
     颜色 $positive（翡翠绿）

  2. 确定性卡片:
     卡片 A: "成功概率" + 圆环进度
     卡片 B: "月供可降低约 ¥{saving}/月"
     卡片 C: "分 {phases} 步完成"

  3. 五维雷达图 <RadarChart :dimensions="scoreDimensions" />
     维度: 利率健康度, 结构合理度, 还款能力, 征信状况, 优化潜力
     数据来自 financeProfile

CTA: "模拟一下效果"
  @click → uni.navigateTo('/pages/page6-rate-simulator/index')

评分 < 60 分支:
  在 Page 4 已经路由到 /pages/low-score/credit-optimization
  展示: "当前更适合优化信用结构" + 30天行动计划预览
```

### Page 6 -- 利率模拟器（最核心交互）

```
文件: pages/page6-rate-simulator/index.vue
导航: 自定义（custom）
背景色: #F8FAFE
状态管理: useProfileStore（doSimulateRate, simulationResult, simulationLoading）

核心组件: 利率滑块
  - 左端: 当前加权 APR（如 24%），标签 "当前"
  - 右端: 最低可能 APR（如 6%），标签 "最优"
  - 用户拖动 → 目标 APR 变化

实时显示面板（随滑块联动）:
  ┌─────────────────────────────────┐
  │  月供变化    ¥24,800 → ¥18,200  │  ← AnimatedNumber
  │  三年节省    ¥237,600            │  ← AnimatedNumber
  │  月供占收入  65% → 48%           │  ← AnimatedNumber
  └─────────────────────────────────┘

API 调用:
  滑块 onChange debounce，调用:
    profileStore.doSimulateRate(targetApr)
  响应实时更新三个数字。
  失败时静默降级（保留上次结果）。

底部 disclaimer:
  "实际利率取决于个人信用状况和金融机构审核"
  字号小，颜色 $text-tertiary

CTA: "了解风险"
  @click → uni.navigateTo('/pages/page7-risk-assessment/index')

评分 < 60 分支:
  在 Page 4 已经路由到 /pages/low-score/credit-repair
  替代利率模拟器，展示信用修复路线图
```

### Page 7 -- 风险评估

```
文件: pages/page7-risk-assessment/index.vue
导航: "风险评估"
状态: 纯展示页面

布局: Q&A 列表（折叠面板）

FAQ 数据:
  Q1: "会不会查我的征信？"
  A1: "优化方案评估阶段不会查询征信。仅在您确认正式申请后，
       合作金融机构才会在获得您授权的情况下查询。"

  Q2: "会不会影响我的信用评分？"
  A2: "方案评估不产生任何征信记录。正式申请阶段的征信查询
       属于正常贷款申请，影响可控。"

  Q3: "如果方案不适合我怎么办？"
  A3: "评估阶段完全免费且无风险。如果当前不适合优化，
       我们会提供免费的信用改善建议。"

  Q4: "需要支付费用吗？"
  A4: "评估和方案制定完全免费。仅在您确认执行且成功后，
       才按节省金额的一定比例收取服务费。"

设计约束:
  - 每个答案具体，不含糊
  - 不说"一般不会"，说"在XX情况下不会"

CTA: "开始准备"
  @click → score >= 60 → '/pages/page8-action-layers/index'
           score < 60  → '/pages/page8-action-layers/index-low-score'
```

### Page 8 -- 分层行动

```
文件: pages/page8-action-layers/index.vue（正常路径）
      pages/page8-action-layers/index-low-score.vue（低分路径）
      pages/page8-action-layers/preaudit.js（预审辅助逻辑）
导航: "行动计划" / "改善行动"
状态管理: useFunnelStore（actionLayers, completeLayer1/2/3）

正常路径（score >= 60）:
  Layer 1: "看看申请需要准备什么"
    操作: 调 generateReport() 生成资料清单（timeout: 30s）
    完成后: completeLayer1(reportId), 解锁 Layer 2

  Layer 2: "一键整理你的申请资料"
    操作: 调 getReport(reportId) 获取结构化文档
    完成后: completeLayer2(), 解锁 Layer 3

  Layer 3: "预审一下通过概率"
    操作: 调 estimatePreAudit() 获取通过概率估算
    完成后: completeLayer3()

  Layer 4: "确认提交申请"（灰色, 标注 "即将上线"）
    MVP 阶段不可点击

  进度条: ProgressBar 1/4 -> 2/4 -> 3/4

低分路径（score < 60）:
  Layer 1: "生成 30 天改善计划"
  Layer 2: "设置还款提醒"
  Layer 3: "30 天后重新评估"

CTA: "查看我的行动计划"
  @click → uni.navigateTo('/pages/page9-companion/index')
```

### Page 9 -- 持续陪伴

```
文件: pages/page9-companion/index.vue
      pages/page9-companion/components/ConsultCard.vue
导航: "我的进度"
状态管理: useFunnelStore（checklist, toggleChecklistItem）

布局:
  1. 顶部正面强化: "你已经迈出了第一步"

  2. Checklist（可勾选，持久化）:
     - organizeStatements:    整理所有账单
     - confirmPaymentDates:   确认各债务最低还款日
     - prioritizeHighApr:     优先偿还高息债务
     - reassessIn30Days:      30天后重新评估

  3. ConsultCard 咨询意向卡片:
     收集用户咨询意向（phone, consultType, remark）
     调用 createConsultation 提交

这不是结果页，是陪伴页。
用户可以反复回来查看进度。
```

---

## 七、共享组件规范

### YouhuaButton

```
文件: components/YouhuaButton.vue
类型: primary（渐变蓝 + 阴影）| secondary（半透明蓝底）| text（纯文字）
Props: text(必填), type, disabled, loading
事件: @click
loading 状态: 显示旋转环 + "处理中..."
尺寸: 高度 100rpx，圆角 pill（200rpx）
按压效果: scale(0.97)
```

### AnimatedNumber

```
文件: components/AnimatedNumber.vue
Props: value, duration(2000ms), formatter, prefix, suffix, color, fontSize, fontWeight
动画: requestAnimationFrame + easeOutCubic 缓动
特性: 从 0 滚动到目标值，支持自定义格式化（默认 toLocaleString('zh-CN')）
字体: tabular-nums（等宽数字，防止滚动时抖动）
```

### ProgressBar

```
文件: components/ProgressBar.vue
Props: current(1-9), total(默认9)
显示: 进度轨道 + 填充条 + 节点圆点 + "current/total" 标签
样式: 轨道 6rpx 灰色，填充 primary-gradient，当前节点带光晕
```

### RadarChart

```
文件: components/RadarChart.vue
Props: dimensions(数组，默认五维), size(280), strokeColor, fillColor
实现: uni.createCanvasContext 自绘
绘制: 5 层背景网格 + 轴线 + 数据多边形填充 + 数据点
标签: absolute 定位在 canvas 外围
```

### SafeAreaBottom

```
文件: components/SafeAreaBottom.vue
作用: 底部安全区占位（iPhone 底部横条）
高度: env(safe-area-inset-bottom)，最小 32rpx
```

---

## 八、设计系统（SCSS 变量）

### 8.1 色彩系统（variables.scss）

```scss
// 主色 — 沉稳深蓝，安全感
$primary: #1B6DB2;
$primary-dark: #134E82;
$primary-light: #E3F0FA;
$primary-glass: rgba(27, 109, 178, 0.08);
$primary-gradient: linear-gradient(135deg, #3A9BDC 0%, #1B6DB2 50%, #134E82 100%);

// 强调色 — 温暖琥珀，数据冲击（不是红色）
$accent: #D97B1A;
$accent-light: #FEF3E2;
$accent-glass: rgba(217, 123, 26, 0.06);
$accent-gradient: linear-gradient(135deg, #F2A94B 0%, #D97B1A 100%);

// 正面色 — 翡翠绿，好消息
$positive: #0FA968;
$positive-light: #E6F9F0;
$positive-glass: rgba(15, 169, 104, 0.06);
$positive-gradient: linear-gradient(135deg, #34D58C 0%, #0FA968 100%);

// 禁止使用红色和告警黄
// $danger: #E53935;  ← 禁用
// $warning: #FF9800; ← 禁用

// 中性色 — 温暖灰阶
$text-primary: #0F172A;
$text-secondary: #64748B;
$text-tertiary: #94A3B8;
$text-inverse: #FFFFFF;

// 表面与背景
$background: #F5F7FA;
$background-warm: #FAF8F5;
$surface: #FFFFFF;
$surface-glass: rgba(255, 255, 255, 0.72);
$surface-glass-border: rgba(255, 255, 255, 0.45);
$divider: #E8ECF1;
$divider-light: #F1F5F9;
```

### 8.2 间距（8pt 网格，rpx 单位）

```scss
$spacing-xs: 8rpx;    $spacing-sm: 16rpx;   $spacing-md: 24rpx;
$spacing-lg: 32rpx;   $spacing-xl: 40rpx;   $spacing-2xl: 48rpx;
$spacing-3xl: 64rpx;
```

### 8.3 圆角

```scss
$radius-xs: 8rpx;     $radius-sm: 12rpx;    $radius-md: 20rpx;
$radius-lg: 28rpx;    $radius-xl: 36rpx;    $radius-2xl: 48rpx;
$radius-pill: 200rpx;   // 按钮、标签
```

### 8.4 字号

```scss
$font-xs: 22rpx;      $font-sm: 26rpx;      $font-md: 30rpx;
$font-lg: 34rpx;      $font-xl: 44rpx;      $font-xxl: 60rpx;
$font-display: 80rpx; $font-hero: 96rpx;    // 冲击数字
```

### 8.5 阴影（多层景深）

```scss
$shadow-xs:  0 1rpx 4rpx rgba(15, 23, 42, 0.04);
$shadow-sm:  0 2rpx 8rpx ..., 0 4rpx 16rpx ...;
$shadow-md:  0 4rpx 12rpx ..., 0 8rpx 32rpx ...;
$shadow-lg:  0 8rpx 24rpx ..., 0 16rpx 48rpx ...;
$shadow-xl:  0 12rpx 36rpx ..., 0 24rpx 64rpx ...;
$shadow-primary: 0 8rpx 32rpx rgba(27, 109, 178, 0.28);  // 主色按钮
$shadow-accent:  0 8rpx 32rpx rgba(217, 123, 26, 0.20);   // 强调按钮
```

### 8.6 动效

```scss
$transition-fast:   0.15s cubic-bezier(0.25, 0.46, 0.45, 0.94);
$transition-normal: 0.3s  cubic-bezier(0.25, 0.46, 0.45, 0.94);
$transition-spring: 0.5s  cubic-bezier(0.34, 1.56, 0.64, 1);      // 弹性
$transition-smooth: 0.4s  cubic-bezier(0.22, 1, 0.36, 1);          // 平滑
```

### 8.7 常用 Mixins（mixins.scss）

```scss
@mixin glass-card     // 毛玻璃卡片（surface + 圆角 + 边框 + 阴影）
@mixin card           // 实底卡片（surface + radius-lg + padding-lg + shadow-sm）
@mixin card-elevated  // 悬浮卡片（强调用，shadow-lg）
@mixin page-bg        // 页面背景渐变（三色渐变 168deg）
@mixin press-effect   // 弹性按压（scale(0.97) + opacity(0.88)）
@mixin bottom-bar     // 底部悬浮栏（含 safe-area-inset-bottom）
@mixin input-field    // 输入框（88rpx 高 + focus 边框变色）
@mixin badge($bg, $color)  // 标签/徽章
@mixin metric-number($color)  // 数据高亮数字（font-xxl + weight-black + tabular-nums）
@mixin gradient-text($gradient)  // 渐变文字（background-clip）
@mixin flex-center    // Flex 居中
@mixin text-ellipsis  // 文字截断
@mixin page-padding   // 页面安全内边距
```

---

## 九、构建与开发

### 9.1 Vite 配置（vite.config.js）

```javascript
import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],

  // H5 开发代理
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },

  // 生产构建
  build: {
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('vue') || id.includes('pinia')) return 'vendor'
          }
        },
      },
    },
    minify: 'terser',
    terserOptions: {
      compress: { drop_console: true, drop_debugger: true },
    },
    chunkSizeWarningLimit: 500,
  },

  // SCSS 配置
  css: {
    preprocessorOptions: {
      scss: {
        silenceDeprecations: ['legacy-js-api'],
      },
    },
  },
})
```

### 9.2 开发命令

```bash
# H5 开发（带 Vite 热更新 + 后端代理）
npm run dev:h5

# 微信小程序开发
npm run dev:mp-weixin

# H5 生产构建
npm run build:h5

# 微信小程序生产构建
npm run build:mp-weixin
```

### 9.3 依赖清单（package.json）

```json
{
  "dependencies": {
    "vue": "^3.4",
    "pinia": "^2.1",
    "@dcloudio/uni-app": "^3.0.0-alpha",
    "@dcloudio/uni-components": "^3.0.0-alpha",
    "@dcloudio/uni-h5": "^3.0.0-alpha",
    "@dcloudio/uni-mp-weixin": "^3.0.0-alpha",
    "pinia-plugin-unistorage": "^0.1.2"
  },
  "devDependencies": {
    "@dcloudio/types": "^3.4",
    "@dcloudio/uni-automator": "^3.0.0-alpha",
    "@dcloudio/vite-plugin-uni": "^3.0.0-alpha",
    "sass": "^1.69",
    "vite": "^5.0",
    "typescript": "^5.3"
  }
}
```

---

## 十、全局样式（App.vue）

```vue
<script setup>
import { onLaunch } from '@dcloudio/uni-app'
import { useAuthStore } from './stores/auth.js'

onLaunch(() => {
  const authStore = useAuthStore()
  authStore.loadFromStorage()
})
</script>

<style lang="scss">
@use './styles/variables.scss' as *;

page {
  background-color: $background;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  color: $text-primary;
  font-size: 28rpx;
  line-height: 1.6;
}

* { -webkit-tap-highlight-color: transparent; }
</style>
```

---

## 十一、客户端前端禁止事项

| 编号 | 禁止事项 | 原因 |
|------|----------|------|
| C-01 | 金额显示用 float toString()，必须用 toLocaleString 或 AnimatedNumber formatter | 精度 + 千分位 |
| C-02 | 不在前端做 APR 计算，所有计算调后端 /engine | 金融计算必须后端确定性执行 |
| C-03 | 页面文案不能出现恐慌性表达（"问题严重""赶紧行动"） | 违反 user-journey 约束 |
| C-04 | Page 4 不能出现"立即申请"按钮 | 违反渐进式漏斗设计 |
| C-05 | 评分 < 60 的用户不能展示"申请失败""不符合条件" | 一次失败体验 = 永久流失 |
| C-06 | Page 2 输入控件用 Slider/选择，不用 TextField | 降低用户输入负担 |
| C-07 | 不在客户端存储敏感数据（token 除外） | 安全合规 |
| C-08 | 所有 HTTP 请求必须通过 request.js，不直接调 uni.request | 统一 Token 注入 + 错误处理 |
| C-09 | 不使用红色或告警黄色 | 设计系统禁止，用橙色替代 |
| C-10 | 组件中不直接引用色值硬编码，必须用 $variables | 设计一致性 |

---

## 十二、命名规范

```
文件名:   kebab-case（page1-safe-entry/index.vue, YouhuaButton.vue 组件用 PascalCase）
组件名:   PascalCase（YouhuaButton, AnimatedNumber, RadarChart）
变量/方法: camelCase（monthlyPayment, doSimulateRate）
Store:    use{Name}Store（useAuthStore, useFunnelStore）
API 方法: camelCase 动词开头（getFinanceProfile, createDebt, assessPressure）
CSS 类名: kebab-case（.youhua-btn, .progress-wrap, .radar-label）
SCSS 变量: kebab-case 带 $ 前缀（$primary, $spacing-md, $font-xl）
```

---

## 十三、与 ai-spec 的映射关系

| 本文件章节 | 依赖的 ai-spec 文件 | 关系 |
|-----------|-------------------|------|
| 路由设计 | user-journey.md 第三章 | 9 页 1:1 映射 |
| API 层 | contracts/openapi.yaml | 全部接口覆盖 |
| 色彩约束 | user-journey.md 第六章 | 禁止表达 -> 禁止颜色 |
| 评分分支 | engine/scoring-model.md | score < 60 走特殊路径 |
| 文案约束 | user-journey.md 第六章 | CTA 按钮文案严格遵循 |
| 数据模型 | domain/entities.md | API 响应 1:1 对应 |
| 错误处理 | contracts/error-codes.md | request.js 统一映射 |
