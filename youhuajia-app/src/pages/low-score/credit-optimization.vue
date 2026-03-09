<template>
  <view class="credit-optimization">
    <FunnelNavBar title="信用优化" />
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

    <!-- 个性化诊断：弱项维度 + 改善提示 -->
    <view v-if="weakDimensions.length > 0" class="diagnosis-card">
      <text class="diagnosis-title">你的改善重点</text>
      <view class="dimension-list">
        <view
          v-for="dim in weakDimensions"
          :key="dim.key"
          class="dimension-item"
        >
          <view class="dim-header">
            <text class="dim-label">{{ dim.label }}</text>
            <view class="dim-score-bar">
              <view class="dim-score-fill" :style="{ width: dim.score + '%' }" />
            </view>
            <text class="dim-score-text">{{ dim.score }}分</text>
          </view>
          <text class="dim-desc">{{ dim.levelDesc }}</text>
          <text class="dim-tip">{{ dim.tip }}</text>
        </view>
      </view>
    </view>

    <!-- 30 天行动计划预览卡片（根据弱项个性化） -->
    <view class="plan-card">
      <text class="plan-card-title">30 天行动计划预览</text>
      <view class="plan-list">
        <view v-for="(item, i) in personalizedPlan" :key="i" class="plan-item">
          <view class="plan-week-dot" />
          <view class="plan-item-content">
            <text class="plan-week">{{ item.week }}</text>
            <text class="plan-task">{{ item.task }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 改善后预估分数 -->
    <view v-if="projectedScore > 0" class="projection-card">
      <text class="projection-label">坚持 30 天后预估评分</text>
      <view class="projection-scores">
        <view class="projection-current">
          <text class="projection-num">{{ funnelStore.score }}</text>
          <text class="projection-unit">当前</text>
        </view>
        <text class="projection-arrow">→</text>
        <view class="projection-target">
          <text class="projection-num projected">{{ projectedScore }}</text>
          <text class="projection-unit">预估</text>
        </view>
      </view>
      <text class="projection-hint">
        {{ projectedScore >= 60 ? '达到 60 分后可进入主优化流程' : '持续改善，逐步接近优化条件' }}
      </text>
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
import { computed } from 'vue'
import { useFunnelStore } from '../../stores/funnel.js'
import { useProfileStore } from '../../stores/profile.js'
import { useDebtStore } from '../../stores/debt.js'
import { calculateScore, simulateImprovement, buildScoreInput } from '../../utils/scoreSimulator.js'
import FunnelNavBar from '../../components/FunnelNavBar.vue'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'

const funnelStore = useFunnelStore()
const profileStore = useProfileStore()
const debtStore = useDebtStore()

// 构造评分输入 → 计算五维分数 → 识别弱项
const scoreInput = computed(() => buildScoreInput(profileStore, funnelStore, debtStore))
const scoreResult = computed(() => calculateScore(scoreInput.value))
const weakDimensions = computed(() => scoreResult.value.weakDimensions)

// What-if 模拟：假设用户执行基本改善操作后的预估分数
const simulation = computed(() =>
  simulateImprovement(scoreInput.value, [
    { type: 'CATCH_UP_PAYMENTS' },
    { type: 'REDUCE_UTILIZATION' },
  ])
)
const projectedScore = computed(() =>
  Math.min(100, Math.round(funnelStore.score + simulation.value.scoreDelta))
)

// 根据弱项维度生成个性化计划
const personalizedPlan = computed(() => {
  const weak = weakDimensions.value
  const plan = []

  // 第 1 周始终是整理账单
  plan.push({ week: '第 1 周', task: '整理账单，确认各债务还款日和最低还款额' })

  // 第 2 周根据最弱维度定制
  const weakestKey = weak.length > 0 ? weak[0].key : null
  if (weakestKey === 'overdue') {
    plan.push({ week: '第 2 周', task: '优先补齐逾期款项，恢复按时还款记录' })
  } else if (weakestKey === 'debtIncomeRatio') {
    plan.push({ week: '第 2 周', task: '优先按时还清最小额度债务，降低月供压力' })
  } else if (weakestKey === 'weightedApr') {
    plan.push({ week: '第 2 周', task: '识别利率最高的债务，优先偿还或协商降息' })
  } else {
    plan.push({ week: '第 2 周', task: '优先按时还清最小额度债务' })
  }

  // 第 3 周：信用使用率
  const hasHighUtilization = weak.some(d => d.key === 'debtIncomeRatio' && d.score <= 30)
  plan.push({
    week: '第 3 周',
    task: hasHighUtilization
      ? '信用卡使用率降至 70% 以下，避免新增借贷'
      : '申请调整还款日至收入日后 3 天，保持稳定还款',
  })

  // 第 4 周始终是重新评估
  plan.push({ week: '第 4 周', task: '重新评估信用状况，查看改善幅度' })

  return plan
})

function goToRepair() {
  funnelStore.advanceStep(6)
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
  color: $text-inverse;
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
  color: $text-secondary;
  line-height: 1.6;
}

/* 个性化诊断卡片 */
.diagnosis-card {
  background-color: $surface;
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
}

.diagnosis-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.dimension-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-lg;
}

.dimension-item {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.dim-header {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
}

.dim-label {
  font-size: $font-sm;
  font-weight: 600;
  color: $text-primary;
  flex-shrink: 0;
  width: 120rpx;
}

.dim-score-bar {
  flex: 1;
  height: 12rpx;
  background: $divider-light;
  border-radius: $radius-pill;
  overflow: hidden;
}

.dim-score-fill {
  height: 100%;
  background: $accent-gradient;
  border-radius: $radius-pill;
  transition: width $transition-normal;
}

.dim-score-text {
  font-size: $font-xs;
  font-weight: 600;
  color: $accent;
  flex-shrink: 0;
  width: 60rpx;
  text-align: right;
}

.dim-desc {
  font-size: $font-xs;
  color: $text-secondary;
  padding-left: 136rpx;
}

.dim-tip {
  font-size: $font-xs;
  color: $primary;
  padding-left: 136rpx;
}

/* 行动计划卡片 */
.plan-card {
  background-color: $surface;
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
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

/* 预估分数卡片 */
.projection-card {
  background-color: $surface;
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-md;
  padding: $spacing-lg;
  box-shadow: $shadow-sm;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.projection-label {
  font-size: $font-sm;
  color: $text-secondary;
  margin-bottom: $spacing-md;
}

.projection-scores {
  display: flex;
  align-items: center;
  gap: $spacing-xl;
  margin-bottom: $spacing-sm;
}

.projection-current,
.projection-target {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}

.projection-num {
  font-size: 56rpx;
  font-weight: $weight-bold;
  color: $accent;

  &.projected {
    color: $positive;
  }
}

.projection-unit {
  font-size: $font-xs;
  color: $text-tertiary;
}

.projection-arrow {
  font-size: 40rpx;
  color: $text-tertiary;
}

.projection-hint {
  font-size: $font-xs;
  color: $text-secondary;
  text-align: center;
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
