/**
 * 统一请求封装
 * baseUrl: /api/v1
 * 自动注入 Bearer Token
 * 错误响应对齐 google.rpc.Status
 * 支持：30s 超时、401 自动 refresh + 重试（含并发防抖）、全局 toast 错误
 */
const BASE_URL = '/api/v1'

// 401 并发防抖：同时多个请求收到 401 时只发起一次 refresh
let refreshingPromise = null

function clearAuthStorage() {
  uni.removeStorageSync('token')
  uni.removeStorageSync('refreshToken')
  uni.removeStorageSync('phone')
}

function redirectToLogin() {
  uni.navigateTo({ url: '/pages/auth/login' })
}

/**
 * 用 refreshToken 刷新 accessToken，返回新 token 或 null（失败）
 * 该请求不触发 401 拦截（skipRefresh: true）
 */
function doRefresh() {
  if (refreshingPromise) return refreshingPromise

  const refreshToken = uni.getStorageSync('refreshToken')
  if (!refreshToken) {
    return Promise.reject(new Error('no refreshToken'))
  }

  refreshingPromise = new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}/auth/sessions:refresh`,
      method: 'POST',
      data: { refreshToken },
      header: { 'Content-Type': 'application/json' },
      timeout: 10000,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const accessToken = res.data?.accessToken
          if (accessToken) {
            uni.setStorageSync('token', accessToken)
            resolve(accessToken)
          } else {
            reject(new Error('no accessToken in refresh response'))
          }
        } else {
          reject(new Error('refresh failed'))
        }
      },
      fail: (err) => {
        reject(err)
      },
    })
  }).finally(() => {
    refreshingPromise = null
  })

  return refreshingPromise
}

/**
 * 核心请求执行，skipRefresh 标记 refresh 请求本身，避免死循环
 */
function execute({ url, method = 'GET', data, params, skipRefresh = false, showToast = true }) {
  return new Promise((resolve, reject) => {
    const token = uni.getStorageSync('token')
    const header = {
      'Content-Type': 'application/json',
    }
    if (token) {
      header['Authorization'] = `Bearer ${token}`
    }

    uni.request({
      url: `${BASE_URL}${url}`,
      method,
      data,
      header,
      timeout: 10000,
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          resolve(res.data)
          return
        }

        // 401 处理：自动 refresh + 重试
        if (res.statusCode === 401 && !skipRefresh) {
          doRefresh()
            .then(() => {
              // refresh 成功，重试原请求
              execute({ url, method, data, params, skipRefresh: true, showToast })
                .then(resolve)
                .catch(reject)
            })
            .catch(() => {
              clearAuthStorage()
              redirectToLogin()
              reject({ code: 401, message: '登录已过期，请重新登录' })
            })
          return
        }

        const error = res.data?.error || { code: res.statusCode, message: '请求失败' }
        if (showToast) {
          uni.showToast({ title: error.message || '请求失败', icon: 'none' })
        }
        reject(error)
      },
      fail: (err) => {
        const error = { code: -1, message: '网络错误', detail: err }
        if (showToast) {
          uni.showToast({ title: '网络错误', icon: 'none' })
        }
        reject(error)
      },
    })
  })
}

/**
 * 统一请求：非 401 错误自动弹全局 toast
 */
export function request({ url, method = 'GET', data, params }) {
  return execute({ url, method, data, params, showToast: true })
}

/**
 * 静默请求：不弹全局 toast，适合后台静默请求（APR 预览、利率模拟等）
 */
export function requestSilent({ url, method = 'GET', data, params }) {
  return execute({ url, method, data, params, showToast: false })
}
