/**
 * 计算引擎 API
 * 对接 /engine 接口（无状态工具方法）
 */
import { request, requestSilent } from './request.js'

/**
 * 压力检测（无需登录）
 * POST /engine/pressure:assess
 */
export function assessPressure(monthlyPayment, monthlyIncome) {
  return request({
    url: '/engine/pressure:assess',
    method: 'POST',
    data: { monthlyPayment, monthlyIncome },
  })
}

/**
 * APR 试算（单笔债务）
 * POST /engine/apr:calculate
 */
export function calculateApr(principal, totalRepayment, loanDays) {
  return requestSilent({
    url: '/engine/apr:calculate',
    method: 'POST',
    data: { principal, totalRepayment, loanDays },
  })
}

/**
 * 利率模拟
 * POST /engine/rate:simulate
 * @param {object} params
 * @param {number} params.currentWeightedApr 当前加权 APR（百分比）
 * @param {number} params.targetApr 目标 APR（百分比）
 * @param {number} params.totalPrincipal 总本金
 * @param {number} params.avgLoanDays 平均借款天数
 * @param {number} params.monthlyIncome 月收入
 */
export function simulateRate(params) {
  return requestSilent({ url: '/engine/rate:simulate', method: 'POST', data: params })
}

/**
 * What-if 评分模拟
 * POST /engine/score:simulate
 * @param {Array} actions - 模拟操作列表 [{ type, debtId, value? }]
 */
export function simulateScore(actions) {
  return requestSilent({ url: '/engine/score:simulate', method: 'POST', data: { actions } })
}

/**
 * 预审通过概率估算
 * POST /engine/preaudit:estimate
 * @returns {{ probability: number, suggestions: string[] }}
 */
export function estimatePreAudit() {
  return requestSilent({ url: '/engine/preaudit:estimate', method: 'POST' })
}
