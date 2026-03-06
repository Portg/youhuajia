# evolution.md — MVP → 资管星 2.0 演进约束

> MVP 阶段的所有设计决策必须兼容 2.0 演进路径。本文件定义预留点与禁区。

---

## 一、版本路线图

| 版本 | 时间 | 核心能力 | 架构形态 |
|------|------|----------|----------|
| V1.0 MVP | 2026.04 | 债务录入 + APR 计算 + 画像 + 评分 + 建议 | 单体应用 |
| V2.0 | 2026.06 | OCR 银行直连 + 重组申请 + 数字人民币 + 分享获利 | 微服务拆分 |
| V2.1 | 2026.08 | 家庭协作 + 成长体系 + 赚钱中心 | 微服务 + 开放平台 |
| V2.2 | 2026.10 | 数据资产变现 + 券商/保险对接 + 高级 AI | 微服务 + 生态 |

---

## 二、数据库预留约束

### 2.1 必须预留的字段（MVP 阶段 nullable，不实现业务逻辑）

```sql
-- t_user 表
family_id         BIGINT        NULL COMMENT '家庭ID，V2.1启用',
member_level      VARCHAR(20)   NULL COMMENT '会员等级，V2.1启用',
growth_value      INT           DEFAULT 0 COMMENT '成长值，V2.1启用',
digital_wallet_id VARCHAR(64)   NULL COMMENT '数字人民币钱包ID，V2.0启用',

-- t_debt 表
family_id         BIGINT        NULL COMMENT '归属家庭ID，V2.1启用',
bank_sync_id      VARCHAR(64)   NULL COMMENT '银行同步关联ID，V2.0启用',
sync_source       VARCHAR(20)   DEFAULT 'MANUAL' COMMENT '数据来源：MANUAL/OCR/BANK_API',

-- t_finance_profile 表
family_profile_id BIGINT        NULL COMMENT '家庭画像ID，V2.1启用',
asset_total       DECIMAL(18,4) NULL COMMENT '总资产，V2.0启用',
net_worth         DECIMAL(18,4) NULL COMMENT '净资产，V2.0启用',
```

### 2.2 必须预留的表占位（MVP 只建表不写业务）

```sql
-- V2.0 需要的表（MVP 建空结构）
CREATE TABLE t_asset_account (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    account_type VARCHAR(30) NOT NULL COMMENT 'BANK_CARD/FUND/HOUSE/VEHICLE/DIGITAL',
    account_name VARCHAR(100),
    estimated_value DECIMAL(18,4),
    -- 标准字段
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0
) COMMENT '资产账户表（V2.0启用）';

CREATE TABLE t_restructure_application (
    id BIGINT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    status VARCHAR(30) NOT NULL,
    -- 标准字段
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted TINYINT DEFAULT 0,
    version INT DEFAULT 0
) COMMENT '重组申请表（V2.0启用）';

-- V2.1 需要的表
CREATE TABLE t_family (
    id BIGINT PRIMARY KEY,
    family_name VARCHAR(50),
    owner_user_id BIGINT NOT NULL,
    invite_code VARCHAR(20) UNIQUE,
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted TINYINT DEFAULT 0
) COMMENT '家庭表（V2.1启用）';

CREATE TABLE t_family_member (
    id BIGINT PRIMARY KEY,
    family_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL COMMENT 'OWNER/ADMIN/VIEWER',
    create_time DATETIME(3) NOT NULL,
    update_time DATETIME(3) NOT NULL,
    deleted TINYINT DEFAULT 0
) COMMENT '家庭成员表（V2.1启用）';
```

### 2.3 金额精度统一规则

```
所有金额字段：DECIMAL(18,4)
  - 整数位14位，最大支持 99,999,999,999,999.9999（百万亿级）
  - 小数位4位，兼容数字人民币最小精度
  - Java 侧统一 BigDecimal，RoundingMode.HALF_UP
  - 前端展示时四舍五入到分（2位小数）
```

---

## 三、接口路径预留（遵循 Google AIP 资源导向设计）

```
MVP 实现：
  /api/v1/auth/*                   # 认证（非资源型）
  /api/v1/debts                    # 债务资源（List/Create）
  /api/v1/debts/{id}               # 债务资源（Get/Update/Delete）
  /api/v1/debts/{id}:confirm       # 自定义方法
  /api/v1/debts/{id}:includeInProfile
  /api/v1/finance-profiles/me      # 画像资源（单例）
  /api/v1/finance-profiles/me:calculate
  /api/v1/incomes:batchCreate      # 收入资源
  /api/v1/ocr-tasks                # OCR 任务资源
  /api/v1/ocr-tasks/{id}:confirm
  /api/v1/reports                  # 报告资源
  /api/v1/reports:generate
  /api/v1/engine/apr:calculate     # 计算工具（无状态）

V2.0 预留（MVP 不实现，路由命名空间不得占用）：
  /api/v1/assets                   # 资产资源
  /api/v1/asset-accounts           # 资产账户资源
  /api/v1/restructure-applications # 重组申请资源
  /api/v1/bank-authorizations      # 银行授权资源
  /api/v1/wallets                  # 数字人民币钱包资源

V2.1 预留：
  /api/v1/families                 # 家庭资源
  /api/v1/families/{id}/members    # 家庭成员子资源
  /api/v1/growth-records           # 成长记录资源
  /api/v1/earn-opportunities       # 增收机会资源
  /api/v1/share-materials          # 分享素材资源
  /api/v1/commissions              # 佣金资源
```

---

## 四、枚举扩展预留

```java
// MVP 实现的枚举必须为后续值预留空间

public enum DebtSourceType {
    MANUAL,        // MVP
    OCR,           // MVP
    BANK_API,      // V2.0
    CREDIT_REPORT  // V2.0
}

public enum RiskLevel {
    LOW,           // MVP
    MEDIUM,        // MVP
    HIGH,          // MVP
    CRITICAL       // V2.0（资管星新增的超高风险等级）
}

public enum UserRole {
    USER,          // MVP
    FAMILY_OWNER,  // V2.1
    FAMILY_ADMIN,  // V2.1
    FAMILY_VIEWER  // V2.1
}

// 资管星 2.0 新增的资产类型枚举
public enum AssetType {
    BANK_CARD,     // V2.0
    FUND,          // V2.0
    HOUSE,         // V2.0
    VEHICLE,       // V2.0
    DIGITAL,       // V2.0 数字资产
    INSURANCE,     // V2.2
    STOCK          // V2.2
}
```

---

## 五、架构拆分预案

```
MVP 阶段：单体应用（所有模块在一个 Spring Boot 项目中）
  但包结构按微服务边界划分，确保未来可直接拆分

V2.0 拆分方案：
  youhua-gateway          → API 网关（Spring Cloud Gateway 或 Nginx）
  youhua-auth             → com.youhua.auth（从 common 中拆出）
  youhua-debt             → com.youhua.debt + com.youhua.engine
  youhua-asset            → com.youhua.asset（新建）
  youhua-profile          → com.youhua.profile
  youhua-ai               → com.youhua.ai
  youhua-restructure      → com.youhua.restructure（新建）

关键约束：
  - MVP 阶段模块间调用必须通过 Service 接口，不得直接注入其他模块的 Repository
  - 跨模块数据查询通过 DTO 传递，不得传递 Entity
  - 这样 V2.0 拆分时只需将 Service 调用改为 Feign/Dubbo 调用
```

---

## 六、配置中心预留

```yaml
# application.yml 结构约定

youhua:
  engine:
    apr:
      warning-threshold: 36.0       # APR 告警阈值（%）
      max-allowed: 100.0            # APR 上限
    scoring:
      weights:                       # 评分权重（可热更新）
        debt-income-ratio: 0.30
        weighted-apr: 0.25
        liquidity: 0.15
        overdue: 0.20
        credit-stability: 0.10
      restructure-threshold: 60     # 重组推荐阈值
  ai:
    deepseek:
      base-url: ${DEEPSEEK_BASE_URL}
      api-key: ${DEEPSEEK_API_KEY}
      ocr-model: deepseek-chat
      suggestion-model: deepseek-chat
      timeout-seconds: 30
      max-retries: 3
  # V2.0 预留配置节点
  bank:
    enabled: false                  # MVP 关闭
  asset:
    enabled: false                  # MVP 关闭
  family:
    enabled: false                  # V2.1 关闭
```

---

## 七、MVP 阶段的明确边界

### 做什么

- 手机号注册登录（验证码）
- 手动债务录入（表单）
- OCR 基础识别（合同/账单图片 → 字段提取）
- APR 实际年化计算
- 加权 APR 计算
- 重组可行性评分（规则 + 模型）
- 财务画像生成
- 优化建议生成（AI 文案）
- 报告导出（PDF）
- 数据加密存储
- 操作日志

### 绝对不做

- 银行 API 直连（V2.0）
- 重组申请流转（V2.0）
- 资金划转/放款（V2.0）
- 数字人民币对接（V2.0）
- 佣金/分享体系（V2.0）
- 家庭协作（V2.1）
- 成长等级体系（V2.1）
- 增收匹配/零工对接（V2.1）
- 券商/保险对接（V2.2）
