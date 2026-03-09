<script setup>
import { ref, computed, onMounted } from 'vue'
import { useFunnelStore } from '../../stores/funnel'
import { listReports } from '../../api/report'

const funnelStore = useFunnelStore()
const isLowScore = computed(() => funnelStore.isLowScore)

// 低分用户：从 funnel store 读取改善计划
const planResult = computed(() => funnelStore.actionLayers.layer1.result)
const hasPlan = computed(() => isLowScore.value && planResult.value?.plan?.length > 0)
const checklist = computed(() => funnelStore.checklist)
const totalChecks = computed(() => Object.keys(checklist.value).length || 1)
const completedChecks = computed(() =>
  Object.values(checklist.value).filter(Boolean).length
)

// 正常用户：从后端加载报告
const reports = ref([])
const loading = ref(true)

onMounted(async () => {
  uni.setNavigationBarTitle({
    title: isLowScore.value ? '我的改善计划' : '我的报告',
  })
  if (isLowScore.value) {
    loading.value = false
    return
  }
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
  uni.navigateTo({
    url: `/pages/mine/report-detail?id=${report.id}`,
    fail: () => {
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
    <!-- 低分用户：改善计划 -->
    <template v-if="isLowScore">
      <view v-if="hasPlan" class="plan-section">
        <view class="plan-card">
          <text class="plan-title">30 天改善计划</text>
          <view class="plan-list">
            <view v-for="(item, i) in planResult.plan" :key="i" class="plan-item">
              <view class="plan-dot" />
              <view class="plan-content">
                <text class="plan-week">第 {{ item.week }} 周</text>
                <text class="plan-task">{{ item.task }}</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 预估分数 -->
        <view v-if="planResult.forecastScore > 0" class="forecast-card">
          <view class="forecast-row">
            <view class="forecast-item">
              <text class="forecast-label">当前评分</text>
              <text class="forecast-value accent">{{ funnelStore.score }}分</text>
            </view>
            <text class="forecast-arrow">→</text>
            <view class="forecast-item">
              <text class="forecast-label">30天后预估</text>
              <text class="forecast-value positive">{{ planResult.forecastScore }}分</text>
            </view>
          </view>
        </view>

        <!-- Checklist 进度 -->
        <view class="checklist-card">
          <text class="checklist-title">清单完成进度</text>
          <view class="checklist-bar-wrap">
            <view class="checklist-bar">
              <view class="checklist-fill" :style="{ width: (completedChecks / totalChecks * 100) + '%' }" />
            </view>
            <text class="checklist-text">{{ completedChecks }}/{{ totalChecks }}</text>
          </view>
        </view>
      </view>

      <!-- 低分用户无计划时 -->
      <view v-else class="empty-wrap">
        <text class="empty-text">暂无改善计划</text>
        <text class="empty-hint">完成信用优化流程后将自动生成</text>
      </view>
    </template>

    <!-- 正常用户：报告列表 -->
    <template v-else>
      <view v-if="loading" class="loading-wrap">
        <text class="loading-text">加载中...</text>
      </view>

      <view v-else-if="reports.length === 0" class="empty-wrap">
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
    </template>
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

/* 报告列表 */
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

/* 改善计划 */
.plan-section {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.plan-card {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  box-shadow: $shadow-sm;
}

.plan-title {
  display: block;
  font-size: $font-md;
  font-weight: $weight-semibold;
  color: $text-primary;
  margin-bottom: $spacing-lg;
}

.plan-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.plan-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
}

.plan-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background-color: $primary;
  margin-top: 10rpx;
  flex-shrink: 0;
}

.plan-content {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.plan-week {
  font-size: $font-xs;
  font-weight: $weight-semibold;
  color: $primary;
}

.plan-task {
  font-size: $font-sm;
  color: $text-primary;
  line-height: 1.5;
}

/* 预估分数 */
.forecast-card {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  box-shadow: $shadow-sm;
}

.forecast-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: $spacing-xl;
}

.forecast-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.forecast-label {
  font-size: $font-xs;
  color: $text-tertiary;
}

.forecast-value {
  font-size: 48rpx;
  font-weight: $weight-bold;

  &.accent {
    color: $accent;
  }

  &.positive {
    color: $positive;
  }
}

.forecast-arrow {
  font-size: 36rpx;
  color: $text-tertiary;
}

/* Checklist 进度 */
.checklist-card {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  box-shadow: $shadow-sm;
}

.checklist-title {
  display: block;
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.checklist-bar-wrap {
  display: flex;
  align-items: center;
  gap: $spacing-md;
}

.checklist-bar {
  flex: 1;
  height: 12rpx;
  background: $divider-light;
  border-radius: $radius-pill;
  overflow: hidden;
}

.checklist-fill {
  height: 100%;
  background: $positive-gradient;
  border-radius: $radius-pill;
  transition: width $transition-normal;
}

.checklist-text {
  font-size: $font-sm;
  font-weight: $weight-semibold;
  color: $positive;
  flex-shrink: 0;
}
</style>
