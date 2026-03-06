<template>
  <text
    class="animated-number"
    :style="{ color: color, fontSize: fontSize + 'rpx', fontWeight: fontWeight }"
  >{{ displayValue }}</text>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'

const props = defineProps({
  value: { type: Number, default: 0 },
  duration: { type: Number, default: 2000 },
  formatter: { type: Function, default: (v) => Math.round(v).toLocaleString('zh-CN') },
  prefix: { type: String, default: '' },
  suffix: { type: String, default: '' },
  color: { type: String, default: '#1A1A2E' },
  fontSize: { type: Number, default: 36 },
  fontWeight: { type: String, default: 'bold' },
})

const displayValue = ref(props.prefix + props.formatter(0) + props.suffix)
let rafId = null

function animateTo(target) {
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
  const start = Date.now()
  const duration = props.duration

  function easeOutCubic(t) { return 1 - Math.pow(1 - t, 3) }

  function tick() {
    const elapsed = Date.now() - start
    const progress = Math.min(elapsed / duration, 1)
    const current = target * easeOutCubic(progress)
    displayValue.value = props.prefix + props.formatter(current) + props.suffix
    if (progress < 1) {
      rafId = requestAnimationFrame(tick)
    } else {
      rafId = null
    }
  }
  rafId = requestAnimationFrame(tick)
}

onMounted(() => {
  if (props.value > 0) animateTo(props.value)
})

onUnmounted(() => {
  if (rafId) { cancelAnimationFrame(rafId); rafId = null }
})

watch(() => props.value, (val) => animateTo(val))
</script>

<style scoped>
.animated-number {
  display: inline-block;
  font-variant-numeric: tabular-nums;
}
</style>
