<template>
  <view class="credit-optimization">
    <ProgressBar :current="5" :total="9" />

    <!-- 正面引导（绝对禁止"申请失败""不符合条件"，F-13） -->
    <view class="positive-header">
      <text class="headline">当前更适合优化信用结构。</text>
      <text class="sub-headline">
        你的财务结构有提升空间，我们为你规划了一条清晰的改善路径
      </text>
    </view>

    <!-- 正面强调卡片 -->
    <view class="highlight-card">
      <view class="highlight-icon">
        <text class="icon-star">★</text>
      </view>
      <view class="highlight-text">
        <text class="highlight-title">信用状况有提升空间</text>
        <text class="highlight-desc">
          像你这样情况的用户，通过调整后有机会改善财务状况。
          调整节奏由你决定。
        </text>
      </view>
    </view>

    <!-- 30 天行动计划预览卡片 -->
    <view class="plan-card">
      <text class="plan-card-title">30 天行动计划预览</text>
      <view class="plan-list">
        <view v-for="(item, i) in planItems" :key="i" class="plan-item">
          <view class="plan-week-dot" />
          <view class="plan-item-content">
            <text class="plan-week">第 {{ item.week }}</text>
            <text class="plan-task">{{ item.task }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 鼓励文案（绝不给 0% 成功率） -->
    <text class="encouragement">
      通过调整后有机会改善财务状况。越早开始，效果越明显。
    </text>

    <SafeAreaBottom />

    <view class="cta-bar">
      <YouhuaButton
        text="查看改善方案"
        type="primary"
        @click="goToRepair"
      />
    </view>
  </view>
</template>

<script setup>
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'

// 30天行动计划内容
const planItems = [
  { week: '第 1 周', task: '整理账单，确认各债务还款日' },
  { week: '第 2 周', task: '优先按时还清最小额度债务' },
  { week: '第 3 周', task: '申请调整还款日至收入日后 3 天' },
  { week: '第 4 周', task: '重新评估信用状况，规划下一步' },
]

function goToRepair() {
  uni.navigateTo({ url: '/pages/low-score/credit-repair' })
}
</script>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.credit-optimization {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: 140rpx;
}

.positive-header {
  padding: 40rpx $spacing-xl 24rpx;
}

.headline {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: $positive;
  line-height: 1.3;
  margin-bottom: 12rpx;
}

.sub-headline {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.6;
}

/* 正面强调卡片 */
.highlight-card {
  background: linear-gradient(135deg, $positive-light, $primary-light);
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
  display: flex;
  gap: $spacing-md;
  align-items: flex-start;
}

.highlight-icon {
  width: 56rpx;
  height: 56rpx;
  background-color: $positive;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.icon-star {
  font-size: 28rpx;
  color: #ffffff;
}

.highlight-text {
  flex: 1;
}

.highlight-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.highlight-desc {
  display: block;
  font-size: $font-sm;
  color: #4b5563;
  line-height: 1.6;
}

/* 行动计划卡片 */
.plan-card {
  background-color: $surface;
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.plan-card-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.plan-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.plan-item {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
}

.plan-week-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background-color: $primary;
  margin-top: 8rpx;
  flex-shrink: 0;
}

.plan-item-content {
  flex: 1;
}

.plan-week {
  display: block;
  font-size: $font-xs;
  color: $primary;
  font-weight: 500;
  margin-bottom: 2rpx;
}

.plan-task {
  display: block;
  font-size: $font-sm;
  color: $text-primary;
  line-height: 1.4;
}

/* 鼓励语 */
.encouragement {
  display: block;
  text-align: center;
  font-size: $font-sm;
  color: $text-secondary;
  padding: 0 $spacing-xl $spacing-lg;
  line-height: 1.7;
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
