<template>
  <view class="confidence-card">
    <!-- 圆环进度：纯 CSS，无 canvas -->
    <view v-if="type === 'ring'" class="card-visual">
      <view class="ring-wrapper">
        <view class="ring-track">
          <view class="ring-fill" :style="ringStyle"></view>
        </view>
        <text class="ring-label">{{ numericValue }}%</text>
      </view>
    </view>

    <!-- 柱状对比 -->
    <view v-else-if="type === 'bar'" class="card-visual">
      <view class="bar-group">
        <view class="bar-item">
          <view class="bar-fill bar-current" style="height: 44px" />
          <text class="bar-hint">现在</text>
        </view>
        <view class="bar-item">
          <view class="bar-fill bar-optimized" :style="{ height: optimizedHeight }" />
          <text class="bar-hint">优化后</text>
        </view>
      </view>
    </view>

    <!-- 分步路径图标 -->
    <view v-else-if="type === 'path'" class="card-visual">
      <view class="path-dots">
        <view
          v-for="i in steps"
          :key="i"
          class="path-dot"
          :class="{ 'dot-active': i === 1 }"
        />
      </view>
    </view>

    <text class="card-title">{{ title }}</text>
    <text class="card-value">{{ value }}</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  icon: { type: String, default: '' },
  title: { type: String, required: true },
  value: { type: String, required: true },
  type: {
    type: String,
    default: 'ring',
    validator: (v) => ['ring', 'bar', 'path'].includes(v),
  },
  steps: { type: Number, default: 3 },
  savingRatio: { type: Number, default: 0.3 },
})

const numericValue = computed(() => parseInt(props.value) || 0)

const optimizedHeight = computed(() => {
  const h = Math.round(44 * (1 - props.savingRatio))
  return Math.max(12, h) + 'px'
})

// conic-gradient 圆环
const ringStyle = computed(() => {
  const pct = Math.min(Math.max(numericValue.value, 0), 100)
  const deg = (pct / 100) * 360
  return {
    background: `conic-gradient(#0FA968 0deg, #0FA968 ${deg}deg, #E8ECF1 ${deg}deg, #E8ECF1 360deg)`,
  }
})
</script>

<style lang="scss" scoped>
@use '../../../styles/variables.scss' as *;

.confidence-card {
  flex: 1;
  background: $surface;
  border-radius: $radius-lg;
  padding: 24rpx 12rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: $shadow-sm;
  gap: 8rpx;
}

.card-visual {
  height: 72px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4rpx;
}

/* 纯 CSS 圆环 */
.ring-wrapper {
  position: relative;
  width: 64px;
  height: 64px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.ring-track {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  overflow: hidden;
}

.ring-fill {
  width: 100%;
  height: 100%;
  border-radius: 50%;
  /* 挖空中心 */
  -webkit-mask: radial-gradient(circle, transparent 58%, #000 62%);
  mask: radial-gradient(circle, transparent 58%, #000 62%);
}

.ring-label {
  position: absolute;
  font-size: 18rpx;
  font-weight: $weight-bold;
  color: $positive;
}

.bar-group {
  display: flex;
  align-items: flex-end;
  gap: 8rpx;
  height: 56px;
}

.bar-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.bar-fill {
  width: 20rpx;
  border-radius: 4rpx 4rpx 0 0;
}

.bar-current {
  background: $primary-light;
}

.bar-optimized {
  background: $positive;
}

.bar-hint {
  font-size: 18rpx;
  color: $text-tertiary;
}

.path-dots {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.path-dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $primary-light;
  border: 2rpx solid $primary;
}

.dot-active {
  background: $primary;
}

.card-title {
  font-size: $font-xs;
  color: $text-secondary;
  text-align: center;
  line-height: 1.3;
}

.card-value {
  font-size: $font-sm;
  font-weight: $weight-bold;
  color: $accent;
  text-align: center;
}
</style>
