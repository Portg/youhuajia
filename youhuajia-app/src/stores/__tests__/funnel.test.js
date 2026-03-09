/**
 * funnel store 单元测试
 * 覆盖：步进逻辑、低分分支判断、评分路径变化、reset、checklist
 * AG-14: 推算 step 不锁定导航，用户可自由前进后退
 * AG-15: 数据不一致时向下降级，取最高「完整」锚点
 */
import { describe, it, expect, beforeEach, vi } from 'vitest'
import { setActivePinia, createPinia } from 'pinia'
import { useFunnelStore } from '../funnel.js'

// mock 改善计划 API，避免在单元测试中发出真实 HTTP 请求
import * as improvementPlanApi from '../../api/improvementPlan.js'
vi.mock('../../api/improvementPlan.js', () => ({
  upsertImprovementPlan: vi.fn(() => Promise.resolve()),
  deleteImprovementPlan: vi.fn(() => Promise.resolve()),
  getImprovementPlan: vi.fn(() => Promise.resolve()),
}))

beforeEach(() => {
  setActivePinia(createPinia())
})

// ============================================================
// 基础状态
// ============================================================
describe('initial state', () => {
  it('should_start_at_step_1_with_score_0', () => {
    const store = useFunnelStore()
    expect(store.currentStep).toBe(1)
    expect(store.score).toBe(0)
    expect(store.isLowScore).toBe(false)
    expect(store.financeProfile).toBeNull()
  })
})

// ============================================================
// 步进逻辑
// ============================================================
describe('advanceStep', () => {
  it('should_set_step_to_given_value', () => {
    const store = useFunnelStore()
    store.advanceStep(5)
    expect(store.currentStep).toBe(5)
  })

  it('should_allow_backward_step', () => {
    const store = useFunnelStore()
    store.advanceStep(8)
    store.advanceStep(3)
    expect(store.currentStep).toBe(3)
  })
})

// ============================================================
// 低分判断 (isLowScore)
// ============================================================
describe('isLowScore', () => {
  it('should_be_false_when_score_is_0', () => {
    const store = useFunnelStore()
    store.setScore(0)
    expect(store.isLowScore).toBe(false)
  })

  it('should_be_true_when_score_between_1_and_59', () => {
    const store = useFunnelStore()
    store.setScore(45)
    expect(store.isLowScore).toBe(true)
  })

  it('should_be_false_when_score_is_60', () => {
    const store = useFunnelStore()
    store.setScore(60)
    expect(store.isLowScore).toBe(false)
  })

  it('should_be_true_at_boundary_59', () => {
    const store = useFunnelStore()
    store.setScore(59)
    expect(store.isLowScore).toBe(true)
  })

  it('should_be_false_when_score_is_negative', () => {
    const store = useFunnelStore()
    store.setScore(-5)
    expect(store.isLowScore).toBe(false)
  })
})

// ============================================================
// setFinanceProfile — 评分路径变化重置 actionLayers
// ============================================================
describe('setFinanceProfile', () => {
  it('should_set_score_from_profile', () => {
    const store = useFunnelStore()
    store.setFinanceProfile({ restructureScore: 75 })
    expect(store.score).toBe(75)
  })

  it('should_reset_actionLayers_when_path_changes_normal_to_lowscore', () => {
    const store = useFunnelStore()
    // Start with normal score
    store.setFinanceProfile({ restructureScore: 70 })
    store.completeLayer1('report-123')
    expect(store.actionLayers.layer1.completed).toBe(true)

    // Score drops to low — path changes, layers should reset
    store.setFinanceProfile({ restructureScore: 45 })
    expect(store.actionLayers.layer1.completed).toBe(false)
    expect(store.actionLayers.layer1.reportId).toBeNull()
  })

  it('should_not_reset_actionLayers_when_path_stays_same', () => {
    const store = useFunnelStore()
    store.setFinanceProfile({ restructureScore: 70 })
    store.completeLayer1('report-123')

    // Score changes but stays in normal range — no reset
    store.setFinanceProfile({ restructureScore: 80 })
    expect(store.actionLayers.layer1.completed).toBe(true)
  })

  it('should_handle_null_profile_gracefully', () => {
    const store = useFunnelStore()
    store.setFinanceProfile(null)
    expect(store.financeProfile).toBeNull()
    expect(store.score).toBe(0)
  })
})

// ============================================================
// Action Layers
// ============================================================
describe('action layers', () => {
  it('should_track_completed_layer_count', () => {
    const store = useFunnelStore()
    expect(store.completedLayerCount).toBe(0)

    store.completeLayer1('report-1')
    expect(store.completedLayerCount).toBe(1)

    store.completeLayer2()
    expect(store.completedLayerCount).toBe(2)

    store.completeLayer3()
    expect(store.completedLayerCount).toBe(3)
  })

  it('should_store_reportId_in_layer1', () => {
    const store = useFunnelStore()
    store.completeLayer1('report-abc')
    expect(store.actionLayers.layer1.reportId).toBe('report-abc')
  })
})

// ============================================================
// Checklist
// ============================================================
describe('checklist', () => {
  it('should_toggle_item', () => {
    const store = useFunnelStore()
    expect(store.checklist.organizeStatements).toBe(false)
    store.toggleChecklistItem('organizeStatements')
    expect(store.checklist.organizeStatements).toBe(true)
    store.toggleChecklistItem('organizeStatements')
    expect(store.checklist.organizeStatements).toBe(false)
  })

  it('should_ignore_unknown_key', () => {
    const store = useFunnelStore()
    store.toggleChecklistItem('unknownKey')
    // Should not throw, no side effects
    expect(store.checklist.organizeStatements).toBe(false)
  })
})

// ============================================================
// Reset
// ============================================================
describe('reset', () => {
  it('should_restore_all_state_to_initial', () => {
    const store = useFunnelStore()
    store.setScore(75)
    store.advanceStep(8)
    store.completeLayer1('r1')
    store.toggleChecklistItem('organizeStatements')

    store.reset()

    expect(store.score).toBe(0)
    expect(store.currentStep).toBe(1)
    expect(store.financeProfile).toBeNull()
    expect(store.actionLayers.layer1.completed).toBe(false)
    expect(store.checklist.organizeStatements).toBe(false)
  })

  it('should_call_deleteImprovementPlan_when_low_score_user_resets', () => {
    const store = useFunnelStore()
    store.setScore(45) // 低分用户
    improvementPlanApi.deleteImprovementPlan.mockClear()

    store.reset()

    expect(improvementPlanApi.deleteImprovementPlan).toHaveBeenCalledTimes(1)
  })

  it('should_not_call_deleteImprovementPlan_when_normal_user_resets', () => {
    const store = useFunnelStore()
    store.setScore(75) // 正常用户
    improvementPlanApi.deleteImprovementPlan.mockClear()

    store.reset()

    expect(improvementPlanApi.deleteImprovementPlan).not.toHaveBeenCalled()
  })
})

// ============================================================
// Pressure
// ============================================================
describe('updatePressure', () => {
  it('should_update_pressure_index_and_level', () => {
    const store = useFunnelStore()
    store.updatePressure(0.67, 'HIGH')
    expect(store.pressureIndex).toBe(0.67)
    expect(store.pressureLevel).toBe('HIGH')
  })
})

// ============================================================
// AG-14: 推算 step 不锁定导航
// ============================================================
describe('AG-14: inferred step does not lock navigation', () => {
  it('should_allow_backward_navigation_regardless_of_inferred_step', () => {
    const store = useFunnelStore()
    // 推算到 step=8
    store.advanceStep(8)
    expect(store.currentStep).toBe(8)

    // 用户可以自由退回到任意低步骤，advanceStep 无锁定
    store.advanceStep(3)
    expect(store.currentStep).toBe(3)

    store.advanceStep(1)
    expect(store.currentStep).toBe(1)
  })

  it('should_allow_forward_navigation_beyond_inferred_step', () => {
    const store = useFunnelStore()
    store.advanceStep(3)
    // 用户可以继续前进超过推算起点
    store.advanceStep(5)
    expect(store.currentStep).toBe(5)
  })
})

// ============================================================
// AG-15: 数据不一致时向下降级
// ============================================================
describe('AG-15: degrade step when data incomplete', () => {
  it('should_degrade_step_when_data_incomplete_has_reports_but_no_debts', async () => {
    // requestSilent mock: reports OK, debts empty, profile OK but no debts = degrade
    const { requestSilent } = await import('../../api/request.js')
    vi.mock('../../api/request.js', () => ({
      requestSilent: vi.fn(),
    }))
    requestSilent.mockImplementation(({ url }) => {
      if (url === '/debts') return Promise.resolve({ debts: [], totalSize: 0 })
      if (url === '/finance-profiles/mine') return Promise.resolve({ restructureScore: 75 })
      if (url === '/reports') return Promise.resolve({ reports: [{ id: '1' }], totalSize: 1 })
      return Promise.reject(new Error('unknown'))
    })

    const store = useFunnelStore()
    await store.inferStep()

    // Has reports and profile but NO debts → incomplete chain → degrade to step=1
    // (Reports require debts; without debts the complete chain is broken)
    expect(store.currentStep).toBe(1)
    vi.restoreAllMocks()
  })

  it('should_infer_step_3_when_only_debts_exist', async () => {
    const { requestSilent } = await import('../../api/request.js')
    vi.mock('../../api/request.js', () => ({
      requestSilent: vi.fn(),
    }))
    requestSilent.mockImplementation(({ url }) => {
      if (url === '/debts') return Promise.resolve({ debts: [{ id: '1' }], totalSize: 1 })
      // profile and reports fail (incomplete)
      return Promise.reject(new Error('not found'))
    })

    const store = useFunnelStore()
    await store.inferStep()

    expect(store.currentStep).toBe(3)
    vi.restoreAllMocks()
  })

  it('should_infer_step_8_only_when_all_three_complete', async () => {
    const { requestSilent } = await import('../../api/request.js')
    vi.mock('../../api/request.js', () => ({
      requestSilent: vi.fn(),
    }))
    requestSilent.mockImplementation(({ url }) => {
      if (url === '/debts') return Promise.resolve({ debts: [{ id: '1' }], totalSize: 1 })
      if (url === '/finance-profiles/mine') return Promise.resolve({ restructureScore: 75 })
      if (url === '/reports') return Promise.resolve({ reports: [{ id: '1' }], totalSize: 1 })
      return Promise.reject(new Error('unknown'))
    })

    const store = useFunnelStore()
    await store.inferStep()

    expect(store.currentStep).toBe(8)
    vi.restoreAllMocks()
  })
})
