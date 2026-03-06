<template>
  <view class="score-radar">
    <canvas
      type="2d"
      :canvas-id="canvasId"
      :id="canvasId"
      class="radar-canvas"
      :style="{ width: size + 'px', height: size + 'px' }"
      @ready="onReady"
    />
    <!-- 维度标签 -->
    <view class="radar-labels">
      <view
        v-for="(dim, i) in dimensions"
        :key="i"
        class="label"
        :style="labelStyle(i)"
      >
        <text class="label-name">{{ dim.name }}</text>
        <text class="label-score">{{ dim.score }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { watch, onMounted, ref } from 'vue'

const props = defineProps({
  dimensions: {
    type: Array,
    default: () => [
      { name: '利率健康度', score: 0 },
      { name: '结构合理度', score: 0 },
      { name: '还款能力', score: 0 },
      { name: '征信状况', score: 0 },
      { name: '优化潜力', score: 0 },
    ],
  },
  size: { type: Number, default: 260 },
})

const canvasId = `score-radar-${Math.random().toString(36).slice(2)}`
const ready = ref(false)

function onReady() {
  ready.value = true
  draw()
}

onMounted(() => {
  setTimeout(() => {
    if (!ready.value) draw()
  }, 400)
})

watch(() => props.dimensions, draw, { deep: true })

function draw() {
  const ctx = uni.createCanvasContext(canvasId)
  const n = props.dimensions.length
  const cx = props.size / 2
  const cy = props.size / 2
  const radius = props.size * 0.33

  // 背景网格
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
    const val = Math.max(0.05, (props.dimensions[i].score || 0) / 100)
    const r = radius * val
    const x = cx + r * Math.cos(angle)
    const y = cy + r * Math.sin(angle)
    i === 0 ? ctx.moveTo(x, y) : ctx.lineTo(x, y)
  }
  ctx.closePath()
  ctx.setFillStyle('rgba(27, 109, 178, 0.18)')
  ctx.fill()
  ctx.setStrokeStyle('#1B6DB2')
  ctx.setLineWidth(2)
  ctx.stroke()

  // 数据点
  for (let i = 0; i < n; i++) {
    const angle = (Math.PI * 2 * i) / n - Math.PI / 2
    const val = Math.max(0.05, (props.dimensions[i].score || 0) / 100)
    const r = radius * val
    ctx.beginPath()
    ctx.arc(cx + r * Math.cos(angle), cy + r * Math.sin(angle), 3, 0, Math.PI * 2)
    ctx.setFillStyle('#1B6DB2')
    ctx.fill()
  }

  ctx.draw()
}

function labelStyle(index) {
  const n = props.dimensions.length
  const angle = (Math.PI * 2 * index) / n - Math.PI / 2
  const r = props.size * 0.47
  const cx = props.size / 2
  const cy = props.size / 2
  const x = cx + r * Math.cos(angle)
  const y = cy + r * Math.sin(angle)
  return {
    position: 'absolute',
    left: x - 34 + 'px',
    top: y - 18 + 'px',
    width: '68px',
    textAlign: 'center',
  }
}
</script>

<style lang="scss" scoped>
@use '../../../src/styles/variables.scss' as *;

.score-radar {
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

.label {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.label-name {
  font-size: 20rpx;
  color: $text-secondary;
  line-height: 1.2;
}

.label-score {
  font-size: 22rpx;
  font-weight: $weight-semibold;
  color: $primary;
  line-height: 1.2;
}
</style>
