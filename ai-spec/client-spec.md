# client-spec.md — 移动客户端技术规范

> 本文件是客户端的完整实现规范。
> 与 user-journey.md（心理路径）、openapi.yaml（接口契约）配合使用。

---

## 一、技术选型：Flutter（非 React Native）

### 选型理由

| 维度 | Flutter | React Native | 决策 |
|------|---------|-------------|------|
| 语言 | Dart（类 Java，强类型，OOP） | TypeScript（需学 React） | ✅ Dart 对 Java 开发者零门槛 |
| 跨平台 | iOS + Android + Web 一套代码 | iOS + Android | ✅ Flutter 多一个 Web 端 |
| UI 一致性 | 自绘引擎，双端完全一致 | 原生组件，双端有差异 | ✅ 9 页漏斗需要精确控制 |
| 动画 | 原生支持，性能好 | 需要桥接 | ✅ 利率模拟器滑块需要流畅动画 |
| Claude Code | Dart 生成质量好 | 也不错 | 平手 |

### MVP 技术栈

```
Flutter 3.x + Dart 3.x
状态管理: Riverpod 2.x（类型安全，适合后端思维）
网络层: Dio + Retrofit（类似 Java 的 OkHttp + Retrofit）
路由: GoRouter（声明式，类似 Spring MVC 路由）
本地存储: SharedPreferences（Token）+ Hive（草稿缓存）
图表: fl_chart（压力仪表盘、雷达图）
动画: Flutter 内置 AnimationController
```

---

## 二、项目结构

```
lib/
├── main.dart                          # 入口
├── app.dart                           # MaterialApp + GoRouter
│
├── core/                              # 基础设施层
│   ├── network/
│   │   ├── api_client.dart            # Dio 配置（baseUrl、拦截器、Token 注入）
│   │   ├── api_exception.dart         # 统一异常（映射 ErrorResponse）
│   │   └── token_manager.dart         # JWT 存储、刷新、过期检测
│   ├── theme/
│   │   ├── app_colors.dart            # 色彩系统（温和蓝/橙，禁止红色告警）
│   │   ├── app_text_styles.dart       # 字体规范
│   │   └── app_theme.dart             # ThemeData
│   └── utils/
│       ├── formatters.dart            # 金额格式化（¥82,400）、百分比（24.0%）
│       └── validators.dart            # 输入校验
│
├── features/                          # 按功能模块组织（匹配后端包结构）
│   ├── auth/                          # 认证模块
│   │   ├── data/
│   │   │   ├── auth_api.dart          # sendSms, createSession, refreshSession
│   │   │   └── auth_repository.dart
│   │   ├── domain/
│   │   │   └── auth_state.dart        # 登录状态枚举
│   │   └── presentation/
│   │       └── sms_login_page.dart    # 验证码登录页
│   │
│   ├── assessment/                    # 评估漏斗（Page 1-4，核心流程）
│   │   ├── data/
│   │   │   ├── engine_api.dart        # assessPressure, calculateApr
│   │   │   ├── debt_api.dart          # CRUD + OCR
│   │   │   └── assessment_repository.dart
│   │   ├── domain/
│   │   │   ├── pressure_result.dart   # 压力指数模型
│   │   │   ├── debt_draft.dart        # 债务草稿（本地缓存）
│   │   │   └── loss_report.dart       # 损失可视化数据
│   │   └── presentation/
│   │       ├── page1_safe_entry.dart       # 安全进入
│   │       ├── page2_pressure_check.dart   # 压力检测
│   │       ├── page3_debt_input.dart       # 债务录入
│   │       ├── page4_loss_report.dart      # 损失可视化
│   │       └── widgets/
│   │           ├── pressure_gauge.dart     # 仪表盘组件
│   │           ├── debt_input_card.dart     # 单笔债务卡片
│   │           ├── ocr_camera_sheet.dart    # OCR 拍照底部弹窗
│   │           └── loss_counter.dart       # 数字滚动动画
│   │
│   ├── optimization/                  # 优化方案（Page 5-7）
│   │   ├── data/
│   │   │   ├── profile_api.dart       # getFinanceProfile, calculateFinanceProfile
│   │   │   └── engine_api.dart        # simulateRate
│   │   ├── domain/
│   │   │   ├── optimization_result.dart
│   │   │   └── rate_simulation.dart
│   │   └── presentation/
│   │       ├── page5_optimization.dart     # 优化空间
│   │       ├── page6_rate_simulator.dart   # 利率模拟器
│   │       ├── page7_risk_assessment.dart  # 风险评估
│   │       └── widgets/
│   │           ├── rate_slider.dart         # 阻尼滑块（核心组件）
│   │           ├── savings_counter.dart     # 实时节省金额
│   │           ├── radar_chart.dart         # 五维评分雷达图
│   │           └── faq_accordion.dart       # Q&A 折叠面板
│   │
│   ├── action/                        # 行动层（Page 8-9）
│   │   ├── data/
│   │   │   ├── report_api.dart        # generateReport, exportReport
│   │   │   └── action_repository.dart
│   │   ├── domain/
│   │   │   ├── action_layer.dart      # 四层行动枚举
│   │   │   └── checklist_item.dart
│   │   └── presentation/
│   │       ├── page8_action_layers.dart    # 分层行动
│   │       ├── page9_companion.dart        # 持续陪伴
│   │       └── widgets/
│   │           ├── layer_progress.dart      # 1/4 → 2/4 进度条
│   │           ├── checklist_tile.dart       # 30/60/90天清单
│   │           └── credit_repair_roadmap.dart # 评分<60 路线图
│   │
│   └── low_score/                     # 评分<60 特殊路径
│       └── presentation/
│           ├── credit_optimization_page.dart  # 替代 Page 5
│           ├── credit_repair_page.dart        # 替代 Page 6
│           └── improvement_plan_page.dart     # 替代 Page 8
│
├── shared/                            # 共享组件
│   ├── widgets/
│   │   ├── youhua_button.dart         # 统一按钮（CTA 文案由 user-journey 约束）
│   │   ├── youhua_scaffold.dart       # 统一页面骨架（进度条 + 返回）
│   │   ├── animated_number.dart       # 数字滚动动画
│   │   └── safe_area_bottom.dart      # 底部安全区
│   └── providers/
│       ├── auth_provider.dart         # 全局登录状态
│       └── funnel_provider.dart       # 漏斗进度状态（当前页、评分）
│
└── routes/
    └── app_router.dart                # GoRouter 路由表
```

---

## 三、路由设计（匹配 9 页漏斗）

```dart
// routes/app_router.dart

final router = GoRouter(
  initialLocation: '/funnel/entry',
  routes: [
    // ========== 漏斗流程（核心） ==========
    GoRoute(
      path: '/funnel/entry',               // Page 1: 安全进入
      builder: (_, __) => const Page1SafeEntry(),
    ),
    GoRoute(
      path: '/funnel/pressure',            // Page 2: 压力检测
      builder: (_, __) => const Page2PressureCheck(),
    ),
    GoRoute(
      path: '/funnel/debts',              // Page 3: 债务录入
      builder: (_, __) => const Page3DebtInput(),
      // ⚠️ 此页需要登录（前两页不需要）
      redirect: (ctx, state) => authGuard(ctx, state),
    ),
    GoRoute(
      path: '/funnel/loss-report',        // Page 4: 损失可视化
      builder: (_, __) => const Page4LossReport(),
    ),
    GoRoute(
      path: '/funnel/optimization',       // Page 5: 优化空间
      builder: (_, state) {
        // 根据评分走不同路径
        final score = state.extra as int? ?? 0;
        return score >= 60
            ? const Page5Optimization()
            : const CreditOptimizationPage();  // 评分<60 特殊路径
      },
    ),
    GoRoute(
      path: '/funnel/simulator',          // Page 6: 利率模拟器
      builder: (_, state) {
        final score = state.extra as int? ?? 0;
        return score >= 60
            ? const Page6RateSimulator()
            : const CreditRepairPage();  // 评分<60: 信用修复路线图
      },
    ),
    GoRoute(
      path: '/funnel/risk',              // Page 7: 风险评估
      builder: (_, __) => const Page7RiskAssessment(),
    ),
    GoRoute(
      path: '/funnel/action',            // Page 8: 分层行动
      builder: (_, state) {
        final score = state.extra as int? ?? 0;
        return score >= 60
            ? const Page8ActionLayers()
            : const ImprovementPlanPage();  // 评分<60: 30天改善计划
      },
    ),
    GoRoute(
      path: '/funnel/companion',          // Page 9: 持续陪伴
      builder: (_, __) => const Page9Companion(),
    ),

    // ========== 认证 ==========
    GoRoute(
      path: '/auth/login',
      builder: (_, __) => const SmsLoginPage(),
    ),
  ],
);
```

### 登录时机

```
Page 1-2：无需登录（assessPressure 接口无需认证）
Page 3  ：首次需要登录（创建债务需要 userId）
Page 4-9：已登录状态
```

---

## 四、API 层实现规范

### 4.1 Dio 客户端配置

```dart
// core/network/api_client.dart
// 
// 对接 openapi.yaml 的基础配置
// baseUrl = /api/v1
// 
// 拦截器:
//   1. AuthInterceptor: 自动注入 Bearer Token
//   2. RefreshInterceptor: 401 时自动调 refreshSession，重试原请求
//   3. ErrorInterceptor: 将后端 ErrorResponse 转为 ApiException
//
// ErrorResponse 格式（对齐 google.rpc.Status）:
//   { "error": { "code": 400, "status": "INVALID_ARGUMENT", "message": "...", "details": [...] } }
//
// 超时配置:
//   connectTimeout: 10s
//   receiveTimeout: 30s（OCR 接口可能慢）
//   sendTimeout: 30s
```

### 4.2 接口映射表（openapi.yaml → Dart API 类）

```
╔═══════════════════════════════════════════════════════════════════════════╗
║  auth_api.dart                                                          ║
╠═══════════════════════╦═══════════════════════╦════════════════════════ ╣
║  Dart 方法             ║ HTTP                  ║ 对应页面                ║
╠═══════════════════════╬═══════════════════════╬════════════════════════ ╣
║  sendSms(phone)       ║ POST /auth/sms:send   ║ 登录页                  ║
║  createSession(code)  ║ POST /auth/sessions   ║ 登录页                  ║
║  refreshSession()     ║ POST /auth/sessions   ║ 拦截器自动              ║
║  revokeSession()      ║ POST /auth/sessions   ║ 设置页                  ║
╚═══════════════════════╩═══════════════════════╩════════════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  engine_api.dart（无状态，无需认证前两个）                                   ║
╠═══════════════════════╦═══════════════════════╦════════════════════════ ╣
║  assessPressure(      ║ POST /engine/         ║ Page 2 压力检测          ║
║    monthlyPayment,    ║   pressure:assess     ║ （无需登录）              ║
║    monthlyIncome)     ║                       ║                         ║
╠═══════════════════════╬═══════════════════════╬════════════════════════ ╣
║  simulateRate(        ║ POST /engine/         ║ Page 6 利率模拟器        ║
║    currentApr,        ║   rate:simulate       ║                         ║
║    targetApr,         ║                       ║                         ║
║    totalPrincipal...) ║                       ║                         ║
╠═══════════════════════╬═══════════════════════╬════════════════════════ ╣
║  calculateApr(        ║ POST /engine/         ║ Page 3 单笔试算          ║
║    principal,         ║   apr:calculate       ║                         ║
║    totalRepayment,    ║                       ║                         ║
║    loanDays)          ║                       ║                         ║
╚═══════════════════════╩═══════════════════════╩════════════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  debt_api.dart                                                          ║
╠═══════════════════════╦═══════════════════════╦════════════════════════ ╣
║  listDebts(pageToken) ║ GET /debts            ║ Page 3 债务列表          ║
║  createDebt(body)     ║ POST /debts           ║ Page 3 手动添加          ║
║  updateDebt(id, mask) ║ PATCH /debts/{id}     ║ Page 3 编辑              ║
║  deleteDebt(id)       ║ DELETE /debts/{id}    ║ Page 3 删除              ║
║  confirmDebt(id)      ║ POST /debts/{id}      ║ Page 3 确认              ║
║                       ║   :confirm            ║                         ║
╠═══════════════════════╬═══════════════════════╬════════════════════════ ╣
║  createOcrTask(file)  ║ POST /ocr-tasks       ║ Page 3 拍照识别          ║
║  getOcrTask(id)       ║ GET /ocr-tasks/{id}   ║ Page 3 轮询结果          ║
║  confirmOcrTask(id)   ║ POST /ocr-tasks/{id}  ║ Page 3 确认 OCR          ║
║                       ║   :confirm            ║                         ║
╚═══════════════════════╩═══════════════════════╩════════════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  profile_api.dart                                                       ║
╠═══════════════════════╦═══════════════════════╦════════════════════════ ╣
║  getFinanceProfile()  ║ GET /finance-profiles ║ Page 4-5 画像数据        ║
║                       ║   /mine               ║                         ║
║  calculateProfile()   ║ POST /finance-profiles║ Page 4 触发计算          ║
║                       ║   /mine:calculate     ║                         ║
╚═══════════════════════╩═══════════════════════╩════════════════════════ ╝

╔═══════════════════════════════════════════════════════════════════════════╗
║  report_api.dart                                                        ║
╠═══════════════════════╦═══════════════════════╦════════════════════════ ╣
║  generateReport()     ║ POST /reports         ║ Page 8 生成报告          ║
║                       ║   :generate           ║                         ║
║  getReport(id)        ║ GET /reports/{id}     ║ Page 9 查看报告          ║
║  exportReport(id)     ║ POST /reports/{id}    ║ Page 8 导出 PDF          ║
║                       ║   :export             ║                         ║
╚═══════════════════════╩═══════════════════════╩════════════════════════ ╝
```

---

## 五、9 页实现规范

### Page 1 — 安全进入

```
文件: features/assessment/presentation/page1_safe_entry.dart
状态: 无状态页面，纯 UI

布局:
  - 顶部 60%：温和插画/Lottie 动画（财务健康主题）
  - 中部标题："看看你是否正在多付利息"
    字号: 24sp, 加粗, 颜色: AppColors.textPrimary
  - 副标题："1分钟检查，不需要提供个人信息"
    字号: 14sp, 颜色: AppColors.textSecondary
  - 底部 CTA 按钮："开始检查"
    样式: 圆角 12dp, 高度 52dp, 渐变蓝
    onTap → context.go('/funnel/pressure')

禁止:
  - 不出现"债务""负债""重组"等词
  - 不用红色
  - 不要求登录
```

### Page 2 — 压力检测

```
文件: features/assessment/presentation/page2_pressure_check.dart
状态管理: Riverpod StateNotifier

核心组件:
  1. 月供滑块
     - 组件: Slider / CupertinoSlider
     - 范围: 0 ~ 50000, 步进 500
     - 初始: 5000
     - 标签: "每月大约还款 ¥{value}"

  2. 收入区间选择
     - 组件: ChoiceChip 组
     - 选项: ["5千以下", "5千-1万", "1万-2万", "2万-5万", "5万以上"]
     - 映射: [3000, 7500, 15000, 35000, 75000]（取中位数传给后端）

  3. 压力仪表盘（实时）
     - 组件: 自定义 CustomPainter
     - 输入: pressureIndex (0-100)
     - 颜色: 0-40 绿色, 40-70 橙色, 70-100 深橙（不用红色）
     - 标签: HEALTHY → "健康", MODERATE → "偏高",
             HEAVY → "较重", SEVERE → "需关注"
     - ⚠️ 不用"危险""严重"等词

API 调用:
  滑块 onChange 时 debounce 500ms，调用:
    POST /engine/pressure:assess
    body: { monthlyPayment: X, monthlyIncome: Y }
  响应实时更新仪表盘，不需要"提交"按钮。

CTA: "查看详细分析 →"
  onTap → 如果未登录, context.go('/auth/login?redirect=/funnel/debts')
          如果已登录, context.go('/funnel/debts')
```

### Page 3 — 债务录入

```
文件: features/assessment/presentation/page3_debt_input.dart
状态管理: Riverpod + 本地 Hive 草稿缓存（防断网丢数据）

布局:
  - 顶部: "录入具体债务，获得精确分析"
  - 已录入统计栏: "已录入 {n} 笔，已发现 ¥{saving} 潜在节省"
    → 每新增一笔实时更新（调 calculateApr 试算）
  - 债务卡片列表 (ListView)
  - 底部三种添加方式:
      [📷 拍照识别]  [✏️ 手动添加]  [📋 快速模板]

三种录入交互:
  1. 手动添加 → 弹出 BottomSheet 表单
     字段: creditorName, productType(下拉), principal, 
           totalRepayment, loanStartDate, loanDays
     每字段下方实时校验提示

  2. 拍照识别 → 调用相机, 上传图片
     流程: createOcrTask → 轮询 getOcrTask (每2秒, 最多30次)
          → 成功后弹出确认 Sheet（预填识别结果, 用户可修改）
          → confirmOcrTask → 自动 createDebt

  3. 快速模板 → 预设常见产品
     列表: ["信用卡", "花呗", "借呗", "微粒贷", "京东白条", ...]
     选中后预填 creditorName + productType，其他用户填

关键约束:
  - 必须支持"先录一笔看效果"，不强制录完所有
  - 每录一笔后自动调 calculateApr 显示该笔 APR
  - 至少录入 1 笔才能进入 Page 4

CTA: "查看分析报告 →"
  onTap → 确认所有债务状态为 CONFIRMED
        → 调 calculateFinanceProfile 触发画像计算
        → context.go('/funnel/loss-report')
```

### Page 4 — 损失可视化

```
文件: features/assessment/presentation/page4_loss_report.dart
状态管理: Riverpod FutureProvider (加载画像数据)

数据来源: getFinanceProfile() → 取出:
  - weightedApr: 加权年化利率
  - threeYearExtraInterest: 三年多付利息
  - monthlyPaymentToIncomeRatio: 月供收入比
  - healthyApr / healthyRatio: 健康线对比值

布局:
  1. 核心冲击数字（占屏幕 40%）
     "如果维持当前结构"
     "3 年将多支付"
     "¥82,400"  ← AnimatedNumber 滚动动画, 2秒从0到目标值
                   字号 48sp, 颜色 AppColors.accent(橙色)
     "相当于 {n} 个月房租" ← 类比文案

  2. 三个对比卡片 (Row)
     卡片 A: "当前加权利率 {apr}% / 市场均值 {healthyApr}%"
     卡片 B: "月供占收入 {ratio}% / 健康线 {healthyRatio}%"
     卡片 C: "债务笔数 {count} / 其中高息 {highCount} 笔"

  3. ⚠️ 这里绝对没有"申请"按钮
     CTA 只有: "看看我的优化空间 →"
     onTap → 读取 financeProfile.score
           → context.go('/funnel/optimization', extra: score)

颜色约束:
  - 不用红色/警告图标
  - 冲击数字用橙色
  - 背景用浅蓝/浅灰
  - 信息提示风格，不是警告风格
```

### Page 5 — 优化空间

```
文件: features/optimization/presentation/page5_optimization.dart

核心: 第一句话必须正面

布局:
  1. 顶部大字: "好消息是，你有优化空间。"
     字号 22sp, 颜色 AppColors.positive(蓝绿色)

  2. 三个确定性卡片:
     卡片 A: "成功概率 {probability}%"  + 圆环进度
     卡片 B: "月供可降低约 ¥{saving}/月" + 柱状对比
     卡片 C: "分 {phases} 步完成" + 路径图标

  3. 五维雷达图 (radar_chart.dart)
     维度: 利率健康度, 结构合理度, 还款能力, 征信状况, 优化潜力
     数据来自 financeProfile.scoreDimensions

CTA: "模拟一下效果 →"
  onTap → context.go('/funnel/simulator', extra: score)

评分 < 60 分支:
  路由自动跳转到 CreditOptimizationPage
  展示: "当前更适合优化信用结构"
  + 30天行动计划预览
  CTA: "查看改善方案 →"
```

### Page 6 — 利率模拟器（最核心交互）

```
文件: features/optimization/presentation/page6_rate_simulator.dart
状态管理: Riverpod StateNotifier (存储滑块当前值)

核心组件: RateSlider (自定义)
  - 文件: features/optimization/presentation/widgets/rate_slider.dart
  - 底层: GestureDetector + AnimationController
  - 左端: 当前加权 APR (如 24%)，标签 "当前"
  - 右端: 最低可能 APR (如 6%)，标签 "最优"
  - 初始位置: 当前 APR（左端）
  - 用户向右拖动 → 目标 APR 降低
  - 阻尼效果: 越靠近最优端，阻力越大
    实现: physics: BouncingScrollPhysics 变体
  - 刻度标记: 每 2% 一个刻度点

实时显示面板 (随滑块联动):
  ┌─────────────────────────────────┐
  │  月供变化    ¥24,800 → ¥18,200  │  ← AnimatedNumber
  │  三年节省    ¥237,600            │  ← AnimatedNumber
  │  月供占收入  65% → 48%           │  ← AnimatedNumber + 进度条
  └─────────────────────────────────┘

API 调用:
  滑块 onChange debounce 300ms，调用:
    POST /engine/rate:simulate
    body: { currentWeightedApr, targetApr, totalPrincipal, avgLoanDays, monthlyIncome }
  响应实时更新三个数字。

底部 disclaimer:
  "实际利率取决于个人信用状况和金融机构审核"
  字号 12sp, 颜色 AppColors.textTertiary

CTA: "了解风险 →"

评分 < 60 分支:
  路由到 CreditRepairPage
  替代利率模拟器，展示 "信用修复路线图"
  时间轴: 30天 → 60天 → 90天 → 重新评估
```

### Page 7 — 风险评估

```
文件: features/optimization/presentation/page7_risk_assessment.dart
状态: 无状态页面，纯 UI

布局: Q&A 列表 (ExpansionTile)

FAQ 数据（硬编码或从后端配置拉取）:
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
  - 不要求任何输入

CTA: "开始准备 →"
```

### Page 8 — 分层行动

```
文件: features/action/presentation/page8_action_layers.dart
状态管理: Riverpod StateNotifier (当前层级, 各层完成状态)

布局: 四层递进卡片 + 顶部进度条

Layer 1: "看看申请需要准备什么"
  操作: 调 generateReport() 生成资料清单
  展示: 所需文件列表 + 预估准备时间
  完成后: ✅ 标记完成, 解锁 Layer 2
  右上角: "暂不继续" 文字按钮

Layer 2: "一键整理你的申请资料"
  操作: 调 getReport(id) 获取结构化文档
  展示: 整理好的申请材料预览
  完成后: ✅ 标记完成, 解锁 Layer 3

Layer 3: "预审一下通过概率"
  操作: 调 exportReport(id) 获取完整评估
  展示: 预估结果 + 建议优化点

Layer 4: "确认提交申请"（灰色, 标注 "即将上线"）
  MVP 阶段不可点击, 显示 "V2.0 功能"

进度条: 1/4 → 2/4 → 3/4
每层之间不自动跳转，用户主动点击"下一步"

CTA (最后): "查看我的行动计划 →"
  onTap → context.go('/funnel/companion')

评分 < 60 分支:
  Layer 1 变为: "生成 30 天改善计划"
  Layer 2 变为: "设置还款提醒"
  Layer 3 变为: "30 天后重新评估"
  Layer 4: 不显示
```

### Page 9 — 持续陪伴

```
文件: features/action/presentation/page9_companion.dart

布局:
  1. 顶部正面强化: "你已经迈出了第一步 🎯"
  2. 进度时间轴: 30天 / 60天 / 90天
     当前位置高亮，已过节点 ✅
  3. Checklist (可勾选):
     □ 整理所有账单
     □ 确认各债务最低还款日
     □ 优先偿还 {highestAprCreditor}
     □ 30天后重新评估
  4. 下一个检查点提醒卡片:
     "还有 {days} 天到下一个检查点"
     [设置提醒] 按钮 → 调用系统日历/推送

这不是结果页，是陪伴页。
用户可以反复回来查看进度。
```

---

## 六、色彩与视觉约束

```dart
// core/theme/app_colors.dart

class AppColors {
  // 主色 — 安全、专业
  static const primary = Color(0xFF2E75B6);       // 蓝色
  static const primaryLight = Color(0xFFD5E8F0);   // 浅蓝背景

  // 强调色 — 冲击数字
  static const accent = Color(0xFFE8852A);          // 橙色（不是红色）
  static const accentLight = Color(0xFFFFF3E8);     // 浅橙背景

  // 正面色 — 好消息
  static const positive = Color(0xFF2BAF7E);        // 蓝绿色
  static const positiveLight = Color(0xFFE8F8F0);

  // ⚠️ 禁止使用
  // static const danger = Color(0xFFE53935);       // 红色 — 禁用
  // static const warning = Color(0xFFFF9800);      // 告警黄 — 禁用

  // 中性色
  static const textPrimary = Color(0xFF1A1A2E);
  static const textSecondary = Color(0xFF6B7280);
  static const textTertiary = Color(0xFF9CA3AF);
  static const background = Color(0xFFF8FAFE);
  static const surface = Color(0xFFFFFFFF);
  static const divider = Color(0xFFE5E7EB);
}
```

---

## 七、Claude Code 生成指令

### 初始化项目

```bash
claude

> 创建 Flutter 项目 youhuajia_app，按照 ai-spec/client-spec.md 的
> 项目结构初始化。
> 包含: Riverpod, Dio, GoRouter, fl_chart, Hive
> pubspec.yaml 完整依赖。
> 创建所有目录和空文件占位。
> 完成后执行 flutter analyze。
```

### 按页面生成（每次一页）

```bash
claude

> 读取 ai-spec/client-spec.md 的 "Page 2 — 压力检测" 规范
> 和 ai-spec/domain/user-journey.md 的 Page 2 约束。
> 生成:
> 1. page2_pressure_check.dart（页面）
> 2. pressure_gauge.dart（自定义仪表盘 CustomPainter）
> 3. engine_api.dart 中的 assessPressure 方法
> 4. 对应的 Riverpod Provider
> 
> 滑块必须是滑块/区间选择，不是输入框。
> 仪表盘用中性词，不用"危险""严重"。
> 完成后执行 flutter analyze。
```

---

## 八、与 ai-spec 的映射关系

| 本文件章节 | 依赖的 ai-spec 文件 | 关系 |
|-----------|-------------------|------|
| 路由设计 | user-journey.md 第三章 | 9 页 1:1 映射 |
| API 层 | contracts/openapi.yaml | 25 个接口全覆盖 |
| 色彩约束 | user-journey.md 第六章 | 禁止表达 → 禁止颜色 |
| 评分分支 | engine/scoring-model.md | score < 60 走特殊路径 |
| 文案约束 | user-journey.md 第六章 | CTA 按钮文案严格遵循 |
| 数据模型 | domain/entities.md | Dart 模型 1:1 对应 |
| 错误处理 | contracts/error-codes.md | ApiException 映射 |

---

## 九、Flutter 项目的 CLAUDE.md

此文件放在 `youhuajia_app/CLAUDE.md`，Claude Code 生成前端代码时自动读取：

```markdown
# 优化家 Flutter 前端 — Claude Code 约束

## 项目概述
债务优化决策工具的移动端，Flutter 3.x + Dart 3.x。
9 页渐进式漏斗，遵循 user-journey.md 心理路径约束。

## 绝对禁止
- F-01: 金额显示不能用 double.toString()，必须用 NumberFormat
- F-02: 不在前端做 APR 计算，所有计算调后端 /engine:calculateApr
- F-03: 页面文案不能出现禁止词（见 forbidden_words.dart）
- F-04: Page 4 不能出现"立即申请"按钮
- F-05: 评分 < 60 的用户不能进入 Page 6-8
- F-06: 输入控件用 Slider/Picker，不用 TextField（Page 2）
- F-07: 不在客户端存储敏感数据（token 除外）
- F-08: 所有 HTTP 请求必须通过 ApiClient，不直接调 Dio

## 命名规范
- 文件名: snake_case (pressure_screen.dart)
- 类名: PascalCase (PressureScreen)
- 变量/方法: camelCase (monthlyPayment)
- Widget: 一个文件一个公共 Widget

## 目录约束
- 每个 Page 在 features/ 下独立目录
- 共享组件在 shared/widgets/
- 数据模型在 models/
- API 调用在 core/api/

## 状态管理
- 用 Riverpod，不用 setState（简单交互除外）
- Provider 命名: xxxProvider / xxxNotifier

## 色彩
- 主色: 蓝色系 (#2E75B6)
- 强调: 橙色 (#E8852A)，不是红色
- 禁止: 红色告警色
```

---

## 十、Agent Teams 前端任务分工

用原生 Claude Code Agent Teams 并行开发 9 个页面：

```
创建 agent team，使用 delegate mode。

Teammate "flutter-arch" (Opus):
  读取 ai-spec/client-spec.md 全文。
  输出以下规范（不写代码）：
  - pubspec.yaml 完整依赖清单
  - lib/core/api/ 全部文件的类名和方法签名
  - lib/models/ 全部 DTO 的字段和类型
  - GoRouter 路由配置
  格式：文件路径 + Dart 类签名 + 依赖关系

Teammate "page-basic" (Sonnet):
  等 flutter-arch 完成后，实现 Page 1, 2, 7（最简单的三页）。
  Page 1: 纯展示，无 API 调用
  Page 2: 两个 Slider + 压力仪表盘（CustomPainter）
  Page 7: Q&A 列表（ExpansionTile）
  每页完成后 flutter analyze。

Teammate "page-core" (Sonnet):
  等 flutter-arch 完成后，实现 Page 3, 4, 6（核心交互页）。
  Page 3: 债务录入表单 + OCR 调用
  Page 4: 损失可视化（fl_chart）+ 数字滚动动画
  Page 6: 利率滑块模拟器 + 实时联动
  每页完成后 flutter analyze。

Teammate "page-action" (Sonnet):
  等 flutter-arch 完成后，实现 Page 5, 8, 9。
  Page 5: 评分分支路由逻辑（< 60 跳 Page 9）
  Page 8: 四层 Stepper
  Page 9: Timeline + Checklist
  每页完成后 flutter analyze。

page-basic, page-core, page-action 操作不同 features/ 子目录，并行不冲突。
```

---

## 十一、MVP 前端时间线

```
Week 2 Day 7:  项目初始化 + core/ 层（ApiClient, Auth, Theme, Models）
Week 2 Day 8:  Page 1 + 2 + 7（简单页面，并行）
Week 2 Day 9:  Page 3（债务录入 + OCR 拍照）
Week 2 Day 10: Page 4（损失可视化 + fl_chart 图表 + 数字动画）
Week 3 Day 11: Page 5 + 6（评分分支 + 利率模拟器滑块）
Week 3 Day 12: Page 8 + 9（四层行动 + 陪伴页）
Week 3 Day 13: 前后端联调（对接真实 API）
Week 3 Day 14: UI 打磨 + 评分<60 路径完整测试
Week 3 Day 15: flutter test + 集成测试
```
