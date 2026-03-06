<template>
  <view class="whatif-section" v-if="inProfileDebts.length">
    <text class="section-title">假如还清一笔？</text>
    <text class="section-desc">选择一笔债务，看看评分会有多少提升</text>

    <!-- 债务选择列表 -->
    <scroll-view scroll-x class="debt-scroll">
      <view
        v-for="debt in inProfileDebts"
        :key="getDebtId(debt)"
        class="debt-chip"
        :class="{ active: selectedDebtId === getDebtId(debt) }"
        @tap="selectDebt(getDebtId(debt))"
      >
        <text class="chip-creditor">{{ debt.creditor || '未知' }}</text>
        <text class="chip-amount">¥{{ formatAmount(debt.principal) }}</text>
      </view>
    </scroll-view>

    <!-- 模拟结果 -->
    <view v-if="profileStore.scoreSimLoading" class="sim-loading">
      <view class="mini-spinner"></view>
      <text class="loading-text">模拟计算中...</text>
    </view>

    <view v-else-if="simResult" class="sim-result">
      <!-- 分数对比 -->
      <view class="score-compare">
        <view class="score-col">
          <text class="score-label">当前评分</text>
          <text class="score-val current">{{ formatScore(simResult.current?.finalScore) }}</text>
        </view>
        <text class="score-arrow">→</text>
        <view class="score-col">
          <text class="score-label">模拟评分</text>
          <text class="score-val simulated">{{ formatScore(simResult.simulated?.finalScore) }}</text>
        </view>
        <view class="score-delta" v-if="scoreDelta !== 0">
          <text class="delta-text" :class="scoreDelta > 0 ? 'delta-up' : 'delta-down'">
            {{ scoreDelta > 0 ? '+' : '' }}{{ scoreDelta.toFixed(1) }}
          </text>
        </view>
      </view>

      <!-- 各维度变化 -->
      <view v-if="simResult.dimensionDeltas?.length" class="deltas-section">
        <view
          v-for="d in simResult.dimensionDeltas"
          :key="d.name"
          class="delta-row"
        >
          <text class="delta-label">{{ d.label }}</text>
          <text
            class="delta-val"
            :class="Number(d.delta) > 0 ? 'delta-up' : Number(d.delta) < 0 ? 'delta-down' : ''"
          >
            {{ Number(d.delta) > 0 ? '+' : '' }}{{ Number(d.delta).toFixed(1) }}
          </text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { useProfileStore } from '../../../stores/profile.js'
import { useDebtStore } from '../../../stores/debt.js'

const profileStore = useProfileStore()
const debtStore = useDebtStore()

const selectedDebtId = ref(null)

const inProfileDebts = computed(() =>
  debtStore.debts.filter(d => d.status === 'IN_PROFILE')
)

const simResult = computed(() => profileStore.scoreSimResult)

const scoreDelta = computed(() => {
  if (!simResult.value?.current || !simResult.value?.simulated) return 0
  return Number(simResult.value.simulated.finalScore) - Number(simResult.value.current.finalScore)
})

function selectDebt(debtId) {
  selectedDebtId.value = debtId
  profileStore.doSimulateScore([{ type: 'PAYOFF', debtId: Number(debtId) }])
}

function getDebtId(debt) {
  return (debt.name || '').split('/').pop() || debt.id
}

function formatAmount(val) {
  if (!val) return '0'
  return Math.round(Number(val)).toLocaleString('zh-CN')
}

function formatScore(val) {
  if (val == null) return '--'
  return Number(val).toFixed(1)
}
</script>

<style lang="scss" scoped>
@use '../../../styles/variables.scss' as *;

.whatif-section {
  margin: 0 $spacing-xl $spacing-lg;
  background-color: $surface;
  border-radius: $radius-md;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
}

.section-title {
  display: block;
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.section-desc {
  display: block;
  font-size: $font-xs;
  color: $text-secondary;
  margin-bottom: $spacing-md;
}

.debt-scroll {
  white-space: nowrap;
  margin-bottom: $spacing-md;
}

.debt-chip {
  display: inline-flex;
  flex-direction: column;
  align-items: center;
  padding: 16rpx 24rpx;
  margin-right: $spacing-sm;
  background-color: #F3F4F6;
  border-radius: $radius-sm;
  border: 2rpx solid transparent;
  transition: all 0.2s;
}

.debt-chip.active {
  background-color: #EBF5FF;
  border-color: #2E75B6;
}

.chip-creditor {
  font-size: $font-xs;
  color: $text-primary;
  font-weight: 500;
  margin-bottom: 4rpx;
}

.chip-amount {
  font-size: $font-xs;
  color: $text-secondary;
}

.sim-loading {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  padding: $spacing-md 0;
}

.mini-spinner {
  width: 36rpx;
  height: 36rpx;
  border: 3rpx solid #D5E8F0;
  border-top-color: #2E75B6;
  border-radius: 50%;
  animation: spin 0.8s linear infinite;
}

@keyframes spin { to { transform: rotate(360deg); } }

.loading-text {
  font-size: $font-xs;
  color: $text-secondary;
}

.sim-result {
  padding-top: 8rpx;
}

.score-compare {
  display: flex;
  flex-direction: row;
  align-items: center;
  justify-content: center;
  gap: 24rpx;
  margin-bottom: $spacing-md;
}

.score-col {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.score-label {
  font-size: $font-xs;
  color: $text-secondary;
  margin-bottom: 8rpx;
}

.score-val {
  font-size: 48rpx;
  font-weight: 800;
}

.score-val.current { color: $text-primary; }
.score-val.simulated { color: #2BAF7E; }

.score-arrow {
  font-size: 36rpx;
  color: $text-tertiary;
}

.score-delta {
  margin-left: 8rpx;
}

.delta-text {
  font-size: $font-md;
  font-weight: 700;
}

.delta-up { color: #2BAF7E; }
.delta-down { color: #E85D5D; }

.deltas-section {
  border-top: 2rpx solid #F3F4F6;
  padding-top: $spacing-sm;
}

.delta-row {
  display: flex;
  flex-direction: row;
  justify-content: space-between;
  align-items: center;
  padding: 8rpx 0;
}

.delta-label {
  font-size: $font-xs;
  color: $text-secondary;
}

.delta-val {
  font-size: $font-sm;
  font-weight: 600;
  color: $text-tertiary;
}
</style>
