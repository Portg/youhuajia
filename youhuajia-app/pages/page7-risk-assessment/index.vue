<script setup>
import { ref } from 'vue'
import ProgressBar from '../../src/components/ProgressBar.vue'
import YouhuaButton from '../../src/components/YouhuaButton.vue'

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

function goToAction() {
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
        <view class="assurance-dot" />
        <text class="assurance-text">
          以上承诺均受《金融消费者保护条例》约束。评估阶段您保有完整的知情权和退出权。
        </text>
      </view>

      <view class="bottom-spacer" />
    </scroll-view>

    <!-- 底部 CTA -->
    <view class="cta-section">
      <YouhuaButton text="开始准备" type="primary" @click="goToAction" />
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../src/styles/variables.scss' as *;
@use '../../src/styles/mixins.scss' as *;

.page {
  min-height: 100vh;
  @include page-bg;
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
  font-size: $font-xl;
  font-weight: $weight-black;
  color: $text-primary;
  margin-bottom: 8rpx;
  letter-spacing: -1rpx;
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
  @include card;
  overflow: hidden;
  border: 1rpx solid transparent;
  transition: border-color $transition-normal, box-shadow $transition-normal;

  &.faq-expanded {
    border-color: rgba(27, 109, 178, 0.12);
    box-shadow: $shadow-md;
  }
}

.question-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: $spacing-md;
  cursor: pointer;
  @include press-effect;
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
  background: $primary-glass;
  @include flex-center;
  flex-shrink: 0;
}

.index-text {
  font-size: $font-xs;
  font-weight: $weight-bold;
  color: $primary;
}

.question-text {
  font-size: $font-md;
  font-weight: $weight-semibold;
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
  font-size: $font-xl;
  color: $text-tertiary;
  font-weight: 300;
  line-height: 1;
}

.answer-wrap {
  padding-top: $spacing-md;
}

.answer-divider {
  height: 1rpx;
  background: $divider-light;
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
  background: $accent-glass;
  @include flex-center;
  flex-shrink: 0;
}

.tag-text {
  font-size: $font-xs;
  font-weight: $weight-bold;
  color: $accent;
}

.answer-text {
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.75;
  flex: 1;
}

.assurance-card {
  background: $positive-glass;
  border: 1rpx solid rgba(15, 169, 104, 0.1);
  border-radius: $radius-lg;
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
  background: $positive;
  flex-shrink: 0;
  margin-top: 12rpx;
}

.assurance-text {
  font-size: $font-xs;
  color: $positive;
  line-height: 1.65;
  flex: 1;
  font-weight: $weight-medium;
}

.bottom-spacer {
  height: 130rpx;
}

.cta-section {
  @include bottom-bar;
}
</style>
