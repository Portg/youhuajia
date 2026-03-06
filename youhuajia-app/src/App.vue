<script setup>
import { onLaunch, onShow, onHide } from '@dcloudio/uni-app'
import { useAuthStore } from './stores/auth.js'

onLaunch(() => {
  try {
    const authStore = useAuthStore()
    authStore.loadFromStorage()
  } catch (e) {
    console.warn('[App] loadFromStorage failed:', e)
  }
})

onShow(() => {})
onHide(() => {})
</script>

<style lang="scss">
@use './styles/variables.scss' as *;

/* 全局基础样式 */
page {
  background-color: $background;
  font-family: -apple-system, BlinkMacSystemFont, 'PingFang SC', 'Helvetica Neue', sans-serif;
  color: $text-primary;
  font-size: 28rpx;
  line-height: 1.6;
}

/* 禁用长按选中 */
* {
  -webkit-tap-highlight-color: transparent;
}

/* 通用间距 */
.page-container {
  min-height: 100vh;
  background-color: $background;
}
</style>
