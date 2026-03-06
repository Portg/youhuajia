<script setup>
import { ref } from 'vue'
import YouhuaButton from '../../components/YouhuaButton.vue'

const current = ref(0)

const slides = [
  {
    icon: '🔍',
    title: '看看你是否多付了利息',
    desc: '输入月供和收入，1分钟生成月供压力指数，发现潜在优化空间',
  },
  {
    icon: '📊',
    title: '3步完成：检测 → 分析 → 行动',
    desc: '从压力检测到债务录入，再到生成个性化优化报告，全程引导',
  },
  {
    icon: '🔒',
    title: '数据安全，不接触资金',
    desc: '所有数据加密传输和存储，仅用于分析，不涉及任何资金操作',
  },
]

function onSwiperChange(e) {
  current.value = e.detail.current
}

function done() {
  uni.setStorageSync('onboardingDone', '1')
  uni.switchTab({ url: '/pages/home/index' })
}

function skip() {
  done()
}
</script>

<template>
  <view class="page">
    <text class="skip-btn" @tap="skip">跳过</text>

    <swiper class="swiper" :current="current" @change="onSwiperChange">
      <swiper-item v-for="(slide, i) in slides" :key="i">
        <view class="slide">
          <text class="slide-icon">{{ slide.icon }}</text>
          <text class="slide-title">{{ slide.title }}</text>
          <text class="slide-desc">{{ slide.desc }}</text>
        </view>
      </swiper-item>
    </swiper>

    <!-- 指示器 -->
    <view class="dots">
      <view
        v-for="(_, i) in slides"
        :key="i"
        class="dot"
        :class="{ 'dot-active': current === i }"
      />
    </view>

    <!-- 底部按钮 -->
    <view class="bottom">
      <YouhuaButton
        v-if="current === slides.length - 1"
        text="开始使用"
        type="primary"
        @click="done"
      />
      <view v-else class="next-wrap" @tap="current++">
        <text class="next-text">下一步</text>
      </view>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #EBF4FB 0%, $background 40%);
  display: flex;
  flex-direction: column;
  padding-bottom: calc(env(safe-area-inset-bottom) + $spacing-xl);
}

.skip-btn {
  position: absolute;
  top: 100rpx;
  right: $spacing-xl;
  font-size: $font-sm;
  color: $text-tertiary;
  z-index: 10;
  padding: $spacing-sm;
}

.swiper {
  flex: 1;
  width: 100%;
}

.slide {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 $spacing-2xl;
  height: 100%;
}

.slide-icon {
  font-size: 160rpx;
  margin-bottom: $spacing-2xl;
}

.slide-title {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $text-primary;
  text-align: center;
  line-height: 1.3;
  margin-bottom: $spacing-lg;
}

.slide-desc {
  font-size: $font-md;
  color: $text-secondary;
  text-align: center;
  line-height: 1.6;
}

.dots {
  display: flex;
  justify-content: center;
  gap: $spacing-sm;
  margin-bottom: $spacing-2xl;
}

.dot {
  width: 16rpx;
  height: 16rpx;
  border-radius: 50%;
  background: $divider;
  transition: all $transition-fast;
}

.dot-active {
  width: 40rpx;
  border-radius: $radius-pill;
  background: $primary;
}

.bottom {
  padding: 0 $spacing-xl;
}

.next-wrap {
  height: 100rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.next-text {
  font-size: $font-lg;
  font-weight: $weight-semibold;
  color: $primary;
}
</style>
