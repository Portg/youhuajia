<script setup>
import { ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import { createConsultation } from '../../api/consultation'
import { useAuthStore } from '../../stores/auth'
import YouhuaButton from '../../components/YouhuaButton.vue'

const authStore = useAuthStore()
const content = ref('')
const submitting = ref(false)

onShow(() => {
  if (!authStore.isLoggedIn) {
    uni.showToast({ title: '请先登录', icon: 'none' })
    setTimeout(() => {
      uni.navigateTo({ url: '/pages/auth/login?redirect=/pages/mine/feedback' })
    }, 500)
  }
})

async function submit() {
  if (!authStore.isLoggedIn) {
    uni.navigateTo({ url: '/pages/auth/login?redirect=/pages/mine/feedback' })
    return
  }
  if (!content.value.trim()) {
    uni.showToast({ title: '请输入反馈内容', icon: 'none' })
    return
  }

  submitting.value = true
  try {
    await createConsultation({
      phone: authStore.phone,
      consultType: 'FEEDBACK',
      remark: content.value.trim(),
    })
    uni.showToast({ title: '提交成功，感谢反馈', icon: 'success' })
    setTimeout(() => uni.navigateBack(), 1500)
  } catch (_) {
    uni.showToast({ title: '提交失败，请稍后再试', icon: 'none' })
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <view class="page">
    <view class="form-section">
      <textarea
        class="feedback-input"
        v-model="content"
        placeholder="请描述你的建议或问题..."
        :maxlength="500"
        auto-height
      />
      <text class="char-count">{{ content.length }}/500</text>
    </view>

    <view class="cta-section">
      <YouhuaButton
        text="提交反馈"
        type="primary"
        :loading="submitting"
        :disabled="!content.trim()"
        @click="submit"
      />
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background-color: $background;
  padding: $spacing-xl;
  display: flex;
  flex-direction: column;
}

.form-section {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-xl;
}

.feedback-input {
  width: 100%;
  min-height: 300rpx;
  font-size: $font-md;
  color: $text-primary;
  line-height: 1.6;
}

.char-count {
  display: block;
  text-align: right;
  font-size: $font-xs;
  color: $text-tertiary;
  margin-top: $spacing-sm;
}

.cta-section {
  margin-top: $spacing-xl;
}
</style>
