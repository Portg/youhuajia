<template>
  <view class="credit-repair">
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
        text="开始准备"
        type="primary"
        @click="goToImprovementPlan"
      />
    </view>
  </view>
</template>

<script setup>
import ProgressBar from '../../src/components/ProgressBar.vue'
import YouhuaButton from '../../src/components/YouhuaButton.vue'
import SafeAreaBottom from '../../src/components/SafeAreaBottom.vue'
import Timeline from '../page9-companion/components/Timeline.vue'

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

function goToImprovementPlan() {
  uni.navigateTo({ url: '/pages/low-score/improvement-plan' })
}
</script>

<style lang="scss" scoped>
@use '../../src/styles/variables.scss' as *;
@use '../../src/styles/mixins.scss' as *;

.credit-repair {
  min-height: 100vh;
  @include page-bg;
  padding-bottom: 150rpx;
}

.page-header {
  padding: $spacing-xl $spacing-xl $spacing-md;
}

.headline {
  display: block;
  font-size: $font-xl;
  font-weight: $weight-black;
  color: $text-primary;
  margin-bottom: 12rpx;
  letter-spacing: -1rpx;
}

.sub-headline {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.5;
}

.section-card {
  @include card-elevated;
  margin: 0 $spacing-xl $spacing-md;
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
  @include card-elevated;
}

.phase-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.phase-badge {
  background: $primary-glass;
  border-radius: $radius-pill;
  padding: 6rpx 18rpx;
}

.phase-day {
  font-size: $font-sm;
  color: $primary;
  font-weight: $weight-semibold;
}

.phase-title {
  font-size: $font-md;
  font-weight: $weight-semibold;
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
  background: $positive;
  margin-top: 10rpx;
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
  background: linear-gradient(135deg, $positive-glass, $primary-glass);
  border: 1rpx solid rgba(15, 169, 104, 0.08);
  border-radius: $radius-xl;
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
  @include bottom-bar;
}
</style>
