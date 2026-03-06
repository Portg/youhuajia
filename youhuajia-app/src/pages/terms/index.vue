<script setup>
</script>

<template>
  <view class="page">
    <scroll-view class="scroll-content" scroll-y>
      <view class="section">
        <text class="title">用户服务协议</text>
        <text class="update-time">更新日期：2026年3月1日</text>
      </view>

      <view class="section">
        <text class="heading">一、服务说明</text>
        <view class="body-block">
          <text class="body">优化家是一款面向个人用户的债务优化分析工具，为你提供债务结构分析、利率评估、优化建议等信息服务。</text>
        </view>
        <view class="body-block">
          <text class="body">本服务仅提供信息参考，不构成任何金融建议、投资建议或贷款服务。最终决策应由你根据自身情况独立判断。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">二、账号注册与使用</text>
        <view class="body-block">
          <text class="body">1. 你需要使用本人手机号注册并登录，每个手机号对应一个账号。</text>
        </view>
        <view class="body-block">
          <text class="body">2. 你应妥善保管账号信息，因账号泄露导致的损失由你自行承担。</text>
        </view>
        <view class="body-block">
          <text class="body">3. 你应确保提供的债务信息真实、准确，虚假信息将导致分析结果不可靠。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">三、服务内容</text>
        <view class="body-block">
          <text class="body">1. 月供压力检测：基于你的收入和月供数据，评估还款压力水平。</text>
        </view>
        <view class="body-block">
          <text class="body">2. 债务结构分析：分析你的债务组成、综合利率和优化空间。</text>
        </view>
        <view class="body-block">
          <text class="body">3. 优化评分：基于多维度模型评估你的债务优化潜力。</text>
        </view>
        <view class="body-block">
          <text class="body">4. 利率模拟：模拟不同利率下的还款变化，帮助你了解优化效果。</text>
        </view>
        <view class="body-block">
          <text class="body">5. AI 分析报告：基于你的数据生成个性化分析报告和改善建议。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">四、免责声明</text>
        <view class="body-block">
          <text class="body">1. 本服务提供的分析结果和建议仅供参考，不保证绝对准确性。</text>
        </view>
        <view class="body-block">
          <text class="body">2. 我们不提供贷款、借款、资金划转等金融服务，不参与任何资金流转。</text>
        </view>
        <view class="body-block">
          <text class="body">3. 因你依据本服务信息做出的决策而产生的后果，由你自行承担。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">五、知识产权</text>
        <view class="body-block">
          <text class="body">本应用的所有内容（包括但不限于文字、图片、界面设计、算法模型）均受知识产权法保护。未经授权，不得复制、修改或用于商业用途。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">六、协议变更</text>
        <view class="body-block">
          <text class="body">我们可能根据业务发展需要修改本协议。修改后的协议将在应用内公示，继续使用本服务即视为接受修改后的协议。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">七、联系方式</text>
        <view class="body-block">
          <text class="body">如对本协议有任何疑问，请联系：support@youhuajia.com</text>
        </view>
      </view>
    </scroll-view>
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
  padding: $spacing-xl;
  padding-bottom: calc($spacing-3xl + env(safe-area-inset-bottom));
}

.section {
  margin-bottom: $spacing-xl;
}

.title {
  display: block;
  font-size: $font-xl;
  font-weight: $weight-bold;
  color: $text-primary;
  margin-bottom: $spacing-xs;
}

.update-time {
  display: block;
  font-size: $font-xs;
  color: $text-tertiary;
}

.heading {
  display: block;
  font-size: $font-md;
  font-weight: $weight-semibold;
  color: $text-primary;
  margin-bottom: $spacing-md;
}

.body-block {
  margin-bottom: $spacing-sm;
}

.body {
  font-size: $font-sm;
  color: $text-secondary;
  line-height: 1.8;
}
</style>
