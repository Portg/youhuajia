/**
 * 咨询意向收集 API
 * 对应 Page 9 意向提交
 */
import { request } from './request'

/**
 * 提交咨询意向
 * POST /api/v1/consultations
 */
export function createConsultation({ phone, consultType, remark }) {
  return request({
    url: '/consultations',
    method: 'POST',
    data: { phone, consultType, remark },
  })
}
