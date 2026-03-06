<script setup>
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useAuthStore } from './src/stores/auth.js'

onLaunch(() => {
  const authStore = useAuthStore()
  authStore.loadFromStorage()
})

onShow(() => {})
onHide(() => {})
</script>

<style lang="scss">
@use './src/styles/variables.scss' as *;

/* 全局基础样式 — 2026 Edition */
page {
  background: linear-gradient(168deg, #F0F4FA 0%, #FAF8F5 35%, #F5F7FA 100%);
  font-family: 'PingFang SC', 'HarmonyOS Sans SC', 'MiSans', -apple-system, BlinkMacSystemFont,
    'Segoe UI', 'Noto Sans SC', sans-serif;
  color: $text-primary;
  font-size: 30rpx;
  line-height: 1.65;
  -webkit-font-smoothing: antialiased;
  -moz-osx-font-smoothing: grayscale;
}

/* 禁用长按选中 */
* {
  -webkit-tap-highlight-color: transparent;
  box-sizing: border-box;
}

/* 通用页面容器 */
.page-container {
  min-height: 100vh;
  background: linear-gradient(168deg, #F0F4FA 0%, #FAF8F5 35%, #F5F7FA 100%);
}
</style>
