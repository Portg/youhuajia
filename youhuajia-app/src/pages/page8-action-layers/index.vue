<template>
  <view class="page8">
    <ProgressBar :current="8" :total="9" />

    <view class="page-header">
      <text class="page-title">分步准备，稳步推进</text>
      <LayerProgress :current="completedCount" :total="3" />
    </view>

    <view class="layers">
      <!-- Layer 1: 生成优化报告 -->
      <ActionLayer
        :layer="1"
        title="生成你的优化报告"
        :status="layer1Status"
        :active="true"
        :locked="false"
        :loading="layer1Loading"
        loading-text="正在为你生成优化报告，AI 分析中..."
        action-text="生成报告"
        @action="onLayer1"
        @skip="onSkip"
      >
        <template #result>
          <view v-if="layer1.result" class="result-view">
            <text v-if="layer1.result.aiSummary" class="ai-summary">
              {{ layer1.result.aiSummary }}
            </text>
            <!-- fallback -->
            <template v-else-if="layer1.result.documentList">
              <view v-for="(doc, i) in layer1.result.documentList" :key="i" class="doc-item">
                <text class="doc-dot">·</text>
                <text class="doc-text">{{ doc }}</text>
              </view>
            </template>
            <!-- 优先还款顺序 -->
            <view v-if="layer1.result.priorityList && layer1.result.priorityList.length" class="priority-list">
              <text class="priority-title">建议优先处理</text>
              <view v-for="(item, i) in layer1.result.priorityList" :key="i" class="priority-item">
                <text class="priority-rank">{{ item.rank || (i + 1) }}</text>
                <view class="priority-info">
                  <text class="priority-creditor">{{ item.creditor }}</text>
                  <text v-if="item.reason" class="priority-reason">{{ item.reason }}</text>
                </view>
              </view>
            </view>
            <!-- 行动步骤 -->
            <view v-if="actionSteps.length" class="action-steps">
              <text class="action-steps-title">行动步骤</text>
              <view v-for="(step, i) in actionSteps" :key="i" class="action-step">
                <text class="step-number">{{ i + 1 }}</text>
                <text class="step-text">{{ step }}</text>
              </view>
            </view>
            <!-- 风险提示 -->
            <view v-if="layer1.result.riskWarnings && layer1.result.riskWarnings.length" class="risk-warnings">
              <view v-for="(w, i) in layer1.result.riskWarnings" :key="i" class="warning-item">
                <text class="warning-text">{{ w }}</text>
              </view>
            </view>
          </view>
        </template>
      </ActionLayer>

      <!-- Layer 2: 准备申请材料 -->
      <ActionLayer
        :layer="2"
        title="准备申请材料"
        :status="layer2Status"
        :active="layer1.completed"
        :locked="!layer1.completed"
        :loading="layer2Loading"
        action-text="查看材料清单"
        @action="onLayer2"
        @skip="onSkip"
      >
        <template #result>
          <view v-if="layer2.result" class="material-result">
            <view v-for="(group, gi) in layer2.result.materialGroups" :key="gi" class="material-group">
              <text class="material-group-title">{{ group.title }}</text>
              <view v-for="(item, ii) in group.items" :key="ii" class="material-item">
                <text class="material-check">☐</text>
                <view class="material-detail">
                  <text class="material-name">{{ item.name }}</text>
                  <text v-if="item.tip" class="material-tip">{{ item.tip }}</text>
                </view>
              </view>
            </view>
            <text class="material-time">预计准备时间：{{ layer2.result.estimatedPrepTime }}</text>
          </view>
        </template>
      </ActionLayer>

      <!-- Layer 3: 预审通过概率 -->
      <ActionLayer
        :layer="3"
        title="预审一下通过概率"
        :status="layer3Status"
        :active="layer2.completed"
        :locked="!layer2.completed"
        :loading="layer3Loading"
        loading-text="正在评估预审通过概率..."
        action-text="开始预审"
        @action="onLayer3"
        @skip="onSkip"
      >
        <template #result>
          <view v-if="layer3.result" class="preaudit-result">
            <view class="preaudit-row">
              <text class="preaudit-label">预估通过概率</text>
              <text class="preaudit-value">
                {{ layer3.result.preApprovalResult?.probability || 72 }}%
              </text>
            </view>
            <text
              v-for="(s, i) in (layer3.result.suggestions || [])"
              :key="i"
              class="suggestion-item"
            >· {{ s }}</text>
          </view>
        </template>
      </ActionLayer>

      <!-- Layer 4: 确认提交申请（V2.0，不可操作） -->
      <ActionLayer
        :layer="4"
        title="确认提交申请"
        status="disabled"
        :active="false"
        :locked="false"
        badge="即将上线"
      />
    </view>

    <!-- CTA -->
    <view class="cta-area">
      <YouhuaButton
        text="查看我的行动计划"
        type="primary"
        :disabled="completedCount < 1"
        @click="goToCompanion"
      />
    </view>

    <SafeAreaBottom />

    <view v-if="errorMsg" class="error-toast">
      <text class="error-text">{{ errorMsg }}</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useFunnelStore } from '../../stores/funnel'
import { useDebtStore } from '../../stores/debt'
import { generateReport } from '../../api/report'
import { estimatePreAudit as apiPreAudit } from '../../api/engine'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import ActionLayer from './components/ActionLayer.vue'
import LayerProgress from './components/LayerProgress.vue'
import { estimatePreAudit } from './preaudit'

const funnelStore = useFunnelStore()
const debtStore = useDebtStore()

// 评分<60 时重定向到低分路径
onMounted(() => {
  if (funnelStore.isLowScore) {
    uni.redirectTo({ url: '/pages/low-score/improvement-plan' })
  }
})

const layer1Loading = ref(false)
const layer2Loading = ref(false)
const layer3Loading = ref(false)
const errorMsg = ref('')

const layer1 = computed(() => funnelStore.actionLayers.layer1)
const layer2 = computed(() => funnelStore.actionLayers.layer2)
const layer3 = computed(() => funnelStore.actionLayers.layer3)
const completedCount = computed(() => funnelStore.completedLayerCount)

// 从 Layer 1 报告中提取行动步骤
const actionSteps = computed(() => {
  const plan = layer1.value.result?.actionPlan
  if (plan && plan.steps) return plan.steps
  return []
})

const layer1Status = computed(() => {
  if (layer1.value.completed) return 'completed'
  if (layer1Loading.value) return 'in_progress'
  return 'pending'
})

const layer2Status = computed(() => {
  if (layer2.value.completed) return 'completed'
  if (layer2Loading.value) return 'in_progress'
  return 'pending'
})

const layer3Status = computed(() => {
  if (layer3.value.completed) return 'completed'
  if (layer3Loading.value) return 'in_progress'
  return 'pending'
})

// 本地 fallback 数据（后端不可用时使用）
const FALLBACK_LAYER1 = {
  reportId: 'local-demo',
  documentList: [
    '身份证正反面照片',
    '近 6 个月银行流水（收入流水）',
    '在还贷款的还款记录截图',
    '工作证明或劳动合同',
    '征信报告（人行官网可免费获取）',
  ],
  estimatedPrepTime: '2-3 天',
}

const FALLBACK_LAYER2 = {
  materialGroups: [
    {
      title: '基础身份材料',
      items: [
        { name: '身份证正反面照片', tip: '确保照片清晰、四角完整' },
        { name: '户口本首页及本人页', tip: '如已迁出可用迁出证明代替' },
      ],
    },
    {
      title: '收入证明',
      items: [
        { name: '近 6 个月银行流水', tip: '需体现工资收入，可去银行柜台打印' },
        { name: '工作证明或劳动合同', tip: '加盖公章的在职证明' },
      ],
    },
    {
      title: '债务相关',
      items: [
        { name: '在还贷款的还款记录截图', tip: '展示按时还款的良好记录' },
        { name: '征信报告', tip: '人行征信中心官网可免费获取，每年 2 次' },
      ],
    },
  ],
  estimatedPrepTime: '2-3 天',
}

async function onLayer1() {
  layer1Loading.value = true
  errorMsg.value = ''
  try {
    const res = await generateReport()
    // 后端返回 name: "reports/{id}"，解析出数字 id
    const reportId = res.name ? res.name.split('/').pop() : (res.reportId || res.id)
    funnelStore.completeLayer1(reportId)
    funnelStore.actionLayers.layer1.result = { ...res, reportId }
  } catch (e) {
    console.warn('[page8] generateReport failed:', e?.message || e)
    funnelStore.completeLayer1(FALLBACK_LAYER1.reportId)
    funnelStore.actionLayers.layer1.result = FALLBACK_LAYER1
  } finally {
    layer1Loading.value = false
  }
}

// 根据用户债务类型生成材料清单
function buildMaterialGroups() {
  const debts = debtStore.debts || []
  const groups = [
    {
      title: '基础身份材料',
      items: [
        { name: '身份证正反面照片', tip: '确保照片清晰、四角完整' },
        { name: '户口本首页及本人页', tip: '如已迁出可用迁出证明代替' },
      ],
    },
    {
      title: '收入证明',
      items: [
        { name: '近 6 个月银行流水', tip: '需体现工资收入，可去银行柜台打印' },
        { name: '工作证明或劳动合同', tip: '加盖公章的在职证明' },
      ],
    },
  ]

  // 根据债务类型动态添加材料（枚举值与后端对齐）
  const debtItems = []
  const hasCreditCard = debts.some(d => d.debtType === 'CREDIT_CARD')
  const hasConsumerLoan = debts.some(d => d.debtType === 'CONSUMER_LOAN')
  const hasMortgage = debts.some(d => d.debtType === 'MORTGAGE')
  const hasBusiness = debts.some(d => d.debtType === 'BUSINESS_LOAN')

  debtItems.push({ name: '征信报告', tip: '人行征信中心官网可免费获取，每年 2 次' })
  debtItems.push({ name: '在还贷款的还款记录截图', tip: '展示按时还款的良好记录' })
  if (hasCreditCard) {
    debtItems.push({ name: '信用卡近3期完整账单', tip: '含已用额度、最低还款额、还款日等信息' })
  }
  if (hasConsumerLoan) {
    debtItems.push({ name: '消费贷/网贷平台借款合同截图', tip: '含利率、期限等关键信息' })
  }
  if (hasMortgage) {
    debtItems.push({ name: '房产证复印件和贷款合同', tip: '确保证件在有效期内' })
  }
  if (hasBusiness) {
    debtItems.push({ name: '营业执照副本', tip: '需在有效期内' })
    debtItems.push({ name: '经营流水或纳税证明', tip: '近 6 个月' })
  }

  groups.push({ title: '债务相关', items: debtItems })
  return groups
}

async function onLayer2() {
  layer2Loading.value = true
  errorMsg.value = ''
  try {
    await new Promise(resolve => setTimeout(resolve, 600)) // 模拟整理
    const materialGroups = buildMaterialGroups()
    const result = { materialGroups, estimatedPrepTime: '2-3 天' }
    funnelStore.completeLayer2()
    funnelStore.actionLayers.layer2.result = result
  } finally {
    layer2Loading.value = false
  }
}

async function onLayer3() {
  layer3Loading.value = true
  errorMsg.value = ''
  try {
    // 调用后端预审引擎（基于 preaudit.meta.yml 规则策略）
    const res = await apiPreAudit()
    funnelStore.completeLayer3()
    funnelStore.actionLayers.layer3.result = {
      preApprovalResult: { probability: res.probability },
      suggestions: res.suggestions,
    }
  } catch (e) {
    console.warn('[page8] preaudit API failed, using local fallback:', e?.message || e)
    // 本地 fallback：使用前端规则估算
    const result = estimatePreAudit({
      score: funnelStore.score,
      monthlyPayment: funnelStore.monthlyPayment,
      monthlyIncome: funnelStore.monthlyIncome,
      debts: debtStore.debts,
    })
    funnelStore.completeLayer3()
    funnelStore.actionLayers.layer3.result = {
      preApprovalResult: { probability: result.probability },
      suggestions: result.suggestions,
    }
  } finally {
    layer3Loading.value = false
  }
}

function onSkip() {
  uni.showModal({
    title: '暂停一下',
    content: '你可以随时回来继续，进度已保存',
    showCancel: false,
    confirmText: '好的',
    confirmColor: '#2E75B6',
  })
}

function goToCompanion() {
  funnelStore.advanceStep(9)
  uni.navigateTo({ url: '/pages/page9-companion/index' })
}
</script>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page8 {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: 140rpx;
}

.page-header {
  background-color: $surface;
  padding: $spacing-lg $spacing-xl $spacing-md;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.page-title {
  display: block;
  font-size: $font-lg;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.layers {
  padding: $spacing-md $spacing-xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

.ai-summary {
  display: block;
  font-size: $font-sm;
  color: #374151;
  line-height: 1.6;
  margin-bottom: $spacing-sm;
}

.doc-item {
  display: flex;
  gap: 8rpx;
  margin-bottom: 8rpx;
}

.doc-dot {
  color: $primary;
  font-size: $font-md;
}

.doc-text {
  font-size: $font-sm;
  color: #374151;
  line-height: 1.4;
  flex: 1;
}

.doc-time {
  display: block;
  font-size: $font-sm;
  color: $positive;
  margin-top: 8rpx;
  font-weight: 500;
}

.priority-list {
  margin-top: $spacing-md;
  padding-top: $spacing-sm;
  border-top: 1rpx solid $divider;
}

.priority-title {
  display: block;
  font-size: $font-sm;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-sm;
}

.priority-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.priority-rank {
  width: 40rpx;
  height: 40rpx;
  border-radius: 50%;
  background: $primary-light;
  color: $primary;
  font-size: $font-xs;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.priority-info {
  flex: 1;
}

.priority-creditor {
  display: block;
  font-size: $font-sm;
  color: $text-primary;
  font-weight: 500;
}

.priority-reason {
  display: block;
  font-size: $font-xs;
  color: $text-secondary;
  margin-top: 4rpx;
}

.action-steps {
  margin-top: $spacing-md;
  padding-top: $spacing-sm;
  border-top: 1rpx solid $divider;
}

.action-steps-title {
  display: block;
  font-size: $font-sm;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-sm;
}

.action-step {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.step-number {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: $positive;
  color: #fff;
  font-size: $font-xs;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.step-text {
  flex: 1;
  font-size: $font-sm;
  color: #374151;
  line-height: 1.5;
}

.risk-warnings {
  margin-top: $spacing-md;
  padding: $spacing-sm;
  background: #FFF7ED;
  border-radius: $radius-sm;
}

.warning-item {
  margin-bottom: 4rpx;
}

.warning-text {
  font-size: $font-xs;
  color: #92400E;
  line-height: 1.5;
}

.material-result {
  padding: 4rpx 0;
}

.material-group {
  margin-bottom: $spacing-md;
}

.material-group-title {
  display: block;
  font-size: $font-sm;
  font-weight: 600;
  color: $text-primary;
  margin-bottom: $spacing-sm;
}

.material-item {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.material-check {
  color: $text-secondary;
  font-size: $font-md;
  flex-shrink: 0;
}

.material-detail {
  flex: 1;
}

.material-name {
  display: block;
  font-size: $font-sm;
  color: $text-primary;
}

.material-tip {
  display: block;
  font-size: $font-xs;
  color: $text-secondary;
  margin-top: 4rpx;
}

.material-time {
  display: block;
  font-size: $font-sm;
  color: $positive;
  font-weight: 500;
  margin-top: $spacing-sm;
  padding-top: $spacing-sm;
  border-top: 1rpx solid $divider;
}

.preaudit-result {
  padding: 4rpx 0;
}

.preaudit-row {
  display: flex;
  align-items: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-sm;
}

.preaudit-label {
  font-size: $font-sm;
  color: $text-secondary;
}

.preaudit-value {
  font-size: 40rpx;
  font-weight: 700;
  color: $positive;
}

.suggestion-item {
  display: block;
  font-size: $font-sm;
  color: #374151;
  line-height: 1.5;
  margin-bottom: 4rpx;
}

.cta-area {
  padding: 0 $spacing-xl $spacing-lg;
}

.error-toast {
  position: fixed;
  bottom: 160rpx;
  left: 50%;
  transform: translateX(-50%);
  background-color: $text-primary;
  border-radius: $radius-sm;
  padding: $spacing-sm $spacing-lg;
}

.error-text {
  color: $surface;
  font-size: $font-sm;
}
</style>
