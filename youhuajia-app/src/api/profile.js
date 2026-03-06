/**
 * 财务画像 API
 * 对接 /finance-profiles 接口
 */
import { request } from './request.js'

/**
 * 获取当前用户财务画像
 * GET /finance-profiles/mine
 */
export function getFinanceProfile() {
  return request({ url: '/finance-profiles/mine', method: 'GET' })
}

/**
 * 触发财务画像计算
 * POST /finance-profiles/mine:calculate
 */
export function calculateFinanceProfile() {
  return request({ url: '/finance-profiles/mine:calculate', method: 'POST', data: {} })
}
