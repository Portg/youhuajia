/**
 * 债务相关 API
 * 对接 openapi.yaml 的 /debts 和 /ocr-tasks 接口
 */
import { request } from './request.js'

/**
 * 获取债务列表
 * GET /debts
 */
export function listDebts(pageSize = 50) {
  return request({ url: '/debts', method: 'GET', params: { pageSize } })
}

/**
 * 创建债务
 * POST /debts
 * @param {object} debt 债务数据
 * @param {string} requestId UUID4 幂等键（AIP-155）
 */
export function createDebt(debt, requestId) {
  return request({ url: '/debts', method: 'POST', data: { requestId, debt } })
}

/**
 * 更新债务
 * PATCH /debts/{id}
 * @param {string} debtId
 * @param {object} debt
 * @param {string} updateMask 逗号分隔字段名（AIP-134）
 */
export function updateDebt(debtId, debt, updateMask) {
  return request({ url: `/debts/${debtId}`, method: 'PATCH', data: { debt, updateMask } })
}

/**
 * 删除债务
 * DELETE /debts/{id}
 */
export function deleteDebt(debtId) {
  return request({ url: `/debts/${debtId}`, method: 'DELETE' })
}

/**
 * 确认债务（DRAFT → CONFIRMED）
 * POST /debts/{id}:confirm
 */
export function confirmDebt(debtId) {
  return request({ url: `/debts/${debtId}:confirm`, method: 'POST', data: {} })
}

/**
 * 创建 OCR 任务（上传图片）
 * POST /ocr-tasks
 * @param {string} filePath 本地文件路径
 * @param {string} [fileType='CONTRACT'] CONTRACT | BILL | SMS_SCREENSHOT
 */
export function createOcrTask(filePath, fileType = 'CONTRACT') {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token') || ''
    uni.uploadFile({
      url: '/api/v1/ocr-tasks',
      filePath,
      name: 'file',
      formData: { fileType },
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          try {
            resolve(JSON.parse(res.data))
          } catch {
            reject({ code: -1, message: '解析响应失败' })
          }
        } else {
          reject({ code: res.statusCode, message: 'OCR 上传失败' })
        }
      },
      fail: (err) => reject({ code: -1, message: err.errMsg || '上传失败' }),
    })
  })
}

/**
 * 查询 OCR 任务状态
 * GET /ocr-tasks/{id}
 */
export function getOcrTask(taskId) {
  return request({ url: `/ocr-tasks/${taskId}`, method: 'GET' })
}

/**
 * 确认 OCR 识别结果，创建债务
 * POST /ocr-tasks/{id}:confirm
 */
export function confirmOcrTask(taskId, debt) {
  return request({ url: `/ocr-tasks/${taskId}:confirm`, method: 'POST', data: { debt } })
}
