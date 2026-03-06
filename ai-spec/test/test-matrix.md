# test-matrix.md — 测试用例矩阵总表

> 本文件汇总所有模块的测试场景，AI 生成测试代码时直接引用。
> 每个用例都有明确的预期结果，不允许模糊断言。

---

## 一、APR 计算测试（详见 engine/apr-calc.md）

此处为索引，完整用例在 apr-calc.md 第五节。

---

## 二、评分模型测试（详见 engine/scoring-model.md）

此处为索引，完整用例在 scoring-model.md 第七节。

---

## 三、状态机测试

### 3.1 债务录入状态转换

| 用例ID | 初始状态 | 事件 | 预期目标状态 | 前置条件 |
|--------|----------|------|-------------|----------|
| SM-D01 | DRAFT | SUBMIT | SUBMITTED | principal>0, loanDays>0, creditor非空 |
| SM-D02 | DRAFT | SUBMIT | 抛异常 | principal=0（guard失败） |
| SM-D03 | SUBMITTED | START_OCR | OCR_PROCESSING | sourceType=OCR, fileUrl非空 |
| SM-D04 | SUBMITTED | MANUAL_CONFIRM | CONFIRMED | sourceType=MANUAL, 必填字段完整 |
| SM-D05 | OCR_PROCESSING | OCR_SUCCESS | PENDING_CONFIRM | confidenceScore>=0 |
| SM-D06 | OCR_PROCESSING | OCR_FAIL | OCR_FAILED | |
| SM-D07 | PENDING_CONFIRM | USER_CONFIRM | CONFIRMED | |
| SM-D08 | PENDING_CONFIRM | USER_REJECT | DRAFT | |
| SM-D09 | CONFIRMED | INCLUDE_IN_PROFILE | IN_PROFILE | |
| SM-D10 | CONFIRMED | USER_EDIT | DRAFT | |
| SM-D11 | IN_PROFILE | USER_EDIT | DRAFT | 触发画像重算 |
| SM-D12 | IN_PROFILE | USER_DELETE | DELETED | 触发画像重算 |
| SM-D13 | OCR_FAILED | RETRY_OCR | OCR_PROCESSING | retryCount<3 |
| SM-D14 | OCR_FAILED | RETRY_OCR | 抛异常 | retryCount=3（达上限） |
| SM-D15 | OCR_FAILED | SWITCH_TO_MANUAL | DRAFT | sourceType变为MANUAL |
| SM-D16 | DRAFT | USER_DELETE | 抛异常 | DRAFT不允许直接删除 |

### 3.2 非法状态转换（负面测试）

| 用例ID | 初始状态 | 事件 | 预期 |
|--------|----------|------|------|
| SM-NEG01 | DRAFT | OCR_SUCCESS | 抛 BizException(402006) |
| SM-NEG02 | CONFIRMED | OCR_FAIL | 抛 BizException(402006) |
| SM-NEG03 | DELETED | SUBMIT | 抛 BizException(402006) |
| SM-NEG04 | IN_PROFILE | INCLUDE_IN_PROFILE | 抛 BizException(402006) |

---

## 四、接口测试

### 4.1 债务创建接口

| 用例ID | 场景 | 输入 | 预期HTTP | 预期Code |
|--------|------|------|----------|----------|
| API-DC01 | 正常创建 | 完整必填字段 | 200 | 200 |
| API-DC02 | 缺少creditor | creditor=null | 200 | 402005 |
| API-DC03 | 本金为0 | principal=0 | 200 | 402002 |
| API-DC04 | 本金为负 | principal=-1000 | 200 | 402002 |
| API-DC05 | 还款额<本金 | total=9000, principal=10000 | 200 | 402003 |
| API-DC06 | 天数为0 | loanDays=0 | 200 | 402004 |
| API-DC07 | 未登录 | 无Token | 401 | 401005 |
| API-DC08 | Token过期 | 过期Token | 401 | 401004 |
| API-DC09 | 债务超50笔 | 已有50笔+新建 | 200 | 402009 |

### 4.2 画像计算接口

| 用例ID | 场景 | 前置 | 预期Code |
|--------|------|------|----------|
| API-PC01 | 正常计算 | 有2笔CONFIRMED债务+收入 | 200 |
| API-PC02 | 无确认债务 | 0笔CONFIRMED债务 | 403001 |
| API-PC03 | 无收入数据 | 有债务无收入 | 200（WARN） |
| API-PC04 | 频繁触发 | 1小时内11次 | 403003 |

### 4.3 OCR 接口

| 用例ID | 场景 | 输入 | 预期Code |
|--------|------|------|----------|
| API-OCR01 | 正常上传 | JPG+CONTRACT | 200 |
| API-OCR02 | 不支持格式 | .doc 文件 | 405001 |
| API-OCR03 | 超大文件 | 15MB | 405002 |
| API-OCR04 | 查询结果-处理中 | PROCESSING状态 | 200, status=PROCESSING |
| API-OCR05 | 查询结果-成功 | SUCCESS状态 | 200, 含extractedFields |
| API-OCR06 | 确认OCR | taskId有效 | 200, 生成Debt记录 |
| API-OCR07 | 重试超限 | retryCount=3 | 405006 |

---

## 五、并发测试

| 用例ID | 场景 | 操作 | 预期 |
|--------|------|------|------|
| CC-01 | 同一债务同时修改 | 两个请求同version | 一个成功，一个返回402007 |
| CC-02 | 画像计算+债务删除 | 同时触发 | 画像计算基于快照，不受影响 |
| CC-03 | 同一用户并发创建 | 10个并发请求 | 全部成功或受限于50笔上限 |

---

## 六、安全测试

| 用例ID | 场景 | 预期 |
|--------|------|------|
| SEC-01 | A用户访问B用户的债务 | 返回402001（不暴露是否存在） |
| SEC-02 | SQL注入 creditor字段 | 参数化查询，不受影响 |
| SEC-03 | XSS remark字段 | 入库转义，返回时安全 |
| SEC-04 | 响应中手机号脱敏 | 138****1234 |
| SEC-05 | 日志中敏感信息 | 不输出明文手机号/身份证号 |
