<template>
  <view class="timeline">
    <view v-for="(milestone, i) in milestones" :key="i" class="timeline-item">
      <!-- 时间标签 -->
      <view class="time-label">
        <text class="day-text">{{ milestone }}天</text>
        <text v-if="labels[i]" class="phase-label">{{ labels[i] }}</text>
      </view>

      <!-- 节点 + 连线 -->
      <view class="node-col">
        <view class="node" :class="nodeClass(i)" >
          <text v-if="isPast(i)" class="node-check">✓</text>
        </view>
        <view v-if="i < milestones.length - 1" class="connector" :class="{ 'connector-done': isPast(i) }" />
      </view>

      <!-- 右侧说明 -->
      <view class="node-content">
        <text class="node-title" :class="{ 'title-active': isCurrent(i) }">
          {{ nodeTitle(i) }}
        </text>
        <text class="node-desc">{{ nodeDesc(i) }}</text>
        <view v-if="isCurrent(i)" class="current-tag">
          <text class="current-tag-text">当前阶段</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  milestones: {
    type: Array,
    default: () => [30, 60, 90],
  },
  labels: {
    type: Array,
    default: () => [],
  },
  currentDay: {
    type: Number,
    default: 1,
  },
})

const defaultTitles = ['整理财务结构', '改善信用状况', '重新评估并优化']
const defaultDescs = [
  '整理账单，设置还款提醒，优先偿还最高 APR',
  '持续按时还款，降低信用卡使用率',
  '重新评估信用状况，制定下一步优化方案',
]

function isPast(i) {
  return props.currentDay > props.milestones[i]
}

function isCurrent(i) {
  const prev = i === 0 ? 0 : props.milestones[i - 1]
  return props.currentDay > prev && props.currentDay <= props.milestones[i]
}

function nodeClass(i) {
  if (isPast(i)) return 'node-past'
  if (isCurrent(i)) return 'node-current'
  return 'node-future'
}

function nodeTitle(i) {
  return props.labels[i] || defaultTitles[i] || ''
}

function nodeDesc(i) {
  return defaultDescs[i] || ''
}
</script>

<style lang="scss" scoped>
@use '../../../src/styles/variables.scss' as *;

.timeline {
  display: flex;
  flex-direction: column;
  gap: 0;
}

.timeline-item {
  display: flex;
  gap: $spacing-sm;
  align-items: flex-start;
}

.time-label {
  width: 72rpx;
  flex-shrink: 0;
  padding-top: 2rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.day-text {
  font-size: $font-xs;
  color: $text-secondary;
  font-weight: $weight-medium;
}

.phase-label {
  font-size: 18rpx;
  color: $text-tertiary;
}

.node-col {
  width: 28rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.node {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  transition: background $transition-normal, box-shadow $transition-normal;
}

.node-past {
  background: $positive;
}

.node-current {
  background: $primary;
  box-shadow: 0 0 0 4rpx $primary-glass;
}

.node-future {
  background: $divider-light;
  border: 2rpx solid $divider;
}

.node-check {
  font-size: 16rpx;
  color: $text-inverse;
  font-weight: $weight-bold;
}

.connector {
  width: 2rpx;
  flex: 1;
  background: $divider-light;
  margin: 4rpx 0 8rpx;
  min-height: 48rpx;
  transition: background $transition-normal;
}

.connector-done {
  background: $positive;
}

.node-content {
  flex: 1;
  padding-bottom: $spacing-lg;
}

.node-title {
  display: block;
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $text-tertiary;
  margin-bottom: 4rpx;
}

.title-active {
  color: $text-primary;
}

.node-desc {
  display: block;
  font-size: $font-xs;
  color: $text-tertiary;
  line-height: 1.5;
  margin-bottom: 8rpx;
}

.current-tag {
  display: inline-block;
  background: $primary-glass;
  border-radius: $radius-pill;
  padding: 4rpx 16rpx;
}

.current-tag-text {
  font-size: 20rpx;
  color: $primary;
  font-weight: $weight-medium;
}
</style>
