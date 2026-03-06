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

<style scoped>
.layer-progress {
  padding: 0 0 16rpx;
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
  background-color: #e5e7eb;
}

.segment-done {
  background-color: #2baf7e;
}

.segment-active {
  background-color: #2e75b6;
}

.segment-pending {
  background-color: #e5e7eb;
}

.connector {
  width: 6rpx;
  height: 6rpx;
  background-color: #e5e7eb;
}

.progress-label {
  font-size: 22rpx;
  color: #6b7280;
}
</style>
