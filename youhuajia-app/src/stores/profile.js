/**
 * 财务画像 Store (Pinia)
 * 管理 Page 4, 6 的画像数据和利率模拟状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { getFinanceProfile, calculateFinanceProfile } from '../api/profile.js'
import { simulateRate, simulateScore } from '../api/engine.js'

export const useProfileStore = defineStore('profile', () => {
  const profile = ref(null)
  const loading = ref(false)
  const error = ref('')
  const simulationResult = ref(null)
  const simulationLoading = ref(false)
  const simulationError = ref(null)
  const scoreSimResult = ref(null)
  const scoreSimLoading = ref(false)

  const threeYearExtraInterest = computed(() => profile.value?.threeYearExtraInterest || 0)
  const weightedApr = computed(() => profile.value?.weightedApr || 0)
  const debtIncomeRatio = computed(() => profile.value?.debtIncomeRatio || 0)
  const score = computed(() => profile.value?.restructureScore || 0)

  // 请求去重：多个页面同时调 loadProfile 时只发一次
  let _loadPromise = null

  async function loadProfile() {
    if (_loadPromise) return _loadPromise
    loading.value = true
    error.value = ''
    _loadPromise = getFinanceProfile()
      .then(data => { profile.value = data })
      .catch(e => { error.value = e.message || '加载失败' })
      .finally(() => { loading.value = false; _loadPromise = null })
    return _loadPromise
  }

  async function triggerCalculation() {
    loading.value = true
    error.value = ''
    try {
      await calculateFinanceProfile()
      profile.value = await getFinanceProfile()
    } catch (e) {
      error.value = e.message || '计算失败'
      throw e
    } finally {
      loading.value = false
    }
  }

  async function doSimulateRate(targetApr) {
    if (!profile.value) return
    simulationLoading.value = true
    try {
      const params = {
        currentWeightedApr: profile.value.weightedApr,
        targetApr,
        totalPrincipal: profile.value.totalDebt,
        avgLoanDays: profile.value.avgLoanDays || 365,
        monthlyIncome: profile.value.monthlyIncome,
      }
      simulationResult.value = await simulateRate(params)
      simulationError.value = null
    } catch (e) {
      if (simulationResult.value !== null) {
        // 已有上次结果，静默降级保持上次数据
        console.warn('[profile] simulateRate failed, keeping last result:', e.message)
      } else {
        // 首次模拟失败，暴露错误供页面展示
        simulationError.value = e.message || '模拟失败，请稍后重试'
      }
    } finally {
      simulationLoading.value = false
    }
  }

  async function doSimulateScore(actions) {
    scoreSimLoading.value = true
    try {
      scoreSimResult.value = await simulateScore(actions)
    } catch (e) {
      console.warn('[profile] simulateScore failed:', e.message)
    } finally {
      scoreSimLoading.value = false
    }
  }

  return {
    profile, loading, error, simulationResult, simulationLoading, simulationError,
    scoreSimResult, scoreSimLoading,
    threeYearExtraInterest, weightedApr, debtIncomeRatio, score,
    loadProfile, triggerCalculation, doSimulateRate, doSimulateScore,
  }
})
