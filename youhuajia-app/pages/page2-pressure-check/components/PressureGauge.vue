<script setup>
import { ref, watch, onMounted, computed } from 'vue'

const props = defineProps({
  index: { type: Number, default: 0 }, // 0-100
  level: { type: String, default: 'HEALTHY' } // HEALTHY | MODERATE | HEAVY | SEVERE
})

const canvasId = 'pressure-gauge'

// 标签映射（绝不用"危险""严重"）
const levelLabels = {
  HEALTHY: '健康',
  MODERATE: '偏高',
  HEAVY: '较重',
  SEVERE: '需关注'
}

// 颜色映射（不使用红色）— 2026 palette
const levelColors = {
  HEALTHY: '#0FA968',   // $positive
  MODERATE: '#D97B1A',  // $accent
  HEAVY: '#B86A15',     // deep amber
  SEVERE: '#B86A15'     // deep amber（不用红色）
}

const levelLabel = computed(() => levelLabels[props.level] || '健康')
const levelColor = computed(() => levelColors[props.level] || '#0FA968')

function drawGauge(index) {
  // #ifdef H5
  const canvas = document.getElementById(canvasId)
  if (!canvas) return
  const ctx = canvas.getContext('2d')
  const w = canvas.width
  const h = canvas.height
  drawOnContext(ctx, w, h, index)
  // #endif

  // #ifdef MP-WEIXIN
  const ctx = uni.createCanvasContext(canvasId)
  const sysInfo = uni.getSystemInfoSync()
  const w = sysInfo.windowWidth * 0.7
  const h = w * 0.55
  drawOnContextMp(ctx, w, h, index)
  ctx.draw()
  // #endif
}

function drawOnContext(ctx, w, h, index) {
  ctx.clearRect(0, 0, w, h)

  const cx = w / 2
  const cy = h * 0.85
  const r = Math.min(w, h) * 0.75

  // 背景弧（灰色轨道）
  ctx.beginPath()
  ctx.arc(cx, cy, r, Math.PI, 2 * Math.PI)
  ctx.lineWidth = 20
  ctx.strokeStyle = '#E8ECF1'
  ctx.lineCap = 'round'
  ctx.stroke()

  // 进度弧
  const pct = Math.min(Math.max(index, 0), 100) / 100
  const endAngle = Math.PI + pct * Math.PI

  // 渐变色
  const grad = ctx.createLinearGradient(cx - r, cy, cx + r, cy)
  grad.addColorStop(0, '#0FA968')
  grad.addColorStop(0.4, '#D97B1A')
  grad.addColorStop(1, '#B86A15')

  ctx.beginPath()
  ctx.arc(cx, cy, r, Math.PI, endAngle)
  ctx.lineWidth = 20
  ctx.strokeStyle = grad
  ctx.lineCap = 'round'
  ctx.stroke()

  // 指针
  const angle = Math.PI + pct * Math.PI
  const px = cx + (r - 4) * Math.cos(angle)
  const py = cy + (r - 4) * Math.sin(angle)
  ctx.beginPath()
  ctx.arc(px, py, 10, 0, 2 * Math.PI)
  ctx.fillStyle = levelColors[props.level] || '#0FA968'
  ctx.fill()
}

function drawOnContextMp(ctx, w, h, index) {
  const cx = w / 2
  const cy = h * 0.85
  const r = Math.min(w, h) * 0.75

  // 背景弧
  ctx.beginPath()
  ctx.arc(cx, cy, r, Math.PI, 2 * Math.PI)
  ctx.setLineWidth(20)
  ctx.setStrokeStyle('#E8ECF1')
  ctx.setLineCap('round')
  ctx.stroke()

  // 进度弧
  const pct = Math.min(Math.max(index, 0), 100) / 100
  const endAngle = Math.PI + pct * Math.PI

  ctx.beginPath()
  ctx.arc(cx, cy, r, Math.PI, endAngle)
  ctx.setLineWidth(20)
  ctx.setStrokeStyle(levelColors[props.level] || '#0FA968')
  ctx.setLineCap('round')
  ctx.stroke()

  // 指针点
  const angle = Math.PI + pct * Math.PI
  const px = cx + (r - 4) * Math.cos(angle)
  const py = cy + (r - 4) * Math.sin(angle)
  ctx.beginPath()
  ctx.arc(px, py, 10, 0, 2 * Math.PI)
  ctx.setFillStyle(levelColors[props.level] || '#0FA968')
  ctx.fill()
}

watch(() => props.index, (val) => {
  drawGauge(val)
})

onMounted(() => {
  setTimeout(() => drawGauge(props.index), 100)
})
</script>

<template>
  <view class="gauge-wrap">
    <canvas
      :id="canvasId"
      :canvas-id="canvasId"
      class="gauge-canvas"
    />
    <view class="gauge-labels">
      <text class="index-number" :style="{ color: levelColor }">{{ index }}</text>
      <text class="level-label" :style="{ color: levelColor }">{{ levelLabel }}</text>
    </view>
    <!-- 刻度标签 -->
    <view class="scale-labels">
      <text class="scale-text">0</text>
      <text class="scale-center">压力指数</text>
      <text class="scale-text">100</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../../src/styles/variables.scss' as *;

.gauge-wrap {
  position: relative;
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-md 0;
}

.gauge-canvas {
  width: 560rpx;
  height: 300rpx;
}

.gauge-labels {
  margin-top: -60rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.index-number {
  font-size: 72rpx;
  font-weight: $weight-black;
  line-height: 1;
  font-variant-numeric: tabular-nums;
}

.level-label {
  font-size: $font-sm;
  font-weight: $weight-semibold;
}

.scale-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 520rpx;
  margin-top: $spacing-md;
}

.scale-text {
  font-size: $font-xs;
  color: $text-tertiary;
}

.scale-center {
  font-size: $font-xs;
  color: $text-tertiary;
}
</style>
