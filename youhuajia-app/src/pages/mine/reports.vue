<script setup>
import { ref, onMounted } from 'vue'
import { listReports, getReport } from '../../api/report'

const reports = ref([])
const loading = ref(true)

onMounted(async () => {
  try {
    const res = await listReports()
    reports.value = res.reports || res || []
  } catch (_) {
    reports.value = []
  } finally {
    loading.value = false
  }
})

function truncate(text, max = 50) {
  if (!text) return ''
  return text.length > max ? text.slice(0, max) + '...' : text
}

function formatTime(time) {
  if (!time) return ''
  return time.replace('T', ' ').slice(0, 16)
}

function viewDetail(report) {
  // 使用简单页面展示报告详情
  uni.navigateTo({
    url: `/pages/mine/report-detail?id=${report.id}`,
    fail: () => {
      // 如果详情页不存在，直接用 modal 展示
      uni.showModal({
        title: '报告详情',
        content: report.aiSummary || report.content || '暂无详细内容',
        showCancel: false,
      })
    },
  })
}
</script>

<template>
  <view class="page">
    <view v-if="loading" class="loading-wrap">
      <text class="loading-text">加载中...</text>
    </view>

    <view v-else-if="reports.length === 0" class="empty-wrap">
      <text class="empty-icon">&#128203;</text>
      <text class="empty-text">暂无报告</text>
      <text class="empty-hint">完成评估后将自动生成</text>
    </view>

    <view v-else class="report-list">
      <view
        v-for="report in reports"
        :key="report.id"
        class="report-item"
        @tap="viewDetail(report)"
      >
        <text class="report-time">{{ formatTime(report.createTime) }}</text>
        <text class="report-summary">{{ truncate(report.aiSummary || report.content) }}</text>
        <text class="report-arrow">&#8250;</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background-color: $background;
  padding: $spacing-xl;
}

.loading-wrap,
.empty-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding-top: 200rpx;
}

.loading-text {
  font-size: $font-md;
  color: $text-secondary;
}

.empty-icon {
  font-size: 80rpx;
  margin-bottom: $spacing-lg;
}

.empty-text {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $text-primary;
  margin-bottom: $spacing-sm;
}

.empty-hint {
  font-size: $font-sm;
  color: $text-tertiary;
}

.report-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.report-item {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-lg $spacing-xl;
  box-shadow: $shadow-xs;
  display: flex;
  flex-direction: column;
  position: relative;

  &:active {
    background: $divider-light;
  }
}

.report-time {
  font-size: $font-xs;
  color: $text-tertiary;
  margin-bottom: $spacing-sm;
}

.report-summary {
  font-size: $font-sm;
  color: $text-primary;
  line-height: 1.5;
  padding-right: $spacing-xl;
}

.report-arrow {
  position: absolute;
  right: $spacing-xl;
  top: 50%;
  transform: translateY(-50%);
  font-size: $font-lg;
  color: $text-tertiary;
}
</style>
