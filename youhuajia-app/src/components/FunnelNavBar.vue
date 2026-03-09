<script setup>
/**
 * 漏斗页自定义导航栏
 * 提供返回箭头 + 首页图标，替代原生导航栏
 */
const props = defineProps({
  title: { type: String, default: '' },
  showHome: { type: Boolean, default: true },
  showBack: { type: Boolean, default: true },
})

const statusBarHeight = uni.getSystemInfoSync().statusBarHeight || 0

function goBack() {
  uni.navigateBack({
    delta: 1,
    fail: () => uni.switchTab({ url: '/pages/home/index' }),
  })
}

function goHome() {
  uni.switchTab({ url: '/pages/home/index' })
}
</script>

<template>
  <view class="funnel-nav-bar">
    <view class="status-bar" :style="{ height: statusBarHeight + 'px' }" />
    <view class="nav-content">
      <view class="nav-left">
        <view v-if="showBack" class="nav-btn" @click="goBack">
          <view class="back-chevron" />
        </view>
        <view v-if="showHome" class="nav-btn nav-btn-home" @click="goHome">
          <view class="home-icon">
            <view class="home-roof" />
            <view class="home-body" />
          </view>
        </view>
      </view>
      <text class="nav-title">{{ title }}</text>
      <view class="nav-right" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../styles/variables.scss' as *;

.funnel-nav-bar {
  background-color: $surface;
}

.status-bar {
  width: 100%;
}

.nav-content {
  height: 88rpx;
  display: flex;
  flex-direction: row;
  align-items: center;
  padding: 0 $spacing-xs;
}

.nav-left {
  display: flex;
  flex-direction: row;
  align-items: center;
  min-width: 120rpx;
}

.nav-btn {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.back-chevron {
  width: 18rpx;
  height: 18rpx;
  border-left: 4rpx solid $text-primary;
  border-bottom: 4rpx solid $text-primary;
  transform: rotate(45deg);
}

/* 首页图标比返回箭头更低调（不鼓励退出，但保留出口） */
.home-icon {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.home-roof {
  width: 0;
  height: 0;
  border-left: 12rpx solid transparent;
  border-right: 12rpx solid transparent;
  border-bottom: 10rpx solid $text-tertiary;
}

.home-body {
  width: 16rpx;
  height: 10rpx;
  background-color: $text-tertiary;
  border-radius: 0 0 2rpx 2rpx;
}

.nav-title {
  flex: 1;
  text-align: center;
  font-size: $font-md;
  font-weight: $weight-semibold;
  color: $text-primary;
}

.nav-right {
  min-width: 120rpx;
}
</style>
