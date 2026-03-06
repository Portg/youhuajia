<script setup>
const version = '1.0.0'
</script>

<template>
  <view class="page">
    <view class="app-info">
      <view class="app-icon">
        <text class="app-icon-text">优</text>
      </view>
      <text class="app-name">优化家</text>
      <text class="app-version">版本 {{ version }}</text>
    </view>

    <view class="section">
      <text class="section-title">产品简介</text>
      <text class="section-content">
        优化家是一款面向个人用户的债务优化决策工具。通过专业的财务分析模型，帮助你全面了解自身债务状况，发现优化空间，制定科学的还款策略。
      </text>
    </view>

    <view class="section">
      <text class="section-title">数据声明</text>
      <text class="section-content">
        我们严格保护你的个人信息。所有数据仅用于为你提供债务优化分析服务，不会向第三方共享。你可以随时联系客服申请删除个人数据。
      </text>
    </view>

    <view class="section">
      <text class="section-title">联系我们</text>
      <text class="section-content">
        如有任何问题或建议，请通过以下方式联系：
      </text>
      <text class="contact-item">客服邮箱：support@youhuajia.com</text>
    </view>
  </view>
</template>

<style lang="scss" scoped>
@use '../../styles/variables.scss' as *;

.page {
  min-height: 100vh;
  background-color: $background;
  padding: $spacing-xl;
}

.app-info {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: $spacing-3xl 0;
}

.app-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: $radius-xl;
  background: $primary-gradient;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: $spacing-lg;
  box-shadow: $shadow-primary;
}

.app-icon-text {
  font-size: 56rpx;
  font-weight: $weight-black;
  color: $text-inverse;
}

.app-name {
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.app-version {
  font-size: $font-sm;
  color: $text-tertiary;
}

.section {
  background: $surface;
  border-radius: $radius-lg;
  padding: $spacing-xl;
  margin-bottom: $spacing-lg;
}

.section-title {
  display: block;
  font-size: $font-md;
  font-weight: $weight-semibold;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.section-content {
  display: block;
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.7;
}

.contact-item {
  display: block;
  font-size: $font-sm;
  color: $text-primary;
  margin-top: $spacing-md;
}
</style>
