<template>
  <view class="improvement-plan">
    <FunnelNavBar title="改善计划" />
    <ProgressBar :current="8" :total="9" />

    <view class="page-header">
      <text class="headline">你的 30 天改善行动</text>
      <text class="sub-headline">每一步都让你的信用状况更好，按自己的节奏来</text>
      <LayerProgress :current="completedCount" :total="3" />
    </view>

    <view class="layers">
      <!-- Layer 1: 生成 30 天改善计划 -->
      <ActionLayer
        :layer="1"
        title="生成 30 天改善计划"
        :status="layer1Status"
        :active="true"
        :locked="false"
        :loading="layer1Loading"
        action-text="生成我的改善计划"
        @action="onLayer1"
        @skip="onSkip"
      >
        <template #result>
          <view class="plan-result">
            <view v-for="(item, i) in personalizedPlan" :key="i" class="plan-item">
              <text class="plan-week">第 {{ item.week }} 周</text>
              <text class="plan-task">{{ item.task }}</text>
            </view>
          </view>

          <!-- 改善后预估分数 -->
          <view v-if="projectedScore > 0" class="forecast-card">
            <view class="forecast-row">
              <text class="forecast-label">当前评分</text>
              <text class="forecast-current">{{ funnelStore.score }}分</text>
            </view>
            <view class="forecast-row">
              <text class="forecast-label">30天后预估</text>
              <text class="forecast-projected">{{ projectedScore }}分</text>
            </view>
            <view class="forecast-bar-wrap">
              <view class="forecast-bar">
                <view class="forecast-bar-current" :style="{ width: (funnelStore.score / 100 * 100) + '%' }" />
                <view class="forecast-bar-projected" :style="{ width: (projectedScore / 100 * 100) + '%' }" />
              </view>
              <view class="forecast-threshold" :style="{ left: '60%' }">
                <text class="threshold-label">60分</text>
              </view>
            </view>
            <text class="forecast-hint">
              {{ projectedScore >= 60
                ? '预计可达主优化流程门槛，坚持执行改善计划'
                : '持续改善中，每一步都在帮你接近目标'
              }}
            </text>

            <!-- 维度变化明细 -->
            <view v-if="dimChanges.length > 0" class="dim-changes">
              <view v-for="change in dimChanges" :key="change.label" class="dim-change-item">
                <text class="dim-change-label">{{ change.label }}</text>
                <text class="dim-change-delta">+{{ change.delta }}分</text>
              </view>
            </view>
          </view>
        </template>
      </ActionLayer>

      <!-- Layer 2: 设置还款提醒 -->
      <ActionLayer
        :layer="2"
        title="设置还款提醒"
        :status="layer2Status"
        :active="layer1.completed"
        :locked="!layer1.completed"
        action-text="设置还款提醒"
        @action="onLayer2"
        @skip="onSkip"
      >
        <template #result>
          <text class="result-success">已设置还款提醒，将在还款日前 3 天通知你</text>
        </template>
      </ActionLayer>

      <!-- Layer 3: 30 天后重新评估 -->
      <ActionLayer
        :layer="3"
        title="30 天后重新评估"
        :status="layer3Status"
        :active="layer2.completed"
        :locked="!layer2.completed"
        action-text="预约 30 天后评估"
        @action="onLayer3"
        @skip="onSkip"
      >
        <template #result>
          <text class="result-success">已预约重新评估，到时我们会提醒你</text>
        </template>
      </ActionLayer>
    </view>

    <!-- CTA -->
    <view class="cta-area">
      <YouhuaButton
        text="查看我的行动计划"
        type="primary"
        :disabled="completedCount < 1"
        @click="goToCompanion"
      />
    </view>

    <SafeAreaBottom />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useFunnelStore } from '../../stores/funnel'
import { useProfileStore } from '../../stores/profile'
import { useDebtStore } from '../../stores/debt'
import { simulateScore } from '../../api/engine'
import { simulateImprovement, buildScoreInput } from '../../utils/scoreSimulator.js'
import FunnelNavBar from '../../components/FunnelNavBar.vue'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import ActionLayer from '../page8-action-layers/components/ActionLayer.vue'
import LayerProgress from '../page8-action-layers/components/LayerProgress.vue'

const funnelStore = useFunnelStore()
const profileStore = useProfileStore()
const debtStore = useDebtStore()

const layer1Loading = ref(false)

const layer1 = computed(() => funnelStore.actionLayers.layer1)
const layer2 = computed(() => funnelStore.actionLayers.layer2)
const layer3 = computed(() => funnelStore.actionLayers.layer3)
const completedCount = computed(() => funnelStore.completedLayerCount)

// 本地评分模拟（不依赖后端）
const scoreInput = computed(() => buildScoreInput(profileStore, funnelStore, debtStore))
const simulation = computed(() =>
  simulateImprovement(scoreInput.value, [
    { type: 'CATCH_UP_PAYMENTS' },
    { type: 'REDUCE_UTILIZATION' },
    { type: 'PAY_OFF_SMALLEST' },
  ])
)
const projectedScore = computed(() =>
  Math.min(100, Math.round(funnelStore.score + simulation.value.scoreDelta))
)
const dimChanges = computed(() => simulation.value.dimChanges)

// 根据弱项生成个性化改善计划
const personalizedPlan = computed(() => {
  const weak = simulation.value.current.weakDimensions
  const plan = []

  plan.push({ week: '1', task: '整理账单，确认最低还款额和还款日' })

  // 第 2 周：根据最弱维度
  const weakestKey = weak.length > 0 ? weak[0].key : null
  if (weakestKey === 'overdue') {
    plan.push({ week: '2', task: '优先补齐逾期款项，恢复按时还款' })
  } else if (weakestKey === 'weightedApr') {
    plan.push({ week: '2', task: '识别利率最高的债务，优先偿还' })
  } else {
    plan.push({ week: '2', task: '优先按时还清最小额度账户' })
  }

  // 第 3 周：使用率控制
  plan.push({ week: '3', task: '信用卡使用率控制在 70% 以下，避免新增借贷' })

  // 第 4 周：重新评估
  plan.push({ week: '4', task: '回到优化家重新评分，查看改善幅度' })

  return plan
})

const layer1Status = computed(() => {
  if (layer1.value.completed) return 'completed'
  if (layer1Loading.value) return 'in_progress'
  return 'pending'
})

const layer2Status = computed(() => {
  if (layer2.value.completed) return 'completed'
  return 'pending'
})

const layer3Status = computed(() => {
  if (layer3.value.completed) return 'completed'
  return 'pending'
})

async function onLayer1() {
  layer1Loading.value = true
  try {
    // 尝试调用后端记录用户行为（非必须）
    await simulateScore([
      { type: 'CATCH_UP_PAYMENTS' },
      { type: 'REDUCE_UTILIZATION' },
    ])
  } catch {
    // 后端不可用时静默降级，本地模拟已提供结果
  }
  // 无论后端是否成功，均使用本地模拟结果
  funnelStore.completeLayer1(null) // 低分路径无报告 ID
  funnelStore.actionLayers.layer1.result = {
    plan: personalizedPlan.value,
    forecastScore: projectedScore.value,
  }
  layer1Loading.value = false
}

function onLayer2() {
  uni.showModal({
    title: '设置还款提醒',
    content: '将在每月还款日前 3 天提醒你，避免逾期',
    confirmText: '确认设置',
    confirmColor: '#1B6DB2',
    success: (res) => {
      if (res.confirm) {
        funnelStore.completeLayer2()
      }
    },
  })
}

function onLayer3() {
  uni.showModal({
    title: '预约重新评估',
    content: '30 天后，我们会提醒你重新评估信用状况',
    confirmText: '好的，期待',
    confirmColor: '#1B6DB2',
    success: (res) => {
      if (res.confirm) {
        funnelStore.completeLayer3()
      }
    },
  })
}

function onSkip() {
  uni.showModal({
    title: '暂停一下',
    content: '你可以随时回来继续，进度已保存',
    showCancel: false,
    confirmText: '好的',
    confirmColor: '#1B6DB2',
  })
}

function goToCompanion() {
  funnelStore.advanceStep(9)
  uni.navigateTo({ url: '/pages/page9-companion/index' })
}
</script>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.improvement-plan {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: 140rpx;
}

.page-header {
  background-color: $surface;
  padding: $spacing-lg $spacing-xl $spacing-md;
  box-shadow: $shadow-xs;
}

.headline {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.sub-headline {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.5;
  margin-bottom: $spacing-md;
}

.layers {
  padding: $spacing-md $spacing-xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

/* 改善计划结果 */
.plan-result {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.plan-item {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.plan-week {
  font-size: $font-xs;
  color: $primary;
  font-weight: 500;
}

.plan-task {
  font-size: $font-sm;
  color: $text-primary;
  line-height: 1.4;
}

/* 预估分数卡片 */
.forecast-card {
  background: $primary-light;
  border-radius: $radius-md;
  padding: $spacing-md;
}

.forecast-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.forecast-label {
  font-size: $font-xs;
  color: $text-secondary;
}

.forecast-current {
  font-size: $font-md;
  font-weight: $weight-semibold;
  color: $accent;
}

.forecast-projected {
  font-size: $font-md;
  font-weight: $weight-bold;
  color: $positive;
}

.forecast-bar-wrap {
  position: relative;
  margin: $spacing-sm 0;
}

.forecast-bar {
  height: 12rpx;
  background: $divider-light;
  border-radius: $radius-pill;
  overflow: hidden;
  position: relative;
}

.forecast-bar-current {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: $accent;
  border-radius: $radius-pill;
  opacity: 0.4;
}

.forecast-bar-projected {
  position: absolute;
  left: 0;
  top: 0;
  height: 100%;
  background: $positive-gradient;
  border-radius: $radius-pill;
}

.forecast-threshold {
  position: absolute;
  top: -4rpx;
  transform: translateX(-50%);
}

.threshold-label {
  font-size: 18rpx;
  color: $text-tertiary;
  background: $surface;
  padding: 0 4rpx;
  border-radius: 4rpx;
}

.forecast-hint {
  display: block;
  font-size: $font-xs;
  color: $text-secondary;
  text-align: center;
  margin-top: 8rpx;
}

/* 维度变化明细 */
.dim-changes {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-sm;
  padding-top: $spacing-sm;
  border-top: 1rpx solid $divider;
}

.dim-change-item {
  display: flex;
  align-items: center;
  gap: 6rpx;
}

.dim-change-label {
  font-size: $font-xs;
  color: $text-secondary;
}

.dim-change-delta {
  font-size: $font-xs;
  font-weight: $weight-semibold;
  color: $positive;
}

.result-success {
  display: block;
  font-size: $font-sm;
  color: $positive;
  line-height: 1.4;
}

.cta-area {
  padding: 0 $spacing-xl $spacing-lg;
}
</style>
