<template>
  <view class="page">
    <FunnelNavBar title="利率模拟" />
    <!-- 加载态 -->
    <view v-if="profileStore.loading && !profileStore.profile" class="loading-wrap">
      <text class="loading-text">加载数据中...</text>
    </view>

    <template v-else>
    <!-- 顶部 -->
    <view class="page-top">
      <text class="page-title">利率模拟器</text>
      <text class="page-sub">拖动滑块，探索你的节省空间</text>
    </view>

    <!-- 当前 APR → 目标 APR 标签 -->
    <view class="apr-header">
      <view class="apr-item">
        <text class="apr-item-label">当前利率</text>
        <text class="apr-item-val apr-current">{{ fmt1(currentApr) }}%</text>
      </view>
      <view class="apr-arrow">
        <text class="arrow-text">→</text>
        <text class="arrow-desc">拖动探索</text>
      </view>
      <view class="apr-item apr-item-right">
        <text class="apr-item-label">目标利率</text>
        <text class="apr-item-val apr-target">{{ fmt1(targetApr) }}%</text>
      </view>
    </view>

    <!-- 自定义滑块 -->
    <view class="slider-wrap">
      <!-- 刻度数字 -->
      <view class="tick-row">
        <text class="tick-num tick-left">{{ fmt1(currentApr) }}%</text>
        <text class="tick-num tick-right">{{ fmt1(bestApr) }}%</text>
      </view>

      <!-- 滑轨容器（触摸 + 鼠标 + 点击） -->
      <view
        class="track-area"
        id="slider-track-area"
        @touchstart="onTouchStart"
        @touchmove.stop.prevent="onTouchMove"
        @touchend="onTouchEnd"
        @mousedown.prevent="onMouseDown"
        @click="onTrackClick"
      >
        <view class="track-bg">
          <!-- 绿色填充（从右到拇指） -->
          <view class="track-fill" :style="{ width: fillPct + '%' }"></view>
          <!-- 刻度点 -->
          <view
            v-for="tick in tickPositions"
            :key="tick.apr"
            class="track-tick"
            :style="{ left: tick.pct + '%' }"
          ></view>
          <!-- 把手 -->
          <view class="thumb" :style="{ left: fillPct + '%' }">
            <view class="thumb-circle">
              <text class="thumb-text">{{ fmt1(targetApr) }}%</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 阻尼提示 -->
      <text class="damping-hint">越接近最优利率，提升难度越大</text>
    </view>

    <!-- 实时结果面板 -->
    <view class="result-card">
      <view class="result-title-row">
        <text class="result-title">优化效果预览</text>
      </view>

      <!-- 月供变化 -->
      <view class="result-row">
        <view class="result-label-col">
          <text class="result-icon">💰</text>
          <text class="result-name">月供变化</text>
        </view>
        <view class="result-val-col">
          <text class="val-before">{{ fmtInt(currentMonthlyPayment) }}</text>
          <text class="val-arrow">→</text>
          <AnimatedNumber
            :value="simulatedMonthlyPayment"
            prefix="¥"
            :formatter="n => Math.round(n).toLocaleString('zh-CN')"
            :color="monthlySavingColor"
            :fontSize="28"
            :duration="400"
          />
        </view>
      </view>

      <!-- 三年节省（突出显示）-->
      <view class="result-row result-row-hl">
        <view class="result-label-col">
          <text class="result-icon">✨</text>
          <text class="result-name">三年节省</text>
        </view>
        <view class="result-val-col">
          <AnimatedNumber
            :value="Math.max(0, threeYearSaving)"
            prefix="¥"
            :formatter="n => Math.round(n).toLocaleString('zh-CN')"
            :color="threeYearSaving > 0 ? '#E8852A' : '#9CA3AF'"
            :fontSize="44"
            fontWeight="800"
            :duration="400"
          />
        </view>
      </view>

      <!-- 月供占收入比 -->
      <view class="result-row">
        <view class="result-label-col">
          <text class="result-icon">📊</text>
          <text class="result-name">月供占收入</text>
        </view>
        <view class="result-val-col result-val-col-v">
          <view class="ratio-nums">
            <text class="val-before">{{ fmt1(currentPaymentRatio) }}%</text>
            <text class="val-arrow">→</text>
            <AnimatedNumber
              :value="simulatedPaymentRatio"
              suffix="%"
              :formatter="n => n.toFixed(1)"
              :color="ratioColor"
              :fontSize="28"
              :duration="400"
            />
          </view>
          <text class="ratio-hint">{{ simulatedPaymentRatio < 30 ? '低于30%健康线 👍' : '建议控制在30%以内' }}</text>
        </view>
      </view>
    </view>

    <!-- 底部：免责说明 + CTA -->
    <view class="bottom">
      <text class="disclaimer">
        实际利率取决于个人信用状况和金融机构审核，以上模拟仅供参考。
      </text>
      <button class="cta-btn" @tap="goRisk">了解风险</button>
    </view>
    </template>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, watch } from 'vue'
import { useProfileStore } from '../../stores/profile.js'
import { useFunnelStore } from '../../stores/funnel.js'
import AnimatedNumber from '../../components/AnimatedNumber.vue'
import FunnelNavBar from '../../components/FunnelNavBar.vue'

const profileStore = useProfileStore()
const funnelStore = useFunnelStore()

// 从画像获取当前 APR 和最优 APR
const currentApr = computed(() => profileStore.weightedApr || 24)
const bestApr = computed(() => profileStore.profile?.bestPossibleApr || 6)

// 关键财务数据（画像优先，funnel 数据兜底）
const effectiveMonthlyPayment = computed(() =>
  profileStore.profile?.monthlyPayment || funnelStore.monthlyPayment || 5000
)
const effectiveTotalDebt = computed(() =>
  profileStore.profile?.totalDebt || (effectiveMonthlyPayment.value * 24)
)
const effectiveMonthlyIncome = computed(() =>
  profileStore.profile?.monthlyIncome || funnelStore.monthlyIncome || 7500
)

// 用户拖动的目标 APR（初始 = 当前 APR，待画像加载后更新）
const targetApr = ref(0)
const targetAprInitialized = ref(false)

// 滑块填充百分比（0 = 最左，100 = 最右/最优）
const fillPct = computed(() => {
  const range = currentApr.value - bestApr.value
  if (range <= 0) return 0
  const moved = currentApr.value - targetApr.value
  return Math.max(0, Math.min(100, (moved / range) * 100))
})

// 刻度点（每 2%）
const tickPositions = computed(() => {
  const result = []
  const range = currentApr.value - bestApr.value
  if (range <= 0) return result
  for (let apr = currentApr.value; apr >= bestApr.value; apr -= 2) {
    result.push({ apr, pct: ((currentApr.value - apr) / range) * 100 })
  }
  return result
})

// 阻尼：后半段阻力增加（模拟物理阻尼）
function applyDamping(raw) {
  if (raw <= 0.5) return raw
  const overflow = raw - 0.5
  return 0.5 + Math.sqrt(overflow / 0.5) * 0.5
}

// 滑块交互状态
let trackRect = null
let startX = 0, startFillPct = 0
let isDragging = false

// 缓存轨道尺寸
function cacheTrackRect(callback) {
  uni.createSelectorQuery()
    .select('#slider-track-area')
    .boundingClientRect(r => {
      if (r && r.width > 0) trackRect = r
      if (callback) callback()
    })
    .exec()
}

// 将 clientX 转换为 targetApr（含阻尼）
function xToApr(clientX, fromDrag) {
  if (!trackRect || trackRect.width <= 0) return
  let raw
  if (fromDrag) {
    const dx = clientX - startX
    raw = Math.max(0, Math.min(1, startFillPct + dx / trackRect.width))
  } else {
    raw = Math.max(0, Math.min(1, (clientX - trackRect.left) / trackRect.width))
  }
  const damped = applyDamping(raw)
  const range = currentApr.value - bestApr.value
  targetApr.value = Math.max(bestApr.value, Math.min(currentApr.value, currentApr.value - damped * range))
  // 所有数值由前端 computed 实时计算，无需调 API
}

// 点击轨道直接跳到对应位置
function onTrackClick(e) {
  if (isDragging) return
  const clientX = e.touches ? e.touches[0].clientX : e.clientX
  cacheTrackRect(() => xToApr(clientX, false))
}

// 触摸拖拽
function onTouchStart(e) {
  isDragging = true
  startX = e.touches[0].clientX
  startFillPct = fillPct.value / 100
  cacheTrackRect()
}

function onTouchMove(e) {
  if (!isDragging || !trackRect) return
  xToApr(e.touches[0].clientX, true)
}

function onTouchEnd() { isDragging = false }

// 鼠标拖拽（桌面 Chrome）
function onMouseDown(e) {
  isDragging = true
  startX = e.clientX
  startFillPct = fillPct.value / 100
  cacheTrackRect()

  const onMouseMove = (ev) => {
    if (!isDragging || !trackRect) return
    xToApr(ev.clientX, true)
  }
  const onMouseUp = () => {
    setTimeout(() => { isDragging = false }, 50)
    document.removeEventListener('mousemove', onMouseMove)
    document.removeEventListener('mouseup', onMouseUp)
  }
  document.addEventListener('mousemove', onMouseMove)
  document.addEventListener('mouseup', onMouseUp)
}

// 结果数据——全部基于前端实时估算，拖拽时即时响应
const currentMonthlyPayment = effectiveMonthlyPayment

const simulatedMonthlyPayment = computed(() => {
  const ratio = currentApr.value > 0 ? targetApr.value / currentApr.value : 1
  return currentMonthlyPayment.value * (0.4 + ratio * 0.6)
})

const threeYearSaving = computed(() => {
  const aprDiff = currentApr.value - targetApr.value
  if (aprDiff <= 0) return 0
  return (aprDiff / 100) * effectiveTotalDebt.value * 3
})

const currentPaymentRatio = computed(() => {
  if (profileStore.debtIncomeRatio > 0) {
    const r = profileStore.debtIncomeRatio
    return r > 1 ? r : r * 100
  }
  return (effectiveMonthlyPayment.value / effectiveMonthlyIncome.value) * 100
})

const simulatedPaymentRatio = computed(() => {
  return currentPaymentRatio.value * (simulatedMonthlyPayment.value / currentMonthlyPayment.value)
})

// 动态颜色：根据变化方向（好转=绿，恶化=橙，无变化=灰）
const monthlySavingColor = computed(() => {
  if (simulatedMonthlyPayment.value < currentMonthlyPayment.value) return '#2BAF7E'
  if (simulatedMonthlyPayment.value > currentMonthlyPayment.value) return '#E8852A'
  return '#6B7280'
})

const ratioColor = computed(() => {
  if (simulatedPaymentRatio.value < currentPaymentRatio.value) return '#2BAF7E'
  if (simulatedPaymentRatio.value > currentPaymentRatio.value) return '#E8852A'
  return '#6B7280'
})

// 格式化
function fmt1(v) { return Number(v).toFixed(1) }
function fmtInt(v) { return '¥' + Math.round(Number(v)).toLocaleString('zh-CN') }

function goRisk() {
  funnelStore.advanceStep(7)
  uni.navigateTo({ url: '/pages/page7-risk-assessment/index' })
}

onMounted(() => {
  // 低分用户重定向到信用修复路径
  if (funnelStore.isLowScore) {
    uni.redirectTo({ url: '/pages/low-score/credit-repair' })
    return
  }
  if (!profileStore.profile) {
    profileStore.loadProfile()
  } else {
    targetApr.value = currentApr.value
  }
})

// 画像加载完成后初始化滑块值
watch(() => profileStore.profile, (p) => {
  if (p && !targetAprInitialized.value) {
    targetApr.value = currentApr.value
    targetAprInitialized.value = true
  }
})
</script>

<style scoped>
.page { min-height: 100vh; background: #F8FAFE; display: flex; flex-direction: column; }

.loading-wrap { flex: 1; display: flex; align-items: center; justify-content: center; }
.loading-text { font-size: 28rpx; color: #6B7280; }

/* 顶部 */
.page-top { padding: 48rpx 32rpx 24rpx; background: #fff; }
.page-title { display: block; font-size: 36rpx; font-weight: 700; color: #1A1A2E; }
.page-sub { display: block; font-size: 26rpx; color: #6B7280; margin-top: 8rpx; }

/* APR 标签 */
.apr-header { display: flex; flex-direction: row; align-items: center; padding: 24rpx 32rpx; background: #fff; margin-bottom: 2rpx; }
.apr-item { flex: 1; display: flex; flex-direction: column; align-items: flex-start; }
.apr-item-right { align-items: flex-end; }
.apr-item-label { font-size: 24rpx; color: #9CA3AF; margin-bottom: 4rpx; }
.apr-item-val { font-size: 48rpx; font-weight: 800; }
.apr-current { color: #E8852A; }
.apr-target { color: #2BAF7E; transition: color .3s; }
.apr-arrow { display: flex; flex-direction: column; align-items: center; gap: 4rpx; }
.arrow-text { font-size: 36rpx; color: #9CA3AF; }
.arrow-desc { font-size: 22rpx; color: #9CA3AF; }

/* 滑块 */
.slider-wrap { background: #fff; margin: 20rpx 32rpx; border-radius: 24rpx; padding: 32rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.06); }
.tick-row { display: flex; flex-direction: row; justify-content: space-between; margin-bottom: 16rpx; }
.tick-num { font-size: 24rpx; color: #9CA3AF; }
.tick-left { color: #E8852A; }
.tick-right { color: #2BAF7E; }

.track-area { padding: 24rpx 0; cursor: grab; user-select: none; }
.track-area:active { cursor: grabbing; }
.track-bg { height: 16rpx; background: #E5E7EB; border-radius: 8rpx; position: relative; }
.track-fill { position: absolute; left: 0; top: 0; height: 100%; background: linear-gradient(90deg,#E8852A,#2BAF7E); border-radius: 8rpx; transition: width .05s; }
.track-tick { position: absolute; top: 50%; transform: translateY(-50%); width: 6rpx; height: 6rpx; background: rgba(255,255,255,.6); border-radius: 50%; }
.thumb { position: absolute; top: 50%; transform: translate(-50%,-50%); z-index: 10; }
.thumb-circle { width: 88rpx; height: 88rpx; background: #2E75B6; border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 4rpx 20rpx rgba(46,117,182,.45); }
.thumb-text { font-size: 20rpx; color: #fff; font-weight: 700; }
.damping-hint { display: block; text-align: center; font-size: 22rpx; color: #9CA3AF; margin-top: 20rpx; }

/* 结果卡片 */
.result-card { margin: 0 32rpx; background: #fff; border-radius: 24rpx; padding: 32rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.06); position: relative; overflow: hidden; }
.result-title-row { display: flex; flex-direction: row; align-items: center; gap: 16rpx; margin-bottom: 28rpx; }
.result-title { font-size: 30rpx; font-weight: 700; color: #1A1A2E; }
.mini-spinner { width: 36rpx; height: 36rpx; border: 3rpx solid #D5E8F0; border-top-color: #2E75B6; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.result-row { display: flex; flex-direction: row; align-items: center; justify-content: space-between; padding: 20rpx 0; border-bottom: 2rpx solid #F3F4F6; }
.result-row:last-child { border-bottom: none; }
.result-row-hl { background: #FFF3E8; margin: 0 -32rpx; padding: 20rpx 32rpx; }
.result-label-col { display: flex; flex-direction: row; align-items: center; gap: 12rpx; }
.result-icon { font-size: 32rpx; }
.result-name { font-size: 28rpx; color: #6B7280; }
.result-val-col { display: flex; flex-direction: row; align-items: center; gap: 12rpx; }
.result-val-col-v { flex-direction: column; align-items: flex-end; gap: 10rpx; }
.val-before { font-size: 28rpx; color: #9CA3AF; text-decoration: line-through; }
.val-arrow { font-size: 24rpx; color: #9CA3AF; }

/* 月供比 */
.ratio-nums { display: flex; flex-direction: row; align-items: center; gap: 12rpx; }
.ratio-hint { font-size: 20rpx; color: #9CA3AF; }

/* 底部 */
.bottom { margin-top: auto; padding: 24rpx 32rpx; padding-bottom: calc(32rpx + env(safe-area-inset-bottom)); }
.disclaimer { display: block; font-size: 22rpx; color: #9CA3AF; text-align: center; line-height: 1.6; margin-bottom: 20rpx; }
.cta-btn { width: 100%; height: 96rpx; background: linear-gradient(135deg,#2E75B6,#1a5fa0); color: #fff; font-size: 32rpx; font-weight: 700; border-radius: 48rpx; border: none; box-shadow: 0 8rpx 24rpx rgba(46,117,182,.3); }
</style>
