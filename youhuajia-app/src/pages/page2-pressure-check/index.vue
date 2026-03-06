<script setup>
import { ref, computed } from 'vue'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import PressureGauge from './components/PressureGauge.vue'
import { assessPressure } from '../../api/engine.js'
import { useFunnelStore } from '../../stores/funnel.js'
import { useAuthStore } from '../../stores/auth.js'
import { formatMoneyInteger } from '../../utils/formatters.js'

const funnelStore = useFunnelStore()
const authStore = useAuthStore()

// 月供滑块状态
const monthlyPayment = ref(5000)
// 收入区间选项
const incomeOptions = [
  { label: '5千以下', value: 3000 },
  { label: '5千-1万', value: 7500 },
  { label: '1万-2万', value: 15000 },
  { label: '2万-5万', value: 35000 },
  { label: '5万以上', value: 75000 }
]
const selectedIncomeIndex = ref(1) // 默认"5千-1万"

const selectedIncome = computed(() => incomeOptions[selectedIncomeIndex.value].value)

// 压力指数状态
const pressureIndex = ref(0)
const pressureLevel = ref('HEALTHY')
const assessLoading = ref(false)

// debounce timer
let debounceTimer = null

const paymentLabel = computed(() => formatMoneyInteger(monthlyPayment.value))

function onPaymentChange(e) {
  monthlyPayment.value = e.detail.value
  scheduleAssess()
}

function selectIncome(index) {
  selectedIncomeIndex.value = index
  scheduleAssess()
}

function scheduleAssess() {
  // 立即本地计算，无需等后端
  localEstimate()
  // 后端精化（可选，失败不影响）
  if (debounceTimer) clearTimeout(debounceTimer)
  debounceTimer = setTimeout(() => {
    remoteAssess()
  }, 500)
}

function localEstimate() {
  if (monthlyPayment.value === 0) {
    pressureIndex.value = 0
    pressureLevel.value = 'HEALTHY'
  } else {
    const ratio = monthlyPayment.value / selectedIncome.value
    const estimated = Math.min(Math.round(ratio * 100), 100)
    let level = 'HEALTHY'
    if (estimated >= 85) level = 'SEVERE'
    else if (estimated >= 70) level = 'HEAVY'
    else if (estimated >= 40) level = 'MODERATE'
    pressureIndex.value = estimated
    pressureLevel.value = level
  }
  // 同步到 funnel store
  funnelStore.monthlyPayment = monthlyPayment.value
  funnelStore.monthlyIncome = selectedIncome.value
  funnelStore.updatePressure(pressureIndex.value, pressureLevel.value)
}

async function remoteAssess() {
  if (monthlyPayment.value === 0) return
  try {
    const res = await assessPressure(monthlyPayment.value, selectedIncome.value)
    pressureIndex.value = Math.round(res.pressureIndex ?? 0)
    pressureLevel.value = res.level ?? 'HEALTHY'
    funnelStore.updatePressure(pressureIndex.value, pressureLevel.value)
  } catch {
    // 后端不可用，保持本地估算值
  }
}

function goToDebts() {
  if (authStore.isLoggedIn) {
    uni.navigateTo({ url: '/pages/page3-debt-input/index' })
  } else {
    uni.navigateTo({ url: '/pages/auth/login?redirect=/pages/page3-debt-input/index' })
  }
}
</script>

<template>
  <view class="page">
    <ProgressBar :current="2" :total="9" />

    <scroll-view class="scroll-content" scroll-y>
      <!-- 页面标题 -->
      <view class="page-header">
        <text class="page-title">月供压力检测</text>
        <text class="page-desc">选择大概数字即可，不需要精确</text>
      </view>

      <!-- 月供滑块 -->
      <view class="section-card">
        <view class="section-header">
          <text class="section-label">每月大约还款</text>
          <text class="section-value">{{ paymentLabel }}</text>
        </view>
        <slider
          :min="0"
          :max="50000"
          :step="500"
          :value="monthlyPayment"
          :activeColor="'#2E75B6'"
          :backgroundColor="'#E5E7EB'"
          :block-size="28"
          @changing="onPaymentChange"
          class="payment-slider"
        />
        <view class="slider-ticks">
          <text class="tick-label">¥0</text>
          <text class="tick-label">¥25,000</text>
          <text class="tick-label">¥50,000</text>
        </view>
      </view>

      <!-- 收入区间选择 -->
      <view class="section-card">
        <text class="section-label">月收入大概</text>
        <view class="chip-group">
          <view
            v-for="(item, index) in incomeOptions"
            :key="index"
            class="chip"
            :class="{ 'chip-active': selectedIncomeIndex === index }"
            @tap="selectIncome(index)"
          >
            <text class="chip-text">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- 压力仪表盘 -->
      <view class="gauge-card">
        <view class="gauge-header">
          <text class="gauge-title">月供压力指数</text>
          <view v-if="assessLoading" class="loading-indicator">
            <text class="loading-text">计算中...</text>
          </view>
        </view>
        <PressureGauge :index="pressureIndex" :level="pressureLevel" />
        <view class="gauge-hint">
          <text class="hint-text">基于月供占收入比例计算</text>
        </view>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部 CTA -->
    <view class="cta-section">
      <YouhuaButton
        text="查看详细分析"
        type="primary"
        @click="goToDebts"
      />
      <view style="height: env(safe-area-inset-bottom); min-height: 16rpx;" />
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
}

.scroll-content {
  flex: 1;
  padding: 0 $spacing-lg;
}

.page-header {
  padding: $spacing-lg 0 $spacing-md;
}

.page-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.page-desc {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
}

.section-card {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-lg;
  margin-bottom: $spacing-md;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-md;
}

.section-label {
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
}

.section-value {
  font-size: $font-xl;
  font-weight: 700;
  color: $primary;
}

.payment-slider {
  width: 100%;
  margin: $spacing-sm 0;
}

.slider-ticks {
  display: flex;
  justify-content: space-between;
}

.tick-label {
  font-size: $font-xs;
  color: $text-tertiary;
}

.chip-group {
  display: flex;
  flex-wrap: wrap;
  gap: $spacing-sm;
  margin-top: $spacing-md;
}

.chip {
  padding: 12rpx 24rpx;
  border-radius: $radius-xl;
  border: 2rpx solid $divider;
  background: $background;
  transition: all 0.2s;

  &:active {
    opacity: 0.7;
  }
}

.chip-active {
  border-color: $primary;
  background: $primary-light;

  .chip-text {
    color: $primary;
    font-weight: 600;
  }
}

.chip-text {
  font-size: $font-sm;
  color: $text-secondary;
}

.gauge-card {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-lg $spacing-lg $spacing-md;
  margin-bottom: $spacing-md;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
}

.gauge-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: $spacing-sm;
}

.gauge-title {
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
}

.loading-indicator {
  display: flex;
  align-items: center;
}

.loading-text {
  font-size: $font-xs;
  color: $text-tertiary;
}

.gauge-hint {
  text-align: center;
  margin-top: $spacing-sm;
}

.hint-text {
  font-size: $font-xs;
  color: $text-tertiary;
}

.bottom-spacer {
  height: 120rpx;
}

.cta-section {
  padding: $spacing-md $spacing-lg;
  background: $surface;
  border-top: 2rpx solid $divider;
}
</style>
