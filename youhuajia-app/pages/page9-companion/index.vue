<template>
  <view class="page9">
    <ProgressBar :current="9" :total="9" />

    <!-- 顶部正面强化 -->
    <view class="hero">
      <text class="headline">你已经迈出了第一步</text>
      <text class="hero-sub">持续关注，每个月都会有改善</text>
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

    <!-- 底部鼓励语 -->
    <text class="encouragement">
      越早调整，节省越多。按自己的节奏来，每一步都算数。
    </text>

    <SafeAreaBottom />
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useFunnelStore } from '../../src/stores/funnel'
import ProgressBar from '../../src/components/ProgressBar.vue'
import YouhuaButton from '../../src/components/YouhuaButton.vue'
import SafeAreaBottom from '../../src/components/SafeAreaBottom.vue'
import Timeline from './components/Timeline.vue'
import ChecklistItem from './components/ChecklistItem.vue'

const funnelStore = useFunnelStore()

const checklist = computed(() => funnelStore.checklist)
const profile = computed(() => funnelStore.financeProfile || {})

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
@use '../../src/styles/variables.scss' as *;
@use '../../src/styles/mixins.scss' as *;

.page9 {
  min-height: 100vh;
  @include page-bg;
  padding-bottom: 40rpx;
}

/* 顶部英雄区 */
.hero {
  background: $primary-gradient;
  padding: 64rpx $spacing-xl 52rpx;
  position: relative;
  overflow: hidden;

  &::after {
    content: '';
    position: absolute;
    top: 0;
    right: -20%;
    width: 60%;
    height: 100%;
    background: radial-gradient(circle, rgba(255, 255, 255, 0.08) 0%, transparent 70%);
    pointer-events: none;
  }
}

.headline {
  display: block;
  font-size: 48rpx;
  font-weight: $weight-black;
  color: $text-inverse;
  margin-bottom: 12rpx;
  letter-spacing: -1rpx;
  position: relative;
  z-index: 1;
}

.hero-sub {
  display: block;
  font-size: $font-sm;
  color: rgba(255, 255, 255, 0.8);
  position: relative;
  z-index: 1;
}

/* 通用卡片 */
.section-card {
  @include card-elevated;
  margin: $spacing-md $spacing-xl;
}

.section-title {
  display: block;
  font-size: $font-md;
  font-weight: $weight-semibold;
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
  height: 6rpx;
  background: $divider-light;
  border-radius: 3rpx;
  overflow: hidden;
}

.progress-fill {
  height: 100%;
  background: $positive-gradient;
  border-radius: 3rpx;
  transition: width $transition-smooth;
}

.progress-label {
  font-size: $font-xs;
  color: $text-secondary;
  flex-shrink: 0;
  font-weight: $weight-medium;
}

/* 提醒卡片 */
.reminder-card {
  background: linear-gradient(135deg, $positive-glass, $primary-glass);
  border: 1rpx solid rgba(15, 169, 104, 0.08);
  border-radius: $radius-xl;
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
  font-weight: $weight-semibold;
  color: $text-primary;
  flex: 1;
}

.reminder-badge {
  background: $positive-gradient;
  border-radius: $radius-pill;
  padding: 6rpx 18rpx;
  box-shadow: 0 4rpx 12rpx rgba(15, 169, 104, 0.25);
}

.reminder-badge-text {
  font-size: $font-xs;
  color: $text-inverse;
  font-weight: $weight-semibold;
}

.reminder-desc {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.6;
  margin-bottom: $spacing-md;
}

/* 鼓励语 */
.encouragement {
  display: block;
  text-align: center;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.75;
  padding: 0 $spacing-xl $spacing-lg;
}
</style>
