<script setup>
import { ref, computed } from 'vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import { useAuthStore } from '../../stores/auth.js'
import { sendSms } from '../../api/auth.js'

const authStore = useAuthStore()

const phone = ref('')
const smsCode = ref('')
const sending = ref(false)
const logging = ref(false)
const countdown = ref(0)
let timer = null

const phoneMasked = computed(() => {
  if (phone.value.length >= 7) {
    return phone.value.slice(0, 3) + '****' + phone.value.slice(7)
  }
  return phone.value
})

const phoneValid = computed(() => /^1[3-9]\d{9}$/.test(phone.value))
const codeValid = computed(() => /^\d{4,6}$/.test(smsCode.value))
const canSend = computed(() => phoneValid.value && countdown.value === 0 && !sending.value)
const canLogin = computed(() => phoneValid.value && codeValid.value && !logging.value)

const sendBtnText = computed(() => {
  if (sending.value) return '发送中...'
  if (countdown.value > 0) return `${countdown.value}s 后重发`
  return '获取验证码'
})

function startCountdown() {
  countdown.value = 60
  timer = setInterval(() => {
    countdown.value--
    if (countdown.value <= 0) {
      clearInterval(timer)
      timer = null
    }
  }, 1000)
}

async function handleSendSms() {
  if (!canSend.value) return
  sending.value = true
  try {
    await sendSms(phone.value)
    startCountdown()
    uni.showToast({ title: '验证码已发送', icon: 'none' })
  } catch (e) {
    uni.showToast({ title: e.message || '发送失败，请稍后重试', icon: 'none' })
  } finally {
    sending.value = false
  }
}

async function handleLogin() {
  if (!canLogin.value) return
  logging.value = true
  try {
    await authStore.login(phone.value, smsCode.value)
    uni.showToast({ title: '登录成功', icon: 'success' })
    // 检查是否有重定向页面
    const pages = getCurrentPages()
    const currentPage = pages[pages.length - 1]
    const redirect = currentPage?.options?.redirect
    setTimeout(() => {
      if (redirect) {
        uni.redirectTo({ url: decodeURIComponent(redirect) })
      } else {
        uni.navigateBack({ fail: () => uni.redirectTo({ url: '/pages/page1-safe-entry/index' }) })
      }
    }, 500)
  } catch (e) {
    uni.showToast({ title: e.message || '登录失败，请检查验证码', icon: 'none' })
  } finally {
    logging.value = false
  }
}
</script>

<template>
  <view class="page">
    <!-- 顶部品牌区域 -->
    <view class="header-section">
      <text class="brand-title">优化家</text>
      <text class="brand-subtitle">登录后开始你的债务优化之旅</text>
    </view>

    <!-- 表单区域 -->
    <view class="form-section">
      <!-- 手机号输入 -->
      <view class="input-group">
        <text class="input-label">手机号</text>
        <view class="input-row">
          <text class="prefix">+86</text>
          <input
            class="input-field"
            v-model="phone"
            type="number"
            maxlength="11"
            placeholder="请输入手机号"
            :placeholder-style="'color: ' + '#9CA3AF'"
          />
        </view>
      </view>

      <!-- 验证码输入 -->
      <view class="input-group">
        <text class="input-label">验证码</text>
        <view class="input-row">
          <input
            class="input-field code-input"
            v-model="smsCode"
            type="number"
            maxlength="6"
            placeholder="请输入验证码"
            :placeholder-style="'color: ' + '#9CA3AF'"
          />
          <view
            class="sms-btn"
            :class="{ 'sms-btn-disabled': !canSend }"
            @tap="handleSendSms"
          >
            <text class="sms-btn-text">{{ sendBtnText }}</text>
          </view>
        </view>
      </view>

      <!-- 登录按钮 -->
      <view class="login-btn-wrapper">
        <YouhuaButton
          text="登录"
          type="primary"
          :disabled="!canLogin"
          :loading="logging"
          @click="handleLogin"
        />
      </view>
    </view>

    <!-- 底部协议 -->
    <view class="footer-section">
      <text class="agreement-text">
        登录即表示同意
        <text class="link">用户协议</text>
        和
        <text class="link">隐私政策</text>
      </text>
    </view>

    <SafeAreaBottom />
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background-color: $background;
  display: flex;
  flex-direction: column;
}

.header-section {
  padding: 120rpx $spacing-xl $spacing-2xl;
}

.brand-title {
  display: block;
  font-size: 56rpx;
  font-weight: 700;
  color: $primary;
  margin-bottom: $spacing-sm;
}

.brand-subtitle {
  display: block;
  font-size: $font-md;
  color: $text-secondary;
}

.form-section {
  flex: 1;
  padding: 0 $spacing-xl;
}

.input-group {
  margin-bottom: $spacing-xl;
}

.input-label {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  margin-bottom: $spacing-sm;
  font-weight: 500;
}

.input-row {
  display: flex;
  align-items: center;
  background: $surface;
  border-radius: $radius-lg;
  padding: 0 $spacing-lg;
  height: 100rpx;
  border: 2rpx solid $divider;
  transition: border-color 0.2s;

  &:focus-within {
    border-color: $primary;
  }
}

.prefix {
  font-size: $font-lg;
  color: $text-primary;
  font-weight: 500;
  margin-right: $spacing-md;
  padding-right: $spacing-md;
  border-right: 2rpx solid $divider;
}

.input-field {
  flex: 1;
  font-size: $font-lg;
  color: $text-primary;
  height: 100%;
}

.code-input {
  flex: 1;
}

.sms-btn {
  flex-shrink: 0;
  padding: $spacing-sm $spacing-lg;
  background: $primary-light;
  border-radius: $radius-md;
  margin-left: $spacing-md;

  &:active {
    opacity: 0.85;
  }
}

.sms-btn-disabled {
  opacity: 0.45;
}

.sms-btn-text {
  font-size: $font-sm;
  color: $primary;
  font-weight: 500;
  white-space: nowrap;
}

.login-btn-wrapper {
  margin-top: $spacing-xl;
}

.footer-section {
  padding: $spacing-xl;
  text-align: center;
}

.agreement-text {
  font-size: $font-xs;
  color: $text-tertiary;
  line-height: 1.6;
}

.link {
  color: $primary;
}
</style>
