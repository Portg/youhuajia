/**
 * 漏斗进度 Pinia Store
 * 管理评分状态、漏斗步骤、各层完成状态
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { requestSilent } from '../api/request.js'
import { upsertImprovementPlan, deleteImprovementPlan } from '../api/improvementPlan.js'

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
    if (profile?.restructureScore) {
      const newScore = Number(profile.restructureScore)
      const wasLowScore = score.value > 0 && score.value < 60
      const willBeLowScore = newScore > 0 && newScore < 60

      // 评分路径变化（低分→正常 或 正常→低分）时重置 actionLayers
      if (score.value > 0 && wasLowScore !== willBeLowScore) {
        actionLayers.value = {
          layer1: { completed: false, reportId: null },
          layer2: { completed: false },
          layer3: { completed: false },
        }
      }

      score.value = newScore
    }
  }

  function advanceStep(step) {
    currentStep.value = step
  }

  function completeLayer1(reportId) {
    actionLayers.value.layer1.completed = true
    actionLayers.value.layer1.reportId = reportId
    syncPlanToBackend()
  }

  function completeLayer2() {
    actionLayers.value.layer2.completed = true
    syncPlanToBackend()
  }

  function completeLayer3() {
    actionLayers.value.layer3.completed = true
    syncPlanToBackend()
  }

  /** 将 actionLayers 当前状态持久化到后端（fire-and-forget，失败不阻断前端） */
  function syncPlanToBackend() {
    upsertImprovementPlan({
      layer1Completed: actionLayers.value.layer1.completed,
      layer1ReportId: actionLayers.value.layer1.reportId,
      layer2Completed: actionLayers.value.layer2.completed,
      layer3Completed: actionLayers.value.layer3.completed,
    }).catch(() => {
      // 持久化失败不阻断前端，本地 unistorage 已保存
    })
  }

  function toggleChecklistItem(key) {
    if (key in checklist.value) {
      checklist.value[key] = !checklist.value[key]
    }
  }

  /**
   * 登录后根据后端数据推算漏斗进度
   * 映射规则：有报告+画像+债务=8, 有画像+债务=5, 有债务=3, 否则=1
   * AG-15: 数据不一致时向下降级，取最高「完整」锚点
   */
  async function inferStep() {
    try {
      const [debtsRes, profileRes, reportsRes, planRes] = await Promise.allSettled([
        requestSilent({ url: '/debts', method: 'GET', data: { pageSize: 1 } }),
        requestSilent({ url: '/finance-profiles/mine', method: 'GET' }),
        requestSilent({ url: '/reports', method: 'GET', data: { pageSize: 1 } }),
        requestSilent({ url: '/improvement-plans/mine', method: 'GET' }),
      ])

      const hasDebts = debtsRes.status === 'fulfilled'
        && (debtsRes.value?.debts?.length > 0 || debtsRes.value?.totalSize > 0)
      const profileData = profileRes.status === 'fulfilled' ? profileRes.value : null
      const hasProfile = !!(profileData && profileData.restructureScore > 0)
      const hasReports = reportsRes.status === 'fulfilled'
        && (reportsRes.value?.reports?.length > 0 || reportsRes.value?.totalSize > 0)

      let step = 1
      if (hasReports && hasProfile && hasDebts) {
        step = 8
      } else if (hasProfile && hasDebts) {
        step = 5
      } else if (hasDebts) {
        step = 3
      }

      currentStep.value = step

      if (profileData) {
        setFinanceProfile(profileData)
      }

      // 同步后端改善计划进度到本地（换设备/重装恢复）
      const planData = planRes.status === 'fulfilled' ? planRes.value : null
      if (planData) {
        actionLayers.value.layer1.completed = planData.layer1Completed ?? false
        actionLayers.value.layer1.reportId  = planData.layer1ReportId ?? null
        actionLayers.value.layer2.completed = planData.layer2Completed ?? false
        actionLayers.value.layer3.completed = planData.layer3Completed ?? false
      }
    } catch (_) {
      // 推算失败不影响登录流程，保持 step=1
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
    // 「重新评估」清空后端改善计划记录（fire-and-forget）
    deleteImprovementPlan().catch(() => {})
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
    inferStep,
    reset,
  }
}, {
  unistorage: {
    paths: ['score', 'currentStep', 'pressureIndex', 'pressureLevel', 'actionLayers', 'checklist'],
  },
})
