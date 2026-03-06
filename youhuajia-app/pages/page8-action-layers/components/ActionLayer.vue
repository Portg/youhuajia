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

<style lang="scss" scoped>
@use '../../../src/styles/variables.scss' as *;
@use '../../../src/styles/mixins.scss' as *;

.action-layer {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
  border: 2rpx solid transparent;
  transition: border-color $transition-normal, box-shadow $transition-normal;
}

.layer-active {
  border-color: rgba(27, 109, 178, 0.2);
  box-shadow: $shadow-md;
}

.layer-completed {
  border-color: rgba(15, 169, 104, 0.15);
  background: $positive-glass;
}

.layer-locked {
  opacity: 0.6;
}

.layer-disabled {
  opacity: 0.5;
  border-style: dashed;
  border-color: $divider;
}

.layer-header {
  display: flex;
  align-items: flex-start;
  gap: $spacing-md;
}

.badge {
  width: 48rpx;
  height: 48rpx;
  border-radius: $radius-md;
  background: $primary;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.badge-done {
  background: $positive;
}

.badge-locked,
.badge-disabled {
  background: $divider;
}

.badge-num,
.badge-check {
  font-size: $font-xs;
  color: $text-inverse;
  font-weight: $weight-bold;
}

.header-content {
  flex: 1;
}

.layer-title {
  display: block;
  font-size: $font-md;
  font-weight: $weight-semibold;
  color: $text-primary;
  margin-bottom: 4rpx;
}

.title-muted {
  color: $text-tertiary;
}

.unlock-hint {
  display: block;
  font-size: $font-xs;
  color: $text-tertiary;
}

.skip-btn {
  font-size: $font-xs;
  color: $text-tertiary;
  flex-shrink: 0;
  padding: 4rpx 8rpx;
}

.v2-badge {
  background: $divider-light;
  border-radius: $radius-sm;
  padding: 4rpx 12rpx;
  flex-shrink: 0;
}

.v2-text {
  font-size: 20rpx;
  color: $text-tertiary;
}

.layer-content {
  margin-top: $spacing-md;
  background: $primary-glass;
  border-radius: $radius-md;
  padding: $spacing-md;
}

.layer-actions {
  margin-top: $spacing-md;
}

.action-btn {
  width: 100%;
  height: 88rpx;
  background: $primary-gradient;
  color: $text-inverse;
  font-size: $font-sm;
  font-weight: $weight-semibold;
  border-radius: $radius-pill;
  border: none;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: $shadow-primary;
  transition: transform $transition-spring, opacity $transition-fast;

  &:active {
    transform: scale(0.97);
  }
}

.action-btn::after {
  border: none;
}

.action-btn[disabled] {
  background: $divider-light;
  color: $text-tertiary;
  box-shadow: none;
}

.action-btn-text {
  font-size: $font-sm;
}

.completed-slot {
  margin-top: $spacing-sm;
}

.disabled-note {
  display: block;
  font-size: $font-xs;
  color: $text-tertiary;
  text-align: center;
  margin-top: $spacing-sm;
}
</style>
