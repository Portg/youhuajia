<script setup>
import { computed } from 'vue'
import { useAuthStore } from '../../stores/auth'
import { useFunnelStore } from '../../stores/funnel'

const authStore = useAuthStore()
const funnelStore = useFunnelStore()

const maskedPhone = computed(() => {
  const p = authStore.phone
  return p?.length >= 7 ? p.slice(0, 3) + '****' + p.slice(-4) : p
})

const avatarLetter = computed(() => {
  const p = authStore.phone
  return p ? p.slice(-1) : '?'
})

const menuItems = computed(() => [
  {
    label: funnelStore.isLowScore ? '我的改善计划' : '我的报告',
    url: '/pages/mine/reports',
  },
  { label: '意见反馈', url: '/pages/mine/feedback' },
  { label: '关于优化家', url: '/pages/mine/about' },
])

function goTo(url) {
  uni.navigateTo({ url })
}

function goLogin() {
  uni.navigateTo({ url: '/pages/auth/login' })
}

function handleLogout() {
  uni.showModal({
    title: '提示',
    content: '确定要退出登录吗？',
    confirmText: '退出',
    confirmColor: '#1B6DB2',
    success: async (res) => {
      if (res.confirm) {
        await authStore.logout()
        uni.switchTab({ url: '/pages/home/index' })
      }
    },
  })
}
</script>

<template>
  <view class="page">
    <!-- 用户信息头部 -->
    <view class="user-header" @tap="!authStore.isLoggedIn && goLogin()">
      <view class="avatar">
        <text class="avatar-text">{{ authStore.isLoggedIn ? avatarLetter : '?' }}</text>
      </view>
      <view class="user-info">
        <text class="user-name" v-if="authStore.isLoggedIn">{{ maskedPhone }}</text>
        <text class="user-name login-hint" v-else>点击登录</text>
      </view>
    </view>

    <!-- 功能列表 -->
    <view class="menu-list">
      <view
        v-for="item in menuItems"
        :key="item.url"
        class="menu-item"
        @tap="goTo(item.url)"
      >
        <text class="menu-label">{{ item.label }}</text>
        <text class="menu-arrow">&#8250;</text>
      </view>
    </view>

    <!-- 底部 -->
    <view class="bottom-section" v-if="authStore.isLoggedIn">
      <view class="logout-btn" @tap="handleLogout">
        <text class="logout-text">退出登录</text>
      </view>
      <text class="deactivate-hint">如需注销账号，请联系客服</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: calc(env(safe-area-inset-bottom) + 120rpx);
}

.user-header {
  display: flex;
  align-items: center;
  padding: $spacing-3xl $spacing-xl $spacing-2xl;
  background: $surface;
}

.avatar {
  width: 100rpx;
  height: 100rpx;
  border-radius: 50%;
  background: $primary-gradient;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: $spacing-lg;
}

.avatar-text {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $text-inverse;
}

.user-name {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $text-primary;

  &.login-hint {
    color: $text-secondary;
  }
}

.menu-list {
  margin-top: $spacing-lg;
  background: $surface;
  border-radius: $radius-lg;
  margin-left: $spacing-xl;
  margin-right: $spacing-xl;
  overflow: hidden;
}

.menu-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg $spacing-xl;
  border-bottom: 1rpx solid $divider-light;

  &:last-child {
    border-bottom: none;
  }

  &:active {
    background: $divider-light;
  }
}

.menu-label {
  font-size: $font-md;
  color: $text-primary;
}

.menu-arrow {
  font-size: $font-lg;
  color: $text-tertiary;
}

.bottom-section {
  margin-top: $spacing-3xl;
  padding: 0 $spacing-xl;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.logout-btn {
  width: 100%;
  height: 88rpx;
  border-radius: $radius-lg;
  background: $surface;
  display: flex;
  align-items: center;
  justify-content: center;

  &:active {
    background: $divider-light;
  }
}

.logout-text {
  font-size: $font-md;
  color: $text-secondary;
}

.deactivate-hint {
  margin-top: $spacing-lg;
  font-size: $font-xs;
  color: $text-tertiary;
}
</style>
