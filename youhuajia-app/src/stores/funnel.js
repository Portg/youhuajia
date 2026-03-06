/**
 * 漏斗进度 Pinia Store
 * 管理评分状态、漏斗步骤、各层完成状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'

export const useFunnelStore = defineStore('funnel', () => {
  // 用户评分（来自后端 financeProfile）
  const score = ref(0)

  // 当前漏斗步骤（1-9）
  const currentStep = ref(1)

  // 财务画像数据
  const financeProfile = ref(null)

  // Page 8 分层行动状态
  const actionLayers = ref({
    layer1: { completed: false, reportId: null },
    layer2: { completed: false },
    layer3: { completed: false },
  })

  // Page 9 Checklist 状态
  const checklist = ref({
    organizeStatements: false,
    confirmPaymentDates: false,
    prioritizeHighApr: false,
    reassessIn30Days: false,
  })

  // Page 2 压力检测数据（由 page-basic 添加）
  const monthlyPayment = ref(5000)
  const monthlyIncome = ref(7500)
  const pressureIndex = ref(0)
  const pressureLevel = ref('HEALTHY')

  function updatePressure(index, level) {
    pressureIndex.value = index
    pressureLevel.value = level
  }

  // 是否低分用户（走特殊路径）
  const isLowScore = computed(() => score.value > 0 && score.value < 60)

  // 已完成的 action layer 数量（用于进度条）
  const completedLayerCount = computed(() => {
    let count = 0
    if (actionLayers.value.layer1.completed) count++
    if (actionLayers.value.layer2.completed) count++
    if (actionLayers.value.layer3.completed) count++
    return count
  })

  function setScore(newScore) {
    score.value = newScore
  }

  function setFinanceProfile(profile) {
    financeProfile.value = profile
    if (profile?.score) {
      score.value = profile.score
    }
  }

  function advanceStep(step) {
    currentStep.value = step
  }

  function completeLayer1(reportId) {
    actionLayers.value.layer1.completed = true
    actionLayers.value.layer1.reportId = reportId
  }

  function completeLayer2() {
    actionLayers.value.layer2.completed = true
  }

  function completeLayer3() {
    actionLayers.value.layer3.completed = true
  }

  function toggleChecklistItem(key) {
    if (key in checklist.value) {
      checklist.value[key] = !checklist.value[key]
    }
  }

  function reset() {
    score.value = 0
    currentStep.value = 1
    financeProfile.value = null
    actionLayers.value = {
      layer1: { completed: false, reportId: null },
      layer2: { completed: false },
      layer3: { completed: false },
    }
    checklist.value = {
      organizeStatements: false,
      confirmPaymentDates: false,
      prioritizeHighApr: false,
      reassessIn30Days: false,
    }
  }

  return {
    score,
    currentStep,
    financeProfile,
    actionLayers,
    checklist,
    monthlyPayment,
    monthlyIncome,
    pressureIndex,
    pressureLevel,
    isLowScore,
    completedLayerCount,
    setScore,
    setFinanceProfile,
    advanceStep,
    updatePressure,
    completeLayer1,
    completeLayer2,
    completeLayer3,
    toggleChecklistItem,
    reset,
  }
})
