<template>
  <view class="page5">
    <ProgressBar :current="5" :total="9" />

    <!-- 正面标题：第一句话必须正面（$positive） -->
    <view class="positive-header">
      <view class="header-glow" />
      <text class="headline">好消息是，你有优化空间。</text>
      <text class="sub-headline">基于你的财务结构分析，我们发现了以下机会</text>
    </view>

    <!-- 三个确定性卡片 -->
    <view class="confidence-cards">
      <ConfidenceCard
        icon="success-rate"
        title="成功概率"
        :value="probability + '%'"
        type="ring"
      />
      <ConfidenceCard
        icon="saving"
        title="月供可降低约"
        :value="'¥' + monthlySaving + '/月'"
        type="bar"
        :saving-ratio="savingRatio"
      />
      <ConfidenceCard
        icon="phases"
        title="分步完成"
        :value="phases + ' 步'"
        type="path"
        :steps="phases"
      />
    </view>

    <!-- 五维雷达图 -->
    <view class="radar-section">
      <text class="section-title">你的财务健康雷达</text>
      <ScoreRadar :dimensions="radarDimensions" :size="260" />
    </view>

    <text class="disclaimer">实际优化效果取决于个人信用状况和金融机构审核</text>

    <SafeAreaBottom />

    <!-- CTA -->
    <view class="cta-bar">
      <YouhuaButton
        text="模拟一下效果"
        type="primary"
        @click="goToSimulator"
      />
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useFunnelStore } from '../../src/stores/funnel'
import ProgressBar from '../../src/components/ProgressBar.vue'
import YouhuaButton from '../../src/components/YouhuaButton.vue'
import SafeAreaBottom from '../../src/components/SafeAreaBottom.vue'
import ConfidenceCard from './components/ConfidenceCard.vue'
import ScoreRadar from './components/ScoreRadar.vue'

const funnelStore = useFunnelStore()

// 评分<60 时重定向到低分路径（F-13）
onMounted(() => {
  if (funnelStore.isLowScore) {
    uni.redirectTo({ url: '/pages/low-score/credit-optimization' })
  }
})

const profile = computed(() => funnelStore.financeProfile || {})

const probability = computed(() => profile.value.successProbability ?? 78)
const monthlySaving = computed(() => {
  const v = profile.value.monthlySaving
  if (!v) return '2,600'
  return Number(v).toLocaleString('zh-CN')
})
const phases = computed(() => profile.value.optimizationPhases ?? 3)

// 节省比例（用于柱状图高度对比）
const savingRatio = computed(() => {
  const saving = profile.value.monthlySaving || 0
  const payment = profile.value.monthlyPayment || 1
  return Math.min(0.7, saving / payment)
})

// 五维雷达图数据（name+score 格式）
const radarDimensions = computed(() => {
  const dims = profile.value.scoreDimensions
  if (dims) {
    return [
      { name: '利率健康度', score: dims.aprHealth ?? 0 },
      { name: '结构合理度', score: dims.structureScore ?? 0 },
      { name: '还款能力', score: dims.repaymentAbility ?? 0 },
      { name: '征信状况', score: dims.creditStatus ?? 0 },
      { name: '优化潜力', score: dims.optimizationPotential ?? 0 },
    ]
  }
  // 默认展示数据
  return [
    { name: '利率健康度', score: 62 },
    { name: '结构合理度', score: 55 },
    { name: '还款能力', score: 70 },
    { name: '征信状况', score: 80 },
    { name: '优化潜力', score: 85 },
  ]
})

function goToSimulator() {
  uni.navigateTo({ url: '/pages/page6-rate-simulator/index' })
}
</script>

<style lang="scss" scoped>
@use '../../src/styles/variables.scss' as *;
@use '../../src/styles/mixins.scss' as *;

.page5 {
  min-height: 100vh;
  @include page-bg;
  padding-bottom: 150rpx;
}

.positive-header {
  padding: $spacing-xl $spacing-xl $spacing-lg;
  position: relative;
  overflow: hidden;
}

.header-glow {
  position: absolute;
  top: -40rpx;
  right: -60rpx;
  width: 300rpx;
  height: 300rpx;
  background: radial-gradient(circle, rgba(15, 169, 104, 0.08) 0%, transparent 70%);
  pointer-events: none;
}

.headline {
  display: block;
  font-size: 48rpx;
  font-weight: $weight-black;
  color: $positive;
  line-height: 1.3;
  margin-bottom: 12rpx;
  letter-spacing: -1rpx;
  position: relative;
  z-index: 1;
}

.sub-headline {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.5;
  position: relative;
  z-index: 1;
}

.confidence-cards {
  display: flex;
  gap: $spacing-sm;
  padding: 0 $spacing-xl $spacing-lg;
}

.radar-section {
  @include card-elevated;
  margin: 0 $spacing-xl $spacing-lg;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.section-title {
  display: block;
  font-size: $font-md;
  font-weight: $weight-semibold;
  color: $text-primary;
  margin-bottom: $spacing-md;
  align-self: flex-start;
}

.disclaimer {
  display: block;
  text-align: center;
  font-size: $font-xs;
  color: $text-tertiary;
  padding: 0 $spacing-xl $spacing-lg;
  line-height: 1.5;
}

.cta-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  @include bottom-bar;
  box-shadow: 0 -4rpx 24rpx rgba(15, 23, 42, 0.06);
}
</style>
