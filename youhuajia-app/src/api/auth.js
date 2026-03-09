/**
 * 认证相关 API
 */
import { request } from './request.js'

/**
 * 当前隐私协议版本号。协议升级时同步更新此常量，无需修改调用方。
 * 对应后端 User.consentVersion 字段（AG-13）。
 */
export const CONSENT_VERSION = 'v1.0'

/**
 * 发送短信验证码
 * POST /auth/sms:send
 */
export function sendSms(phone) {
  return request({ url: '/auth/sms:send', method: 'POST', data: { phone } })
}

/**
 * 创建会话（登录）
 * POST /auth/sessions
 */
export function createSession(phone, smsCode, consentVersion = CONSENT_VERSION) {
  return request({ url: '/auth/sessions', method: 'POST', data: { phone, smsCode, consentVersion } })
}

/**
 * 刷新 Token
 * POST /auth/sessions:refresh
 */
export function refreshSession(refreshToken) {
  return request({ url: '/auth/sessions:refresh', method: 'POST', data: { refreshToken } })
}

/**
 * 撤销会话（登出）
 * POST /auth/sessions:revoke
 */
export function revokeSession() {
  return request({ url: '/auth/sessions:revoke', method: 'POST' })
}
