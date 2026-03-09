/**
 * 改善计划 API
 */
import { request } from './request.js'

/**
 * 查询当前用户改善计划
 * GET /improvement-plans/mine
 * 无记录时后端返回全 false 默认值，前端无需特殊处理
 */
export function getImprovementPlan() {
  return request({ url: '/improvement-plans/mine', method: 'GET' })
}

/**
 * 保存/更新改善计划状态（upsert，不保留历史）
 * PATCH /improvement-plans/mine
 * @param {{ layer1Completed, layer1ReportId, layer2Completed, layer3Completed }} data
 */
export function upsertImprovementPlan(data) {
  return request({ url: '/improvement-plans/mine', method: 'PATCH', data })
}

/**
 * 删除改善计划（重新评估时清空）
 * DELETE /improvement-plans/mine
 */
export function deleteImprovementPlan() {
  return request({ url: '/improvement-plans/mine', method: 'DELETE' })
}
