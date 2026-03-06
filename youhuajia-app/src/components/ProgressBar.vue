<script setup>
import { computed } from 'vue'

const props = defineProps({
  current: { type: Number, required: true }, // 1-9
  total: { type: Number, default: 9 }
})

const percent = computed(() => Math.round((props.current / props.total) * 100))
</script>

<template>
  <view class="progress-wrap">
    <view class="progress-track">
      <view class="progress-fill" :style="{ width: percent + '%' }" />
      <!-- 节点 -->
      <view
        v-for="i in total"
        :key="i"
        class="progress-dot"
        :class="{ 'dot-done': i <= current, 'dot-current': i === current }"
        :style="{ left: (i / total) * 100 + '%' }"
      />
    </view>
    <text class="progress-label">{{ current }}<text class="label-sep">/</text>{{ total }}</text>
  </view>
</template>

<style lang="scss" scoped>
@use '../styles/variables.scss' as *;

.progress-wrap {
  display: flex;
  align-items: center;
  padding: $spacing-md $spacing-xl $spacing-sm;
  gap: $spacing-sm;
}

.progress-track {
  flex: 1;
  height: 6rpx;
  background: $divider;
  border-radius: 3rpx;
  overflow: visible;
  position: relative;
}

.progress-fill {
  height: 100%;
  background: $primary-gradient;
  border-radius: 3rpx;
  transition: width $transition-smooth;
  position: relative;
  z-index: 1;
}

.progress-dot {
  position: absolute;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background: $divider;
  z-index: 2;
  transition: all $transition-normal;
}

.dot-done {
  background: $primary;
  width: 10rpx;
  height: 10rpx;
}

.dot-current {
  background: $primary;
  width: 14rpx;
  height: 14rpx;
  box-shadow: 0 0 0 4rpx $primary-glass;
}

.progress-label {
  font-size: $font-xs;
  font-weight: $weight-semibold;
  color: $primary;
  white-space: nowrap;
  min-width: 56rpx;
  text-align: right;
}

.label-sep {
  color: $text-tertiary;
  font-weight: $weight-normal;
}
</style>
