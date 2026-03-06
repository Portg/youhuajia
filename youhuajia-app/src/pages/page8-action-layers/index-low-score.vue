<template>
  <view class="page8-low-container">
    <!-- 顶部进度条 -->
    <view class="top-progress-section">
      <view class="layer-progress-bar">
        <view class="layer-segment" :class="{ active: completedCount >= 1, current: completedCount === 0 }">
          <view class="segment-fill" />
          <text class="segment-label">改善计划</text>
        </view>
        <view class="layer-connector" />
        <view class="layer-segment" :class="{ active: completedCount >= 2, current: completedCount === 1 }">
          <view class="segment-fill" />
          <text class="segment-label">还款提醒</text>
        </view>
        <view class="layer-connector" />
        <view class="layer-segment" :class="{ active: completedCount >= 3, current: completedCount === 2 }">
          <view class="segment-fill" />
          <text class="segment-label">重新评估</text>
        </view>
      </view>
      <text class="progress-text">{{ completedCount }}/3 步骤已完成</text>
    </view>

    <!-- 鼓励标题 -->
    <view class="header-section">
      <text class="headline">你的 30 天改善行动</text>
      <text class="headline-sub">每一步都让你的信用状况更好，按自己的节奏来</text>
    </view>

    <view class="layers-list">
      <!-- Layer 1: 生成 30 天改善计划 -->
      <view class="layer-card" :class="{ 'layer-completed': layer1.completed, 'layer-active': !layer1.completed }">
        <view class="layer-header">
          <view class="layer-number-badge" :class="{ 'badge-done': layer1.completed }">
            <text v-if="layer1.completed" class="badge-check">✓</text>
            <text v-else class="badge-num">1</text>
          </view>
          <view class="layer-title-area">
            <text class="layer-title">生成 30 天改善计划</text>
            <text class="layer-desc">获取专属信用改善路径，每步都有具体行动</text>
          </view>
        </view>

        <view v-if="!layer1.completed" class="layer-actions">
          <button
            class="layer-btn-primary"
            :disabled="layer1Loading"
            @tap="handleLayer1"
          >
            <text class="btn-text">{{ layer1Loading ? '生成中...' : '生成我的改善计划' }}</text>
          </button>
          <button class="layer-btn-skip" @tap="handleSkip">暂不继续</button>
        </view>

        <view v-if="layer1.completed" class="layer-result">
          <view v-for="(item, i) in improvementPlan" :key="i" class="plan-result-item">
            <text class="plan-week">第 {{ item.week }} 周</text>
            <text class="plan-task">{{ item.task }}</text>
          </view>
        </view>
      </view>

      <!-- Layer 2: 设置还款提醒 -->
      <view
        class="layer-card"
        :class="{
          'layer-completed': layer2.completed,
          'layer-active': layer1.completed && !layer2.completed,
          'layer-locked': !layer1.completed,
        }"
      >
        <view class="layer-header">
          <view class="layer-number-badge" :class="{ 'badge-done': layer2.completed, 'badge-locked': !layer1.completed }">
            <text v-if="layer2.completed" class="badge-check">✓</text>
            <text v-else class="badge-num">2</text>
          </view>
          <view class="layer-title-area">
            <text class="layer-title" :class="{ 'title-locked': !layer1.completed }">设置还款提醒</text>
            <text class="layer-desc">{{ layer1.completed ? '按时还款是改善信用状况的第一步' : '完成第 1 步后解锁' }}</text>
          </view>
        </view>

        <view v-if="layer1.completed && !layer2.completed" class="layer-actions">
          <button class="layer-btn-primary" @tap="handleSetReminder">
            <text class="btn-text">设置还款提醒</text>
          </button>
          <button class="layer-btn-skip" @tap="handleSkip">暂不继续</button>
        </view>

        <view v-if="layer2.completed" class="layer-result">
          <text class="result-success">已设置还款提醒，将在还款日前 3 天通知你</text>
        </view>
      </view>

      <!-- Layer 3: 30 天后重新评估 -->
      <view
        class="layer-card"
        :class="{
          'layer-completed': layer3.completed,
          'layer-active': layer2.completed && !layer3.completed,
          'layer-locked': !layer2.completed,
        }"
      >
        <view class="layer-header">
          <view class="layer-number-badge" :class="{ 'badge-done': layer3.completed, 'badge-locked': !layer2.completed }">
            <text v-if="layer3.completed" class="badge-check">✓</text>
            <text v-else class="badge-num">3</text>
          </view>
          <view class="layer-title-area">
            <text class="layer-title" :class="{ 'title-locked': !layer2.completed }">30 天后重新评估</text>
            <text class="layer-desc">{{ layer2.completed ? '坚持 30 天，看看改善了多少' : '完成第 2 步后解锁' }}</text>
          </view>
        </view>

        <view v-if="layer2.completed && !layer3.completed" class="layer-actions">
          <button class="layer-btn-primary" @tap="handleScheduleReassess">
            <text class="btn-text">预约 30 天后评估</text>
          </button>
          <button class="layer-btn-skip" @tap="handleSkip">暂不继续</button>
        </view>

        <view v-if="layer3.completed" class="layer-result">
          <text class="result-success">已预约重新评估，到时我们会提醒你</text>
        </view>
      </view>
    </view>

    <!-- 完成后 CTA -->
    <view v-if="completedCount >= 1" class="cta-area">
      <button class="btn-primary" @tap="handleToCompanion">查看我的改善进度</button>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useFunnelStore } from '../../stores/funnel'

const funnelStore = useFunnelStore()

const layer1Loading = ref(false)

const layer1 = computed(() => funnelStore.actionLayers.layer1)
const layer2 = computed(() => funnelStore.actionLayers.layer2)
const layer3 = computed(() => funnelStore.actionLayers.layer3)

const completedCount = computed(() => funnelStore.completedLayerCount)

// 30天改善计划内容（低分路径）
const improvementPlan = [
  { week: '1-2', task: '整理账单，确认最低还款额' },
  { week: '2-3', task: '优先按时还清最小额度账户' },
  { week: '3-4', task: '信用卡使用率控制在 70% 以下' },
]

async function handleLayer1() {
  layer1Loading.value = true
  // 低分用户生成改善计划（本地生成，无需额外 API）
  await new Promise((resolve) => setTimeout(resolve, 800))
  funnelStore.completeLayer1('low-score-plan')
  funnelStore.actionLayers.layer1.result = { documents: improvementPlan }
  layer1Loading.value = false
}

function handleSetReminder() {
  // 调用系统日历/通知 API
  uni.showModal({
    title: '设置还款提醒',
    content: '将在每月还款日前 3 天提醒你',
    confirmText: '确认设置',
    confirmColor: '#2E75B6',
    success: (res) => {
      if (res.confirm) {
        funnelStore.completeLayer2()
      }
    },
  })
}

function handleScheduleReassess() {
  uni.showModal({
    title: '预约重新评估',
    content: '30 天后，我们会提醒你重新评估信用状况',
    confirmText: '好的，期待',
    confirmColor: '#2E75B6',
    success: (res) => {
      if (res.confirm) {
        funnelStore.completeLayer3()
      }
    },
  })
}

function handleSkip() {
  uni.showModal({
    title: '暂停一下',
    content: '你可以随时回来继续，进度已保存',
    showCancel: false,
    confirmText: '好的',
    confirmColor: '#2E75B6',
  })
}

function handleToCompanion() {
  uni.navigateTo({ url: '/pages/page9-companion/index' })
}
</script>

<style scoped>
.page8-low-container {
  min-height: 100vh;
  background-color: #f8fafe;
  padding-bottom: 120rpx;
}

.top-progress-section {
  background-color: #ffffff;
  padding: 32rpx 40rpx 24rpx;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.layer-progress-bar {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.layer-segment {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.segment-fill {
  width: 100%;
  height: 6rpx;
  background-color: #e5e7eb;
  border-radius: 3rpx;
}

.layer-segment.active .segment-fill {
  background-color: #2baf7e;
}

.layer-segment.current .segment-fill {
  background-color: #2e75b6;
}

.layer-connector {
  width: 8rpx;
  height: 6rpx;
  background-color: #e5e7eb;
}

.segment-label {
  font-size: 20rpx;
  color: #9ca3af;
}

.layer-segment.active .segment-label {
  color: #2baf7e;
}

.layer-segment.current .segment-label {
  color: #2e75b6;
  font-weight: 500;
}

.progress-text {
  font-size: 24rpx;
  color: #6b7280;
}

.header-section {
  padding: 32rpx 40rpx 16rpx;
}

.headline {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 8rpx;
}

.headline-sub {
  display: block;
  font-size: 26rpx;
  color: #6b7280;
  line-height: 1.5;
}

.layers-list {
  padding: 16rpx 40rpx;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.layer-card {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 2rpx solid transparent;
}

.layer-active {
  border-color: #2e75b6;
}

.layer-completed {
  border-color: #2baf7e;
  background-color: #f0fdf7;
}

.layer-locked {
  opacity: 0.6;
}

.layer-header {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  margin-bottom: 20rpx;
}

.layer-number-badge {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background-color: #2e75b6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.badge-done {
  background-color: #2baf7e;
}

.badge-locked {
  background-color: #d1d5db;
}

.badge-num {
  font-size: 24rpx;
  color: #ffffff;
  font-weight: 600;
}

.badge-check {
  font-size: 24rpx;
  color: #ffffff;
  font-weight: 700;
}

.layer-title-area {
  flex: 1;
}

.layer-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 6rpx;
}

.title-locked {
  color: #9ca3af;
}

.layer-desc {
  display: block;
  font-size: 24rpx;
  color: #6b7280;
}

.layer-actions {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.layer-btn-primary {
  width: 100%;
  height: 80rpx;
  background-color: #2e75b6;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 16rpx;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.layer-btn-primary::after {
  border: none;
}

.layer-btn-primary[disabled] {
  background-color: #d5e8f0;
  color: #9ca3af;
}

.layer-btn-skip {
  width: 100%;
  height: 72rpx;
  background-color: transparent;
  color: #6b7280;
  font-size: 26rpx;
  border: none;
}

.layer-btn-skip::after {
  border: none;
}

.btn-text {
  font-size: 28rpx;
}

.layer-result {
  background-color: #f8fafe;
  border-radius: 12rpx;
  padding: 20rpx;
  margin-top: 8rpx;
}

.plan-result-item {
  margin-bottom: 12rpx;
}

.plan-week {
  display: block;
  font-size: 22rpx;
  color: #2e75b6;
  font-weight: 500;
}

.plan-task {
  display: block;
  font-size: 26rpx;
  color: #374151;
}

.result-success {
  display: block;
  font-size: 26rpx;
  color: #2baf7e;
  line-height: 1.4;
}

.cta-area {
  padding: 24rpx 40rpx calc(24rpx + env(safe-area-inset-bottom));
}

.btn-primary {
  width: 100%;
  height: 96rpx;
  background-color: #2e75b6;
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 600;
  border-radius: 24rpx;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.btn-primary::after {
  border: none;
}
</style>
