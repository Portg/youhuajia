# intent.md — 优化家业务意图 + Anti-Goals

> 最高优先级文件：每次 AI 生成任务第一优先读取

---

## 核心业务意图

**优化家是一个债务优化决策引擎**，帮助个人用户：
1. 清晰认知自身债务现状（APR、加权利率、月供压力）
2. 发现可优化空间（高息债务识别、还款顺序建议）
3. 获得可执行的分阶段优化方案（30/60/90 天行动计划）

**核心价值主张**：「看清 → 理解 → 行动」，不代替用户决策，只提供决策依据。

---

## Anti-Goals（绝对不做）

> 每条 Anti-Goal 必须有对应 @Test 方法验证

| 编号 | Anti-Goal | 违反后果 | 对应测试方法名 |
|------|-----------|----------|---------------|
| AG-01 | 不生成任何资金划转/放款/代扣代码 | 合规红线，项目被关停 | `should_throw_when_generating_fund_transfer_code` |
| AG-02 | 不向用户推荐具体金融产品或贷款渠道 | 合规红线，涉及金融营销牌照 | `should_not_contain_product_recommendation` |
| AG-03 | 不使用 float/double 进行金额/利率计算 | 浮点精度丢失导致金融事故 | `should_use_BigDecimal_for_all_financial_fields` |
| AG-04 | 不在日志中输出身份证号/银行卡号/手机号明文 | 合规红线，数据泄露 | `should_mask_sensitive_fields_in_log` |
| AG-05 | 不使用恐慌性表达催促用户行动 | 违反用户心理路径，破坏信任 | `should_reject_panic_expression_in_ai_copy` |
| AG-06 | 不在损失可视化页面(Page 4)出现"申请"按钮 | 违反渐进式漏斗设计 | `should_not_show_apply_button_on_page4` |
| AG-07 | 不对评分<60的用户展示"失败/不符合"等否定文案 | 一次失败体验=永久流失 | `should_show_repair_path_when_score_below_60` |
| AG-08 | 不调用大模型进行 APR/评分/加权利率计算 | 金融核心计算必须确定性可复现 | `should_not_invoke_llm_for_financial_calc` |
| AG-09 | 不硬编码评分权重/APR告警值等业务阈值 | 阈值变更需重新部署 | `should_load_thresholds_from_config` |
| AG-10 | 不在 Controller 层直接操作 Mapper | 必须经过 Service 层 | `should_not_call_mapper_from_controller` |
| AG-11 | AI 不得自行发明 openapi.yaml 未定义的接口或字段 | 接口契约是前后端合同 | `should_match_openapi_contract` |
| AG-12 | 不在用户界面使用专业术语（APR、加权利率、负债收入比） | 用户无法理解 | `should_use_plain_language_in_ui` |

---

## 数据敏感级别

| 字段 | 敏感级别 | 日志处理 | 响应处理 |
|------|----------|----------|----------|
| userId | 高敏 | 仅显示后4位 | 不返回前端 |
| phone | 高敏 | 中间4位脱敏 `138****1234` | 中间4位脱敏 |
| idCard | 高敏 | 中间8位脱敏 | 不返回前端 |
| bankCard | 高敏 | 仅显示后4位 | 仅显示后4位 |
| monthlyIncome | 中敏 | 可输出 | 正常返回 |
| debtAmount | 中敏 | 可输出 | 正常返回 |
| apr | 低敏 | 可输出 | 正常返回 |
| score | 低敏 | 可输出 | 正常返回 |

---

## 业务边界

### 做什么
- 债务信息录入（手动 + OCR 辅助）
- APR / 加权利率 / 月供压力 计算
- 五维评分（重构可行性评估）
- 规则引擎校验（数据完整性、异常值检测）
- AI 生成个性化优化建议（文案层面，非计算层面）
- 损失可视化（三年多付利息、月租等价物）
- PDF 报告导出

### 不做什么
- 资金划转、放款、代扣
- 征信查询、征信报告解读
- 具体产品推荐、渠道导流
- 用户间社交、社区功能
- 自动化还款操作

---

## 用户分群策略

> 匹配优先级：HIGH_DEBT > MORTGAGE_HEAVY > YOUNG_BORROWER > DEFAULT
> 权威来源：`engine/scoring-model.md` 第三节 + `engine/strategies/*.meta.yml`

| 分群 | 识别条件 | 策略差异 |
|------|----------|----------|
| HIGH_DEBT | 负债收入比 > 0.70 或负债笔数 >= 5 | DIR 权重 0.35（↑），7 档细粒度 DIR 分段，优先展示月供压力缓解方案 |
| MORTGAGE_HEAVY | 房贷笔数占总负债 > 50% | LIQ/CST 权重提升，DIR 权重降低，弱化房贷（无法优化），聚焦消费贷 |
| YOUNG_BORROWER | 负债笔数 <= 2 且平均借贷天数 < 365 | CST 权重 0.15（↑），鼓励性语调，强调信用积累 |
| DEFAULT | 以上都不命中 | 标准五维评分流程，均衡考虑各维度 |
