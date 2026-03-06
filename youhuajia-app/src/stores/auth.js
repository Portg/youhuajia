/**
 * 认证 Pinia Store
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { createSession, refreshSession, revokeSession } from '../api/auth.js'

export const useAuthStore = defineStore('auth', () => {
  // 只读一次 storage，不在 loadFromStorage 里重复读
  const token = ref(uni.getStorageSync('token') || '')
  const refreshToken = ref(uni.getStorageSync('refreshToken') || '')
  const phone = ref(uni.getStorageSync('phone') || '')
  const loggedIn = ref(!!token.value)

  const isLoggedIn = computed(() => !!token.value && loggedIn.value)

  async function login(phoneNum, code) {
    const res = await createSession(phoneNum, code)
    token.value = res.accessToken
    refreshToken.value = res.refreshToken
    phone.value = phoneNum
    loggedIn.value = true
    uni.setStorageSync('token', res.accessToken)
    uni.setStorageSync('refreshToken', res.refreshToken)
    uni.setStorageSync('phone', phoneNum)
  }

  async function logout() {
    try {
      await revokeSession()
    } catch (_) {}
    token.value = ''
    refreshToken.value = ''
    phone.value = ''
    loggedIn.value = false
    uni.removeStorageSync('token')
    uni.removeStorageSync('refreshToken')
    uni.removeStorageSync('phone')
  }

  async function refresh() {
    const res = await refreshSession(refreshToken.value)
    token.value = res.accessToken
    uni.setStorageSync('token', res.accessToken)
  }

  // store 初始化时已从 storage 读取，无需再次调用
  function loadFromStorage() {
    // noop — 保留接口兼容，实际读取已在 ref 初始化完成
  }

  return { token, refreshToken, phone, loggedIn, isLoggedIn, login, logout, refresh, loadFromStorage }
}, { persist: false })
