<template>
  <view class="page">
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

      <!-- 滑轨容器（用于手势捕获） -->
      <view
        class="track-area"
        id="slider-track-area"
        @touchstart="onTouchStart"
        @touchmove.stop="onTouchMove"
        @touchend="onTouchEnd"
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
        <view class="mini-spinner" v-if="profileStore.simulationLoading"></view>
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
            color="#2BAF7E"
            :fontSize="32"
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
            :value="threeYearSaving"
            prefix="¥"
            :formatter="n => Math.round(n).toLocaleString('zh-CN')"
            color="#E8852A"
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
              :color="simulatedPaymentRatio < 30 ? '#2BAF7E' : '#E8852A'"
              :fontSize="32"
              :duration="400"
            />
          </view>
          <!-- 月供比进度条 -->
          <view class="ratio-bar">
            <view
              class="ratio-fill"
              :class="simulatedPaymentRatio < 30 ? 'ratio-ok' : 'ratio-high'"
              :style="{ width: Math.min(simulatedPaymentRatio, 100) + '%' }"
            ></view>
            <!-- 健康线标记 -->
            <view class="ratio-marker" style="left: 30%">
              <text class="ratio-marker-label">健康线</text>
            </view>
          </view>
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
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useProfileStore } from '../../src/stores/profile.js'
import AnimatedNumber from '../../src/components/AnimatedNumber.vue'

const profileStore = useProfileStore()

// 从画像获取当前 APR 和最优 APR
const currentApr = computed(() => profileStore.weightedApr || 24)
const bestApr = computed(() => profileStore.profile?.bestPossibleApr || 6)

// 用户拖动的目标 APR（初始 = 当前 APR）
const targetApr = ref(24)

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

// 触摸状态
let trackW = 0, trackLeft = 0, startX = 0, startFillPct = 0
let isTouching = false

function resolveTrackRect() {
  return new Promise((resolve) => {
    uni.createSelectorQuery()
      .select('#slider-track-area')
      .boundingClientRect(r => resolve(r))
      .exec()
  })
}

async function onTouchStart(e) {
  isTouching = true
  startX = e.touches[0].clientX
  const rect = await resolveTrackRect()
  if (rect) { trackW = rect.width; trackLeft = rect.left }
  startFillPct = fillPct.value / 100
}

function onTouchMove(e) {
  if (!isTouching || trackW === 0) return
  const dx = e.touches[0].clientX - startX
  let raw = Math.max(0, Math.min(1, startFillPct + dx / trackW))
  const damped = applyDamping(raw)
  const range = currentApr.value - bestApr.value
  targetApr.value = Math.max(bestApr.value, Math.min(currentApr.value, currentApr.value - damped * range))
  scheduleSimulate()
}

function onTouchEnd() { isTouching = false }

// debounce 300ms 调后端
let simTimer = null
function scheduleSimulate() {
  if (simTimer) clearTimeout(simTimer)
  simTimer = setTimeout(() => profileStore.doSimulateRate(targetApr.value), 300)
}

onUnmounted(() => { if (simTimer) clearTimeout(simTimer) })

// 结果数据（后端结果优先，否则前端估算）
const currentMonthlyPayment = computed(() => profileStore.profile?.monthlyPayment || 0)

const simulatedMonthlyPayment = computed(() => {
  // API 返回字段: targetMonthly (arch spec)
  if (profileStore.simulationResult?.targetMonthly) {
    return profileStore.simulationResult.targetMonthly
  }
  // 前端粗估（按利率比例，仅用于动画显示）
  const ratio = currentApr.value > 0 ? targetApr.value / currentApr.value : 1
  return currentMonthlyPayment.value * (0.4 + ratio * 0.6)
})

const threeYearSaving = computed(() => {
  // API 返回字段: threeYearSaving (arch spec)
  if (profileStore.simulationResult?.threeYearSaving) {
    return profileStore.simulationResult.threeYearSaving
  }
  return Math.max(0, (currentMonthlyPayment.value - simulatedMonthlyPayment.value) * 36)
})

const currentPaymentRatio = computed(() => {
  // API 返回字段: currentRatio (arch spec)
  if (profileStore.simulationResult?.currentRatio != null) {
    const r = profileStore.simulationResult.currentRatio
    return r > 1 ? r : r * 100
  }
  const r = profileStore.debtIncomeRatio || 0
  return r > 1 ? r : r * 100
})

const simulatedPaymentRatio = computed(() => {
  // API 返回字段: targetRatio (arch spec)
  if (profileStore.simulationResult?.targetRatio != null) {
    const r = profileStore.simulationResult.targetRatio
    return r > 1 ? r : r * 100
  }
  if (currentMonthlyPayment.value <= 0) return 0
  return currentPaymentRatio.value * (simulatedMonthlyPayment.value / currentMonthlyPayment.value)
})

// 格式化
function fmt1(v) { return Number(v).toFixed(1) }
function fmtInt(v) { return '¥' + Math.round(Number(v)).toLocaleString('zh-CN') }

function goRisk() {
  uni.navigateTo({ url: '/pages/page7-risk-assessment/index' })
}

onMounted(async () => {
  if (!profileStore.profile) await profileStore.loadProfile()
  targetApr.value = currentApr.value
  scheduleSimulate()
})
</script>

<style scoped>
.page { min-height: 100vh; background: linear-gradient(168deg, #F0F4FA 0%, #FAF8F5 35%, #F5F7FA 100%); display: flex; flex-direction: column; }

/* 顶部 */
.page-top { padding: 48rpx 40rpx 24rpx; background: rgba(255,255,255,0.72); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px); }
.page-title { display: block; font-size: 44rpx; font-weight: 900; color: #0F172A; letter-spacing: -1rpx; }
.page-sub { display: block; font-size: 26rpx; color: #64748B; margin-top: 8rpx; }

/* APR 标签 */
.apr-header { display: flex; flex-direction: row; align-items: center; padding: 24rpx 40rpx; background: rgba(255,255,255,0.72); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px); margin-bottom: 2rpx; }
.apr-item { flex: 1; display: flex; flex-direction: column; align-items: flex-start; }
.apr-item-right { align-items: flex-end; }
.apr-item-label { font-size: 22rpx; color: #94A3B8; margin-bottom: 4rpx; text-transform: uppercase; letter-spacing: 1rpx; }
.apr-item-val { font-size: 52rpx; font-weight: 900; letter-spacing: -2rpx; }
.apr-current { color: #D97B1A; }
.apr-target { color: #0FA968; transition: color .3s; }
.apr-arrow { display: flex; flex-direction: column; align-items: center; gap: 4rpx; }
.arrow-text { font-size: 36rpx; color: #94A3B8; }
.arrow-desc { font-size: 22rpx; color: #94A3B8; }

/* 滑块 */
.slider-wrap { background: #fff; margin: 20rpx 40rpx; border-radius: 28rpx; padding: 32rpx; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.04), 0 8rpx 32rpx rgba(15,23,42,.06); }
.tick-row { display: flex; flex-direction: row; justify-content: space-between; margin-bottom: 16rpx; }
.tick-num { font-size: 22rpx; color: #94A3B8; font-weight: 500; }
.tick-left { color: #D97B1A; }
.tick-right { color: #0FA968; }

.track-area { padding: 24rpx 0; }
.track-bg { height: 14rpx; background: #E8ECF1; border-radius: 7rpx; position: relative; }
.track-fill { position: absolute; left: 0; top: 0; height: 100%; background: linear-gradient(90deg, #D97B1A 0%, #0FA968 100%); border-radius: 7rpx; transition: width .05s; }
.track-tick { position: absolute; top: 50%; transform: translateY(-50%); width: 6rpx; height: 6rpx; background: rgba(255,255,255,.6); border-radius: 50%; }
.thumb { position: absolute; top: 50%; transform: translate(-50%,-50%); z-index: 10; }
.thumb-circle { width: 88rpx; height: 88rpx; background: linear-gradient(135deg, #3A9BDC, #1B6DB2); border-radius: 50%; display: flex; align-items: center; justify-content: center; box-shadow: 0 8rpx 32rpx rgba(27,109,178,.35); }
.thumb-text { font-size: 20rpx; color: #fff; font-weight: 700; }
.damping-hint { display: block; text-align: center; font-size: 22rpx; color: #94A3B8; margin-top: 20rpx; }

/* 结果卡片 */
.result-card { margin: 0 40rpx; background: #fff; border-radius: 28rpx; padding: 32rpx; box-shadow: 0 4rpx 12rpx rgba(15,23,42,.04), 0 8rpx 32rpx rgba(15,23,42,.06); position: relative; overflow: hidden; }
.result-title-row { display: flex; flex-direction: row; align-items: center; gap: 16rpx; margin-bottom: 28rpx; }
.result-title { font-size: 30rpx; font-weight: 700; color: #0F172A; }
.mini-spinner { width: 28rpx; height: 28rpx; border: 3rpx solid #E8ECF1; border-top-color: #1B6DB2; border-radius: 50%; animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.result-row { display: flex; flex-direction: row; align-items: center; justify-content: space-between; padding: 20rpx 0; border-bottom: 1rpx solid #F1F5F9; }
.result-row:last-child { border-bottom: none; }
.result-row-hl { background: rgba(217,123,26,0.06); margin: 0 -32rpx; padding: 20rpx 32rpx; border-radius: 20rpx; }
.result-label-col { display: flex; flex-direction: row; align-items: center; gap: 12rpx; }
.result-icon { font-size: 32rpx; }
.result-name { font-size: 28rpx; color: #64748B; }
.result-val-col { display: flex; flex-direction: row; align-items: center; gap: 12rpx; }
.result-val-col-v { flex-direction: column; align-items: flex-end; gap: 10rpx; }
.val-before { font-size: 28rpx; color: #94A3B8; text-decoration: line-through; }
.val-arrow { font-size: 24rpx; color: #94A3B8; }

/* 月供比进度条 */
.ratio-nums { display: flex; flex-direction: row; align-items: center; gap: 12rpx; }
.ratio-bar { width: 240rpx; height: 8rpx; background: #E8ECF1; border-radius: 4rpx; position: relative; overflow: visible; }
.ratio-fill { height: 100%; border-radius: 4rpx; transition: width .4s cubic-bezier(0.22,1,0.36,1); }
.ratio-ok { background: linear-gradient(90deg, #34D58C, #0FA968); }
.ratio-high { background: linear-gradient(90deg, #F2A94B, #D97B1A); }
.ratio-marker { position: absolute; top: -5rpx; width: 4rpx; height: 18rpx; background: #1B6DB2; transform: translateX(-50%); border-radius: 2rpx; }
.ratio-marker-label { position: absolute; top: -30rpx; left: 50%; transform: translateX(-50%); font-size: 18rpx; color: #1B6DB2; white-space: nowrap; font-weight: 500; }

/* 底部 */
.bottom { margin-top: auto; padding: 24rpx 40rpx; padding-bottom: calc(32rpx + env(safe-area-inset-bottom)); }
.disclaimer { display: block; font-size: 22rpx; color: #94A3B8; text-align: center; line-height: 1.65; margin-bottom: 20rpx; }
.cta-btn { width: 100%; height: 100rpx; background: linear-gradient(135deg, #3A9BDC 0%, #1B6DB2 50%, #134E82 100%); color: #fff; font-size: 34rpx; font-weight: 700; border-radius: 200rpx; border: none; box-shadow: 0 8rpx 32rpx rgba(27,109,178,.28); }
</style>
