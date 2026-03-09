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
日期：2026-03-09 任务类型：漏斗页退出机制 + 低分用户"我的"页面适配 关联切片：前端漏斗流程 / 导航结构
使用的 Prompt 版本：无（SPEC-DISCOVER 驱动）

### 本次完成了什么

**一、SPEC-DISCOVER：漏斗页退出机制**

- 完成 Phase 1（业务追问）+ Phase 2（破坏者模式），Phase 3 跳过（无新增数据字段）
- 更新 `domain/user-journey.md`：新增"漏斗页退出机制"章节

**二、FunnelNavBar 组件实现（新建 `src/components/FunnelNavBar.vue`）**

- 导航栏左侧：返回箭头（navigateBack + fail 兜底 switchTab）+ 首页图标（switchTab）
- CSS 绘制图标（无外部依赖），首页图标用 `$text-tertiary` 比返回箭头更低调
- 状态栏高度自适应（`uni.getSystemInfoSync().statusBarHeight`）

**三、13 个漏斗页集成 FunnelNavBar**

- `pages.json`：11 个页面从原生导航切换到 `navigationStyle: "custom"`
- Page 2/3/5/7/8/9 + 5 个低分页面：添加 import + 组件标签
- Page 4/6（已是 custom）：添加组件 + Page 4 修复 `.content { height: 100vh }` → `flex: 1`
- Page 1 不加首页图标（返回即可回首页）

**四、Review 修复（2 项）**

1. `FunnelNavBar.goBack()` 添加 `fail` 回调：页面栈为空时 fallback 到 switchTab 首页
2. Page 4 `.loading-wrap/.error-wrap` 从 `min-height: 100vh` 改为 `flex: 1`

**五、低分用户"我的"页面适配（3 个文件）**

1. `mine/index.vue` — 菜单条件文案：低分用户显示"我的改善计划"，正常用户显示"我的报告"
2. `mine/reports.vue` — 低分用户展示本地改善计划（30天计划 + 预估分数 + checklist 进度），正常用户保持原有报告列表
3. `home/index.vue` — 已完成态：低分用户显示"改善计划已生成 + 信用改善进行中，坚持 30 天后重新评估"

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| 漏斗页退出方式 | 导航栏首页按钮（非全程 TabBar） | TabBar 与底部 CTA 空间冲突 + 分散漏斗专注感 + uni-app 技术限制 |
| 退出后恢复路径 | 两步闭环（回首页 → 继续评估） | MVP 阶段中断是低频操作；单步方案会产生双实例问题 |
| 退出是否需要确认弹窗 | 不需要 | Pinia unistorage 已自动持久化，无数据丢失风险 |
| 低分用户"我的报告"处理 | 条件文案（同一位置不同标签） | 新增独立入口会造成更大歧义（用户反馈） |

### AI 推理链（关键决策必填）

决策：导航栏首页按钮 vs 全程 TabBar
1. 我读取了 user-journey.md 的按钮出现时机约束和漏斗设计哲学（"每一步只推半步"）
2. 我注意到每个漏斗页底部都有 CTA 按钮（"继续""查看结果"等）
3. 我考虑了三个方案：A.导航栏加首页按钮、B.全程自定义 TabBar、C.导航栏加退出确认弹窗
4. 方案 B 的问题：(a) 底部 CTA 和 TabBar 两层底栏在小屏手机体验差 (b) 随时可见 TabBar 鼓励离开，降低漏斗完成率 (c) uni-app 非 TabBar 页无法显示原生 TabBar，需每页引入自定义组件，维护成本高
5. 方案 C 的问题：数据已通过 Pinia unistorage 自动持久化，弹窗确认增加不必要摩擦
6. 因此选择方案 A，因为实现简单、不破坏漏斗专注感、不占用底部空间

决策：低分用户"我的报告"条件文案 vs 新增入口
1. 我读取了 mine/index.vue 的菜单结构：报告/反馈/关于 三项
2. 我注意到低分用户不会通过 generateReport API 生成报告，进入"我的报告"会看到空态
3. 我考虑了「新增一个改善计划入口」和「同一位置条件显示不同文案」
4. 用户指出：两个入口并存更容易歧义（用户分不清"报告"和"改善计划"的区别）
5. 因此选择条件文案：同一个菜单位置，低分用户看到"我的改善计划"，正常用户看到"我的报告"

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| 全程自定义 TabBar | 底部空间冲突 + 违反漏斗设计 + 技术成本高 |
| navigateTo 首页（保留漏斗页面栈） | 产生两个首页实例，体验不一致 |
| 退出时二次确认弹窗 | 数据已自动持久化，弹窗增加摩擦 |
| "我的"页面新增独立"改善计划"入口 | 两个入口造成更大歧义（用户反馈） |
| 低分用户也调 generateReport 生成后端报告 | 需要后端支持低分报告类型，MVP 阶段过重 |

### 遗留问题（下次会话继续）

- [ ] reports 页面原生导航栏标题仍是"我的报告"，低分用户进入时与菜单文案不一致（建议用 `uni.setNavigationBarTitle()` 动态设置）
- [ ] reports.vue checklist 总数硬编码为 4（建议改为 `Object.keys(checklist.value).length`）
- [ ] pages.json 中 11 个切换到 custom nav 的页面仍保留 `navigationBarTitleText`（无功能影响，语义冗余）
- [ ] PROMPT-GOLDEN-TESTS 所有 Golden Test 待首次运行
- [ ] @AiGenerated 注解类尚未在项目中创建

### Spec 变更建议

- 文件：domain/user-journey.md 内容：已完成（新增"漏斗页退出机制"章节）
- 文件：domain/user-journey.md 内容：Mine 页"功能菜单列表"条目建议补充"低分用户显示'我的改善计划'"说明
---

---
日期：2026-03-09 任务类型：scoreSimulator 分群支持 + client-spec 文档补全 关联切片：前端漏斗流程 / 评分引擎
使用的 Prompt 版本：无（直接实现）

### 本次完成了什么

**一、scoreSimulator.js 分群评分完整实现**

1. **`matchSegment(input)`** — 按优先级匹配 4 种分群：HIGH_DEBT（DIR>0.70 或 debtCount>=5）> MORTGAGE_HEAVY（房贷笔数/总笔数>50%）> YOUNG_BORROWER（debtCount<=2 且 avgLoanDays<365）> DEFAULT
2. **4 组分群权重** — `SEGMENT_WEIGHTS` 常量，对应 strategies/*.meta.yml
3. **HIGH_DEBT 7 档 DIR** — `scoreDIR_HIGH_DEBT()` 函数（95/85/65/45/30/15/5），阈值对齐 high-debt.pmml
4. **`calculateScore` 升级** — 新增可选 `segment` 参数，不传则自动匹配；返回值增加 `segment` 字段
5. **`buildScoreInput` 增加 `mortgageCount`** — 从 debts 数组统计 productType/debtType 为 MORTGAGE 的笔数

**二、client-spec.md 文档补全（3 处）**

1. 项目结构树新增 `utils/scoreSimulator.js`
2. 新增「工具函数：scoreSimulator.js」完整文档（4 个导出函数 + 4 组权重 + 使用场景）
3. ai-spec 映射表新增 `engine/strategies/*.meta.yml` 行

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| calculateScore 是否破坏现有 API | 向后兼容（segment 为可选参数，返回值新增字段） | 两个调用方 credit-optimization/improvement-plan 解构赋值不受新字段影响 |
| MORTGAGE_HEAVY 匹配条件 | mortgageCount/debtCount > 50% | scoring-model.md 第三节明确「有 MORTGAGE 类型债务占比 > 50%」 |
| 维度元数据是否也按分群区分 | 暂保持 DEFAULT 描述不区分 | 权重和 DIR 评分档是核心差异，描述文案差异（如 YOUNG_BORROWER 鼓励语调）可后续按需补充 |

### AI 推理链（关键决策必填）

决策：calculateScore 向后兼容设计
1. 我检查了两个调用方：credit-optimization.vue:115 和 improvement-plan.vue:143
2. 两处都用 `calculateScore(scoreInput.value)` 无第二参数，解构 `{ finalScore, dimensions, weakDimensions }`
3. 新代码：不传 segment 时自动调用 matchSegment，返回值新增 segment 字段
4. 影响分析：如果用户数据恰好命中 HIGH_DEBT/MORTGAGE_HEAVY/YOUNG_BORROWER，评分结果会与纯 DEFAULT 不同 — 这正是期望行为（分群差异化）
5. 因此选择可选参数方案，既保持调用兼容，又自动启用分群匹配

决策：MORTGAGE_HEAVY 需要 mortgageCount
1. 我读取了 scoring-model.md 第三节：MORTGAGE_HEAVY 匹配条件是「有 MORTGAGE 类型债务占比 > 50%」
2. 原 buildScoreInput 不包含房贷信息，无法匹配
3. 我检查了 page3-debt-input 的 productType 枚举：CREDIT_CARD/CONSUMER_LOAN/BUSINESS_LOAN/MORTGAGE/OTHER
4. 在 buildScoreInput 中新增 mortgageCount 字段，从 debts 数组过滤 productType === 'MORTGAGE'
5. 同时兼容 debtType（page8 使用 debtType 字段名）

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| 为每个分群维护独立的 DIMENSION_META（含鼓励性描述） | 增加代码量但用户感知有限，当前 UI 只展示分数和改善建议，文案差异化可后续补充 |
| calculateScore 强制要求传 segment 参数 | 破坏现有两处调用，且调用方通常不关心分群——自动匹配更合理 |

**Review 修复（4 项）：**

1. **[Spec 对齐] intent.md 分群条件** — 更新为与 scoring-model.md 一致（HIGH_DEBT: DIR>0.70 或 debtCount>=5；MORTGAGE_HEAVY: >50%；YOUNG_BORROWER: debtCount<=2 且 avgLoanDays<365）
2. **[AG-09] 权重版本标注** — SEGMENT_WEIGHTS 顶部加注释 `同步自 strategies/*.meta.yml v1.0`
3. **[Bug] simulateImprovement segment 固定** — 改善操作后使用 current.segment 评估 simulated，避免跨分群 dimChanges 误导
4. **[测试] 新增 scoreSimulator.test.js（43 用例全通过）**：
   - matchSegment 10 用例（4 分群 + 优先级 + 边界）
   - DEFAULT 评分 SC-01~SC-06（6 用例）
   - 弱项维度识别 2 用例
   - HIGH_DEBT 7 档 DIR 3 用例
   - MORTGAGE_HEAVY / YOUNG_BORROWER 权重 2 用例
   - 自动分群集成 2 用例
   - What-if 模拟 6 用例（含 segment 固定验证）
   - buildScoreInput 4 用例
   - 边界值 8 用例

### 遗留问题（下次会话继续）

- [ ] 正常流程 Page 5/Page 8 未接入客户端评分模拟（可选增强）
- [ ] PROMPT-GOLDEN-TESTS 所有 Golden Test 待首次运行
- [ ] @AiGenerated 注解类尚未在项目中创建
- [ ] DIMENSION_META 分群差异化描述（YOUNG_BORROWER 鼓励语调等）可后续补充
- [ ] scoring-model.md 第十二节测试矩阵预期总分与 fallback 规则推导不一致（SC-02~SC-06），建议重新验算

### Spec 变更建议

- 文件：scoring-model.md 第十二节 内容：SC-02~SC-06 预期总分需重新验算（与 Section 5 fallback 规则精确推导值有出入）
---

---
日期：2026-03-09 任务类型：低分路径体验提升 + 客户端评分模拟 + 策略模型文件 关联切片：前端漏斗流程 / 评分引擎
使用的 Prompt 版本：无（直接实现）

### 本次完成了什么

**一、低分路径 UX 体验适配（3 处）**

1. **首页"进行中态"步骤标签** — 新增 `lowScoreStepLabels` + `currentStepLabel` computed，低分用户看到对应实际页面的标签（信用优化引导/修复路线图/常见问题/改善行动）
2. **Page 9 伴随页全面区分低分用户** — 仪式弹窗（"改善计划已生成"）、目标卡片（当前评分→60分进度）、时间轴标题、Checklist（信用修复导向）共 4 处条件分支
3. **硬编码颜色修复** — `#4b5563`→`$text-secondary`（3 文件）、`#ffffff`→`$text-inverse`、`confirmColor` 从 `#2E75B6` 修正为 `#1B6DB2`（匹配 `$primary`）

**二、客户端评分模拟器（新建 `src/utils/scoreSimulator.js`）**

- `calculateScore(input)` — 五维评分 + 弱项识别 + 分级描述 + 改善提示
- `simulateImprovement(input, improvements)` — What-if 模拟（4 种改善操作：补齐逾期/降低使用率/还清小额/降息）
- `buildScoreInput(profileStore, funnelStore, debtStore)` — 从 store 数据构造评分输入
- 维度元数据（label/levels/tip）与 meta.yml 保持一致

**三、低分页面个性化改造（2 页）**

1. **credit-optimization.vue** — 新增弱项维度诊断卡片（分数柱状图 + 分级描述 + 改善提示）+ 个性化 30 天计划（根据最弱维度定制第 2 周任务）+ 改善后预估分数卡片
2. **improvement-plan.vue** — Layer 1 结果增强：预估分数进度条（当前 vs 预估 vs 60 分阈值线）+ 维度变化明细 + 后端 API 降级为行为记录（本地计算为主）

**四、策略模型文件（新建 `ai-spec/engine/strategies/`，8 个文件）**

| 分群 | PMML | meta.yml | 权重差异 | 风险边界 |
|------|------|----------|---------|---------|
| DEFAULT | default.pmml | default.meta.yml | DIR=0.30 APR=0.25 LIQ=0.15 OVD=0.20 CST=0.10 | [80,60,40] |
| HIGH_DEBT | high-debt.pmml | high-debt.meta.yml | DIR=0.35↑ APR=0.20↓ + 7 档细粒度 DIR | [75,55,35] |
| MORTGAGE_HEAVY | mortgage-heavy.pmml | mortgage-heavy.meta.yml | LIQ=0.20↑ CST=0.15↑ DIR=0.25↓ | [80,60,40] |
| YOUNG_BORROWER | young-borrower.pmml | young-borrower.meta.yml | CST=0.15↑ + 鼓励性描述 | [80,60,40] |

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| 评分模拟放前端 vs 继续依赖后端 | 前端本地计算为主，后端降级为行为记录 | scoring-model fallback 逻辑是简单 if/else，前端完全可复用；离线友好；低分用户不应因后端不可用而中断流程 |
| scoreSimulator 初版只用 DEFAULT 权重 | 先 DEFAULT，分群匹配下次补 | 低分用户体验提升是本次优先级，分群匹配是增强功能；4 组 PMML 已就位，前端补上 matchSegment 即可 |
| Page 9 低分用户目标卡片展示什么 | 当前评分→60 分进度（替代节省金额） | 低分用户未做利率模拟，"预估可节省"数据无意义；60 分是重组门槛（scoring-model.md 第七节），是低分用户的真实目标 |
| PMML partialScore 值 | 预乘权重（rawScore × weight） | PMML Scorecard 标准做法，finalScore = initialScore + Σ(partialScores)，Java 端直接求和无需额外计算 |
| 策略文件放置位置 | ai-spec/engine/strategies/ | 后端目录尚不存在；策略文件与 scoring-model.md 同属引擎规范层，放在一起便于查找；后端创建时 copy 到 src/main/resources/ |

### AI 推理链（关键决策必填）

决策：评分模拟放前端
1. 我读取了 scoring-model.md 第五节 fallback 逻辑：5 个 if/else 函数 + 加权求和
2. 我注意到逻辑复杂度很低（无循环/无递归/无外部依赖），纯确定性计算
3. 我考虑了「继续依赖后端 simulateScore API」和「前端本地实现 + 后端降级为行为记录」
4. 纯依赖后端的问题：improvement-plan 上次改造已经加了 try/catch 降级到静态数据，说明后端不可用是常态；且降级后用户看到的是硬编码的通用计划，失去个性化
5. 因此选择前端本地计算，因为：(a) 逻辑简单可复用 (b) 离线友好 (c) 可实时响应用户数据变化 (d) 后端 API 仍保留用于行为记录，不影响后端追踪

决策：Page 9 低分用户目标卡片
1. 我读取了 page9-companion 原有代码：目标卡片展示「预估可节省」和「目标节省」金额
2. 我注意到低分用户的 estimatedSaving 来源是 debtStore.estimatedSaving（APR 超过市场均值 18% 的超额利息 × 3 年）
3. 我考虑了「保持节省金额展示」和「改为评分进度展示」
4. 保持节省金额的问题：低分用户未经过 Page 6 利率模拟，没有主动探索过节省空间，展示节省金额缺少上下文；且低分用户的核心关切是"能否达到 60 分进入主流程"，不是"能省多少钱"
5. 因此选择评分进度（当前分 / 60 分目标），因为与 scoring-model.md 第七节的 restructure_threshold=60 对齐，是低分用户的真实目标

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| scoreSimulator 一步到位支持 4 种分群权重 | 增加代码复杂度，本次优先级是低分体验；PMML 文件已就位，下次补 matchSegment 即可 |
| 前端用 Canvas 画五维雷达图 | 低分页面信息量已经很大（弱项诊断+计划+预估分数），雷达图增加认知负担；柱状图更直观 |
| Page 9 低分用户完全独立页面 | user-journey.md 明确"与正常流程共享 Page 9"，且 ConsultCard 咨询预约功能两种用户都需要 |

### 遗留问题（下次会话继续）

- [ ] `scoreSimulator.js` 只用 DEFAULT 权重 — 需补充 `matchSegment(input)` + 4 组权重 + HIGH_DEBT 细粒度 DIR（7 档）
- [ ] 正常流程 Page 5/Page 8 未接入客户端评分模拟（可选增强）
- [ ] client-spec.md 需补充 `utils/scoreSimulator.js` 和 `ai-spec/engine/strategies/` 的说明
- [ ] PROMPT-GOLDEN-TESTS 所有 Golden Test 待首次运行
- [ ] @AiGenerated 注解类尚未在项目中创建

### Spec 变更建议

- 文件：client-spec.md 内容：新增 `utils/scoreSimulator.js` 说明（五维评分 + What-if 模拟 + store 数据构造）
- 文件：scoring-model.md 内容：第四节补充 `strategies/` 目录已创建 4 组 PMML + meta.yml，附文件清单
- 文件：client-spec.md 内容：credit-optimization 和 improvement-plan 页面描述更新（个性化诊断 + 本地评分模拟）
---

---
日期：2026-03-09 任务类型：ai-spec 反向更新（低分路径文档补全） 关联切片：前端漏斗流程
使用的 Prompt 版本：无（直接修复）

### 本次完成了什么

1. **test/mock-data.md 补充三种用户漏斗路由表** — 新增第二节，正常流程（用户A/B）vs 低分流程（用户C）逐步对比表，含首页 stepPageMap/lowScorePageMap 代码参考，Page 6/7 低分守卫标注
2. **client-spec.md 5 处更新**：
   - 项目结构说明添加 `src/` 是 inputDir 的提示 + `index.html` 入口
   - `low-score/` 目录补充 `risk-faq.vue`（Step 7）
   - 路由表添加 `risk-faq` 行
   - Page 6 规范补充低分路由守卫说明
   - Page 7 规范补充低分路由守卫说明

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| mock-data 路由表格式 | 正常/低分双列对比表 + 代码块 | 测试人员可直接对照验证每个 Step 的页面路径，代码块可复制到测试用例 |
| client-spec Page 6/7 更新方式 | 在现有 Page 描述中追加"低分路由守卫"段 | 保持 spec 结构不变，守卫逻辑是现有页面的补充行为而非独立功能 |

### AI 推理链（关键决策必填）

决策：mock-data 路由表放在哪个位置
1. 我读取了 test/mock-data.md 现有结构：第一节用户画像、第二节 OCR Mock
2. 我注意到路由表跨用户画像，不属于某个特定用户数据
3. 我考虑了「在每个用户画像 JSON 中加 expectedRoutes 字段」和「独立新增第二节」
4. 在 JSON 中加字段的问题：路由是前端行为，与后端 Mock 数据混淆；且三种用户的路由需要横向对比
5. 因此选择独立新增第二节，原第二节 OCR 改为第三节，保持结构清晰

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| 在用户画像 JSON 中加 expectedRoutes | 路由是前端行为，不应混入后端 Mock 数据结构 |
| 路由表放入 client-spec.md 而非 mock-data.md | SCRATCHPAD 建议放 mock-data，且路由表服务于测试验证 |

### 遗留问题（下次会话继续）

- [ ] PROMPT-GOLDEN-TESTS 所有 Golden Test 待首次运行
- [ ] @AiGenerated 注解类尚未在项目中创建

### Spec 变更建议

- （无新增，本次已完成所有待更新 spec）
---

---
日期：2026-03-09 任务类型：目录统一 + ai-spec 反向更新 关联切片：前端项目结构
使用的 Prompt 版本：无（直接修复）

### 本次完成了什么

**一、发现并修复 pages/ vs src/pages/ 目录冗余问题**

1. **确认构建系统使用 src/ 作为 inputDir** — uni-app vite 插件默认 `UNI_INPUT_DIR = path.resolve(root, 'src')`，根目录的 pages.json/main.js/App.vue/pages/ 全部是冗余的，构建不读取
2. **发现上次会话的改动全部进了错误位置** — 5 项低分修复（advanceStep/risk-faq/API调用/CTA导航/redirect守卫）改到了根目录 pages/，实际构建的 src/pages/ 未受影响
3. **合并改动到 src/ 正确位置**：
   - `src/pages/low-score/credit-optimization.vue` — 添加 funnelStore + advanceStep(6)
   - `src/pages/low-score/credit-repair.vue` — CTA 改为"了解常见问题"→risk-faq + advanceStep(7)
   - `src/pages/low-score/risk-faq.vue` — 新建（4 个低分专属 FAQ）
   - `src/pages/low-score/improvement-plan.vue` — 替换 setTimeout 为 simulateScore API + 降级
   - `src/pages/page6-rate-simulator/index.vue` — onMounted 低分 redirect → credit-repair
   - `src/pages/page7-risk-assessment/index.vue` — onMounted 低分 redirect → risk-faq
   - `src/pages.json` — 注册 risk-faq 路由
4. **删除根目录冗余文件** — pages/、pages.json、main.js、App.vue、manifest.json、uni.scss
5. **验证构建通过** — `npm run build:h5` 成功

**二、ai-spec 反向更新**

- `domain/user-journey.md` 第五节从 3 行粗描扩充为完整的低分流程页面定义（5 个页面逐一描述）

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| src/ vs 根目录哪个是 source of truth | src/（由 vite 插件源码确认） | `UNI_INPUT_DIR` 默认为 `path.resolve(root, 'src')` |
| 保留根目录文件 vs 删除 | 删除 | 冗余文件导致上次会话改错位置，必须消除歧义 |
| risk-faq 样式方案 | 使用 src/ 已有的设计令牌（$variables），不用 mixins | 保持与 src/ 其他低分页面风格一致 |

### AI 推理链（关键决策必填）

决策：确认 src/ 是 inputDir
1. 我检查了 index.html 入口 → 引用 `/src/main.js`
2. 我搜索了 vite-plugin-uni 源码中的 `UNI_INPUT_DIR`
3. 我在 `dist/configResolved/env.js` 中发现：`if (!process.env.UNI_INPUT_DIR) { process.env.UNI_INPUT_DIR = path.resolve(config.root, 'src') }`
4. 项目未设置 `UNI_INPUT_DIR` 环境变量
5. 因此 src/ 是默认的构建输入目录，根目录文件全部冗余

决策：删除根目录冗余文件
1. 我注意到上次会话的 5 项修复全部改到了根目录 pages/
2. 构建系统读取的是 src/pages.json 和 src/pages/，这些修改完全无效
3. 我考虑了「保留根目录文件但标记废弃」和「直接删除」
4. 保留的问题：未来 AI 会话仍会混淆两个目录，重蹈覆辙
5. 因此选择删除，消除歧义源头

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| 将 inputDir 改为根目录 | 违反 uni-app CLI 默认约定，src/ 已有完整的 api/components/stores/styles 结构 |
| 保留根目录文件作为备份 | 冗余文件是本次问题的根因，必须彻底消除 |

### 遗留问题（下次会话继续）

- [ ] PROMPT-GOLDEN-TESTS 所有 Golden Test 待首次运行
- [ ] @AiGenerated 注解类尚未在项目中创建
- [ ] test/mock-data.md 补充三种子用户在各步骤的预期页面路由表

### Spec 变更建议

- 文件：client-spec.md 内容：明确 `src/` 为项目源码根目录，所有页面路径相对于 src/pages/
- 文件：test/mock-data.md 内容：补充低分流程路由表（13800000003 → credit-optimization/credit-repair/risk-faq/improvement-plan/page9）
---

---
日期：2026-03-09 任务类型：低分用户流程差异修复 关联切片：前端漏斗流程
使用的 Prompt 版本：无（直接修复）

### 本次完成了什么

**修复 SCRATCHPAD 遗留问题 1-5（三种子用户流程差异）**

1. **Page 6/7 低分 redirect 守卫** — page6-rate-simulator 和 page7-risk-assessment 新增 `onMounted` 低分检测，分别重定向到 credit-repair 和 risk-faq
2. **低分 improvement-plan API 调用** — 替换 `setTimeout` mock 为 `simulateScore` API 调用，后端可记录低分用户行为；失败时降级到静态数据（离线友好）
3. **进度条编号统一** — 低分流程 Step 5→6→7→8→9 与正常流程平行，不再跳号
4. **actionLayers 路径变化重置** — funnel.js `setFinanceProfile()` 检测低分→正常或正常→低分切换时自动重置 actionLayers
5. **低分 Step 7 等价页** — 新建 `pages/low-score/risk-faq.vue`，4 个低分专属 FAQ（为什么不适合直接优化/30天计划有效吗/改善后效果/是否付费）

**附加修复**
- 所有低分页面补齐 `advanceStep(N)` 调用（credit-optimization→6, credit-repair→7, risk-faq→8, improvement-plan→9）
- 首页 `continueAssessment()` 增加 lowScorePageMap，低分用户 Step 5+ 恢复到正确页面
- pages.json 注册 risk-faq 页面

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| 低分 Step 7 内容 | 独立 risk-faq.vue（低分专属 FAQ） | 正常流程 Q&A 聚焦申请流程，低分用户关心的是"为什么不能直接优化"和"30天计划是否有效"，内容完全不同 |
| improvement-plan API 方案 | 调用 simulateScore + 降级 | 无需新建后端 endpoint，复用评分模拟接口记录用户行为；失败降级保证可用性 |
| credit-repair CTA 导航 | 插入 risk-faq 而非直接到 improvement-plan | 保持低分流程 5→6→7→8→9 与正常流程步数一致，消除进度条跳号 |

### AI 推理链（关键决策必填）

决策：低分 Step 7 内容 — 独立页面 vs 复用 page7
1. 我读取了 pages/page7-risk-assessment/index.vue 的 FAQ 内容
2. 我注意到现有 FAQ 关注"征信查询""信用评分影响""费用"——全部围绕正式申请流程
3. 我考虑了「复用 page7（条件渲染不同 FAQ）」和「独立新建 risk-faq.vue」
4. 复用方案的问题：page7 已有 redirect 到 risk-faq 的逻辑，如果在 page7 内部条件渲染低分内容，redirect 逻辑会冲突；且两套 FAQ 内容无重叠，混在一起增加维护成本
5. 因此选择独立新建，因为两组 FAQ 内容完全不同、redirect 逻辑更清晰、符合低分流程独立路径的架构

决策：credit-repair 导航链插入 risk-faq
1. 我读取了 SCRATCHPAD 遗留问题"进度条编号混乱（低分页面标记 Step 5/6/8 但内容不同）"和"低分流程缺少 Step 7 等价页"
2. 我注意到正常流程 5→6→7→8→9 每步都有对应页面，低分流程原来是 5→6→8（跳过 7）
3. 我考虑了「合并 credit-repair 和 FAQ 为一个 Step 6 页面」和「在 credit-repair(6) 与 improvement-plan(8) 之间插入 risk-faq(7)」
4. 合并方案的问题：credit-repair 已经很长（三个阶段的详细行动建议），加入 FAQ 会信息过载
5. 因此选择插入 risk-faq，修改 credit-repair CTA 从 "开始准备"→improvement-plan 改为 "了解常见问题"→risk-faq

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| 为低分用户新建后端 API endpoint | MVP 阶段不新增后端接口，复用 simulateScore 足够记录行为 |
| 在 page7 内部条件渲染低分 FAQ | redirect 逻辑冲突 + 两套内容无重叠 |
| 低分流程不设 Step 7 | 进度条跳号给用户感觉流程不完整 |

### 遗留问题（下次会话继续）

- [ ] PROMPT-GOLDEN-TESTS 所有 Golden Test 待首次运行
- [ ] @AiGenerated 注解类尚未在项目中创建
- [ ] `pages/` 与 `src/pages/` 存在文件重复（root pages/ 是 uni-app 路由入口，src/pages/ 是开发版本），需统一或建立同步机制
- [ ] src/pages/ 版本的 page6/page7 等已有 advanceStep 等改进但未同步到 pages/（本次修改了 pages/ 版本）

### Spec 变更建议

- 文件：domain/user-journey.md 内容：补充低分流程的完整页面流转定义（credit-optimization[5] → credit-repair[6] → risk-faq[7] → improvement-plan[8] → page9[9]）
- 文件：test/mock-data.md 内容：补充三种子用户在各步骤的预期页面路由表
---

---
日期：2026-03-09 任务类型：工程规范升级 + 技能包优化 + 低分流程排查 关联切片：无（跨领域）

### 本次完成了什么

**一、ai-spec 规范升级（源自《AI 原生项目开发规范 v1》docx）**
- 新建 10 个规范文件：intent.md、TEST-SPEC.md、VIBE-CHECKLIST.md、SCRATCHPAD.md、CONTEXT-SLICE.md、SPEC-DISCOVERY.md、AGENT-PROTOCOL.md、PROMPT-LIBRARY.md、PROMPT-GOLDEN-TESTS.md
- 新建 PROMPT-VERSIONS/ 目录（generate-api/generate-entity/generate-test/code-review 各 v1.0 + CHANGELOG）
- 更新 ai-spec/README.md（四层架构全景 + 文件速查表）
- 更新 CLAUDE.md 文档索引（拆分为 5.1 意图层+质量层 / 5.2 工程层）

**二、技能包优化**
- 新建 4 个项目级技能：/vibe-check、/review、/scratchpad、/spec-discover
- 升级 3 个 Agent 角色（manager/leader/worker）引用新增规范
- 更新用户级 /team 命令纳入新增 ai-spec 文件

**三、低分用户流程排查**
- 确认 13800000001(82分)/13800000002(62分) 走正常流程，13800000003(22分) 走低分修复流程
- 发现 5 个实现问题（2严重/3一般）

### 关键决策

| 决策内容 | 选择方案 | 原因摘要 |
|----------|----------|----------|
| 新增规范文件 vs 合并到现有文件 | 独立新建 | docx 定义的四层架构有独立职责，合并会模糊边界 |
| 现有文件（PLAYBOOK/TEMPLATES/PATTERNS）保留 vs 替换 | 保留+补充 | 现有文件侧重团队协作方法论，新文件侧重质量守门，互补不冲突 |
| intent.md Anti-Goals 编号方案 | AG-xx 前缀 | 区分 CLAUDE.md 的 F-xx 禁止项，两者互补：F-xx 是技术禁令，AG-xx 是业务边界 |

### AI 推理链（关键决策必填）

决策：新增规范文件 vs 合并到现有文件
1. 我读取了 docx《AI 原生项目开发规范》的四层架构定义（工程层/意图层/对话层/质量层）
2. 我注意到现有 ai-spec 集中在工程层（openapi/entities/engine），缺乏意图层和质量层
3. 我考虑了「合并到现有文件」和「独立新建」两种方案
4. 合并方案的问题：TEST-SPEC 如果合并到 test-matrix.md 会混淆「测试用例」和「测试规范」；VIBE-CHECKLIST 如果合并到 AGENT-TEAMS-PLAYBOOK 会稀释 Agent 协作方法论
5. 因此选择独立新建，因为四层架构的每层有独立的使用时机和阅读者（Manager 读 CONTEXT-SLICE，Coder 读 TEST-SPEC，Reviewer 读 VIBE-CHECKLIST）

决策：intent.md Anti-Goals 编号方案
1. 我读取了 CLAUDE.md 第二节「绝对禁止」表（F-01~F-16）
2. 我注意到 F-xx 是技术实现层禁令（float禁用/Hibernate禁用等），而 docx 的 Anti-Goals 是业务意图层约束（不推荐产品/不催促用户）
3. 我考虑了「继续 F-17 编号」和「独立 AG-xx 编号」
4. 继续 F-17 的问题：混淆技术禁令和业务边界，Reviewer 无法区分检查维度
5. 因此选择 AG-xx 编号，因为两套编号对应两个层次（CLAUDE.md 技术层 / intent.md 业务层），互补不重叠

### 放弃的方案

| 方案描述 | 放弃原因 |
|----------|----------|
| 替换现有 AGENT-TEAMS-PLAYBOOK | 现有文件有详细的九步实操和成本分析，新 AGENT-PROTOCOL 补充交接契约但不替代方法论 |
| 将 PROMPT-VERSIONS 内容直接写入 PROMPT-PATTERNS | PATTERNS 是方法论（如何写好 Prompt），VERSIONS 是版本管理（具体 Prompt 历史），职责不同 |

### 遗留问题（下次会话继续）

- [ ] Page 6/7 缺少低分用户 redirect 守卫（直接 URL 可绕过低分路由）
- [ ] 低分 Page 8（improvement-plan）零 API 调用，后端无记录
- [ ] 进度条编号混乱（低分页面标记 Step 5/6/8 但内容不同）
- [ ] actionLayers 状态在评分变化时不重置（低分→正常/正常→低分）
- [ ] 低分流程缺少 Step 7 等价页
- [ ] PROMPT-GOLDEN-TESTS 所有 Golden Test 待首次运行
- [ ] @AiGenerated 注解类尚未在项目中创建

### Spec 变更建议

- 文件：test/mock-data.md 内容：补充「三个种子用户预期流程差异」说明表，明确 001/002 走正常流程、003 走低分修复流程
- 文件：domain/user-journey.md 内容：补充低分流程的页面流转定义（credit-optimization → credit-repair → improvement-plan → page9），当前仅定义了正常流程
---
