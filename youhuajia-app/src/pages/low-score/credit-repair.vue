<template>
  <view class="credit-repair">
    <FunnelNavBar title="信用修复" />
    <ProgressBar :current="6" :total="9" />

    <view class="page-header">
      <text class="headline">信用修复路线图</text>
      <text class="sub-headline">分三个阶段，稳步改善你的信用结构</text>
    </view>

    <!-- 30/60/90 天时间轴（带阶段标签） -->
    <view class="section-card">
      <Timeline
        :milestones="[30, 60, 90]"
        :labels="['基础修复', '结构优化', '重新评估']"
        :current-day="1"
      />
    </view>

    <!-- 各阶段详细行动建议 -->
    <view class="phases-section">
      <view v-for="(phase, i) in phases" :key="i" class="phase-card">
        <view class="phase-header">
          <view class="phase-badge">
            <text class="phase-day">{{ phase.days }}天</text>
          </view>
          <text class="phase-title">{{ phase.title }}</text>
        </view>
        <view class="phase-actions">
          <view v-for="(action, j) in phase.actions" :key="j" class="action-item">
            <view class="action-dot" />
            <text class="action-text">{{ action }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 鼓励文案（绝不使用恐慌性表达） -->
    <view class="encouragement-card">
      <text class="encouragement-text">
        像你这样情况的用户，通过调整后有机会改善财务状况。
        调整节奏由你决定，每一步都算数。
      </text>
    </view>

    <SafeAreaBottom />

    <view class="cta-bar">
      <YouhuaButton
        text="了解常见问题"
        type="primary"
        @click="goToRiskFaq"
      />
    </view>
  </view>
</template>

<script setup>
import { useFunnelStore } from '../../stores/funnel.js'
import FunnelNavBar from '../../components/FunnelNavBar.vue'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import Timeline from '../page9-companion/components/Timeline.vue'

const funnelStore = useFunnelStore()

// 三个阶段的行动建议
const phases = [
  {
    days: 30,
    title: '基础修复',
    actions: [
      '补齐所有账单逾期，恢复按时还款',
      '将信用卡使用率降至 70% 以下',
      '整理所有债务，确认还款日期',
    ],
  },
  {
    days: 60,
    title: '结构优化',
    actions: [
      '优先偿还最小额度账户，减少账户数量',
      '调整各账户还款顺序，优先处理高利率',
      '避免在此期间新增信用查询',
    ],
  },
  {
    days: 90,
    title: '重新评估',
    actions: [
      '回到优化家重新评分，查看改善幅度',
      '根据新评分决定下一步优化方向',
      '若评分已达 60+，可进入主优化流程',
    ],
  },
]

function goToRiskFaq() {
  funnelStore.advanceStep(7)
  uni.navigateTo({ url: '/pages/low-score/risk-faq' })
}
</script>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.credit-repair {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: 140rpx;
}

.page-header {
  padding: 40rpx $spacing-xl 24rpx;
}

.headline {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 12rpx;
}

.sub-headline {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.5;
}

.section-card {
  background-color: $surface;
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

/* 阶段卡片 */
.phases-section {
  padding: 0 $spacing-xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.phase-card {
  background-color: $surface;
  border-radius: $radius-md;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.phase-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.phase-badge {
  background-color: $primary-light;
  border-radius: $radius-sm;
  padding: 6rpx 16rpx;
}

.phase-day {
  font-size: $font-sm;
  color: $primary;
  font-weight: 600;
}

.phase-title {
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
}

.phase-actions {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.action-item {
  display: flex;
  gap: 12rpx;
  align-items: flex-start;
}

.action-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 50%;
  background-color: $positive;
  margin-top: 8rpx;
  flex-shrink: 0;
}

.action-text {
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.5;
  flex: 1;
}

/* 鼓励文案 */
.encouragement-card {
  background: linear-gradient(135deg, $positive-light, $primary-light);
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
}

.encouragement-text {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.7;
  text-align: center;
}

.cta-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  padding: $spacing-md $spacing-xl;
  padding-bottom: calc(#{$spacing-md} + env(safe-area-inset-bottom));
  background-color: $surface;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
}
</style>
