<script setup>
import YouhuaButton from '../../src/components/YouhuaButton.vue'

function goToPressure() {
  uni.navigateTo({ url: '/pages/page2-pressure-check/index' })
}
</script>

<template>
  <view class="page">
    <!-- 顶部渐变插画区域 -->
    <view class="hero-section">
      <view class="hero-glow" />
      <image
        class="hero-image"
        src="/static/images/entry-hero.png"
        mode="aspectFit"
      />
    </view>

    <!-- 标题区域 -->
    <view class="title-section">
      <text class="main-title">看看你是否</text>
      <text class="main-title title-accent">正在多付利息</text>
      <text class="sub-title">1 分钟检查，不需要提供个人信息</text>

      <!-- 三个特性说明 -->
      <view class="features">
        <view class="feature-item" v-for="(f, i) in features" :key="i">
          <view class="feature-icon">
            <text class="icon-text">{{ f.icon }}</text>
          </view>
          <view class="feature-content">
            <text class="feature-text">{{ f.text }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部 CTA -->
    <view class="cta-section">
      <YouhuaButton text="开始检查" type="primary" @click="goToPressure" />
      <text class="disclaimer">数据仅在本次会话中使用，不会上传至服务器</text>
    </view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      features: [
        { icon: '🔒', text: '只需两个数字，保护隐私' },
        { icon: '⚡', text: '即时生成月供压力指数' },
        { icon: '💡', text: '发现潜在优化空间' },
      ]
    }
  }
}
</script>

<style lang="scss" scoped>
@use '../../src/styles/variables.scss' as *;
@use '../../src/styles/mixins.scss' as *;

.page {
  min-height: 100vh;
  @include page-bg;
  display: flex;
  flex-direction: column;
  padding-bottom: env(safe-area-inset-bottom);
}

.hero-section {
  height: 50vh;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
  padding: $spacing-3xl $spacing-xl 0;
}

.hero-glow {
  position: absolute;
  top: -20%;
  left: -10%;
  width: 120%;
  height: 100%;
  background: radial-gradient(ellipse at 30% 40%, rgba(27, 109, 178, 0.08) 0%, transparent 60%),
              radial-gradient(ellipse at 70% 60%, rgba(15, 169, 104, 0.05) 0%, transparent 50%);
  pointer-events: none;
}

.hero-image {
  width: 100%;
  height: 100%;
  position: relative;
  z-index: 1;
}

.title-section {
  flex: 1;
  padding: $spacing-xl $spacing-xl $spacing-lg;
}

.main-title {
  display: block;
  font-size: 52rpx;
  font-weight: $weight-black;
  color: $text-primary;
  line-height: 1.25;
  letter-spacing: -1rpx;
}

.title-accent {
  @include gradient-text($primary-gradient);
  margin-bottom: $spacing-md;
}

.sub-title {
  display: block;
  font-size: $font-md;
  color: $text-secondary;
  margin-bottom: $spacing-2xl;
}

.features {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.feature-item {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.feature-icon {
  width: 56rpx;
  height: 56rpx;
  border-radius: $radius-md;
  background: $surface;
  box-shadow: $shadow-xs;
  @include flex-center;
  flex-shrink: 0;
}

.icon-text {
  font-size: 28rpx;
}

.feature-content {
  flex: 1;
}

.feature-text {
  font-size: $font-sm;
  color: $text-secondary;
  font-weight: $weight-medium;
}

.cta-section {
  padding: $spacing-lg $spacing-xl $spacing-2xl;
}

.disclaimer {
  display: block;
  text-align: center;
  font-size: $font-xs;
  color: $text-tertiary;
  margin-top: $spacing-md;
}
</style>
