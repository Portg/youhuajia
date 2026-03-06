<script setup>
import YouhuaButton from '../../components/YouhuaButton.vue'

function goToPressure() {
  uni.navigateTo({ url: '/pages/page2-pressure-check/index' })
}
</script>

<template>
  <view class="page">
    <!-- 顶部插画区域 -->
    <view class="hero-section">
      <image
        class="hero-image"
        src="/static/images/entry-hero.png"
        mode="aspectFit"
      />
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

    <!-- 底部 CTA -->
    <view class="cta-section">
      <YouhuaButton text="开始检查" type="primary" @click="goToPressure" />
      <text class="disclaimer">数据仅在本次会话中使用，不会上传至服务器</text>
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
  height: 55vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #EBF4FB 0%, $background 100%);
  padding: $spacing-2xl $spacing-xl 0;
}

.hero-image {
  width: 100%;
  height: 100%;
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
