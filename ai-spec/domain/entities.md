# entities.md — 领域实体与数据模型定义

> 本文件定义 MVP 阶段所有核心实体的字段、约束和关系。
> AI 生成 Entity 类、Repository、Flyway 迁移脚本时必须参照本文件。
> 
> **命名规范遵循 Google AIP**：
> - 数据库列名：snake_case（如 create_time）
> - Java 字段名：camelCase（如 createTime）
> - JSON 字段名：camelCase
> - 时间字段统一用 xxxTime 后缀（不用 xxxAt）
> - 布尔字段不加 is 前缀
> - 每个资源在 API 响应中包含 `name` 字段（资源名称，如 "debts/12345"）
>   该字段不存数据库，由 Service 层拼接

---

## 一、实体关系总览

```
User (1) ──→ (N) Debt
User (1) ──→ (1) FinanceProfile
User (1) ──→ (N) IncomeRecord
Debt (N) ──→ (1) FinanceProfile（通过 userId 关联，画像聚合债务数据）
User (1) ──→ (N) ScoreRecord（评分记录，关联策略版本）
User (1) ──→ (N) OperationLog
User (1) ──→ (N) OcrTask
OcrTask (1) ──→ (0..1) Debt（OCR 成功后关联生成的债务记录）
```

---

## 二、实体详细定义

### 2.1 User — 用户

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, 雪花算法 | 用户ID |
| phone | VARCHAR(20) | NOT NULL, UK | 手机号（加密存储） |
| phone_hash | VARCHAR(64) | NOT NULL, UK | 手机号哈希（用于查询） |
| nickname | VARCHAR(50) | | 昵称 |
| avatar_url | VARCHAR(256) | | 头像地址 |
| status | VARCHAR(20) | NOT NULL, DEFAULT 'ACTIVE' | ACTIVE / FROZEN / CANCELLED |
| last_login_at | DATETIME(3) | | 最后登录时间 |
| last_login_ip | VARCHAR(45) | | 最后登录IP |
| device_fingerprint | VARCHAR(128) | | 设备指纹 |
| family_id | BIGINT | NULL | 家庭ID（V2.1预留） |
| member_level | VARCHAR(20) | NULL | 会员等级（V2.1预留） |
| growth_value | INT | DEFAULT 0 | 成长值（V2.1预留） |
| digital_wallet_id | VARCHAR(64) | NULL | 数字人民币钱包（V2.0预留） |
| create_time | DATETIME(3) | NOT NULL | |
| update_time | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除 |

**索引**：
- `uk_user_phone_hash (phone_hash)` — 唯一索引，用于登录查询
- `idx_user_status (status)` — 普通索引

---

### 2.2 Debt — 债务

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 债务ID |
| user_id | BIGINT | NOT NULL, IDX | 用户ID |
| creditor | VARCHAR(100) | NOT NULL | 债权机构名称 |
| debt_type | VARCHAR(30) | NOT NULL | CREDIT_CARD / CONSUMER_LOAN / BUSINESS_LOAN / MORTGAGE / OTHER |
| principal | DECIMAL(18,4) | NOT NULL | 借款本金 |
| total_repayment | DECIMAL(18,4) | NOT NULL | 总还款额（含利息+费用） |
| nominal_rate | DECIMAL(10,6) | | 名义利率（合同标注） |
| apr | DECIMAL(10,6) | | 实际年化利率（系统计算） |
| loan_days | INT | NOT NULL | 借款天数 |
| monthly_payment | DECIMAL(18,4) | | 月供金额 |
| remaining_principal | DECIMAL(18,4) | | 剩余本金 |
| remaining_periods | INT | | 剩余期数 |
| start_date | DATE | | 借款起始日 |
| end_date | DATE | | 借款到期日 |
| overdue_status | VARCHAR(20) | DEFAULT 'NORMAL' | NORMAL / OVERDUE_30 / OVERDUE_60 / OVERDUE_90_PLUS |
| overdue_days | INT | DEFAULT 0 | 逾期天数 |
| source_type | VARCHAR(20) | NOT NULL | MANUAL / OCR / BANK_API |
| ocr_task_id | BIGINT | NULL | OCR 任务ID（如有） |
| confidence_score | DECIMAL(5,2) | NULL | OCR 识别置信度（0-100） |
| status | VARCHAR(20) | NOT NULL | DRAFT / SUBMITTED / OCR_PROCESSING / PENDING_CONFIRM / CONFIRMED / IN_PROFILE |
| remark | VARCHAR(500) | | 备注 |
| family_id | BIGINT | NULL | 家庭ID（V2.1预留） |
| bank_sync_id | VARCHAR(64) | NULL | 银行同步ID（V2.0预留） |
| create_time | DATETIME(3) | NOT NULL | |
| update_time | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | DEFAULT 0 | |
| version | INT | DEFAULT 0 | 乐观锁 |

**索引**：
- `idx_debt_user_id (user_id)` — 按用户查询
- `idx_debt_user_status (user_id, status)` — 按用户+状态查询
- `idx_debt_creditor (creditor)` — 按机构查询

**业务规则**：
- `principal > 0`
- `total_repayment >= principal`
- `loan_days > 0`
- `apr` 由系统计算，用户不可直接修改

---

### 2.3 FinanceProfile — 财务画像

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 画像ID |
| user_id | BIGINT | NOT NULL, UK | 用户ID（一对一） |
| total_debt | DECIMAL(18,4) | NOT NULL | 总负债 |
| debt_count | INT | NOT NULL | 债务笔数 |
| weighted_apr | DECIMAL(10,6) | NOT NULL | 加权年化利率 |
| monthly_payment | DECIMAL(18,4) | NOT NULL | 月供总额 |
| monthly_income | DECIMAL(18,4) | | 月收入（用户填写） |
| debt_income_ratio | DECIMAL(10,6) | | 负债收入比 |
| liquidity_score | DECIMAL(5,2) | | 资产流动性评分(0-100) |
| restructure_score | DECIMAL(5,2) | | 重组可行性评分(0-100) |
| risk_level | VARCHAR(20) | | LOW / MEDIUM / HIGH / CRITICAL |
| overdue_count | INT | DEFAULT 0 | 逾期笔数 |
| highest_apr_debt_id | BIGINT | NULL | 最高利率债务ID |
| score_detail_json | JSON | | 评分维度明细（JSON结构） |
| last_calculated_at | DATETIME(3) | | 最近一次计算时间 |
| asset_total | DECIMAL(18,4) | NULL | 总资产（V2.0预留） |
| net_worth | DECIMAL(18,4) | NULL | 净资产（V2.0预留） |
| family_profile_id | BIGINT | NULL | 家庭画像ID（V2.1预留） |
| create_time | DATETIME(3) | NOT NULL | |
| update_time | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | DEFAULT 0 | |
| version | INT | DEFAULT 0 | 乐观锁 |

**索引**：
- `uk_profile_user_id (user_id)` — 唯一索引
- `idx_profile_risk_level (risk_level)` — 按风险等级查询

**score_detail_json 示例结构**：
```json
{
  "debtIncomeRatio": { "value": 0.62, "score": 75, "weight": 0.30, "rank": 1 },
  "weightedApr": { "value": 21.4, "score": 60, "weight": 0.25, "rank": 2 },
  "liquidity": { "value": 45, "score": 45, "weight": 0.15, "rank": 4 },
  "overdue": { "value": 0, "score": 90, "weight": 0.20, "rank": 3 },
  "creditStability": { "value": 80, "score": 80, "weight": 0.10, "rank": 5 }
}
```

---

### 2.4 IncomeRecord — 收入记录

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL, IDX | 用户ID |
| income_type | VARCHAR(30) | NOT NULL | SALARY / BUSINESS / FREELANCE / INVESTMENT / OTHER |
| amount | DECIMAL(18,4) | NOT NULL | 月收入金额 |
| primary | TINYINT | DEFAULT 0 | 是否主要收入来源 |
| verification_status | VARCHAR(20) | DEFAULT 'UNVERIFIED' | UNVERIFIED / VERIFIED |
| create_time | DATETIME(3) | NOT NULL | |
| update_time | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | DEFAULT 0 | |

---

### 2.5 OcrTask — OCR 识别任务

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | 任务ID |
| user_id | BIGINT | NOT NULL, IDX | 用户ID |
| file_url | VARCHAR(512) | NOT NULL | 上传文件地址（MinIO） |
| file_type | VARCHAR(20) | NOT NULL | CONTRACT / BILL / SMS_SCREENSHOT |
| status | VARCHAR(20) | NOT NULL | PENDING / PROCESSING / SUCCESS / FAILED |
| raw_result_json | JSON | | 原始识别结果 |
| extracted_fields_json | JSON | | 提取的结构化字段 |
| confidence_score | DECIMAL(5,2) | | 整体置信度 |
| error_message | VARCHAR(500) | | 失败原因 |
| retry_count | INT | DEFAULT 0 | 重试次数 |
| debt_id | BIGINT | NULL | 关联的债务ID（确认后生成） |
| create_time | DATETIME(3) | NOT NULL | |
| update_time | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | DEFAULT 0 | |

**extracted_fields_json 示例**：
```json
{
  "creditor": { "value": "招商银行", "confidence": 0.95 },
  "principal": { "value": 50000.00, "confidence": 0.88 },
  "totalRepayment": { "value": 56200.00, "confidence": 0.82 },
  "nominalRate": { "value": 0.045, "confidence": 0.91 },
  "loanDays": { "value": 365, "confidence": 0.85 },
  "startDate": { "value": "2025-06-01", "confidence": 0.78 }
}
```

---

### 2.6 OperationLog — 操作日志

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL, IDX | 用户ID |
| module | VARCHAR(30) | NOT NULL | DEBT / PROFILE / ENGINE / AI / AUTH |
| action | VARCHAR(50) | NOT NULL | CREATE / UPDATE / DELETE / CALCULATE / GENERATE |
| target_type | VARCHAR(30) | | 操作对象类型 |
| target_id | BIGINT | | 操作对象ID |
| detail_json | JSON | | 操作详情 |
| ip | VARCHAR(45) | | 操作IP |
| user_agent | VARCHAR(256) | | User-Agent |
| create_time | DATETIME(3) | NOT NULL | |

**注意**：操作日志表不设 update_time 和 deleted，日志只追加不修改不删除。

---

### 2.7 OptimizationReport — 优化报告

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK | |
| user_id | BIGINT | NOT NULL, IDX | 用户ID |
| profile_snapshot_json | JSON | NOT NULL | 生成时的画像快照 |
| priority_list_json | JSON | NOT NULL | 优化优先级排序 |
| action_plan_json | JSON | NOT NULL | 90天行动计划 |
| ai_summary | TEXT | | AI 生成的文字建议 |
| risk_warnings | JSON | | 风险提示列表 |
| explainability_json | JSON | | 可解释性数据 |
| report_version | INT | DEFAULT 1 | 报告版本（同一用户多次测评） |
| create_time | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | DEFAULT 0 | |

---

### 2.8 ScoreRecord — 评分记录

| 字段 | 类型 | 约束 | 说明 |
|------|------|------|------|
| id | BIGINT | PK, 雪花算法 | |
| user_id | BIGINT | NOT NULL, IDX | 用户ID |
| strategy_name | VARCHAR(100) | NOT NULL | 使用的策略名（如"稳健策略"） |
| strategy_version | VARCHAR(50) | NOT NULL | .meta.yml 中的 version |
| segment | VARCHAR(20) | NOT NULL | 命中的分群（DEFAULT/HIGH_DEBT/MORTGAGE_HEAVY/YOUNG_BORROWER） |
| final_score | DECIMAL(5,2) | NOT NULL | 最终得分 0-100 |
| risk_level | VARCHAR(20) | NOT NULL | LOW/MEDIUM/HIGH/CRITICAL |
| recommendation | VARCHAR(30) | NOT NULL | RESTRUCTURE_RECOMMENDED/OPTIMIZE_FIRST/CREDIT_BUILDING |
| dimension_scores_json | JSON | | 各维度 partialScore |
| reason_codes_json | JSON | | ReasonCode1~3 |
| input_snapshot_json | JSON | | ScoreInput 快照 |
| score_delta | DECIMAL(5,2) | | 与该用户上次评分的差值 |
| create_time | DATETIME(3) | NOT NULL | |
| deleted | TINYINT | DEFAULT 0 | 逻辑删除 |

**索引**：
- `idx_score_record_user_id (user_id)` — 按用户查询
- `idx_score_record_strategy (strategy_name, strategy_version)` — 按策略版本聚合分析

**注意**：评分记录表无 `update_time` 和 `version`，记录只追加不修改。

---

**action_plan_json 示例**：
```json
{
  "phases": [
    {
      "phase": 1,
      "title": "第1-30天：紧急止血",
      "actions": [
        {
          "type": "REPAY_PRIORITY",
          "debtId": 10001,
          "description": "优先清偿XX消费贷（APR 45.2%）",
          "expectedSaving": 3200.00
        }
      ]
    },
    {
      "phase": 2,
      "title": "第31-60天：结构优化",
      "actions": []
    },
    {
      "phase": 3,
      "title": "第61-90天：巩固提升",
      "actions": []
    }
  ]
}
```
