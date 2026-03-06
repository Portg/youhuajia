<script setup>
defineProps({
  text: { type: String, required: true },
  type: { type: String, default: 'primary' }, // 'primary' | 'secondary' | 'text'
  disabled: { type: Boolean, default: false },
  loading: { type: Boolean, default: false }
})

defineEmits(['click'])
</script>

<template>
  <view
    class="youhua-btn"
    :class="[`btn-${type}`, { 'btn-disabled': disabled, 'btn-loading': loading }]"
    @tap="!disabled && !loading && $emit('click')"
  >
    <view v-if="loading" class="loading-ring" />
    <text class="btn-text">{{ loading ? '处理中...' : text }}</text>
  </view>
</template>

<style lang="scss" scoped>
@use '../styles/variables.scss' as *;

.youhua-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: $radius-pill;
  height: 100rpx;
  padding: 0 $spacing-3xl;
  transition: transform $transition-fast, opacity $transition-fast, box-shadow $transition-normal;
  cursor: pointer;
  position: relative;
  overflow: hidden;

  &:active {
    transform: scale(0.97);
  }
}

.btn-primary {
  background: $primary-gradient;
  box-shadow: $shadow-primary;

  .btn-text {
    color: $text-inverse;
    font-size: $font-lg;
    font-weight: $weight-bold;
    letter-spacing: 2rpx;
  }

  &:active {
    box-shadow: 0 4rpx 16rpx rgba(27, 109, 178, 0.35);
  }
}

.btn-secondary {
  background: $primary-glass;
  border: 2rpx solid rgba(27, 109, 178, 0.2);

  .btn-text {
    color: $primary;
    font-size: $font-lg;
    font-weight: $weight-semibold;
  }

  &:active {
    background: rgba(27, 109, 178, 0.12);
  }
}

.btn-text {
  background: transparent;

  .btn-text {
    color: $primary;
    font-size: $font-md;
    font-weight: $weight-medium;
  }
}

.btn-disabled {
  opacity: 0.4;
  pointer-events: none;
}

.loading-ring {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  border: 3rpx solid rgba(255, 255, 255, 0.3);
  border-top-color: $text-inverse;
  animation: spin-ring 0.7s linear infinite;
  margin-right: 14rpx;
}

@keyframes spin-ring {
  to { transform: rotate(360deg); }
}
</style>
