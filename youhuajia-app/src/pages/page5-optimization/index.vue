<template>
  <view class="page5">
    <ProgressBar :current="5" :total="9" />

    <!-- 正面标题：第一句话必须正面（$positive #2BAF7E） -->
    <view class="positive-header">
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

    <!-- 评分解读与改善建议 -->
    <ScoreExplain v-if="explainDimensions.length" :dimensions="explainDimensions" />

    <!-- What-If 模拟 -->
    <WhatIfSimulator />

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
import { useFunnelStore } from '../../stores/funnel'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import ConfidenceCard from './components/ConfidenceCard.vue'
import ScoreRadar from './components/ScoreRadar.vue'
import ScoreExplain from './components/ScoreExplain.vue'
import WhatIfSimulator from './components/WhatIfSimulator.vue'

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

// 五维雷达图数据（name+score 格式）— 使用后端 DimensionDetail 列表
const radarDimensions = computed(() => {
  const dims = profile.value.scoreDimensions // List<DimensionDetail> from backend
  if (dims && dims.length) {
    return dims.map(d => ({ name: d.label, score: Number(d.score) || 0 }))
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

// 需要改善的维度（score < 70，按 score 升序）
const explainDimensions = computed(() => {
  const dims = profile.value.scoreDimensions
  if (!dims || !dims.length) return []
  return dims
    .filter(d => Number(d.score) < 70)
    .sort((a, b) => Number(a.score) - Number(b.score))
    .slice(0, 3)
})

function goToSimulator() {
  uni.navigateTo({ url: '/pages/page6-rate-simulator/index' })
}
</script>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page5 {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: 140rpx;
}

.positive-header {
  padding: 40rpx $spacing-xl 24rpx;
}

.headline {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: $positive;
  line-height: 1.3;
  margin-bottom: 12rpx;
}

.sub-headline {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.5;
}

.confidence-cards {
  display: flex;
  gap: $spacing-sm;
  padding: 0 $spacing-xl $spacing-lg;
}

.radar-section {
  background-color: $surface;
  border-radius: $radius-md;
  margin: 0 $spacing-xl $spacing-lg;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  display: flex;
  flex-direction: column;
  align-items: center;
}

.section-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
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
  padding: $spacing-md $spacing-xl;
  padding-bottom: calc(#{$spacing-md} + env(safe-area-inset-bottom));
  background-color: $surface;
  box-shadow: 0 -2px 12px rgba(0, 0, 0, 0.08);
}
</style>
