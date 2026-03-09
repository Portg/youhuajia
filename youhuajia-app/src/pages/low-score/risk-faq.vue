<script setup>
import { ref } from 'vue'
import { useFunnelStore } from '../../stores/funnel.js'
import FunnelNavBar from '../../components/FunnelNavBar.vue'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import SafeAreaBottom from '../../components/SafeAreaBottom.vue'

const funnelStore = useFunnelStore()

// 低分用户专属 FAQ（关注信用修复而非申请流程）
const faqs = ref([
  {
    id: 1,
    question: '为什么当前不适合直接优化？',
    answer: '你的信用结构目前有待改善。直接申请可能面临较高利率，反而增加负担。先通过 30 天基础修复，让信用状况回到更有利的位置，届时优化效果会更明显。',
    expanded: false,
  },
  {
    id: 2,
    question: '30 天改善计划真的有效吗？',
    answer: '根据历史数据，坚持按时还款 30 天、控制信用卡使用率在 70% 以下的用户，信用状况普遍有所改善。关键是保持还款纪录的连续性。',
    expanded: false,
  },
  {
    id: 3,
    question: '改善后能达到什么效果？',
    answer: '完成基础修复后，你可以重新回到优化家评估。如果评分达到 60 分以上，就可以进入利率模拟和方案定制流程，探索更多节省空间。',
    expanded: false,
  },
  {
    id: 4,
    question: '这个过程需要付费吗？',
    answer: '改善计划完全免费。我们提供的还款提醒、评估预约等服务均不收取任何费用。只有在未来成功优化并实际节省后，才会按比例收取服务费。',
    expanded: false,
  },
])

function toggleFaq(id) {
  const faq = faqs.value.find(f => f.id === id)
  if (faq) {
    faq.expanded = !faq.expanded
  }
}

function goToImprovementPlan() {
  funnelStore.advanceStep(8)
  uni.navigateTo({ url: '/pages/low-score/improvement-plan' })
}
</script>

<template>
  <view class="page">
    <FunnelNavBar title="常见问题" />
    <ProgressBar :current="7" :total="9" />

    <scroll-view class="scroll-content" scroll-y>
      <view class="page-header">
        <text class="page-title">了解常见问题</text>
        <text class="page-desc">每一步都透明，让你放心行动</text>
      </view>

      <view class="faq-list">
        <view
          v-for="faq in faqs"
          :key="faq.id"
          class="faq-item"
          :class="{ 'faq-expanded': faq.expanded }"
        >
          <view class="question-row" @tap="toggleFaq(faq.id)">
            <view class="question-left">
              <view class="question-index">
                <text class="index-text">Q{{ faq.id }}</text>
              </view>
              <text class="question-text">{{ faq.question }}</text>
            </view>
            <view class="chevron" :class="{ 'chevron-up': faq.expanded }">
              <text class="chevron-icon">›</text>
            </view>
          </view>

          <view v-show="faq.expanded" class="answer-wrap">
            <view class="answer-divider" />
            <view class="answer-content">
              <view class="answer-tag">
                <text class="tag-text">A</text>
              </view>
              <text class="answer-text">{{ faq.answer }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="assurance-card">
        <view class="assurance-dot" />
        <text class="assurance-text">
          改善计划完全由你掌控节奏，随时可以暂停或重新开始。
        </text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <view class="cta-section">
      <YouhuaButton text="开始改善行动" type="primary" @click="goToImprovementPlan" />
    </view>

    <SafeAreaBottom />
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background-color: $background;
  display: flex;
  flex-direction: column;
}

.scroll-content {
  flex: 1;
  padding: 0 $spacing-xl;
}

.page-header {
  padding: $spacing-lg 0 $spacing-md;
}

.page-title {
  display: block;
  font-size: 40rpx;
  font-weight: 700;
  color: $text-primary;
  margin-bottom: 8rpx;
}

.page-desc {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
}

.faq-list {
  display: flex;
  flex-direction: column;
  gap: $spacing-md;
  margin-bottom: $spacing-md;
}

.faq-item {
  background-color: $surface;
  border-radius: $radius-md;
  padding: $spacing-lg;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.06);
  overflow: hidden;
  border: 1rpx solid transparent;
  transition: border-color 0.3s, box-shadow 0.3s;

  &.faq-expanded {
    border-color: rgba(27, 109, 178, 0.12);
    box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
  }
}

.question-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  cursor: pointer;
}

.question-left {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  flex: 1;
}

.question-index {
  width: 48rpx;
  height: 48rpx;
  border-radius: $radius-md;
  background-color: $primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.index-text {
  font-size: $font-xs;
  font-weight: 700;
  color: $primary;
}

.question-text {
  font-size: $font-md;
  font-weight: 600;
  color: $text-primary;
  line-height: 1.5;
  flex: 1;
}

.chevron {
  transform: rotate(90deg);
  transition: transform 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
  flex-shrink: 0;

  &.chevron-up {
    transform: rotate(-90deg);
  }
}

.chevron-icon {
  font-size: 40rpx;
  color: $text-tertiary;
  font-weight: 300;
  line-height: 1;
}

.answer-wrap {
  padding-top: $spacing-md;
}

.answer-divider {
  height: 1rpx;
  background-color: $divider;
  margin-bottom: $spacing-md;
}

.answer-content {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
}

.answer-tag {
  width: 48rpx;
  height: 48rpx;
  border-radius: $radius-md;
  background-color: $accent-light;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tag-text {
  font-size: $font-xs;
  font-weight: 700;
  color: $accent;
}

.answer-text {
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.75;
  flex: 1;
}

.assurance-card {
  background: linear-gradient(135deg, $positive-light, $primary-light);
  border-radius: $radius-md;
  padding: $spacing-md $spacing-lg;
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.assurance-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background-color: $positive;
  flex-shrink: 0;
  margin-top: 12rpx;
}

.assurance-text {
  font-size: $font-xs;
  color: $positive;
  line-height: 1.65;
  flex: 1;
  font-weight: 500;
}

.bottom-spacer {
  height: 130rpx;
}

.cta-section {
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
