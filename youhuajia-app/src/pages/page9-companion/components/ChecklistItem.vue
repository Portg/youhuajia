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

<style scoped>
.checklist-item {
  display: flex;
  gap: 20rpx;
  align-items: flex-start;
  padding: 4rpx 0;
}

.checkbox {
  width: 40rpx;
  height: 40rpx;
  border-radius: 10rpx;
  border: 3rpx solid #d1d5db;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  margin-top: 2rpx;
  transition: background-color 0.2s, border-color 0.2s;
}

.checkbox.checked {
  background-color: #2baf7e;
  border-color: #2baf7e;
}

.check-mark {
  font-size: 22rpx;
  color: #ffffff;
  font-weight: 700;
}

.item-content {
  flex: 1;
}

.item-text {
  display: block;
  font-size: 28rpx;
  color: #1a1a2e;
  line-height: 1.4;
  margin-bottom: 4rpx;
}

.text-done {
  color: #9ca3af;
  text-decoration: line-through;
}

.item-tip {
  display: block;
  font-size: 22rpx;
  color: #9ca3af;
  line-height: 1.4;
}
</style>
