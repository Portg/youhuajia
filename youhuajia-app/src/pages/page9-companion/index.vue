<template>
  <view class="page9">
    <ProgressBar :current="9" :total="9" />

    <!-- 完成仪式（首次进入时展示） -->
    <view v-if="showCeremony" class="ceremony-overlay" @tap="dismissCeremony">
      <view class="ceremony-card" @tap.stop>
        <text class="ceremony-emoji">🎉</text>
        <text class="ceremony-title">恭喜完成全部评估！</text>
        <text class="ceremony-desc">
          你已经比 90% 的人更了解自己的债务状况，接下来按计划执行，每一步都在帮你节省。
        </text>
        <view v-if="funnelStore.score > 0" class="ceremony-score">
          <text class="ceremony-score-label">优化评分</text>
          <text class="ceremony-score-value">{{ funnelStore.score }}</text>
          <text class="ceremony-score-unit">分</text>
        </view>
        <view class="ceremony-btn" @tap="dismissCeremony">
          <text class="ceremony-btn-text">开始行动</text>
        </view>
      </view>
    </view>

    <!-- 顶部正面强化 -->
    <view class="hero">
      <text class="headline">你已经迈出了第一步</text>
      <text class="hero-sub">持续关注，每个月都会有改善</text>
    </view>

    <!-- 目标进度卡片 -->
    <view class="goal-card">
      <view class="goal-row">
        <view class="goal-item">
          <text class="goal-label">预估可节省</text>
          <text class="goal-value accent">¥{{ estimatedSavingFormatted }}</text>
        </view>
        <view class="goal-divider" />
        <view class="goal-item">
          <text class="goal-label">目标节省</text>
          <text class="goal-value primary">¥{{ targetSavingFormatted }}</text>
        </view>
      </view>
      <view class="goal-progress-wrap">
        <view class="goal-progress-track">
          <view class="goal-progress-fill" :style="{ width: savingProgress + '%' }" />
        </view>
        <text class="goal-progress-text">{{ savingProgress }}%</text>
      </view>

      <view class="steps-row">
        <view class="step-dot" v-for="i in 4" :key="i" :class="{ 'step-done': i <= actionStepsCompleted }" />
        <text class="steps-label">进度 {{ actionStepsCompleted }}/4 步</text>
      </view>
    </view>

    <!-- 30/60/90 天进度时间轴 -->
    <view class="section-card">
      <text class="section-title">你的优化进度</text>
      <Timeline
        :milestones="[30, 60, 90]"
        :current-day="currentDay"
      />
    </view>

    <!-- 可勾选 Checklist -->
    <view class="section-card">
      <text class="section-title">今日行动清单</text>

      <!-- 空状态引导（首次到达时） -->
      <view v-if="completedCount === 0" class="empty-guide">
        <text class="empty-guide-text">从第一项开始，逐步完成清单。每勾选一项都在帮你改善财务状况。</text>
      </view>

      <view class="checklist">
        <ChecklistItem
          text="整理所有账单"
          tip="了解每笔债务的还款日和金额"
          :checked="checklist.organizeStatements"
          @update:checked="(v) => funnelStore.toggleChecklistItem('organizeStatements')"
        />
        <ChecklistItem
          text="确认各债务最低还款日"
          tip="避免逾期是改善信用的第一步"
          :checked="checklist.confirmPaymentDates"
          @update:checked="(v) => funnelStore.toggleChecklistItem('confirmPaymentDates')"
        />
        <ChecklistItem
          :text="'优先偿还 ' + highestAprCreditor"
          tip="高利率债务节省效果最显著"
          :checked="checklist.prioritizeHighApr"
          @update:checked="(v) => funnelStore.toggleChecklistItem('prioritizeHighApr')"
        />
        <ChecklistItem
          text="30天后重新评估"
          tip="看看改善了多少，调整下一步方向"
          :checked="checklist.reassessIn30Days"
          @update:checked="(v) => funnelStore.toggleChecklistItem('reassessIn30Days')"
        />
      </view>

      <!-- Checklist 进度条 -->
      <view class="checklist-footer">
        <view class="progress-track">
          <view class="progress-fill" :style="{ width: checklistProgress + '%' }" />
        </view>
        <text class="progress-label">{{ completedCount }}/4 已完成</text>
      </view>
    </view>

    <!-- 下一个检查点提醒卡片 -->
    <view class="reminder-card">
      <view class="reminder-header">
        <text class="reminder-title">下一个检查点</text>
        <view class="reminder-badge">
          <text class="reminder-badge-text">{{ daysToNext }} 天后</text>
        </view>
      </view>
      <text class="reminder-desc">{{ nextCheckpointDesc }}</text>
      <YouhuaButton
        text="设置提醒"
        type="secondary"
        @click="setReminder"
      />
    </view>

    <!-- 意向咨询 -->
    <ConsultCard />

    <!-- 底部鼓励语 -->
    <text class="encouragement">
      越早调整，节省越多。按自己的节奏来，每一步都算数。
    </text>

    <SafeAreaBottom />
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useFunnelStore } from '../../stores/funnel'
import { useDebtStore } from '../../stores/debt'
import { useProfileStore } from '../../stores/profile'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import Timeline from './components/Timeline.vue'
import ChecklistItem from './components/ChecklistItem.vue'
import ConsultCard from './components/ConsultCard.vue'

const funnelStore = useFunnelStore()
const debtStore = useDebtStore()
const profileStore = useProfileStore()

// 完成仪式（仅首次展示）
const showCeremony = ref(false)
onMounted(() => {
  const shown = uni.getStorageSync('page9CeremonyShown')
  if (!shown) {
    showCeremony.value = true
  }
  // 确保债务和画像数据已加载（用于预估节省金额）
  if (debtStore.totalCount === 0) debtStore.loadDebts()
  if (!profileStore.profile) profileStore.loadProfile()
})
function dismissCeremony() {
  showCeremony.value = false
  uni.setStorageSync('page9CeremonyShown', '1')
}

const checklist = computed(() => funnelStore.checklist)
const profile = computed(() => funnelStore.financeProfile || {})

// ---- 目标进度 ----
// 预估节省：优先用后端 financeProfile 的 threeYearExtraInterest，兜底用 debtStore 本地计算
const estimatedSaving = computed(() => {
  const backendValue = Number(profile.value.threeYearExtraInterest) || 0
  if (backendValue > 0) return backendValue
  return debtStore.estimatedSaving || 0
})
const estimatedSavingFormatted = computed(() => estimatedSaving.value.toLocaleString('zh-CN'))

// 系统自动设定目标：预估节省的 120%（给用户一个可达但有挑战的目标）
const targetSaving = computed(() => {
  const est = estimatedSaving.value
  if (est <= 0) return 10000
  return Math.ceil(est * 1.2 / 1000) * 1000 // 向上取整到千
})
const targetSavingFormatted = computed(() => targetSaving.value.toLocaleString('zh-CN'))

// 节省进度百分比
const savingProgress = computed(() => {
  if (targetSaving.value <= 0) return 0
  return Math.min(100, Math.round((estimatedSaving.value / targetSaving.value) * 100))
})

// 行动步骤完成数（漏斗 layer 1-3 + checklist 全部完成算第4步）
const actionStepsCompleted = computed(() => {
  let steps = funnelStore.completedLayerCount // 0-3
  if (completedCount.value >= 4) steps++ // checklist 全部完成
  return Math.min(4, steps)
})

// 当前天数（实际场景从持久化存储读取）
const currentDay = ref(1)

// 最高 APR 债权人名称
const highestAprCreditor = computed(() => {
  return profile.value.highestAprCreditor || '最高利率债务'
})

// 已完成 checklist 数量
const completedCount = computed(() => {
  return Object.values(checklist.value).filter(Boolean).length
})

// Checklist 进度百分比
const checklistProgress = computed(() => {
  return (completedCount.value / 4) * 100
})

// 下一个检查点
const daysToNext = ref(29)
const nextCheckpointDesc = ref('整理账单、确认还款日，完成 30 天改善计划第一阶段')

function setReminder() {
  uni.showModal({
    title: '设置检查点提醒',
    content: `将在 ${daysToNext.value} 天后提醒你进行下一次评估`,
    confirmText: '确认设置',
    confirmColor: '#2E75B6',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: '提醒已设置', icon: 'success', duration: 2000 })
      }
    },
  })
}
</script>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page9 {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: 40rpx;
}

/* 顶部英雄区 */
.hero {
  background: linear-gradient(135deg, $primary 0%, #1a5a9e 100%);
  padding: 60rpx $spacing-xl 48rpx;
}

.headline {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #ffffff;
  margin-bottom: 12rpx;
}

.hero-sub {
  display: block;
  font-size: $font-sm;
  color: rgba(255, 255, 255, 0.85);
}

/* 目标进度卡片 */
.goal-card {
  background: $surface;
  border-radius: $radius-md;
  margin: $spacing-md $spacing-xl;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
}

.goal-row {
  display: flex;
  align-items: center;
  margin-bottom: $spacing-lg;
}

.goal-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.goal-divider {
  width: 2rpx;
  height: 60rpx;
  background: $divider;
}

.goal-label {
  font-size: $font-xs;
  color: $text-tertiary;
}

.goal-value {
  font-size: 40rpx;
  font-weight: $weight-bold;

  &.accent {
    color: $accent;
  }
  &.primary {
    color: $primary;
  }
}

.goal-progress-wrap {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-lg;
}

.goal-progress-track {
  flex: 1;
  height: 12rpx;
  background: $divider-light;
  border-radius: $radius-pill;
  overflow: hidden;
}

.goal-progress-fill {
  height: 100%;
  background: $positive-gradient;
  border-radius: $radius-pill;
  transition: width $transition-normal;
}

.goal-progress-text {
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $positive;
  flex-shrink: 0;
}

.steps-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.step-dot {
  width: 20rpx;
  height: 20rpx;
  border-radius: 50%;
  background: $divider;
  transition: background $transition-fast;

  &.step-done {
    background: $positive;
  }
}

.steps-label {
  font-size: $font-xs;
  color: $text-secondary;
  margin-left: auto;
}

/* 通用卡片 */
.section-card {
  background-color: $surface;
  border-radius: $radius-md;
  margin: $spacing-md $spacing-xl;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.section-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

/* Checklist */
.checklist {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.checklist-footer {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.progress-track {
  flex: 1;
  height: 8rpx;
  background-color: $divider;
  border-radius: 4rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background-color: $positive;
  border-radius: 4rpx;
  transition: width 0.3s ease;
}

.progress-label {
  font-size: $font-xs;
  color: $text-secondary;
  flex-shrink: 0;
}

/* 提醒卡片 */
.reminder-card {
  background: linear-gradient(135deg, $positive-light, $primary-light);
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
}

.reminder-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: 12rpx;
}

.reminder-title {
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  flex: 1;
}

.reminder-badge {
  background-color: $positive;
  border-radius: 20rpx;
  padding: 4rpx 16rpx;
}

.reminder-badge-text {
  font-size: $font-xs;
  color: #ffffff;
  font-weight: 600;
}

.reminder-desc {
  display: block;
  font-size: $font-sm;
  color: #4b5563;
  line-height: 1.5;
  margin-bottom: $spacing-md;
}

/* 完成仪式 */
.ceremony-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
  padding: 0 $spacing-xl;
}

.ceremony-card {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-2xl $spacing-xl;
  display: flex;
  flex-direction: column;
  align-items: center;
  width: 100%;
}

.ceremony-emoji {
  font-size: 120rpx;
  margin-bottom: $spacing-lg;
}

.ceremony-title {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.ceremony-desc {
  font-size: $font-sm;
  color: $text-secondary;
  text-align: center;
  line-height: 1.6;
  margin-bottom: $spacing-xl;
}

.ceremony-score {
  display: flex;
  align-items: baseline;
  gap: 8rpx;
  margin-bottom: $spacing-xl;
}

.ceremony-score-label {
  font-size: $font-sm;
  color: $text-tertiary;
}

.ceremony-score-value {
  font-size: 72rpx;
  font-weight: $weight-bold;
  color: $primary;
}

.ceremony-score-unit {
  font-size: $font-sm;
  color: $text-tertiary;
}

.ceremony-btn {
  width: 100%;
  height: 88rpx;
  background: $primary;
  border-radius: $radius-md;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ceremony-btn-text {
  font-size: $font-md;
  font-weight: 600;
  color: #ffffff;
}

/* 空状态引导 */
.empty-guide {
  background: $primary-light;
  border-radius: $radius-sm;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
}

.empty-guide-text {
  font-size: $font-sm;
  color: $primary;
  line-height: 1.5;
}

/* 鼓励语 */
.encouragement {
  display: block;
  text-align: center;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.7;
  padding: 0 $spacing-xl $spacing-lg;
}
</style>
