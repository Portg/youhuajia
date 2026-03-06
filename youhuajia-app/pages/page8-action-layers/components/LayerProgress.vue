<template>
  <view class="layer-progress">
    <view class="segments">
      <template v-for="i in total" :key="i">
        <view class="segment" :class="segmentClass(i)" />
        <view v-if="i < total" class="connector" />
      </template>
    </view>
    <text class="progress-label">{{ current }}/{{ total }} 步骤已完成</text>
  </view>
</template>

<script setup>
const props = defineProps({
  current: { type: Number, default: 0 },
  total: { type: Number, default: 3 },
})

function segmentClass(i) {
  if (i <= props.current) return 'segment-done'
  if (i === props.current + 1) return 'segment-active'
  return 'segment-pending'
}
</script>

<style lang="scss" scoped>
@use '../../../src/styles/variables.scss' as *;

.layer-progress {
  padding: 0 0 $spacing-sm;
}

.segments {
  display: flex;
  align-items: center;
  margin-bottom: 8rpx;
}

.segment {
  flex: 1;
  height: 6rpx;
  border-radius: 3rpx;
  background: $divider-light;
  transition: background $transition-normal;
}

.segment-done {
  background: $positive-gradient;
}

.segment-active {
  background: $primary;
}

.segment-pending {
  background: $divider-light;
}

.connector {
  width: 6rpx;
  height: 6rpx;
  background: $divider-light;
}

.progress-label {
  font-size: $font-xs;
  color: $text-secondary;
  font-weight: $weight-medium;
}
</style>
