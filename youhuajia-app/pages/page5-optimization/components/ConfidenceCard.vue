<template>
  <view class="confidence-card">
    <!-- 圆环进度 -->
    <view v-if="type === 'ring'" class="card-visual">
      <view class="ring-wrapper">
        <canvas
          type="2d"
          :canvas-id="ringId"
          :id="ringId"
          class="ring-canvas"
          style="width: 64px; height: 64px"
          @ready="drawRing"
        />
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
import { computed, onMounted } from 'vue'

const props = defineProps({
  icon: { type: String, default: '' },
  title: { type: String, required: true },
  value: { type: String, required: true },
  type: {
    type: String,
    default: 'ring',
    validator: (v) => ['ring', 'bar', 'path'].includes(v),
  },
  // 总步数（type=path 时使用）
  steps: { type: Number, default: 3 },
  // 月供节省比例（type=bar 时使用），0-1
  savingRatio: { type: Number, default: 0.3 },
})

const ringId = `ring-${Math.random().toString(36).slice(2)}`

// 从 value 中提取数字（如 "78%" → 78）
const numericValue = computed(() => {
  return parseInt(props.value) || 0
})

// 优化后柱高度
const optimizedHeight = computed(() => {
  const h = Math.round(44 * (1 - props.savingRatio))
  return Math.max(12, h) + 'px'
})

function drawRing() {
  const pct = numericValue.value
  const ctx = uni.createCanvasContext(ringId)
  const cx = 32
  const cy = 32
  const r = 24
  const start = -Math.PI / 2
  const end = start + (Math.PI * 2 * pct) / 100

  ctx.beginPath()
  ctx.arc(cx, cy, r, 0, Math.PI * 2)
  ctx.setStrokeStyle('#E8ECF1')
  ctx.setLineWidth(5)
  ctx.stroke()

  ctx.beginPath()
  ctx.arc(cx, cy, r, start, end)
  ctx.setStrokeStyle('#0FA968')
  ctx.setLineWidth(5)
  ctx.stroke()

  ctx.draw()
}

onMounted(() => {
  if (props.type === 'ring') {
    setTimeout(drawRing, 300)
  }
})
</script>

<style lang="scss" scoped>
@use '../../../src/styles/variables.scss' as *;

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

/* 圆环 */
.ring-wrapper {
  position: relative;
  width: 64px;
  height: 64px;
}

.ring-canvas {
  display: block;
}

.ring-label {
  position: absolute;
  top: 50%;
  left: 50%;
  transform: translate(-50%, -50%);
  font-size: 18rpx;
  font-weight: $weight-bold;
  color: $positive;
}

/* 柱状对比 */
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

/* 路径图标 */
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
