<script setup>
import { ref } from 'vue'
import ProgressBar from '../../components/ProgressBar.vue'
import YouhuaButton from '../../components/YouhuaButton.vue'
import { useFunnelStore } from '../../stores/funnel.js'

// Q&A 数据（硬编码，每条答案具体不含糊，不说"一般不会"）
const faqs = ref([
  {
    id: 1,
    question: '会不会查我的征信？',
    answer: '优化方案评估阶段不会查询征信。仅在您确认正式申请后，合作金融机构才会在获得您授权的情况下查询。评估阶段您的征信记录不受任何影响。',
    expanded: false
  },
  {
    id: 2,
    question: '会不会影响我的信用评分？',
    answer: '方案评估不产生任何征信记录。正式申请阶段的征信查询属于正常贷款申请，影响可控。通常单次查询对评分的影响在 2-5 分以内，且 3 个月后自动恢复。',
    expanded: false
  },
  {
    id: 3,
    question: '如果方案不适合我怎么办？',
    answer: '评估阶段完全免费且无风险。如果当前不适合优化，我们会提供免费的信用改善建议，并在条件成熟后（通常 30-90 天）重新为您评估。',
    expanded: false
  },
  {
    id: 4,
    question: '需要支付费用吗？',
    answer: '评估和方案制定完全免费。仅在您确认执行且成功降低利率后，才按实际节省金额的一定比例收取服务费。如果没有节省，不收取任何费用。',
    expanded: false
  }
])

function toggleFaq(id) {
  const faq = faqs.value.find(f => f.id === id)
  if (faq) {
    faq.expanded = !faq.expanded
  }
}

const funnelStore = useFunnelStore()

function goToAction() {
  funnelStore.advanceStep(8)
  uni.navigateTo({ url: '/pages/page8-action-layers/index' })
}
</script>

<template>
  <view class="page">
    <ProgressBar :current="7" :total="9" />

    <scroll-view class="scroll-content" scroll-y>
      <!-- 页面标题 -->
      <view class="page-header">
        <text class="page-title">了解常见问题</text>
        <text class="page-desc">透明告知，让你放心每一步</text>
      </view>

      <!-- FAQ 列表 -->
      <view class="faq-list">
        <view
          v-for="faq in faqs"
          :key="faq.id"
          class="faq-item"
          :class="{ 'faq-expanded': faq.expanded }"
        >
          <!-- 问题行 -->
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

          <!-- 答案区 -->
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

      <!-- 安心说明 -->
      <view class="assurance-card">
        <text class="assurance-icon">i</text>
        <text class="assurance-text">
          以上承诺均受《金融消费者保护条例》约束。评估阶段您保有完整的知情权和退出权。
        </text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部 CTA -->
    <view class="cta-section">
      <YouhuaButton text="开始准备" type="primary" @click="goToAction" />
      <view style="height: env(safe-area-inset-bottom); min-height: 16rpx;" />
    </view>
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
  padding: 0 $spacing-lg;
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
  background: $surface;
  border-radius: $radius-lg;
  overflow: hidden;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.05);
  border: 2rpx solid transparent;
  transition: border-color 0.2s;

  &.faq-expanded {
    border-color: $primary-light;
  }
}

.question-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: $spacing-lg;
  gap: $spacing-md;
  cursor: pointer;

  &:active {
    background: $background;
  }
}

.question-left {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  flex: 1;
}

.question-index {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: $primary-light;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.index-text {
  font-size: 20rpx;
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
  transition: transform 0.25s ease;
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
  padding: 0 $spacing-lg $spacing-lg;
}

.answer-divider {
  height: 2rpx;
  background: $divider;
  margin-bottom: $spacing-md;
}

.answer-content {
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
}

.answer-tag {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: $accent-light;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tag-text {
  font-size: 20rpx;
  font-weight: 700;
  color: $accent;
}

.answer-text {
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.7;
  flex: 1;
}

.assurance-card {
  background: $positive-light;
  border-radius: $radius-lg;
  padding: $spacing-md $spacing-lg;
  display: flex;
  align-items: flex-start;
  gap: $spacing-sm;
  margin-bottom: $spacing-md;
}

.assurance-icon {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  background: $positive;
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 700;
  font-style: italic;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  text-align: center;
  line-height: 36rpx;
}

.assurance-text {
  font-size: $font-xs;
  color: $positive;
  line-height: 1.6;
  flex: 1;
}

.bottom-spacer {
  height: 120rpx;
}

.cta-section {
  padding: $spacing-md $spacing-lg;
  background: #FFFFFF;
  border-top: 2rpx solid $divider;
}
</style>
