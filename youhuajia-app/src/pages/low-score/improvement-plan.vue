<template>
  <view class="improvement-plan">
    <ProgressBar :current="8" :total="9" />

    <view class="page-header">
      <text class="headline">你的 30 天改善行动</text>
      <text class="sub-headline">每一步都让你的信用状况更好，按自己的节奏来</text>
      <LayerProgress :current="completedCount" :total="3" />
    </view>

    <view class="layers">
      <!-- Layer 1: 生成 30 天改善计划 -->
      <ActionLayer
        :layer="1"
        title="生成 30 天改善计划"
        :status="layer1Status"
        :active="true"
        :locked="false"
        :loading="layer1Loading"
        action-text="生成我的改善计划"
        @action="onLayer1"
        @skip="onSkip"
      >
        <template #result>
          <view class="plan-result">
            <view v-for="(item, i) in improvementPlan" :key="i" class="plan-item">
              <text class="plan-week">第 {{ item.week }} 周</text>
              <text class="plan-task">{{ item.task }}</text>
            </view>
          </view>
        </template>
      </ActionLayer>

      <!-- Layer 2: 设置还款提醒 -->
      <ActionLayer
        :layer="2"
        title="设置还款提醒"
        :status="layer2Status"
        :active="layer1.completed"
        :locked="!layer1.completed"
        action-text="设置还款提醒"
        @action="onLayer2"
        @skip="onSkip"
      >
        <template #result>
          <text class="result-success">已设置还款提醒，将在还款日前 3 天通知你</text>
        </template>
      </ActionLayer>

      <!-- Layer 3: 30 天后重新评估 -->
      <ActionLayer
        :layer="3"
        title="30 天后重新评估"
        :status="layer3Status"
        :active="layer2.completed"
        :locked="!layer2.completed"
        action-text="预约 30 天后评估"
        @action="onLayer3"
        @skip="onSkip"
      >
        <template #result>
          <text class="result-success">已预约重新评估，到时我们会提醒你</text>
        </template>
      </ActionLayer>

      <!-- Layer 4: 不展示（低分路径只有3层） -->
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
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useFunnelStore } from '../../stores/funnel'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'
import ActionLayer from '../page8-action-layers/components/ActionLayer.vue'
import LayerProgress from '../page8-action-layers/components/LayerProgress.vue'

const funnelStore = useFunnelStore()

const layer1Loading = ref(false)

const layer1 = computed(() => funnelStore.actionLayers.layer1)
const layer2 = computed(() => funnelStore.actionLayers.layer2)
const layer3 = computed(() => funnelStore.actionLayers.layer3)
const completedCount = computed(() => funnelStore.completedLayerCount)

const improvementPlan = [
  { week: '1-2', task: '整理账单，确认最低还款额和还款日' },
  { week: '2-3', task: '优先按时还清最小额度账户' },
  { week: '3-4', task: '信用卡使用率控制在 70% 以下' },
]

const layer1Status = computed(() => {
  if (layer1.value.completed) return 'completed'
  if (layer1Loading.value) return 'in_progress'
  return 'pending'
})

const layer2Status = computed(() => {
  if (layer2.value.completed) return 'completed'
  return 'pending'
})

const layer3Status = computed(() => {
  if (layer3.value.completed) return 'completed'
  return 'pending'
})

async function onLayer1() {
  layer1Loading.value = true
  await new Promise((resolve) => setTimeout(resolve, 600))
  funnelStore.completeLayer1('low-score-plan')
  funnelStore.actionLayers.layer1.result = { plan: improvementPlan }
  layer1Loading.value = false
}

function onLayer2() {
  uni.showModal({
    title: '设置还款提醒',
    content: '将在每月还款日前 3 天提醒你，避免逾期',
    confirmText: '确认设置',
    confirmColor: '#2E75B6',
    success: (res) => {
      if (res.confirm) {
        funnelStore.completeLayer2()
      }
    },
  })
}

function onLayer3() {
  uni.showModal({
    title: '预约重新评估',
    content: '30 天后，我们会提醒你重新评估信用状况',
    confirmText: '好的，期待',
    confirmColor: '#2E75B6',
    success: (res) => {
      if (res.confirm) {
        funnelStore.completeLayer3()
      }
    },
  })
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

.improvement-plan {
  min-height: 100vh;
  background-color: $background;
  padding-bottom: 140rpx;
}

.page-header {
  background-color: $surface;
  padding: $spacing-lg $spacing-xl $spacing-md;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.04);
}

.headline {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.sub-headline {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.5;
  margin-bottom: $spacing-md;
}

.layers {
  padding: $spacing-md $spacing-xl;
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
}

/* 改善计划结果 */
.plan-result {
  display: flex;
  flex-direction: column;
  gap: $spacing-sm;
}

.plan-item {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.plan-week {
  font-size: $font-xs;
  color: $primary;
  font-weight: 500;
}

.plan-task {
  font-size: $font-sm;
  color: $text-primary;
  line-height: 1.4;
}

.result-success {
  display: block;
  font-size: $font-sm;
  color: $positive;
  line-height: 1.4;
}

.cta-area {
  padding: 0 $spacing-xl $spacing-lg;
}
</style>
