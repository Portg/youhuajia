<template>
  <view class="score-explain">
    <text class="section-title">影响评分的关键因素</text>
    <view
      v-for="(dim, i) in dimensions"
      :key="i"
      class="explain-card"
    >
      <view class="card-indicator" :class="indicatorClass(dim)"></view>
      <view class="card-body">
        <view class="card-header">
          <text class="dim-label">{{ dim.label }}</text>
          <text class="dim-score" :class="indicatorClass(dim)">{{ Number(dim.score).toFixed(0) }}分</text>
        </view>
        <text v-if="dim.explanation" class="dim-explanation">{{ dim.explanation }}</text>
        <text v-if="dim.improvementTip" class="dim-tip">{{ dim.improvementTip }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
defineProps({
  dimensions: {
    type: Array,
    default: () => [],
  },
})

function indicatorClass(dim) {
  const score = Number(dim.score) || 0
  if (score >= 70) return 'level-good'
  if (score >= 40) return 'level-warn'
  return 'level-bad'
}
</script>

<style lang="scss" scoped>
@use '../../../styles/variables.scss' as *;

.score-explain {
  margin: 0 $spacing-xl $spacing-lg;
}

.section-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.explain-card {
  display: flex;
  flex-direction: row;
  background-color: $surface;
  border-radius: $radius-md;
  margin-bottom: $spacing-sm;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
}

.card-indicator {
  width: 8rpx;
  flex-shrink: 0;
}

.level-good { background-color: #2BAF7E; color: #2BAF7E; }
.level-warn { background-color: #E8852A; color: #E8852A; }
.level-bad { background-color: #E85D5D; color: #E85D5D; }

.card-body {
  flex: 1;
  padding: $spacing-md;
}

.card-header {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.dim-label {
  font-size: $font-sm;
  font-weight: 600;
  color: $text-primary;
}

.dim-score {
  font-size: $font-sm;
  font-weight: 700;
}

.dim-explanation {
  display: block;
  font-size: $font-xs;
  color: $text-secondary;
  line-height: 1.5;
  margin-bottom: 8rpx;
}

.dim-tip {
  display: block;
  font-size: $font-xs;
  color: #2E75B6;
  line-height: 1.5;
}
</style>
