<template>
  <view class="page">
    <!-- 加载态 -->
    <view class="loading-wrap" v-if="profileStore.loading">
      <view class="loader">
        <view class="loader-ring" />
        <view class="loader-ring loader-ring-2" />
      </view>
      <text class="loading-text">正在分析你的财务结构...</text>
    </view>

    <!-- 错误态 -->
    <view class="error-wrap" v-else-if="profileStore.error">
      <text class="error-icon">⚠</text>
      <text class="error-text">{{ profileStore.error }}</text>
      <view class="retry-btn" @tap="profileStore.loadProfile()">
        <text class="retry-text">重试</text>
      </view>
    </view>

    <!-- 主内容 -->
    <scroll-view class="content" scroll-y v-else>
      <!-- 核心冲击区：三年多付利息 -->
      <view class="impact-block">
        <view class="impact-glow" />
        <text class="impact-context">如果维持当前结构</text>
        <text class="impact-label">3 年将多支付</text>
        <view class="impact-number">
          <AnimatedNumber
            :value="profileStore.threeYearExtraInterest"
            :duration="2000"
            prefix="¥"
            :formatter="numFmt"
            color="#D97B1A"
            :fontSize="96"
            fontWeight="900"
          />
        </view>
        <view class="analogy-badge" v-if="analogyMonths > 0">
          <text class="analogy-text">
            相当于 <text class="analogy-num">{{ analogyMonths }}</text> 个月房租
          </text>
        </view>
      </view>

      <!-- 三个对比卡片 -->
      <view class="cards-wrap">
        <!-- 卡片 A：利率对比 -->
        <view class="card">
          <text class="card-title">利率对比</text>
          <view class="card-row">
            <view class="metric-col">
              <text class="metric-label">你的加权利率</text>
              <text class="metric-val metric-accent">{{ fmt1(profileStore.weightedApr) }}%</text>
            </view>
            <view class="vsep" />
            <view class="metric-col">
              <text class="metric-label">市场参考值</text>
              <text class="metric-val metric-primary">{{ MARKET_RATE }}%</text>
            </view>
          </view>
          <view class="bar-track">
            <view class="bar-fill" :style="{ width: aprBarWidth + '%' }" />
          </view>
          <view class="bar-labels">
            <text class="bar-label-l">0%</text>
            <text class="bar-label-m" :style="{ left: MARKET_RATE / 36 * 100 + '%' }">市场均值</text>
            <text class="bar-label-r">36%</text>
          </view>
        </view>

        <!-- 卡片 B：月供压力 -->
        <view class="card">
          <text class="card-title">月供压力</text>
          <view class="card-row">
            <view class="metric-col">
              <text class="metric-label">月供占收入</text>
              <text class="metric-val metric-accent">{{ fmt1(paymentRatioPct) }}%</text>
            </view>
            <view class="vsep" />
            <view class="metric-col">
              <text class="metric-label">健康线</text>
              <text class="metric-val metric-primary">{{ HEALTHY_RATIO }}%</text>
            </view>
          </view>
          <view class="bar-track">
            <view
              class="bar-fill"
              :class="paymentRatioPct > HEALTHY_RATIO ? 'bar-fill-heavy' : 'bar-fill-ok'"
              :style="{ width: Math.min(paymentRatioPct, 100) + '%' }"
            />
            <view class="bar-marker" :style="{ left: HEALTHY_RATIO + '%' }" />
          </view>
          <text class="bar-hint" v-if="paymentRatioPct > HEALTHY_RATIO">
            月供占比偏高，有优化空间
          </text>
        </view>

        <!-- 卡片 C：债务结构 -->
        <view class="card">
          <text class="card-title">债务结构</text>
          <view class="count-row">
            <view class="count-col">
              <text class="count-num">{{ profile?.debtCount || 0 }}</text>
              <text class="count-label">总债务笔数</text>
            </view>
            <view class="vsep" />
            <view class="count-col">
              <text class="count-num count-accent">{{ highInterestCount }}</text>
              <text class="count-label">高息笔数</text>
            </view>
          </view>
          <view class="card-note-wrap" v-if="highInterestCount > 0">
            <text class="card-note">APR > 24% 的债务是优化重点</text>
          </view>
        </view>
      </view>

      <!-- 信息提示（蓝色，非警告风格） -->
      <view class="info-banner">
        <view class="info-dot" />
        <text class="info-text">
          以上分析基于你录入的 {{ profile?.debtCount || 0 }} 笔债务，仅供参考，不构成借贷建议。
        </text>
      </view>

      <!-- CTA 区域：⚠️ 严格遵守 F-12，绝无"申请"按钮 -->
      <view class="cta-wrap">
        <view class="cta-btn" @tap="handleGoOptimization">
          <text class="cta-text">看看我的优化空间</text>
          <text class="cta-arrow">→</text>
        </view>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useProfileStore } from '../../src/stores/profile.js'
import AnimatedNumber from '../../src/components/AnimatedNumber.vue'

const profileStore = useProfileStore()
const profile = computed(() => profileStore.profile)

// 常量（对应 application.yml 配置的市场均值）
const MARKET_RATE = 18   // 市场均值 APR %
const HEALTHY_RATIO = 30 // 月供收入比健康线 %
const MONTHLY_RENT = 3000 // 参考月租

function numFmt(v) { return Math.round(v).toLocaleString('zh-CN') }
function fmt1(v) { return Number(v).toFixed(1) }

// 月供收入比（转为百分比）
const paymentRatioPct = computed(() => {
  const r = profile.value?.debtIncomeRatio || 0
  return r > 1 ? r : r * 100
})

// 相当于几个月房租
const analogyMonths = computed(() => {
  const extra = profileStore.threeYearExtraInterest
  if (!extra || extra <= 0) return 0
  return Math.round(extra / MONTHLY_RENT)
})

// APR 进度条宽度（相对 36% 满值）
const aprBarWidth = computed(() => Math.min((profileStore.weightedApr / 36) * 100, 100))

// 高息债务笔数（APR > 24%）
const highInterestCount = computed(() => profile.value?.highInterestDebtCount || 0)

function handleGoOptimization() {
  const score = profileStore.score
  uni.navigateTo({ url: `/pages/page5-optimization/index?score=${score}` })
}

onMounted(() => {
  if (!profile.value) profileStore.loadProfile()
})
</script>

<style lang="scss" scoped>
@use '../../src/styles/variables.scss' as *;
@use '../../src/styles/mixins.scss' as *;

.page {
  min-height: 100vh;
  @include page-bg;
}

/* 加载 / 错误 */
.loading-wrap, .error-wrap {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: $spacing-lg;
  padding: $spacing-2xl;
}

.loader {
  position: relative;
  width: 80rpx;
  height: 80rpx;
}

.loader-ring {
  position: absolute;
  inset: 0;
  border: 4rpx solid $divider;
  border-top-color: $primary;
  border-radius: 50%;
  animation: spin-ring .9s cubic-bezier(0.45, 0.05, 0.55, 0.95) infinite;
}

.loader-ring-2 {
  inset: 8rpx;
  border-top-color: $accent;
  animation-duration: 1.3s;
  animation-direction: reverse;
}

@keyframes spin-ring { to { transform: rotate(360deg); } }

.loading-text {
  font-size: $font-md;
  color: $text-secondary;
}

.error-icon {
  font-size: 64rpx;
}

.error-text {
  font-size: $font-md;
  color: $text-secondary;
  text-align: center;
}

.retry-btn {
  padding: 16rpx 48rpx;
  background: $primary-gradient;
  border-radius: $radius-pill;
  box-shadow: $shadow-primary;
  @include press-effect;
}

.retry-text {
  color: $text-inverse;
  font-size: $font-md;
  font-weight: $weight-semibold;
}

.content { height: 100vh; }

/* 冲击区 */
.impact-block {
  position: relative;
  background: $surface;
  padding: 60rpx $spacing-xl 48rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  overflow: hidden;
}

.impact-glow {
  position: absolute;
  top: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 200%;
  height: 200rpx;
  background: radial-gradient(ellipse at center, rgba(217, 123, 26, 0.06) 0%, transparent 70%);
  pointer-events: none;
}

.impact-context {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  margin-bottom: 8rpx;
  position: relative;
  z-index: 1;
}

.impact-label {
  display: block;
  font-size: $font-lg;
  font-weight: $weight-bold;
  color: $text-primary;
  margin-bottom: 20rpx;
  position: relative;
  z-index: 1;
}

.impact-number {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20rpx;
  position: relative;
  z-index: 1;
}

.analogy-badge {
  background: $accent-light;
  border-radius: $radius-pill;
  padding: 8rpx 24rpx;
}

.analogy-text {
  font-size: $font-sm;
  color: $text-secondary;
}

.analogy-num {
  font-size: $font-lg;
  font-weight: $weight-black;
  color: $accent;
}

/* 卡片区 */
.cards-wrap {
  padding: $spacing-md $spacing-xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.card {
  @include card;
}

.card-title {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  font-weight: $weight-semibold;
  margin-bottom: $spacing-md;
  text-transform: uppercase;
  letter-spacing: 2rpx;
}

.card-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.metric-col {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.metric-label {
  font-size: $font-xs;
  color: $text-tertiary;
  margin-bottom: 8rpx;
}

.metric-val {
  font-size: $font-xl;
  font-weight: $weight-black;
  letter-spacing: -1rpx;
}

.metric-accent { color: $accent; }
.metric-primary { color: $primary; }

.vsep {
  width: 2rpx;
  height: 56rpx;
  background: $divider-light;
  border-radius: 1rpx;
}

/* 进度条 */
.bar-track {
  height: 10rpx;
  background: $divider-light;
  border-radius: 5rpx;
  position: relative;
  overflow: visible;
}

.bar-fill {
  height: 100%;
  border-radius: 5rpx;
  background: $accent-gradient;
  transition: width 0.8s $transition-smooth;
}

.bar-fill-ok { background: $positive-gradient; }
.bar-fill-heavy { background: $accent-gradient; }

.bar-marker {
  position: absolute;
  top: -4rpx;
  width: 4rpx;
  height: 18rpx;
  background: $primary;
  border-radius: 2rpx;
  transform: translateX(-50%);
}

.bar-labels {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  margin-top: 8rpx;
  position: relative;
}

.bar-label-l, .bar-label-r {
  font-size: $font-xs;
  color: $text-tertiary;
}

.bar-label-m {
  position: absolute;
  transform: translateX(-50%);
  font-size: $font-xs;
  color: $primary;
  font-weight: $weight-medium;
}

.bar-hint {
  display: block;
  font-size: $font-xs;
  color: $text-secondary;
  margin-top: $spacing-sm;
}

/* 笔数 */
.count-row {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: $spacing-2xl;
  margin-bottom: $spacing-sm;
}

.count-col {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.count-num {
  @include metric-number;
}

.count-accent { color: $accent; }

.count-label {
  font-size: $font-xs;
  color: $text-tertiary;
  margin-top: 4rpx;
}

.card-note-wrap {
  background: $accent-glass;
  border-radius: $radius-sm;
  padding: 12rpx $spacing-md;
}

.card-note {
  font-size: $font-xs;
  color: $text-secondary;
}

/* 信息横幅 */
.info-banner {
  margin: 0 $spacing-xl $spacing-md;
  background: $primary-glass;
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-lg;
  display: flex;
  flex-direction: row;
  align-items: flex-start;
  gap: $spacing-sm;
  border: 1rpx solid rgba(27, 109, 178, 0.1);
}

.info-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: $primary;
  flex-shrink: 0;
  margin-top: 12rpx;
}

.info-text {
  font-size: $font-xs;
  color: $primary;
  line-height: 1.65;
  flex: 1;
}

/* CTA — 绝对没有"申请"按钮（F-12） */
.cta-wrap {
  padding: 0 $spacing-xl 56rpx;
  padding-bottom: calc(56rpx + env(safe-area-inset-bottom));
}

.cta-btn {
  width: 100%;
  height: 100rpx;
  background: $primary-gradient;
  border-radius: $radius-pill;
  box-shadow: $shadow-primary;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-sm;
  @include press-effect;
}

.cta-text {
  color: $text-inverse;
  font-size: $font-lg;
  font-weight: $weight-bold;
  letter-spacing: 2rpx;
}

.cta-arrow {
  color: rgba(255, 255, 255, 0.7);
  font-size: $font-xl;
}
</style>
