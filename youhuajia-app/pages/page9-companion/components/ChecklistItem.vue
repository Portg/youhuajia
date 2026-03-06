<template>
  <view class="checklist-item" @tap="toggle">
    <view class="checkbox" :class="{ checked: checked }">
      <text v-if="checked" class="check-mark">✓</text>
    </view>
    <view class="item-content">
      <text class="item-text" :class="{ 'text-done': checked }">{{ text }}</text>
      <text v-if="tip" class="item-tip">{{ tip }}</text>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  text: { type: String, required: true },
  checked: { type: Boolean, default: false },
  tip: { type: String, default: '' },
})

const emit = defineEmits(['update:checked'])

function toggle() {
  emit('update:checked', !props.checked)
  // 持久化勾选状态
  try {
    const key = `checklist_${encodeURIComponent(props.text)}`
    uni.setStorageSync(key, !props.checked)
  } catch (e) {
    // ignore
  }
}
</script>

<style lang="scss" scoped>
@use '../../../src/styles/variables.scss' as *;
@use '../../../src/styles/mixins.scss' as *;

.checklist-item {
  display: flex;
  gap: $spacing-md;
  align-items: flex-start;
  padding: 4rpx 0;
  @include press-effect;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border-radius: $radius-sm;
  border: 2rpx solid $divider;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2rpx;
  transition: background $transition-fast, border-color $transition-fast;
}

.checkbox.checked {
  background: $positive;
  border-color: $positive;
}

.check-mark {
  font-size: $font-xs;
  color: $text-inverse;
  font-weight: $weight-bold;
}

.item-content {
  flex: 1;
}

.item-text {
  display: block;
  font-size: $font-sm;
  color: $text-primary;
  line-height: 1.4;
  margin-bottom: 4rpx;
  transition: color $transition-fast;
}

.text-done {
  color: $text-tertiary;
  text-decoration: line-through;
}

.item-tip {
  display: block;
  font-size: $font-xs;
  color: $text-tertiary;
  line-height: 1.4;
}
</style>
