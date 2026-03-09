<template>
  <view class="page">
    <FunnelNavBar title="分析报告" />
    <!-- 加载态 -->
    <view class="loading-wrap" v-if="profileStore.loading">
      <view class="spinner"></view>
      <text class="loading-text">正在分析你的财务结构...</text>
    </view>

    <!-- 错误态 -->
    <view class="error-wrap" v-else-if="profileStore.error">
      <text class="error-text">{{ profileStore.error }}</text>
      <button class="retry-btn" @tap="profileStore.loadProfile()">重试</button>
    </view>

    <!-- 主内容 -->
    <scroll-view class="content" scroll-y v-else>
      <!-- 核心冲击区：三年多付利息 -->
      <view class="impact-block">
        <text class="impact-context">如果维持当前结构</text>
        <text class="impact-label">3 年将多支付</text>
        <view class="impact-number">
          <AnimatedNumber
            :value="profileStore.threeYearExtraInterest"
            :duration="2000"
            prefix="¥"
            :formatter="numFmt"
            color="#E8852A"
            :fontSize="96"
            fontWeight="800"
          />
        </view>
        <text class="impact-analogy" v-if="analogyMonths > 0">
          相当于
          <text class="analogy-num">{{ analogyMonths }}</text>
          个月房租
        </text>
      </view>

      <!-- 三个对比卡片 -->
      <view class="cards-wrap">
        <!-- 卡片 A：利率对比 -->
        <view class="card">
          <text class="card-title">利率对比</text>
          <view class="card-row">
            <view class="metric-col">
              <text class="metric-label">你的综合利率</text>
              <text class="metric-val metric-accent">{{ fmt1(profileStore.weightedApr) }}%</text>
            </view>
            <view class="vsep"></view>
            <view class="metric-col">
              <text class="metric-label">市场参考值</text>
              <text class="metric-val metric-primary">{{ MARKET_RATE }}%</text>
            </view>
          </view>
          <view class="bar-track">
            <view class="bar-fill" :style="{ width: aprBarWidth + '%' }"></view>
          </view>
          <view class="bar-labels">
            <text class="bar-label-l">0%</text>
            <text class="bar-label-m" :style="{ left: MARKET_RATE / 36 * 100 + '%' }">市场均值</text>
            <text class="bar-label-r">36%</text>
          </view>
        </view>

        <!-- 卡片 B：月供压力 -->
        <view class="card">
          <text class="card-title">月供压力</text>
          <view class="card-row">
            <view class="metric-col">
              <text class="metric-label">月供占收入</text>
              <text class="metric-val metric-accent">{{ fmt1(paymentRatioPct) }}%</text>
            </view>
            <view class="vsep"></view>
            <view class="metric-col">
              <text class="metric-label">健康线</text>
              <text class="metric-val metric-primary">{{ HEALTHY_RATIO }}%</text>
            </view>
          </view>
          <view class="bar-track">
            <view
              class="bar-fill"
              :class="paymentRatioPct > HEALTHY_RATIO ? 'bar-fill-heavy' : 'bar-fill-ok'"
              :style="{ width: Math.min(paymentRatioPct, 100) + '%' }"
            ></view>
            <view class="bar-marker" :style="{ left: HEALTHY_RATIO + '%' }"></view>
          </view>
          <text class="bar-hint" v-if="paymentRatioPct > HEALTHY_RATIO">
            月供占比偏高，有优化空间
          </text>
        </view>

        <!-- 卡片 C：债务结构 -->
        <view class="card">
          <text class="card-title">债务结构</text>
          <view class="count-row">
            <view class="count-col">
              <text class="count-num">{{ profile?.debtCount || 0 }}</text>
              <text class="count-label">总债务笔数</text>
            </view>
            <view class="vsep"></view>
            <view class="count-col">
              <text class="count-num count-accent">{{ highInterestCount }}</text>
              <text class="count-label">高息笔数</text>
            </view>
          </view>
          <text class="card-note" v-if="highInterestCount > 0">
            年化利率超过 24% 的债务是优化重点
          </text>
        </view>
      </view>

      <!-- 单笔债务提示 -->
      <view class="info-banner" v-if="(profile?.debtCount || 0) === 1">
        <text class="info-icon">ℹ</text>
        <text class="info-text">
          目前仅有一笔债务，优化空间取决于后续债务结构。录入更多债务可获得更精准的分析。
        </text>
      </view>

      <!-- 信息提示（蓝色，非警告风格） -->
      <view class="info-banner">
        <text class="info-icon">ℹ</text>
        <text class="info-text">
          以上分析基于你录入的 {{ profile?.debtCount || 0 }} 笔债务，仅供参考，不构成借贷建议。
        </text>
      </view>

      <!-- 报告声明 -->
      <view class="report-disclaimer">
        <text class="disclaimer-text">
          本报告仅供个人参考，不构成金融建议，请勿作为申请材料使用。
        </text>
      </view>

      <!-- CTA 区域：⚠️ 严格遵守 F-12，绝无"申请"按钮 -->
      <view class="cta-wrap">
        <button class="cta-btn" @tap="handleGoOptimization">
          看看我的优化空间 →
        </button>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, onMounted } from 'vue'
import { useProfileStore } from '../../stores/profile.js'
import { useFunnelStore } from '../../stores/funnel.js'
import AnimatedNumber from '../../components/AnimatedNumber.vue'
import FunnelNavBar from '../../components/FunnelNavBar.vue'

const profileStore = useProfileStore()
const funnelStore = useFunnelStore()
const profile = computed(() => profileStore.profile)

// 常量（对应 application.yml 配置的市场均值）
const MARKET_RATE = 18   // 市场均值 APR %
const HEALTHY_RATIO = 30 // 月供收入比健康线 %
const MONTHLY_RENT = 3000 // 参考月租

function numFmt(v) { return Math.round(v).toLocaleString('zh-CN') }
function fmt1(v) { return Number(v).toFixed(1) }

// 月供收入比（转为百分比）
const paymentRatioPct = computed(() => {
  const r = profile.value?.debtIncomeRatio || 0
  return r > 1 ? r : r * 100
})

// 相当于几个月房租
const analogyMonths = computed(() => {
  const extra = profileStore.threeYearExtraInterest
  if (!extra || extra <= 0) return 0
  return Math.round(extra / MONTHLY_RENT)
})

// APR 进度条宽度（相对 36% 满值）
const aprBarWidth = computed(() => Math.min((profileStore.weightedApr / 36) * 100, 100))

// 高息债务笔数（APR > 24%）
const highInterestCount = computed(() => profile.value?.highInterestDebtCount || 0)

function handleGoOptimization() {
  const score = profileStore.score
  funnelStore.advanceStep(5)
  uni.navigateTo({ url: `/pages/page5-optimization/index?score=${score}` })
}

onMounted(() => {
  if (!profile.value) profileStore.loadProfile()
})
</script>

<style scoped>
.page { min-height: 100vh; background: #F8FAFE; display: flex; flex-direction: column; }

/* 加载 / 错误 */
.loading-wrap,.error-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 24rpx; padding: 48rpx; }
.spinner { width: 80rpx; height: 80rpx; border: 6rpx solid #D5E8F0; border-top-color: #2E75B6; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.loading-text,.error-text { font-size: 28rpx; color: #6B7280; text-align: center; }
.retry-btn { padding: 16rpx 48rpx; background: #2E75B6; color: #fff; border-radius: 40rpx; font-size: 28rpx; border: none; }

.content { flex: 1; }

/* 冲击区 */
.impact-block { background: #fff; padding: 56rpx 32rpx 48rpx; display: flex; flex-direction: column; align-items: center; }
.impact-context { display: block; font-size: 26rpx; color: #6B7280; margin-bottom: 8rpx; }
.impact-label { display: block; font-size: 34rpx; font-weight: 600; color: #1A1A2E; margin-bottom: 20rpx; }
.impact-number { display: flex; align-items: center; justify-content: center; margin-bottom: 20rpx; }
.impact-analogy { font-size: 28rpx; color: #6B7280; }
.analogy-num { font-size: 36rpx; font-weight: 800; color: #E8852A; }

/* 卡片区 */
.cards-wrap { padding: 24rpx 32rpx; display: flex; flex-direction: column; gap: 16rpx; }
.card { background: #fff; border-radius: 20rpx; padding: 28rpx; box-shadow: 0 2rpx 12rpx rgba(0,0,0,.06); }
.card-title { display: block; font-size: 26rpx; color: #6B7280; font-weight: 600; margin-bottom: 20rpx; }
.card-row { display: flex; flex-direction: row; align-items: center; gap: 24rpx; margin-bottom: 20rpx; }
.metric-col { flex: 1; display: flex; flex-direction: column; align-items: center; }
.metric-label { font-size: 24rpx; color: #9CA3AF; margin-bottom: 8rpx; }
.metric-val { font-size: 40rpx; font-weight: 800; }
.metric-accent { color: #E8852A; }
.metric-primary { color: #2E75B6; }
.vsep { width: 2rpx; height: 60rpx; background: #E5E7EB; }

/* 进度条 */
.bar-track { height: 12rpx; background: #E5E7EB; border-radius: 6rpx; position: relative; overflow: visible; }
.bar-fill { height: 100%; border-radius: 6rpx; background: linear-gradient(90deg,#E8852A,#f0a050); transition: width .8s ease; }
.bar-fill-ok { background: linear-gradient(90deg,#2BAF7E,#52c49a); }
.bar-fill-heavy { background: linear-gradient(90deg,#E8852A,#f0a050); }
.bar-marker { position: absolute; top: -4rpx; width: 4rpx; height: 20rpx; background: #2E75B6; border-radius: 2rpx; transform: translateX(-50%); }
.bar-labels { display: flex; flex-direction: row; justify-content: space-between; margin-top: 8rpx; position: relative; }
.bar-label-l,.bar-label-r { font-size: 22rpx; color: #9CA3AF; }
.bar-label-m { position: absolute; transform: translateX(-50%); font-size: 22rpx; color: #2E75B6; }
.bar-hint { display: block; font-size: 24rpx; color: #6B7280; margin-top: 12rpx; }

/* 笔数 */
.count-row { display: flex; flex-direction: row; align-items: center; justify-content: center; gap: 40rpx; margin-bottom: 12rpx; }
.count-col { display: flex; flex-direction: column; align-items: center; }
.count-num { font-size: 64rpx; font-weight: 800; color: #1A1A2E; }
.count-accent { color: #E8852A; }
.count-label { font-size: 24rpx; color: #9CA3AF; margin-top: 4rpx; }
.card-note { display: block; font-size: 24rpx; color: #6B7280; }

/* 信息横幅（蓝色信息风格，非警告） */
.info-banner { margin: 0 32rpx 24rpx; background: #D5E8F0; border-radius: 16rpx; padding: 20rpx 24rpx; display: flex; flex-direction: row; align-items: flex-start; gap: 12rpx; }
.info-icon { font-size: 28rpx; color: #2E75B6; flex-shrink: 0; }
.info-text { font-size: 24rpx; color: #2E75B6; line-height: 1.6; flex: 1; }

/* 报告声明 */
.report-disclaimer { margin: 0 32rpx 24rpx; text-align: center; }
.disclaimer-text { font-size: 22rpx; color: #9CA3AF; line-height: 1.6; }

/* CTA — 绝对没有"申请"按钮（F-12） */
.cta-wrap { padding: 0 32rpx 56rpx; padding-bottom: calc(56rpx + env(safe-area-inset-bottom)); }
.cta-btn { width: 100%; height: 96rpx; background: linear-gradient(135deg,#2E75B6,#1a5fa0); color: #fff; font-size: 32rpx; font-weight: 700; border-radius: 48rpx; border: none; box-shadow: 0 8rpx 24rpx rgba(46,117,182,.3); }
</style>
