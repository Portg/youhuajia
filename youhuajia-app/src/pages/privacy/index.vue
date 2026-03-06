<script setup>
</script>

<template>
  <view class="page">
    <scroll-view class="scroll-content" scroll-y>
      <view class="section">
        <text class="title">隐私保护说明</text>
        <text class="update-time">更新日期：2026年3月1日</text>
      </view>

      <view class="section">
        <text class="heading">一、我们收集哪些信息</text>
        <view class="body-block">
          <text class="body">1. 手机号码：用于账号注册与登录验证，存储时使用 SHA-256 哈希加密，原始号码不留存。</text>
        </view>
        <view class="body-block">
          <text class="body">2. 债务信息：包括债权方名称、借款金额、还款总额、借款期限等，仅用于生成债务优化分析。</text>
        </view>
        <view class="body-block">
          <text class="body">3. 收入信息：月收入区间，用于计算月供压力指数。</text>
        </view>
        <view class="body-block">
          <text class="body">4. 设备信息：设备型号和操作系统版本，用于保障服务兼容性。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">二、我们如何使用信息</text>
        <view class="body-block">
          <text class="body">所有数据仅用于为你提供债务优化分析服务，包括：生成财务画像、计算优化评分、出具分析报告、提供改善建议。</text>
        </view>
        <view class="body-block">
          <text class="body">我们不会将你的个人信息用于广告推荐、信用评估或任何与债务优化无关的用途。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">三、信息共享与披露</text>
        <view class="body-block">
          <text class="body">我们不会向任何第三方共享、出售或披露你的个人信息，以下情况除外：</text>
        </view>
        <view class="body-block">
          <text class="body">1. 获得你的明确授权同意；</text>
        </view>
        <view class="body-block">
          <text class="body">2. 根据法律法规的强制性要求。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">四、信息存储与保护</text>
        <view class="body-block">
          <text class="body">你的数据存储在中国境内的加密服务器中。我们采用行业标准的安全措施保护你的信息，包括传输加密（HTTPS）、存储加密、访问控制和日志审计。</text>
        </view>
        <view class="body-block">
          <text class="body">手机号以哈希形式存储，敏感字段在接口返回时自动脱敏（如手机号显示为 138****1234）。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">五、你的权利</text>
        <view class="body-block">
          <text class="body">你可以随时：查看和修改你录入的债务信息；通过"我的"页面退出登录清除本地数据；联系客服申请删除账号及所有关联数据。</text>
        </view>
        <view class="body-block">
          <text class="body">账号删除申请将在 15 个工作日内处理完毕，届时你的所有数据将被永久删除且不可恢复。</text>
        </view>
      </view>

      <view class="section">
        <text class="heading">六、联系我们</text>
        <view class="body-block">
          <text class="body">如对本隐私说明有任何疑问，请联系：support@youhuajia.com</text>
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
