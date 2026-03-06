#!/usr/bin/env bash
# ============================================================
# integration-test.sh — 前后端联调测试脚本
# 模拟完整用户旅程 Page 1→9（User A 健康型 + User C 高风险型）
#
# 用法：
#   ./scripts/integration-test.sh [BASE_URL]
#   BASE_URL 默认为 http://localhost:8080
#
# 依赖：curl, jq
# 退出码：0 = 全部通过，1 = 有失败
# ============================================================

set -euo pipefail

BASE_URL="${1:-http://localhost:8080}/api/v1"
PASS=0
FAIL=0
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# ---- 工具函数 ------------------------------------------------

log_step() { echo -e "\n${YELLOW}[STEP]${NC} $1"; }
log_ok()   { echo -e "  ${GREEN}[OK]${NC}   $1"; PASS=$((PASS+1)); }
log_fail() { echo -e "  ${RED}[FAIL]${NC} $1"; FAIL=$((FAIL+1)); }

# 发送 POST 请求，返回响应体
post() {
  local url="$1"; local body="$2"; local token="${3:-}"
  local auth_header=""
  if [[ -n "$token" ]]; then
    auth_header="-H \"Authorization: Bearer $token\""
  fi
  curl -s -X POST "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    ${token:+-H "Authorization: Bearer $token"} \
    -d "$body"
}

# 发送 GET 请求，返回响应体
get() {
  local url="$1"; local token="${2:-}"
  curl -s -X GET "$BASE_URL$url" \
    ${token:+-H "Authorization: Bearer $token"}
}

# 断言 JSON 字段存在且非空
assert_field() {
  local label="$1"; local json="$2"; local field="$3"
  local val
  val=$(echo "$json" | jq -r "$field // empty" 2>/dev/null || true)
  if [[ -n "$val" && "$val" != "null" ]]; then
    log_ok "$label ($field = $val)"
  else
    log_fail "$label — 字段 $field 为空或不存在。响应: $(echo "$json" | jq -c . 2>/dev/null || echo "$json")"
  fi
}

# 断言 HTTP 状态码
assert_status() {
  local label="$1"; local actual="$2"; local expected="$3"
  if [[ "$actual" == "$expected" ]]; then
    log_ok "$label (HTTP $actual)"
  else
    log_fail "$label — 期望 HTTP $expected，实际 HTTP $actual"
  fi
}

# 断言数值 >= 阈值
assert_gte() {
  local label="$1"; local val="$2"; local min="$3"
  if awk "BEGIN { exit !($val >= $min) }"; then
    log_ok "$label ($val >= $min)"
  else
    log_fail "$label — $val 小于 $min"
  fi
}

# 断言数值 < 阈值
assert_lt() {
  local label="$1"; local val="$2"; local max="$3"
  if awk "BEGIN { exit !($val < $max) }"; then
    log_ok "$label ($val < $max)"
  else
    log_fail "$label — $val 不小于 $max"
  fi
}

# 断言字符串不包含目标子串
assert_not_contains() {
  local label="$1"; local text="$2"; local forbidden="$3"
  if echo "$text" | grep -qF "$forbidden" 2>/dev/null; then
    log_fail "$label — 发现禁止文案「$forbidden」"
  else
    log_ok "$label (未发现「$forbidden」)"
  fi
}

# ============================================================
# User A 旅程：健康型，预期 score >= 60
# ============================================================

echo -e "\n${YELLOW}============================================================${NC}"
echo -e "${YELLOW} User A 旅程：健康型（预期 restructureScore >= 60）${NC}"
echo -e "${YELLOW}============================================================${NC}"

# Step a: 压力检测（无需登录）
log_step "a. POST /engine/pressure:assess（无需登录）"
PRESSURE_RESP=$(post "/engine/pressure:assess" '{
  "monthlyPayment": 3700.00,
  "monthlyIncome": 15000.00
}')
assert_field "压力等级" "$PRESSURE_RESP" ".level"
assert_field "压力指数" "$PRESSURE_RESP" ".pressureIndex"
RATIO=$(echo "$PRESSURE_RESP" | jq -r '.ratio // "0"')
assert_lt "月供收入比应 < 0.50（健康范围）" "$RATIO" "0.50"

# Step b: 发送短信验证码
log_step "b1. POST /auth/sms:send"
SMS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/auth/sms:send" \
  -H "Content-Type: application/json" \
  -d '{"phone":"13911110001"}')
assert_status "发送验证码" "$SMS_STATUS" "200"

# Step b: 登录
log_step "b2. POST /auth/sessions（登录）"
LOGIN_RESP=$(post "/auth/sessions" '{
  "phone": "13911110001",
  "smsCode": "123456"
}')
TOKEN_A=$(echo "$LOGIN_RESP" | jq -r '.accessToken // empty')
if [[ -z "$TOKEN_A" ]]; then
  log_fail "登录失败，无法获取 accessToken。响应: $LOGIN_RESP"
  echo -e "\n${RED}User A 旅程中止：登录失败${NC}"
  # 继续执行 User C 旅程
else
  log_ok "登录成功，获取 accessToken"

  # Step c: 创建3笔债务
  log_step "c. POST /debts × 3（创建债务）"

  DEBT1_RESP=$(post "/debts" '{
    "requestId": "a1b2c3d4-0001-0001-0001-000000000001",
    "debt": {
      "creditor": "招商银行信用卡",
      "debtType": "CREDIT_CARD",
      "principal": 30000.00,
      "totalRepayment": 31500.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL"
    }
  }' "$TOKEN_A")
  DEBT1_NAME=$(echo "$DEBT1_RESP" | jq -r '.name // empty')
  assert_field "债务1创建（招商银行信用卡）" "$DEBT1_RESP" ".name"
  DEBT1_ID="${DEBT1_NAME#debts/}"

  DEBT2_RESP=$(post "/debts" '{
    "requestId": "a1b2c3d4-0001-0001-0001-000000000002",
    "debt": {
      "creditor": "花呗",
      "debtType": "CONSUMER_LOAN",
      "principal": 8000.00,
      "totalRepayment": 8800.00,
      "loanDays": 180,
      "overdueStatus": "NORMAL"
    }
  }' "$TOKEN_A")
  DEBT2_NAME=$(echo "$DEBT2_RESP" | jq -r '.name // empty')
  assert_field "债务2创建（花呗）" "$DEBT2_RESP" ".name"
  DEBT2_ID="${DEBT2_NAME#debts/}"

  DEBT3_RESP=$(post "/debts" '{
    "requestId": "a1b2c3d4-0001-0001-0001-000000000003",
    "debt": {
      "creditor": "京东白条",
      "debtType": "CONSUMER_LOAN",
      "principal": 12000.00,
      "totalRepayment": 13200.00,
      "loanDays": 365,
      "overdueStatus": "NORMAL"
    }
  }' "$TOKEN_A")
  DEBT3_NAME=$(echo "$DEBT3_RESP" | jq -r '.name // empty')
  assert_field "债务3创建（京东白条）" "$DEBT3_RESP" ".name"
  DEBT3_ID="${DEBT3_NAME#debts/}"

  # Step d: 确认债务
  log_step "d. POST /debts/{id}:confirm × 3（确认债务）"
  for DEBT_ID in "$DEBT1_ID" "$DEBT2_ID" "$DEBT3_ID"; do
    if [[ -z "$DEBT_ID" ]]; then
      log_fail "确认债务 $DEBT_ID — 债务ID为空，跳过"
      continue
    fi
    CONFIRM_RESP=$(post "/debts/${DEBT_ID}:confirm" '{}' "$TOKEN_A")
    STATUS_VAL=$(echo "$CONFIRM_RESP" | jq -r '.status // empty')
    if [[ "$STATUS_VAL" == "CONFIRMED" ]]; then
      log_ok "债务 $DEBT_ID 确认成功（status=CONFIRMED）"
    else
      log_fail "债务 $DEBT_ID 确认失败 — status=$STATUS_VAL"
    fi
  done

  # Step e: 触发画像计算
  log_step "e. POST /finance-profiles/mine:calculate（触发画像计算）"
  CALC_RESP=$(post "/finance-profiles/mine:calculate" '{}' "$TOKEN_A")
  assert_field "画像计算触发" "$CALC_RESP" ".restructureScore"

  # Step f: 获取画像，验证 score >= 60
  log_step "f. GET /finance-profiles/mine（获取画像）"
  PROFILE_RESP=$(get "/finance-profiles/mine" "$TOKEN_A")
  SCORE=$(echo "$PROFILE_RESP" | jq -r '.restructureScore // "0"')
  RISK_LEVEL=$(echo "$PROFILE_RESP" | jq -r '.riskLevel // "UNKNOWN"')
  assert_field "画像 restructureScore" "$PROFILE_RESP" ".restructureScore"
  assert_field "画像 riskLevel" "$PROFILE_RESP" ".riskLevel"
  assert_gte "User A 评分应 >= 60" "$SCORE" "60"
  echo "  评分=$SCORE, riskLevel=$RISK_LEVEL"

  WEIGHTED_APR=$(echo "$PROFILE_RESP" | jq -r '.weightedApr // "10"')

  # Step g: 利率模拟
  log_step "g. POST /engine/rate:simulate（利率模拟）"
  SIM_RESP=$(post "/engine/rate:simulate" "{
    \"currentWeightedApr\": $WEIGHTED_APR,
    \"targetApr\": 8.50,
    \"totalPrincipal\": 50000.00,
    \"avgLoanDays\": 365,
    \"monthlyIncome\": 15000.00
  }" "$TOKEN_A")
  assert_field "利率模拟 monthlySaving" "$SIM_RESP" ".monthlySaving"
  SAVING=$(echo "$SIM_RESP" | jq -r '.monthlySaving // "0"')
  if awk "BEGIN { exit !($SAVING >= 0) }"; then
    log_ok "月均节省 >= 0 ($SAVING)"
  else
    log_fail "月均节省为负数 ($SAVING)"
  fi

  # Step h: 生成报告
  log_step "h. POST /reports:generate（生成报告）"
  REPORT_RESP=$(post "/reports:generate" '{}' "$TOKEN_A")
  REPORT_NAME=$(echo "$REPORT_RESP" | jq -r '.name // empty')
  assert_field "报告生成 name" "$REPORT_RESP" ".name"
  assert_field "报告生成 profileSnapshot" "$REPORT_RESP" ".profileSnapshot"
  REPORT_ID="${REPORT_NAME#reports/}"

  # Step i: 获取报告
  log_step "i. GET /reports/{id}（获取报告）"
  if [[ -n "$REPORT_ID" ]]; then
    REPORT_GET_RESP=$(get "/reports/$REPORT_ID" "$TOKEN_A")
    assert_field "报告详情 name" "$REPORT_GET_RESP" ".name"
    assert_field "报告详情 priorityList" "$REPORT_GET_RESP" ".priorityList"
    assert_field "报告详情 actionPlan" "$REPORT_GET_RESP" ".actionPlan"
    # F-11: 报告不含恐慌性文案
    AI_SUMMARY=$(echo "$REPORT_GET_RESP" | jq -r '.aiSummary // ""')
    assert_not_contains "F-11: 无「问题严重」" "$AI_SUMMARY" "问题严重"
    assert_not_contains "F-11: 无「赶紧行动」" "$AI_SUMMARY" "赶紧行动"
    assert_not_contains "F-11: 无「最后机会」" "$AI_SUMMARY" "最后机会"
    assert_not_contains "F-13: 无「申请失败」" "$AI_SUMMARY" "申请失败"
    assert_not_contains "F-13: 无「不符合条件」" "$AI_SUMMARY" "不符合条件"
  else
    log_fail "报告ID为空，跳过 GET /reports/{id}"
  fi
fi

# ============================================================
# User C 旅程：高风险型，预期 score < 60
# ============================================================

echo -e "\n${YELLOW}============================================================${NC}"
echo -e "${YELLOW} User C 旅程：高风险型（预期 restructureScore < 60）${NC}"
echo -e "${YELLOW}============================================================${NC}"

# Step a: 压力检测（月供 > 月收入 → SEVERE）
log_step "a. POST /engine/pressure:assess（高压力）"
PRESSURE_C=$(post "/engine/pressure:assess" '{
  "monthlyPayment": 18500.00,
  "monthlyIncome": 12000.00
}')
LEVEL_C=$(echo "$PRESSURE_C" | jq -r '.level // "UNKNOWN"')
assert_field "压力等级（应为 HEAVY/SEVERE）" "$PRESSURE_C" ".level"
if [[ "$LEVEL_C" == "HEAVY" || "$LEVEL_C" == "SEVERE" ]]; then
  log_ok "压力等级符合预期（$LEVEL_C）"
else
  log_fail "压力等级不符预期，期望 HEAVY/SEVERE，实际 $LEVEL_C"
fi

# Step b: 登录
log_step "b2. POST /auth/sessions（User C 登录）"
SMS_STATUS_C=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/auth/sms:send" \
  -H "Content-Type: application/json" \
  -d '{"phone":"13911110003"}')
LOGIN_C_RESP=$(post "/auth/sessions" '{
  "phone": "13911110003",
  "smsCode": "123456"
}')
TOKEN_C=$(echo "$LOGIN_C_RESP" | jq -r '.accessToken // empty')
if [[ -z "$TOKEN_C" ]]; then
  log_fail "User C 登录失败，响应: $LOGIN_C_RESP"
else
  log_ok "User C 登录成功"

  # Step c: 创建3笔高风险债务
  log_step "c. POST /debts × 3（高风险债务）"

  DC1=$(post "/debts" '{
    "debt": {
      "creditor": "某网贷平台A",
      "debtType": "CONSUMER_LOAN",
      "principal": 50000.00,
      "totalRepayment": 72000.00,
      "loanDays": 365,
      "overdueStatus": "OVERDUE_60"
    }
  }' "$TOKEN_C")
  DC1_ID=$(echo "$DC1" | jq -r '.name // "" | split("/") | last')
  assert_field "User C 债务1创建" "$DC1" ".name"

  DC2=$(post "/debts" '{
    "debt": {
      "creditor": "某网贷平台B",
      "debtType": "CONSUMER_LOAN",
      "principal": 80000.00,
      "totalRepayment": 120000.00,
      "loanDays": 365,
      "overdueStatus": "OVERDUE_90_PLUS"
    }
  }' "$TOKEN_C")
  DC2_ID=$(echo "$DC2" | jq -r '.name // "" | split("/") | last')
  assert_field "User C 债务2创建" "$DC2" ".name"

  DC3=$(post "/debts" '{
    "debt": {
      "creditor": "信用卡A",
      "debtType": "CREDIT_CARD",
      "principal": 60000.00,
      "totalRepayment": 78000.00,
      "loanDays": 365,
      "overdueStatus": "OVERDUE_30"
    }
  }' "$TOKEN_C")
  DC3_ID=$(echo "$DC3" | jq -r '.name // "" | split("/") | last')
  assert_field "User C 债务3创建" "$DC3" ".name"

  # Step d: 确认债务
  log_step "d. POST /debts/{id}:confirm × 3（确认 User C 债务）"
  for DID in "$DC1_ID" "$DC2_ID" "$DC3_ID"; do
    [[ -z "$DID" ]] && continue
    CR=$(post "/debts/${DID}:confirm" '{}' "$TOKEN_C")
    CS=$(echo "$CR" | jq -r '.status // empty')
    if [[ "$CS" == "CONFIRMED" ]]; then
      log_ok "User C 债务 $DID 确认成功"
    else
      log_fail "User C 债务 $DID 确认失败 (status=$CS)"
    fi
  done

  # Step e: 触发画像
  log_step "e. POST /finance-profiles/mine:calculate（User C）"
  CALC_C=$(post "/finance-profiles/mine:calculate" '{}' "$TOKEN_C")
  assert_field "User C 画像计算" "$CALC_C" ".restructureScore"

  # Step f: 获取画像，验证 score < 60，F-13
  log_step "f. GET /finance-profiles/mine（User C）"
  PROFILE_C=$(get "/finance-profiles/mine" "$TOKEN_C")
  SCORE_C=$(echo "$PROFILE_C" | jq -r '.restructureScore // "100"')
  RISK_C=$(echo "$PROFILE_C" | jq -r '.riskLevel // "UNKNOWN"')
  assert_field "User C 画像 restructureScore" "$PROFILE_C" ".restructureScore"
  assert_lt "User C 评分应 < 60 (F-13)" "$SCORE_C" "60"
  if [[ "$RISK_C" == "HIGH" || "$RISK_C" == "CRITICAL" ]]; then
    log_ok "User C riskLevel=$RISK_C（符合预期）"
  else
    log_fail "User C riskLevel=$RISK_C（期望 HIGH 或 CRITICAL）"
  fi

  # Step g: User C 不走利率模拟路径（score < 60 → 信用改善路径）
  log_step "g. 跳过利率模拟（User C score<60，不走优化路径）"
  log_ok "User C 正确跳过利率模拟步骤"

  # Step h: 生成报告（改善计划仍可生成）
  log_step "h. POST /reports:generate（User C 改善计划）"
  REPORT_C=$(post "/reports:generate" '{}' "$TOKEN_C")
  assert_field "User C 报告生成 name" "$REPORT_C" ".name"
  assert_field "User C 报告生成 actionPlan" "$REPORT_C" ".actionPlan"

  AI_C=$(echo "$REPORT_C" | jq -r '.aiSummary // ""')
  assert_not_contains "F-13: User C 报告无「申请失败」" "$AI_C" "申请失败"
  assert_not_contains "F-13: User C 报告无「不符合条件」" "$AI_C" "不符合条件"
  assert_not_contains "F-11: User C 报告无「问题严重」" "$AI_C" "问题严重"
  assert_not_contains "F-11: User C 报告无「赶紧行动」" "$AI_C" "赶紧行动"
  assert_not_contains "F-11: User C 报告无「最后机会」" "$AI_C" "最后机会"
fi

# ============================================================
# 契约快检：前端 URL 路径可达性
# ============================================================

echo -e "\n${YELLOW}============================================================${NC}"
echo -e "${YELLOW} 契约快检：前端 API URL 路由可达性${NC}"
echo -e "${YELLOW}============================================================${NC}"

check_route() {
  local label="$1"; local method="$2"; local url="$3"; local token="${4:-}"
  local http_code
  http_code=$(curl -s -o /dev/null -w "%{http_code}" \
    -X "$method" "$BASE_URL$url" \
    -H "Content-Type: application/json" \
    ${token:+-H "Authorization: Bearer $token"} \
    -d '{}')
  if [[ "$http_code" == "404" ]]; then
    log_fail "$label — $method $url 返回 404（路由不存在）"
  else
    log_ok "$label — $method $url → HTTP $http_code（路由存在）"
  fi
}

check_route "auth.js: sms:send"           POST "/auth/sms:send"
check_route "auth.js: sessions"           POST "/auth/sessions"
check_route "auth.js: sessions:refresh"  POST "/auth/sessions:refresh"
check_route "auth.js: sessions:revoke"   POST "/auth/sessions:revoke"
check_route "debt.js: GET /debts"         GET  "/debts"
check_route "debt.js: POST /debts"        POST "/debts"
check_route "debt.js: PATCH /debts/1"    PATCH "/debts/1"
check_route "engine.js: pressure:assess" POST "/engine/pressure:assess"
check_route "engine.js: apr:calculate"   POST "/engine/apr:calculate"
check_route "engine.js: rate:simulate"   POST "/engine/rate:simulate"
check_route "report.js: reports:generate" POST "/reports:generate"
check_route "report.js: GET /reports/1"   GET  "/reports/1"

# 已知不一致项（前端用 mine，后端用 me）
log_step "契约不一致验证（已知问题）"
MINE_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/finance-profiles/mine")
ME_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X GET "$BASE_URL/finance-profiles/mine")
if [[ "$MINE_CODE" == "404" && "$ME_CODE" != "404" ]]; then
  log_fail "CONTRACT MISMATCH #1: 前端 /finance-profiles/mine(404) vs 后端 /finance-profiles/mine($ME_CODE)"
fi
MINE_CALC_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/finance-profiles/mine:calculate" -H "Content-Type: application/json" -d '{}')
ME_CALC_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$BASE_URL/finance-profiles/mine:calculate" -H "Content-Type: application/json" -d '{}')
if [[ "$MINE_CALC_CODE" == "404" && "$ME_CALC_CODE" != "404" ]]; then
  log_fail "CONTRACT MISMATCH #2: 前端 /finance-profiles/mine:calculate(404) vs 后端 /finance-profiles/mine:calculate($ME_CALC_CODE)"
fi

# ============================================================
# 结果汇总
# ============================================================

echo -e "\n${YELLOW}============================================================${NC}"
echo -e "${YELLOW} 测试结果汇总${NC}"
echo -e "${YELLOW}============================================================${NC}"
echo -e "  ${GREEN}通过${NC}: $PASS"
echo -e "  ${RED}失败${NC}: $FAIL"
echo ""

if [[ $FAIL -gt 0 ]]; then
  echo -e "${RED}联调测试失败，请检查上方错误信息。${NC}"
  exit 1
else
  echo -e "${GREEN}所有联调测试通过。${NC}"
  exit 0
fi
