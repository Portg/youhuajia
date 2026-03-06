<script setup>
import YouhuaButton from '../../components/YouhuaButton.vue'
import { useFunnelStore } from '../../stores/funnel.js'

const funnelStore = useFunnelStore()

function goToPressure() {
  funnelStore.advanceStep(2)
  uni.navigateTo({ url: '/pages/page2-pressure-check/index' })
}
</script>

<template>
  <view class="page">
    <!-- 顶部品牌区域 -->
    <view class="hero-section">
      <view class="hero-placeholder">
        <view class="app-icon">
          <text class="app-icon-text">优</text>
        </view>
        <text class="hero-brand">优化家</text>
      </view>
    </view>

    <!-- 标题区域 -->
    <view class="title-section">
      <text class="main-title">看看你是否正在多付利息</text>
      <text class="sub-title">1分钟检查，不需要提供个人信息</text>

      <!-- 三个特性说明 -->
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

    <!-- 信任标识 -->
    <view class="trust-bar">
      <view class="trust-item">
        <text class="trust-icon">🔒</text>
        <text class="trust-label">加密传输</text>
      </view>
      <view class="trust-item">
        <text class="trust-icon">🚫</text>
        <text class="trust-label">不接触资金</text>
      </view>
      <view class="trust-item">
        <text class="trust-icon">📋</text>
        <text class="trust-label">仅供参考</text>
      </view>
    </view>

    <!-- 底部 CTA -->
    <view class="cta-section">
      <YouhuaButton text="开始检查" type="primary" @click="goToPressure" />
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
  padding-bottom: env(safe-area-inset-bottom);
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

.trust-bar {
  display: flex;
  justify-content: center;
  gap: $spacing-2xl;
  padding: 0 $spacing-xl $spacing-md;
}

.trust-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.trust-icon {
  font-size: 36rpx;
}

.trust-label {
  font-size: $font-xs;
  color: $text-tertiary;
}

.cta-section {
  padding: $spacing-lg $spacing-xl $spacing-xl;
}

.disclaimer {
  display: block;
  text-align: center;
  font-size: $font-xs;
  color: $text-tertiary;
  margin-top: $spacing-md;
}
</style>
