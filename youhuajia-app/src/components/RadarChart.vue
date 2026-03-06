<template>
  <view class="radar-chart-container">
    <canvas
      :id="canvasId"
      :canvas-id="canvasId"
      class="radar-canvas"
      :style="{ width: size + 'px', height: size + 'px' }"
    />
    <!-- 维度标签 -->
    <view class="radar-labels">
      <view
        v-for="(dim, index) in dimensions"
        :key="index"
        class="radar-label"
        :style="getLabelStyle(index)"
      >
        <text class="label-name">{{ dim.name }}</text>
        <text class="label-value">{{ dim.value }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, onMounted, watch, getCurrentInstance } from 'vue'

const props = defineProps({
  dimensions: {
    type: Array,
    default: () => [
      { name: '利率健康度', value: 0 },
      { name: '结构合理度', value: 0 },
      { name: '还款能力', value: 0 },
      { name: '征信状况', value: 0 },
      { name: '优化潜力', value: 0 },
    ],
  },
  size: {
    type: Number,
    default: 280,
  },
  strokeColor: {
    type: String,
    default: '#1B6DB2',
  },
  fillColor: {
    type: String,
    default: 'rgba(27, 109, 178, 0.15)',
  },
})

const canvasId = `radar-${Date.now()}-${Math.random().toString(36).slice(2)}`
const instance = getCurrentInstance()

onMounted(() => {
  setTimeout(drawRadar, 300)
})

watch(
  () => props.dimensions,
  () => drawRadar(),
  { deep: true },
)

function drawRadar() {
  const ctx = uni.createCanvasContext(canvasId, instance)
  const n = props.dimensions.length
  if (n < 3) return
  const cx = props.size / 2
  const cy = props.size / 2
  const radius = props.size * 0.35

  // 背景网格（5层）
  for (let layer = 1; layer <= 5; layer++) {
    const r = (radius * layer) / 5
    ctx.beginPath()
    for (let i = 0; i < n; i++) {
      const angle = (Math.PI * 2 * i) / n - Math.PI / 2
      const x = cx + r * Math.cos(angle)
      const y = cy + r * Math.sin(angle)
      i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
    }
    ctx.closePath()
    ctx.setStrokeStyle('#E8ECF1')
    ctx.setLineWidth(1)
    ctx.stroke()
  }

  // 轴线
  for (let i = 0; i < n; i++) {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2
    ctx.beginPath()
    ctx.moveTo(cx, cy)
    ctx.lineTo(cx + radius * Math.cos(angle), cy + radius * Math.sin(angle))
    ctx.setStrokeStyle('#E8ECF1')
    ctx.stroke()
  }

  // 数据多边形
  ctx.beginPath()
  for (let i = 0; i < n; i++) {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2
    const val = (props.dimensions[i].value || 0) / 100
    const r = radius * Math.max(0.05, val)
    const x = cx + r * Math.cos(angle)
    const y = cy + r * Math.sin(angle)
    i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
  }
  ctx.closePath()
  ctx.setFillStyle(props.fillColor)
  ctx.fill()
  ctx.setStrokeStyle(props.strokeColor)
  ctx.setLineWidth(2)
  ctx.stroke()

  // 数据点
  for (let i = 0; i < n; i++) {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2
    const val = (props.dimensions[i].value || 0) / 100
    const r = radius * Math.max(0.05, val)
    ctx.beginPath()
    ctx.arc(cx + r * Math.cos(angle), cy + r * Math.sin(angle), 4, 0, Math.PI * 2)
    ctx.setFillStyle(props.strokeColor)
    ctx.fill()
  }

  ctx.draw()
}

function getLabelStyle(index) {
  const n = props.dimensions.length
  const angle = (Math.PI * 2 * index) / n - Math.PI / 2
  const labelRadius = props.size * 0.48
  const cx = props.size / 2
  const cy = props.size / 2
  const x = cx + labelRadius * Math.cos(angle)
  const y = cy + labelRadius * Math.sin(angle)

  return {
    position: 'absolute',
    left: x - 36 + 'px',
    top: y - 20 + 'px',
    width: '72px',
    textAlign: 'center',
  }
}
</script>

<style lang="scss" scoped>
@use '../styles/variables.scss' as *;

.radar-chart-container {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
}

.radar-canvas {
  display: block;
}

.radar-labels {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  pointer-events: none;
}

.radar-label {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.label-name {
  font-size: 11px;
  color: $text-secondary;
  line-height: 1.2;
}

.label-value {
  font-size: 12px;
  font-weight: $weight-semibold;
  color: $primary;
  line-height: 1.2;
}
</style>
