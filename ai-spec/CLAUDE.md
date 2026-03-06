# CLAUDE.md — 优化家项目 AI 协作总纲

> 本文件是所有 AI 代码生成的最高优先级约束。

## 一、项目概述

- **项目名称**：优化家（YouHuaJia），MVP V1.0
- **核心定位**：面向个人用户的债务优化决策引擎
- **技术栈**：Java 21 + Spring Boot 3.4 + MyBatis-Plus 3.5 + MySQL 8 + Redis + Flyway + Spring AI (DeepSeek) + React 18/TypeScript + Ant Design Mobile
- **ORM 选型**：MyBatis-Plus（不用 JPA），金融场景需精确控制 SQL
- **MVP 不引入**：ES、微服务框架、消息队列（用 Spring Event 替代）

---

## 二、绝对禁止

| 编号 | 禁止事项 | 原因 |
|------|----------|------|
| F-01 | 金额/利率计算使用 float/double | 必须 BigDecimal，浮点精度丢失导致金融事故 |
| F-02 | APR/评分/加权利率计算调用大模型 | 金融核心计算必须确定性可复现 |
| F-03 | 生成资金划转、放款、代扣代码 | MVP 不涉及资金流 |
| F-04 | 日志输出身份证号、银行卡号、手机号明文 | 合规红线 |
| F-05 | @Transactional 嵌套事务 | 事务传播语义复杂，易引发数据不一致 |
| F-06 | Hibernate auto-ddl（create/update） | 必须用 Flyway 迁移管理 Schema |
| F-07 | Controller 层直接操作 Mapper/Repository | 必须经过 Service 层 |
| F-08 | 枚举字段使用 ordinal 持久化 | 枚举顺序变动导致数据错乱，MyBatis-Plus 用 @EnumValue |
| F-09 | 硬编码业务阈值（评分权重、APR 告警值） | 评分权重/阈值放入 PMML 模型 + .meta.yml；APR/规则运维参数放入 application.yml |
| F-10 | 生成"模拟借贷""模拟放款"等擦边功能 | 合规红线 |
| F-11 | AI 文案使用恐慌性表达（"问题严重""赶紧行动""最后机会"） | 违反用户心理路径设计 |
| F-12 | 损失可视化页面（Page 4）出现"申请"按钮 | 违反渐进式漏斗设计 |
| F-13 | 评分 < 60 的用户展示"申请失败""不符合条件" | 一次失败体验 = 永久流失 |

---

## 三、强制约束

### 3.1 数据层

- 金额字段：`BigDecimal` + DB 列 `DECIMAL(18,4)`
- 比率字段：`BigDecimal` + DB 列 `DECIMAL(10,6)`
- 所有表必含：`id`(雪花算法 Long), `create_time`, `update_time`, `deleted`(逻辑删除)
- 并发控制表加 `version`（乐观锁），MyBatis-Plus `@Version`
- 时间列：`DATETIME(3)`，Java 用 `LocalDateTime`
- 枚举持久化：存字符串值，配合 `@EnumValue` 注解
- 字符集：utf8mb4
- 索引命名：`idx_{表名}_{字段名}`，唯一约束：`uk_{表名}_{字段名}`

### 3.2 接口层

遵循 Google API Design Guide（AIP-121~161），以下为项目特定规则：

- URL 前缀 `/api/v1/`，资源名词复数，自定义方法用冒号（`:confirm`, `:calculate`）
- PATCH 必须携带 `updateMask`（AIP-134），响应返回完整资源
- 分页用 `pageSize` + `pageToken`（AIP-158），不用 page/offset
- Create 请求支持 `requestId`（UUID4）防重复提交（AIP-155）
- 错误响应体对齐 `google.rpc.Status`：`{ error: { code, message, status, details }, traceId }`
- 所有入参 JSR 380 校验，所有方法 `@Operation` 注解
- 敏感字段响应时脱敏（手机号中间4位*，身份证中间8位*）

### 3.3 服务层

- 状态变更必须通过状态机驱动，不得直接 set 状态值
- 业务异常统一 `BizException(ErrorCode)`，禁止裸抛 RuntimeException
- 所有写操作必须有操作日志（OperationLog）
- 计算过程必须有中间结果日志（DEBUG 级别）

### 3.4 测试

- 每个 Service 必须有对应 `{ClassName}Test`
- 金融计算类测试覆盖率 >= 95%
- 覆盖：正常路径 + 边界值 + 异常输入 + 并发场景
- JUnit 5 + Mockito + AssertJ，方法命名：`should_{结果}_when_{条件}`

---

## 四、命名规范

遵循 Google AIP-140，美式英语，避免宽泛名称（info, data, instance）。

**类命名**：Entity 无后缀（`Debt`），DTO 加 `Request/Response`，Service/Impl/Controller/Mapper 按后缀。

**字段命名**：

| 场景 | 规范 | 正确 | 错误 |
|------|------|------|------|
| Java/JSON | camelCase | `createTime` | `create_time` |
| 数据库列 | snake_case | `create_time` | `createTime` |
| URL 路径 | kebab-case | `/finance-profiles` | `/financeProfiles` |
| 布尔字段 | 无 is 前缀 | `deleted` | `isDeleted` |
| 时间字段 | xxxTime 后缀 | `createTime` | `createdAt` |
| 集合字段 | 复数 | `debts` | `debtList` |

**Git Commit**：`feat(debt): 新增债务录入接口` / `fix(engine): 修复APR精度` / `test(scoring): 补充边界测试`

---

## 五、文档索引

按需读取 `ai-spec/` 下的规范文件：

| 文件 | 何时读取 |
|------|----------|
| `contracts/openapi.yaml` | 生成 Controller/DTO/前端调用时 |
| `contracts/error-codes.md` | 生成异常处理时 |
| `domain/entities.md` | 生成 Entity/Mapper/迁移脚本时 |
| `domain/state-machines.yaml` | 生成状态流转逻辑时 |
| `domain/evolution.md` | 设计数据模型和接口结构时 |
| `domain/user-journey.md` | **生成前端页面或 AI 文案时必读** |
| `client-spec.md` | Flutter 客户端规范 |
| `engine/apr-calc.md` | 生成 APR 计算引擎时 |
| `engine/scoring-model.md` | 生成评分引擎时（含 PMML 模型规范、用户分群、热加载） |
| `engine/engine-impl-spec.md` | 三大引擎详细实现规范 |
| `engine/rules.md` | 生成规则引擎时 |
| `test/test-matrix.md` | 生成测试时 |
| `test/mock-data.md` | 生成测试数据时 |
| `prompts/*.md` | 生成 OCR/建议/报告模块时 |
