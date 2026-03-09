<script setup>
import { computed, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { useFunnelStore } from '../../stores/funnel'
import { useAuthStore } from '../../stores/auth'
import { useDebtStore } from '../../stores/debt'
import YouhuaButton from '../../components/YouhuaButton.vue'

const funnelStore = useFunnelStore()
const authStore = useAuthStore()
const debtStore = useDebtStore()

// 首次使用引导（仅触发一次）
onShow(() => {
  const done = uni.getStorageSync('onboardingDone')
  if (!done) {
    uni.navigateTo({ url: '/pages/onboarding/index' })
    return
  }
  // 预加载债务数据，确保 continueAssessment 降级逻辑有数据可判断
  if (authStore.isLoggedIn && funnelStore.currentStep > 1) {
    debtStore.loadDebts()
  }
})

const stepPageMap = {
  1: '/pages/page1-safe-entry/index',
  2: '/pages/page2-pressure-check/index',
  3: '/pages/page3-debt-input/index',
  4: '/pages/page4-loss-report/index',
  5: '/pages/page5-optimization/index',
  6: '/pages/page6-rate-simulator/index',
  7: '/pages/page7-risk-assessment/index',
  8: '/pages/page8-action-layers/index',
  9: '/pages/page9-companion/index',
}

// 低分用户 Step 5-8 走独立路径
const lowScorePageMap = {
  5: '/pages/low-score/credit-optimization',
  6: '/pages/low-score/credit-repair',
  7: '/pages/low-score/risk-faq',
  8: '/pages/low-score/improvement-plan',
  9: '/pages/page9-companion/index',
}

const stepLabels = {
  1: '开始检查',
  2: '快速检查',
  3: '债务录入',
  4: '分析报告',
  5: '优化空间',
  6: '利率模拟',
  7: '风险评估',
  8: '行动计划',
  9: '我的进度',
}

// 低分用户 Step 5-8 走独立路径，标签需对应实际页面
const lowScoreStepLabels = {
  5: '信用优化引导',
  6: '修复路线图',
  7: '常见问题',
  8: '改善行动',
  9: '我的进度',
}

const currentStepLabel = computed(() => {
  const step = funnelStore.currentStep
  if (funnelStore.isLowScore && lowScoreStepLabels[step]) {
    return lowScoreStepLabels[step]
  }
  return stepLabels[step] || '评估中'
})

// 三态判断
const state = computed(() => {
  if (!authStore.isLoggedIn || funnelStore.currentStep <= 1) return 'new'
  if (funnelStore.currentStep >= 9) return 'completed'
  return 'inProgress'
})

const progressPercent = computed(() => {
  return Math.round(((funnelStore.currentStep - 1) / 8) * 100)
})

const pressureLevelText = computed(() => {
  const map = { HEALTHY: '健康', MODERATE: '中等', HIGH: '偏高', CRITICAL: '严重' }
  return map[funnelStore.pressureLevel] || '未检测'
})

function startCheck() {
  uni.navigateTo({ url: '/pages/page1-safe-entry/index' })
}

function continueAssessment() {
  // 数据感知跳转：根据实际完成状态决定目标页
  const step = funnelStore.currentStep
  let targetStep = step

  // 如果压力检测未完成但 step 已到 3+，回退到 2
  if (step >= 3 && funnelStore.pressureIndex <= 0) {
    targetStep = 2
  }
  // 如果无债务数据但 step 已到 4+，回退到 3
  else if (step >= 4 && debtStore.totalCount === 0) {
    targetStep = 3
  }

  // 低分用户 Step 5+ 走独立路径
  if (funnelStore.isLowScore && targetStep >= 5) {
    const url = lowScorePageMap[targetStep] || lowScorePageMap[5]
    uni.navigateTo({ url })
    return
  }

  const url = stepPageMap[targetStep] || stepPageMap[1]
  uni.navigateTo({ url })
}

function viewProgress() {
  uni.navigateTo({ url: '/pages/page9-companion/index' })
}

function restartAssessment() {
  funnelStore.reset()
  uni.navigateTo({ url: '/pages/page1-safe-entry/index' })
}
</script>

<template>
  <view class="page">
    <!-- 新用户 / 未登录 -->
    <view v-if="state === 'new'" class="state-new">
      <view class="hero-section">
        <view class="hero-placeholder">
          <view class="app-icon">
            <text class="app-icon-text">优</text>
          </view>
          <text class="hero-brand">优化家</text>
        </view>
      </view>

      <view class="title-section">
        <text class="main-title">看看你是否正在多付利息</text>
        <text class="sub-title">1分钟检查，不需要提供个人信息</text>

        <view class="features">
          <view class="feature-item">
            <view class="feature-dot" />
            <text class="feature-text">只需两个数字，保护隐私</text>
          </view>
          <view class="feature-item">
            <view class="feature-dot" />
            <text class="feature-text">即时生成月供压力指数</text>
          </view>
          <view class="feature-item">
            <view class="feature-dot" />
            <text class="feature-text">发现潜在优化空间</text>
          </view>
        </view>
      </view>

      <view class="cta-section">
        <YouhuaButton text="开始检查" type="primary" @click="startCheck" />
        <text class="disclaimer">数据加密传输与存储，仅用于债务优化分析</text>
      </view>
    </view>

    <!-- 进行中 -->
    <view v-else-if="state === 'inProgress'" class="state-progress">
      <view class="progress-header">
        <text class="greeting">继续你的评估</text>
        <text class="progress-hint">已完成 {{ progressPercent }}%</text>
      </view>

      <view class="progress-card">
        <view class="progress-bar-wrap">
          <view class="progress-bar" :style="{ width: progressPercent + '%' }" />
        </view>

        <view class="progress-info">
          <view class="info-item">
            <text class="info-label">当前步骤</text>
            <text class="info-value">{{ currentStepLabel }}</text>
          </view>
          <view v-if="funnelStore.score > 0" class="info-item">
            <text class="info-label">优化评分</text>
            <text class="info-value score">{{ funnelStore.score }}分</text>
          </view>
          <view v-if="funnelStore.pressureIndex > 0" class="info-item">
            <text class="info-label">压力指数</text>
            <text class="info-value">{{ (funnelStore.pressureIndex * 100).toFixed(0) }}% · {{ pressureLevelText }}</text>
          </view>
        </view>
      </view>

      <view class="cta-section">
        <YouhuaButton text="继续评估" type="primary" @click="continueAssessment" />
        <text class="save-hint">数据已自动保存，随时可以继续</text>
      </view>
    </view>

    <!-- 已完成 -->
    <view v-else class="state-completed">
      <view class="completed-header">
        <view class="completed-icon-wrap">
          <text class="completed-icon">&#10003;</text>
        </view>
        <text class="completed-title">{{ funnelStore.isLowScore ? '改善计划已生成' : '评估已完成' }}</text>
        <text class="completed-subtitle" v-if="funnelStore.score > 0 && !funnelStore.isLowScore">
          你的优化评分：{{ funnelStore.score }}分
        </text>
        <text class="completed-subtitle" v-else-if="funnelStore.isLowScore">
          信用改善进行中，坚持 30 天后重新评估
        </text>
      </view>

      <view class="cta-section">
        <YouhuaButton text="查看进度" type="primary" @click="viewProgress" />
        <view class="restart-wrap">
          <YouhuaButton text="重新评估" type="secondary" @click="restartAssessment" />
        </view>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background-color: $background;
  display: flex;
  flex-direction: column;
  padding-bottom: calc(env(safe-area-inset-bottom) + 120rpx);
}

/* ---- 新用户态 ---- */
.state-new {
  display: flex;
  flex-direction: column;
  flex: 1;
}

.hero-section {
  height: 40vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #EBF4FB 0%, $background 100%);
  padding: $spacing-2xl $spacing-xl 0;
}

.hero-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: $spacing-md;
}

.app-icon {
  width: 160rpx;
  height: 160rpx;
  border-radius: $radius-xl;
  background: $primary-gradient;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-primary;
}

.app-icon-text {
  font-size: 80rpx;
  font-weight: $weight-black;
  color: $text-inverse;
}

.hero-brand {
  font-size: 56rpx;
  font-weight: 700;
  color: $primary;
}

.title-section {
  flex: 1;
  padding: $spacing-xl $spacing-xl $spacing-lg;
}

.main-title {
  display: block;
  font-size: 48rpx;
  font-weight: 700;
  color: $text-primary;
  line-height: 1.3;
  margin-bottom: $spacing-md;
}

.sub-title {
  display: block;
  font-size: 28rpx;
  color: $text-secondary;
  margin-bottom: $spacing-xl;
}

.features {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.feature-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background-color: $positive;
  flex-shrink: 0;
}

.feature-text {
  font-size: 26rpx;
  color: $text-secondary;
}

/* ---- 进行中态 ---- */
.state-progress {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: $spacing-2xl $spacing-xl 0;
}

.progress-header {
  margin-bottom: $spacing-xl;
}

.greeting {
  display: block;
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.progress-hint {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
}

.progress-card {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  box-shadow: $shadow-sm;
}

.progress-bar-wrap {
  height: 12rpx;
  background: $divider-light;
  border-radius: $radius-pill;
  overflow: hidden;
  margin-bottom: $spacing-xl;
}

.progress-bar {
  height: 100%;
  background: $primary-gradient;
  border-radius: $radius-pill;
  transition: width $transition-normal;
}

.progress-info {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.info-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.info-label {
  font-size: $font-sm;
  color: $text-tertiary;
}

.info-value {
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $text-primary;

  &.score {
    color: $primary;
    font-weight: $weight-bold;
  }
}

/* ---- 已完成态 ---- */
.state-completed {
  display: flex;
  flex-direction: column;
  flex: 1;
  padding: $spacing-2xl $spacing-xl 0;
}

.completed-header {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-3xl 0;
}

.completed-icon-wrap {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: $positive-light;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $spacing-xl;
}

.completed-icon {
  font-size: 60rpx;
  color: $positive;
}

.completed-title {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $text-primary;
  margin-bottom: $spacing-sm;
}

.completed-subtitle {
  font-size: $font-md;
  color: $text-secondary;
}

/* ---- 公共 ---- */
.cta-section {
  padding: $spacing-lg $spacing-xl $spacing-xl;
  margin-top: auto;
}

.save-hint {
  display: block;
  text-align: center;
  font-size: $font-xs;
  color: $positive;
  margin-top: $spacing-md;
}

.restart-wrap {
  margin-top: $spacing-md;
}

.disclaimer {
  display: block;
  text-align: center;
  font-size: $font-xs;
  color: $text-tertiary;
  margin-top: $spacing-md;
}
</style>
