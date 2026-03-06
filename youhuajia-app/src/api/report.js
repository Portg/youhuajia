/**
 * 报告相关 API
 * 对应 Page 8 分层行动
 */
import { request } from './request'

/**
 * 生成申请资料清单报告
 * POST /api/v1/reports:generate
 */
export function generateReport() {
  return request({
    url: '/reports:generate',
    method: 'POST',
    timeout: 30000, // AI 生成建议耗时较长
  })
}

/**
 * 获取报告详情
 * GET /api/v1/reports/{id}
 * @param {string} id 报告ID
 */
export function getReport(id) {
  return request({
    url: `/reports/${id}`,
    method: 'GET',
  })
}

/**
 * 导出报告（预审通过概率）
 * GET /api/v1/reports/{id}:export
 * @param {string} id 报告ID
 */
export function exportReport(id) {
  return request({
    url: `/reports/${id}:export`,
    method: 'GET',
  })
}

/**
 * 获取报告列表
 * GET /api/v1/reports
 * @param {number} pageSize 每页数量
 * @param {string} pageToken 分页令牌
 */
export function listReports(pageSize = 10, pageToken = '') {
  return request({
    url: '/reports',
    method: 'GET',
    data: { pageSize, ...(pageToken ? { pageToken } : {}) },
  })
}
