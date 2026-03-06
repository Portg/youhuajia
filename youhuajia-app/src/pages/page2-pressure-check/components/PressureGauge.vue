<script setup>
import { computed, watch, onMounted, getCurrentInstance } from 'vue'

const props = defineProps({
  index: { type: Number, default: 0 },
  level: { type: String, default: 'HEALTHY' }
})

const levelLabels = {
  HEALTHY: '健康',
  MODERATE: '偏高',
  HEAVY: '较重',
  SEVERE: '需关注'
}

const levelColors = {
  HEALTHY: '#0FA968',
  MODERATE: '#D97B1A',
  HEAVY: '#B86A15',
  SEVERE: '#B86A15'
}

const levelLabel = computed(() => levelLabels[props.level] || '健康')
const levelColor = computed(() => levelColors[props.level] || '#0FA968')
const fillDeg = computed(() => Math.min(Math.max(props.index, 0), 100) / 100 * 180)

const canvasId = `pg-${Math.random().toString(36).slice(2, 8)}`
const instance = getCurrentInstance()

// 画布实际像素尺寸（mount 时查询一次）
let W = 250, H = 140

onMounted(() => {
  uni.createSelectorQuery().in(instance)
    .select(`#${canvasId}`)
    .boundingClientRect(rect => {
      if (rect && rect.width > 0) {
        W = rect.width
        H = rect.height
      }
      draw()
    })
    .exec()
})

watch(() => [props.index, props.level], draw)

function draw() {
  const ctx = uni.createCanvasContext(canvasId, instance)
  const cx = W / 2
  const cy = H * 0.82
  const midR = W * 0.38
  const thickness = W * 0.14

  // 灰色轨道（完整半圆）
  ctx.beginPath()
  ctx.arc(cx, cy, midR, Math.PI, 0, false)
  ctx.setStrokeStyle('#E8ECF1')
  ctx.setLineWidth(thickness)
  ctx.setLineCap('round')
  ctx.stroke()

  // 彩色填充
  const fd = fillDeg.value
  if (fd > 0.5) {
    const endAngle = Math.PI - (fd * Math.PI / 180)
    const grd = ctx.createLinearGradient(cx - midR, cy, cx + midR, cy)
    grd.addColorStop(0, '#0FA968')
    grd.addColorStop(0.4, '#D97B1A')
    grd.addColorStop(1, '#B86A15')

    ctx.beginPath()
    ctx.arc(cx, cy, midR, Math.PI, endAngle, false)
    ctx.setStrokeStyle(grd)
    ctx.setLineWidth(thickness)
    ctx.setLineCap('round')
    ctx.stroke()
  }

  // 指针圆点
  const pAngle = Math.PI - (fd * Math.PI / 180)
  const px = cx + midR * Math.cos(pAngle)
  const py = cy + midR * Math.sin(pAngle)

  ctx.beginPath()
  ctx.arc(px, py, W * 0.028, 0, Math.PI * 2)
  ctx.setFillStyle('#FFFFFF')
  ctx.fill()

  ctx.beginPath()
  ctx.arc(px, py, W * 0.02, 0, Math.PI * 2)
  ctx.setFillStyle(levelColor.value)
  ctx.fill()

  ctx.draw()
}
</script>

<template>
  <view class="gauge-wrap">
    <view class="gauge-area">
      <view class="canvas-box">
        <canvas
          :canvas-id="canvasId"
          :id="canvasId"
          class="gauge-canvas"
        />
        <!-- 数值 + 等级（叠加在 canvas 上） -->
        <view class="gauge-labels">
          <text class="index-number" :style="{ color: levelColor }">{{ index }}</text>
          <text class="level-label" :style="{ color: levelColor }">{{ levelLabel }}</text>
        </view>
      </view>

      <!-- 刻度 -->
      <view class="scale-labels">
        <text class="scale-text">0</text>
        <text class="scale-center">压力指数</text>
        <text class="scale-text">100</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../../styles/variables.scss' as *;

.gauge-wrap {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-md 0 0;
}

.gauge-area {
  width: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.canvas-box {
  position: relative;
  width: 500rpx;
  height: 280rpx;
}

.gauge-canvas {
  width: 500rpx;
  height: 280rpx;
}

/* 数值文字叠加在 canvas 底部中心 */
.gauge-labels {
  position: absolute;
  bottom: 8rpx;
  left: 0;
  right: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
  pointer-events: none;
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

/* 刻度标签 */
.scale-labels {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 500rpx;
  margin-top: $spacing-xs;
  margin-bottom: $spacing-sm;
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
