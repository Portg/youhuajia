# mock-data.md — 测试 Mock 数据定义

> 用于后端 Seed 脚本、前端 Mock Server、自动化测试。
> 每个用户画像代表一种典型场景。

---

## 一、用户画像定义

### 用户A：轻度负债白领（健康型）

```json
{
  "user": {
    "id": 100001,
    "phone": "13800000001",
    "nickname": "小明"
  },
  "incomes": [
    { "type": "SALARY", "amount": 15000.00, "isPrimary": true }
  ],
  "debts": [
    {
      "creditor": "招商银行信用卡",
      "debtType": "CREDIT_CARD",
      "principal": 30000.00,
      "totalRepayment": 31500.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "花呗",
      "debtType": "CONSUMER_LOAN",
      "principal": 8000.00,
      "totalRepayment": 8800.00,
      "loanDays": 180,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "京东白条",
      "debtType": "CONSUMER_LOAN",
      "principal": 12000.00,
      "totalRepayment": 13200.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL",
      "sourceType": "OCR",
      "confidenceScore": 88.5,
      "status": "IN_PROFILE"
    }
  ],
  "expectedProfile": {
    "totalDebt": 50000.00,
    "debtCount": 3,
    "weightedAPR": 8.44,
    "monthlyPayment": 3700.00,
    "debtIncomeRatio": 0.2467,
    "riskLevel": "LOW",
    "restructureScore": 82
  }
}
```

---

### 用户B：中度负债创业者（需关注型）

```json
{
  "user": {
    "id": 100002,
    "phone": "13800000002",
    "nickname": "老王"
  },
  "incomes": [
    { "type": "BUSINESS", "amount": 20000.00, "isPrimary": true },
    { "type": "FREELANCE", "amount": 3000.00, "isPrimary": false }
  ],
  "debts": [
    {
      "creditor": "工商银行经营贷",
      "debtType": "BUSINESS_LOAN",
      "principal": 200000.00,
      "totalRepayment": 240000.00,
      "loanDays": 730,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "微众银行",
      "debtType": "CONSUMER_LOAN",
      "principal": 50000.00,
      "totalRepayment": 62000.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL",
      "sourceType": "OCR",
      "confidenceScore": 75.0,
      "status": "IN_PROFILE"
    },
    {
      "creditor": "平安普惠",
      "debtType": "CONSUMER_LOAN",
      "principal": 30000.00,
      "totalRepayment": 39000.00,
      "loanDays": 365,
      "overdueStatus": "OVERDUE_30",
      "overdueDays": 15,
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    }
  ],
  "expectedProfile": {
    "totalDebt": 280000.00,
    "debtCount": 3,
    "weightedAPR": 16.57,
    "monthlyPayment": 12200.00,
    "debtIncomeRatio": 0.5304,
    "riskLevel": "MEDIUM",
    "restructureScore": 62
  }
}
```

---

### 用户C：重度负债个体户（高风险型）

```json
{
  "user": {
    "id": 100003,
    "phone": "13800000003",
    "nickname": "阿强"
  },
  "incomes": [
    { "type": "BUSINESS", "amount": 12000.00, "isPrimary": true }
  ],
  "debts": [
    {
      "creditor": "某网贷平台A",
      "debtType": "CONSUMER_LOAN",
      "principal": 50000.00,
      "totalRepayment": 72000.00,
      "loanDays": 365,
      "overdueStatus": "OVERDUE_60",
      "overdueDays": 45,
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "某网贷平台B",
      "debtType": "CONSUMER_LOAN",
      "principal": 80000.00,
      "totalRepayment": 120000.00,
      "loanDays": 365,
      "overdueStatus": "OVERDUE_90_PLUS",
      "overdueDays": 120,
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "信用卡A",
      "debtType": "CREDIT_CARD",
      "principal": 60000.00,
      "totalRepayment": 78000.00,
      "loanDays": 365,
      "overdueStatus": "OVERDUE_30",
      "overdueDays": 20,
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "信用卡B",
      "debtType": "CREDIT_CARD",
      "principal": 40000.00,
      "totalRepayment": 52000.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL",
      "sourceType": "OCR",
      "confidenceScore": 62.0,
      "status": "IN_PROFILE"
    },
    {
      "creditor": "经营贷",
      "debtType": "BUSINESS_LOAN",
      "principal": 150000.00,
      "totalRepayment": 195000.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "亲友借款",
      "debtType": "OTHER",
      "principal": 100000.00,
      "totalRepayment": 100000.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    },
    {
      "creditor": "某分期平台",
      "debtType": "CONSUMER_LOAN",
      "principal": 20000.00,
      "totalRepayment": 28000.00,
      "loanDays": 180,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    }
  ],
  "expectedProfile": {
    "totalDebt": 500000.00,
    "debtCount": 7,
    "weightedAPR": 29.0,
    "monthlyPayment": 18500.00,
    "debtIncomeRatio": 1.5417,
    "riskLevel": "CRITICAL",
    "restructureScore": 22
  }
}
```

---

### 用户D：零利息特殊场景

```json
{
  "user": {
    "id": 100004,
    "phone": "13800000004",
    "nickname": "小李"
  },
  "incomes": [
    { "type": "SALARY", "amount": 8000.00, "isPrimary": true }
  ],
  "debts": [
    {
      "creditor": "花呗免息分期",
      "debtType": "CONSUMER_LOAN",
      "principal": 6000.00,
      "totalRepayment": 6000.00,
      "loanDays": 90,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "IN_PROFILE"
    }
  ],
  "expectedProfile": {
    "totalDebt": 6000.00,
    "debtCount": 1,
    "weightedAPR": 0.0,
    "monthlyPayment": 2000.00,
    "debtIncomeRatio": 0.25,
    "riskLevel": "LOW",
    "restructureScore": 88
  }
}
```

---

### 用户E：仅草稿未确认（边界场景）

```json
{
  "user": {
    "id": 100005,
    "phone": "13800000005",
    "nickname": "测试用户"
  },
  "incomes": [],
  "debts": [
    {
      "creditor": "测试机构",
      "debtType": "CONSUMER_LOAN",
      "principal": 10000.00,
      "totalRepayment": 12000.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL",
      "sourceType": "MANUAL",
      "status": "DRAFT"
    }
  ],
  "expectedProfile": null,
  "expectedError": "403001 - 暂无已确认的债务数据"
}
```

---

## 二、三种用户的漏斗页面路由表

> 根据评分不同，Step 5-8 走不同页面路径。Step 1-4 和 Step 9 共享。

```
╔══════╦══════════════════════════════════════════════════════════════════════════════════╗
║ Step ║  用户A（82分）/ 用户B（62分）          ║  用户C（22分，低分路径）                    ║
║      ║  正常流程（score >= 60）                ║  低分流程（score < 60）                     ║
╠══════╬═══════════════════════════════════════╬════════════════════════════════════════════╣
║  1   ║ pages/page1-safe-entry/index          ║ （共享）                                    ║
║  2   ║ pages/page2-pressure-check/index      ║ （共享）                                    ║
║  3   ║ pages/page3-debt-input/index          ║ （共享）                                    ║
║  4   ║ pages/page4-loss-report/index         ║ （共享，CTA 导航到不同 Step 5）              ║
╠══════╬═══════════════════════════════════════╬════════════════════════════════════════════╣
║  5   ║ pages/page5-optimization/index        ║ pages/low-score/credit-optimization        ║
║      ║ "好消息是，你有优化空间"                ║ "当前更适合优化信用结构"                      ║
╠══════╬═══════════════════════════════════════╬════════════════════════════════════════════╣
║  6   ║ pages/page6-rate-simulator/index      ║ pages/low-score/credit-repair              ║
║      ║ 利率模拟器（滑块交互）                  ║ 信用修复路线图（30/60/90天）                  ║
║      ║ ⚠ onMounted 低分守卫 → redirect       ║                                             ║
╠══════╬═══════════════════════════════════════╬════════════════════════════════════════════╣
║  7   ║ pages/page7-risk-assessment/index     ║ pages/low-score/risk-faq                   ║
║      ║ 正常流程 FAQ（征信/评分/费用）          ║ 低分专属 FAQ（为什么不适合/30天有效吗/效果/费用）║
║      ║ ⚠ onMounted 低分守卫 → redirect       ║                                             ║
╠══════╬═══════════════════════════════════════╬════════════════════════════════════════════╣
║  8   ║ pages/page8-action-layers/index       ║ pages/low-score/improvement-plan           ║
║      ║ 四层递进（资料清单/整理/预审/提交）      ║ 三层递进（改善计划/还款提醒/预约评估）         ║
╠══════╬═══════════════════════════════════════╬════════════════════════════════════════════╣
║  9   ║ pages/page9-companion/index           ║ （共享）                                    ║
╚══════╩═══════════════════════════════════════╩════════════════════════════════════════════╝
```

### 首页 continueAssessment() 路由映射

```javascript
// 正常流程
const stepPageMap = {
  1: '/pages/page1-safe-entry/index',
  2: '/pages/page2-pressure-check/index',
  3: '/pages/page3-debt-input/index',
  4: '/pages/page4-loss-report/index',
  5: '/pages/page5-optimization/index',
  6: '/pages/page6-rate-simulator/index',
  7: '/pages/page7-risk-assessment/index',
  8: '/pages/page8-action-layers/index',
  9: '/pages/page9-companion/index',
}

// 低分用户 Step 5-8 走独立路径
const lowScorePageMap = {
  5: '/pages/low-score/credit-optimization',
  6: '/pages/low-score/credit-repair',
  7: '/pages/low-score/risk-faq',
  8: '/pages/low-score/improvement-plan',
  9: '/pages/page9-companion/index',
}
```

---

## 三、OCR Mock 数据

### 成功场景 — 借款合同

```json
{
  "taskId": 200001,
  "fileType": "CONTRACT",
  "status": "SUCCESS",
  "confidenceScore": 85.5,
  "extractedFields": {
    "creditor": { "value": "招商银行", "confidence": 0.95 },
    "principal": { "value": 100000.00, "confidence": 0.88 },
    "totalRepayment": { "value": 118000.00, "confidence": 0.82 },
    "nominalRate": { "value": 0.068, "confidence": 0.91 },
    "loanDays": { "value": 365, "confidence": 0.85 },
    "startDate": { "value": "2025-06-01", "confidence": 0.78 }
  }
}
```

### 低置信度场景 — 短信截图

```json
{
  "taskId": 200002,
  "fileType": "SMS_SCREENSHOT",
  "status": "SUCCESS",
  "confidenceScore": 52.0,
  "extractedFields": {
    "creditor": { "value": "某金融", "confidence": 0.60 },
    "principal": { "value": 30000.00, "confidence": 0.45 },
    "totalRepayment": { "value": null, "confidence": 0 },
    "loanDays": { "value": 180, "confidence": 0.55 }
  }
}
```

### 失败场景

```json
{
  "taskId": 200003,
  "fileType": "CONTRACT",
  "status": "FAILED",
  "confidenceScore": null,
  "errorMessage": "图片模糊无法识别关键信息",
  "retryCount": 2
}
```
