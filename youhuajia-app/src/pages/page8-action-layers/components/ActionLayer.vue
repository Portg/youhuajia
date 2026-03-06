<template>
  <view
    class="action-layer"
    :class="{
      'layer-active': status === 'in_progress' || (active && status === 'pending'),
      'layer-completed': status === 'completed',
      'layer-locked': locked,
      'layer-disabled': status === 'disabled',
    }"
  >
    <view class="layer-header">
      <!-- 层号徽章 -->
      <view class="badge" :class="badgeClass">
        <text v-if="status === 'completed'" class="badge-check">✓</text>
        <text v-else class="badge-num">{{ layer }}</text>
      </view>

      <view class="header-content">
        <text class="layer-title" :class="{ 'title-muted': locked || status === 'disabled' }">
          {{ title }}
        </text>
        <text v-if="locked" class="unlock-hint">完成第 {{ layer - 1 }} 步后解锁</text>
      </view>

      <!-- 右上角"暂不继续"出口（Layer 1/2/3 的 pending/in_progress 状态） -->
      <text
        v-if="showSkip"
        class="skip-btn"
        @tap.stop="$emit('skip')"
      >暂不继续</text>

      <!-- V2.0 Badge -->
      <view v-if="badge" class="v2-badge">
        <text class="v2-text">{{ badge }}</text>
      </view>
    </view>

    <!-- 内容插槽（由父组件传入） -->
    <view v-if="active && status !== 'disabled' && !locked" class="layer-content">
      <slot />
    </view>

    <!-- 操作按钮 -->
    <view
      v-if="active && !locked && status !== 'completed' && status !== 'disabled'"
      class="layer-actions"
    >
      <button
        class="action-btn"
        :disabled="loading"
        @tap="$emit('action')"
      >
        <text class="action-btn-text">{{ loading ? '处理中...' : actionText }}</text>
      </button>
    </view>

    <!-- 已完成时回看 -->
    <view v-if="status === 'completed'" class="completed-slot">
      <slot name="result" />
    </view>

    <!-- disabled 说明 -->
    <text v-if="status === 'disabled'" class="disabled-note">正式申请功能即将上线，敬请期待</text>
  </view>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  layer: { type: Number, required: true },
  title: { type: String, required: true },
  status: {
    type: String,
    default: 'pending',
    validator: (v) => ['pending', 'in_progress', 'completed', 'disabled'].includes(v),
  },
  active: { type: Boolean, default: false },
  locked: { type: Boolean, default: false },
  badge: { type: String, default: '' },
  loading: { type: Boolean, default: false },
  actionText: { type: String, default: '开始' },
})

defineEmits(['action', 'skip'])

const badgeClass = computed(() => ({
  'badge-done': props.status === 'completed',
  'badge-locked': props.locked,
  'badge-disabled': props.status === 'disabled',
}))

const showSkip = computed(() => {
  return (
    props.active &&
    !props.locked &&
    props.status !== 'completed' &&
    props.status !== 'disabled'
  )
})
</script>

<style scoped>
.action-layer {
  background-color: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  border: 2rpx solid transparent;
}

.layer-active {
  border-color: #2e75b6;
}

.layer-completed {
  border-color: #2baf7e;
  background-color: #f0fdf7;
}

.layer-locked {
  opacity: 0.6;
}

.layer-disabled {
  opacity: 0.5;
  border-style: dashed;
  border-color: #d1d5db;
}

.layer-header {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}

.badge {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background-color: #2e75b6;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.badge-done {
  background-color: #2baf7e;
}

.badge-locked,
.badge-disabled {
  background-color: #d1d5db;
}

.badge-num,
.badge-check {
  font-size: 22rpx;
  color: #ffffff;
  font-weight: 700;
}

.header-content {
  flex: 1;
}

.layer-title {
  display: block;
  font-size: 30rpx;
  font-weight: 600;
  color: #1a1a2e;
  margin-bottom: 4rpx;
}

.title-muted {
  color: #9ca3af;
}

.unlock-hint {
  display: block;
  font-size: 22rpx;
  color: #9ca3af;
}

.skip-btn {
  font-size: 24rpx;
  color: #9ca3af;
  flex-shrink: 0;
  padding: 4rpx 8rpx;
}

.v2-badge {
  background-color: #f3f4f6;
  border-radius: 8rpx;
  padding: 4rpx 12rpx;
  flex-shrink: 0;
}

.v2-text {
  font-size: 20rpx;
  color: #9ca3af;
}

.layer-content {
  margin-top: 20rpx;
  background-color: #f8fafe;
  border-radius: 12rpx;
  padding: 20rpx;
}

.layer-actions {
  margin-top: 20rpx;
}

.action-btn {
  width: 100%;
  height: 80rpx;
  background-color: #2e75b6;
  color: #ffffff;
  font-size: 28rpx;
  font-weight: 600;
  border-radius: 16rpx;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
}

.action-btn::after {
  border: none;
}

.action-btn[disabled] {
  background-color: #d5e8f0;
  color: #9ca3af;
}

.action-btn-text {
  font-size: 28rpx;
}

.completed-slot {
  margin-top: 16rpx;
}

.disabled-note {
  display: block;
  font-size: 24rpx;
  color: #9ca3af;
  text-align: center;
  margin-top: 16rpx;
}
</style>
