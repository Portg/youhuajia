<template>
  <view class="page8">
    <ProgressBar :current="8" :total="9" />

    <view class="page-header">
      <text class="page-title">分步准备，稳步推进</text>
      <LayerProgress :current="completedCount" :total="3" />
    </view>

    <view class="layers">
      <!-- Layer 1: 看看申请需要准备什么 -->
      <ActionLayer
        :layer="1"
        title="看看申请需要准备什么"
        :status="layer1Status"
        :active="true"
        :locked="false"
        :loading="layer1Loading"
        action-text="生成资料清单"
        @action="onLayer1"
        @skip="onSkip"
      >
        <template v-if="layer1.result">
          <view v-for="(doc, i) in (layer1.result.documentList || [])" :key="i" class="doc-item">
            <text class="doc-dot">·</text>
            <text class="doc-text">{{ doc }}</text>
          </view>
          <text class="doc-time">
            预计准备时间：{{ layer1.result.estimatedPrepTime || '3 天' }}
          </text>
        </template>

        <template #result>
          <view v-if="layer1.result" class="result-view">
            <view v-for="(doc, i) in (layer1.result.documentList || [])" :key="i" class="doc-item">
              <text class="doc-dot">·</text>
              <text class="doc-text">{{ doc }}</text>
            </view>
            <text class="doc-time">预计准备：{{ layer1.result.estimatedPrepTime || '3 天' }}</text>
          </view>
        </template>
      </ActionLayer>

      <!-- Layer 2: 一键整理你的申请资料 -->
      <ActionLayer
        :layer="2"
        title="一键整理你的申请资料"
        :status="layer2Status"
        :active="layer1.completed"
        :locked="!layer1.completed"
        :loading="layer2Loading"
        action-text="一键整理资料"
        @action="onLayer2"
        @skip="onSkip"
      >
        <template #result>
          <text v-if="layer2.result" class="result-summary">
            {{ layer2.result.summary || '申请材料已整理完毕' }}
          </text>
        </template>
      </ActionLayer>

      <!-- Layer 3: 预审一下通过概率 -->
      <ActionLayer
        :layer="3"
        title="预审一下通过概率"
        :status="layer3Status"
        :active="layer2.completed"
        :locked="!layer2.completed"
        :loading="layer3Loading"
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
import { generateReport, getReport, exportReport } from '../../api/report'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import ActionLayer from './components/ActionLayer.vue'
import LayerProgress from './components/LayerProgress.vue'

const funnelStore = useFunnelStore()

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
  summary: '已根据您的债务情况整理了申请资料模板，包含收入证明、还款计划书和征信说明信。',
}

const FALLBACK_LAYER3 = {
  preApprovalResult: { probability: 72 },
  suggestions: [
    '建议优先偿还利率最高的信用卡债务',
    '保持近 3 个月无新增借款记录',
    '准备详细的收入证明可提高通过率',
  ],
}

async function onLayer1() {
  layer1Loading.value = true
  errorMsg.value = ''
  try {
    const res = await generateReport()
    funnelStore.completeLayer1(res.reportId || res.id)
    funnelStore.actionLayers.layer1.result = res
  } catch {
    // 后端不可用，使用本地 fallback
    funnelStore.completeLayer1(FALLBACK_LAYER1.reportId)
    funnelStore.actionLayers.layer1.result = FALLBACK_LAYER1
  } finally {
    layer1Loading.value = false
  }
}

// 判断是否为本地 fallback 的 reportId（非数字即为 fallback）
function isLocalReport(id) { return !id || isNaN(Number(id)) }

async function onLayer2() {
  layer2Loading.value = true
  errorMsg.value = ''
  try {
    const reportId = layer1.value.reportId
    if (isLocalReport(reportId)) throw new Error('local-fallback')
    const res = await getReport(reportId)
    funnelStore.completeLayer2()
    funnelStore.actionLayers.layer2.result = res
  } catch {
    funnelStore.completeLayer2()
    funnelStore.actionLayers.layer2.result = FALLBACK_LAYER2
  } finally {
    layer2Loading.value = false
  }
}

async function onLayer3() {
  layer3Loading.value = true
  errorMsg.value = ''
  try {
    const reportId = layer1.value.reportId
    if (isLocalReport(reportId)) throw new Error('local-fallback')
    const res = await exportReport(reportId)
    funnelStore.completeLayer3()
    funnelStore.actionLayers.layer3.result = res
  } catch {
    funnelStore.completeLayer3()
    funnelStore.actionLayers.layer3.result = FALLBACK_LAYER3
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

.result-summary {
  display: block;
  font-size: $font-sm;
  color: #374151;
  line-height: 1.5;
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
