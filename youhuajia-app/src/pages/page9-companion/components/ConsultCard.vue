<template>
  <view class="consult-card">
    <text class="consult-title">需要专人协助？</text>
    <text class="consult-desc">留下联系方式，我们会在 1 个工作日内联系你</text>

    <!-- 咨询类型选择 -->
    <view class="type-selector">
      <view
        v-for="opt in typeOptions"
        :key="opt.value"
        class="type-option"
        :class="{ active: consultType === opt.value }"
        @click="consultType = opt.value"
      >
        <text class="type-label">{{ opt.label }}</text>
      </view>
    </view>

    <!-- 手机号 -->
    <view class="phone-row">
      <text class="phone-label">联系电话</text>
      <text v-if="useStoredPhone && !editingPhone" class="phone-display" @tap="editingPhone = true">{{ maskedPhone }}</text>
      <input
        v-else
        v-model="phone"
        class="phone-input"
        type="number"
        maxlength="11"
        placeholder="请输入手机号"
        :disabled="submitted"
      />
      <text v-if="useStoredPhone && !editingPhone" class="phone-change" @tap="editingPhone = true">修改</text>
    </view>

    <!-- 补充说明（可选） -->
    <view class="remark-row">
      <textarea
        v-model="remark"
        class="remark-input"
        placeholder="补充说明（选填）"
        maxlength="200"
        :disabled="submitted"
      />
    </view>

    <!-- 提交按钮 -->
    <view v-if="!submitted" class="submit-row">
      <button
        class="submit-btn"
        :disabled="!canSubmit || submitting"
        :loading="submitting"
        @click="onSubmit"
      >
        {{ submitting ? '提交中...' : '预约咨询' }}
      </button>
    </view>

    <!-- 提交成功状态 -->
    <view v-else class="success-section">
      <view class="success-icon-wrap">
        <text class="success-icon">&#10003;</text>
      </view>
      <text class="success-title">咨询已提交</text>
      <text class="success-desc">我们将在 1 个工作日内通过电话联系你</text>
      <view class="success-info">
        <view class="info-row">
          <text class="info-label">咨询类型</text>
          <text class="info-value">{{ typeOptions.find(o => o.value === consultType)?.label }}</text>
        </view>
        <view class="info-row">
          <text class="info-label">联系电话</text>
          <text class="info-value">{{ maskedPhone }}</text>
        </view>
      </view>
      <text class="success-hint">如需修改，请联系客服 support@youhuajia.com</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { createConsultation } from '../../../api/consultation'
import { useAuthStore } from '../../../stores/auth'

const authStore = useAuthStore()

const typeOptions = [
  { label: '债务优化', value: 'DEBT_OPTIMIZATION' },
  { label: '利率协商', value: 'RATE_NEGOTIATION' },
  { label: '综合咨询', value: 'GENERAL' },
]

const consultType = ref('DEBT_OPTIMIZATION')
const phone = ref(authStore.phone || '')
const remark = ref('')
const submitting = ref(false)
const submitted = ref(false)
const useStoredPhone = !!authStore.phone
const editingPhone = ref(false)

const canSubmit = computed(() => {
  return /^1[3-9]\d{9}$/.test(phone.value) && consultType.value
})

const maskedPhone = computed(() => {
  const p = phone.value
  return p?.length >= 7 ? p.slice(0, 3) + '****' + p.slice(-4) : p
})

async function onSubmit() {
  if (!canSubmit.value || submitting.value) return
  submitting.value = true
  try {
    await createConsultation({
      phone: phone.value,
      consultType: consultType.value,
      remark: remark.value || undefined,
    })
    submitted.value = true
  } catch (e) {
    console.warn('[ConsultCard] submit failed:', e?.message || e)
  } finally {
    submitting.value = false
  }
}
</script>

<style lang="scss" scoped>
@use '../../../styles/variables.scss' as *;

.consult-card {
  background-color: $surface;
  border-radius: $radius-md;
  margin: $spacing-md $spacing-xl;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.consult-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.consult-desc {
  display: block;
  font-size: $font-xs;
  color: $text-secondary;
  margin-bottom: $spacing-md;
}

.type-selector {
  display: flex;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.type-option {
  flex: 1;
  padding: 16rpx 0;
  border: 2rpx solid $divider;
  border-radius: $radius-sm;
  text-align: center;
  transition: all 0.2s;
}

.type-option.active {
  border-color: $primary;
  background-color: $primary-light;
}

.type-label {
  font-size: $font-xs;
  color: $text-secondary;
}

.type-option.active .type-label {
  color: $primary;
  font-weight: 600;
}

.phone-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
  padding: 16rpx 20rpx;
  background: $background;
  border-radius: $radius-sm;
}

.phone-label {
  font-size: $font-sm;
  color: $text-secondary;
  flex-shrink: 0;
}

.phone-display {
  flex: 1;
  font-size: $font-sm;
  color: $text-primary;
}

.phone-change {
  font-size: $font-xs;
  color: $primary;
  flex-shrink: 0;
}

.phone-input {
  flex: 1;
  font-size: $font-sm;
  color: $text-primary;
  border: none;
  background: transparent;
}

.remark-row {
  margin-bottom: $spacing-md;
}

.remark-input {
  width: 100%;
  height: 120rpx;
  font-size: $font-sm;
  color: $text-primary;
  padding: 16rpx 20rpx;
  background: $background;
  border-radius: $radius-sm;
  border: none;
  box-sizing: border-box;
}

.submit-row {
  margin-top: $spacing-sm;
}

.submit-btn {
  width: 100%;
  height: 80rpx;
  background: $primary;
  color: #ffffff;
  font-size: $font-md;
  font-weight: 600;
  border: none;
  border-radius: $radius-sm;
  line-height: 80rpx;
}

.submit-btn[disabled] {
  opacity: 0.5;
}

.success-section {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-md 0;
}

.success-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: $positive-light;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $spacing-md;
}

.success-icon {
  font-size: 40rpx;
  color: $positive;
}

.success-title {
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.success-desc {
  font-size: $font-sm;
  color: $text-secondary;
  margin-bottom: $spacing-lg;
}

.success-info {
  width: 100%;
  background: $background;
  border-radius: $radius-sm;
  padding: $spacing-md;
  margin-bottom: $spacing-md;
}

.info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: $spacing-xs 0;
}

.info-label {
  font-size: $font-xs;
  color: $text-tertiary;
}

.info-value {
  font-size: $font-xs;
  color: $text-primary;
  font-weight: 500;
}

.success-hint {
  font-size: $font-xs;
  color: $text-tertiary;
  text-align: center;
}
</style>
