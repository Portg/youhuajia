<template>
  <view class="page">
    <FunnelNavBar title="债务录入" />
    <!-- 顶部标题 -->
    <view class="page-header">
      <text class="header-title">录入具体债务，获得精确分析</text>
      <text class="header-sub">支持手动填写、拍照识别、快速模板</text>
    </view>

    <!-- 已录入统计栏 -->
    <view class="stat-bar" :class="debtStore.totalCount > 0 ? 'stat-bar-active' : ''">
      <text class="stat-text" v-if="debtStore.totalCount > 0">
        已录入 <text class="stat-num">{{ debtStore.totalCount }}</text> 笔，
        已发现 <text class="stat-money">{{ formatMoneyInt(debtStore.estimatedSaving) }}</text> 潜在节省
      </text>
      <text class="stat-text-empty" v-else>先录一笔，看看能节省多少</text>
    </view>

    <!-- 债务卡片列表 -->
    <scroll-view class="debt-list" scroll-y v-if="debtStore.debts.length > 0">
      <view class="debt-card" v-for="debt in debtStore.debts" :key="debt.name || debt.id">
        <view class="debt-main">
          <view class="debt-badge" :class="debtTypeClass(debt.debtType)">
            {{ debtTypeLabel(debt.debtType) }}
          </view>
          <view class="debt-info">
            <text class="debt-creditor">{{ debt.creditor }}</text>
            <text class="debt-principal">{{ formatMoney(debt.principal) }}</text>
          </view>
        </view>
        <view class="debt-right">
          <text class="debt-apr" v-if="debt.apr">年化 {{ formatRate(debt.apr) }}</text>
          <text class="debt-status" :class="'s-' + (debt.status || '').toLowerCase()">
            {{ statusLabel(debt.status) }}
          </text>
        </view>
        <text class="debt-delete" @tap="handleDelete(debt)">×</text>
      </view>
    </scroll-view>

    <!-- 加载中 -->
    <view class="empty-wrap" v-else-if="debtStore.loading">
      <text class="empty-icon">📋</text>
      <text class="empty-title">加载中...</text>
    </view>

    <!-- 空态 -->
    <view class="empty-wrap" v-else>
      <text class="empty-icon">📋</text>
      <text class="empty-title">暂无债务记录</text>
      <text class="empty-hint">使用下方方式录入第一笔</text>
    </view>

    <!-- OCR 识别中 -->
    <view class="ocr-mask" v-if="ocrStatus === 'UPLOADING' || ocrStatus === 'PROCESSING'">
      <view class="ocr-card">
        <view class="spinner"></view>
        <text class="ocr-msg">{{ ocrStatus === 'UPLOADING' ? '上传图片中...' : '识别账单中...' }}</text>
        <text class="ocr-cancel" @tap="debtStore.cancelOcr()">取消</text>
      </view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="methods">
        <view class="method-btn" @tap="handleOcr">
          <text class="method-icon">📷</text>
          <text class="method-label">拍照识别</text>
        </view>
        <view class="method-btn method-primary" @tap="openManualForm">
          <text class="method-icon">✏️</text>
          <text class="method-label">手动录入</text>
        </view>
        <view class="method-btn" @tap="showTpl = true">
          <text class="method-icon">📋</text>
          <text class="method-label">快速模板</text>
        </view>
      </view>
      <button
        class="cta-btn"
        :class="debtStore.confirmedCount === 0 ? 'cta-disabled' : ''"
        :disabled="debtStore.confirmedCount === 0"
        @tap="handleGoReport"
      >
        查看分析报告
      </button>
      <text class="cta-tip" v-if="debtStore.confirmedCount === 0">至少确认 1 笔债务后可查看</text>
    </view>

    <!-- 手动录入表单弹窗 -->
    <view class="overlay" v-if="showForm" @tap.self="showForm = false">
      <view class="sheet">
        <view class="sheet-header">
          <text class="sheet-title">{{ formTitle }}</text>
          <text class="sheet-close" @tap="showForm = false">×</text>
        </view>
        <scroll-view class="sheet-scroll" scroll-y>
          <view class="form-wrap">
            <!-- 债权方 -->
            <view class="field">
              <text class="field-label">债权方 <text class="required">*</text></text>
              <input class="field-input" v-model="form.creditor" placeholder="如：招商银行、花呗" maxlength="100" />
              <text class="field-err" v-if="errs.creditor">{{ errs.creditor }}</text>
            </view>
            <!-- 产品类型 -->
            <view class="field">
              <text class="field-label">产品类型 <text class="required">*</text></text>
              <view class="type-chips">
                <view
                  v-for="opt in TYPE_OPTIONS"
                  :key="opt.value"
                  class="chip"
                  :class="form.debtType === opt.value ? 'chip-active' : ''"
                  @tap="form.debtType = opt.value"
                >{{ opt.label }}</view>
              </view>
              <text class="field-err" v-if="errs.debtType">{{ errs.debtType }}</text>
            </view>
            <!-- 本金 -->
            <view class="field">
              <text class="field-label">本金（元）<text class="required">*</text></text>
              <input class="field-input" v-model="form.principal" type="digit" placeholder="借款本金" />
              <text class="field-err" v-if="errs.principal">{{ errs.principal }}</text>
            </view>
            <!-- 总还款 -->
            <view class="field">
              <text class="field-label">总还款额（元）<text class="required">*</text></text>
              <input class="field-input" v-model="form.totalRepayment" type="digit" placeholder="所有期数还款之和" />
              <text class="field-err" v-if="errs.totalRepayment">{{ errs.totalRepayment }}</text>
            </view>
            <!-- 借款日期 -->
            <view class="field">
              <text class="field-label">借款日期</text>
              <picker mode="date" :value="form.startDate" @change="e => form.startDate = e.detail.value">
                <view class="field-picker">
                  <text :class="form.startDate ? 'picker-val' : 'picker-ph'">
                    {{ form.startDate || '请选择（选填）' }}
                  </text>
                </view>
              </picker>
            </view>
            <!-- 借款天数 -->
            <view class="field">
              <text class="field-label">借款天数 <text class="required">*</text></text>
              <input class="field-input" v-model="form.loanDays" type="number" placeholder="如 12 期 = 360 天" />
              <text class="field-err" v-if="errs.loanDays">{{ errs.loanDays }}</text>
            </view>
            <!-- APR 试算预览 -->
            <view class="apr-preview" v-if="aprPreview !== null">
              <text class="apr-label">预计年利率：</text>
              <text class="apr-value">{{ formatRate(aprPreview) }}</text>
              <text class="apr-note" v-if="aprPreview > 24">年化偏高，留意成本</text>
            </view>
          </view>
        </scroll-view>
        <view class="sheet-footer">
          <button class="btn-cancel" @tap="showForm = false">取消</button>
          <button class="btn-confirm" @tap="submitForm" :loading="submitting">确认录入</button>
        </view>
      </view>
    </view>

    <!-- 快速模板 -->
    <view class="overlay" v-if="showTpl" @tap.self="showTpl = false">
      <view class="sheet sheet-sm">
        <view class="sheet-header">
          <text class="sheet-title">选择常见产品</text>
          <text class="sheet-close" @tap="showTpl = false">×</text>
        </view>
        <view class="tpl-list">
          <view class="tpl-item" v-for="t in TEMPLATES" :key="t.creditor" @tap="applyTpl(t)">
            <text class="tpl-name">{{ t.creditor }}</text>
            <text class="tpl-type">{{ t.typeLabel }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- OCR 确认弹窗 -->
    <view class="overlay" v-if="showOcrConfirm" @tap.self="cancelOcrConfirm">
      <view class="sheet">
        <view class="sheet-header">
          <text class="sheet-title">确认识别结果</text>
          <text class="sheet-sub">请核实信息，可手动修改</text>
        </view>
        <scroll-view class="sheet-scroll" scroll-y>
          <view class="form-wrap">
            <view class="confidence-bar" v-if="ocrForm.confidence">
              <text class="confidence-label">识别置信度</text>
              <text class="confidence-val">{{ Math.round(ocrForm.confidence) }}%</text>
            </view>
            <view class="field">
              <text class="field-label">债权方</text>
              <input class="field-input" v-model="ocrForm.creditor" />
            </view>
            <view class="field">
              <text class="field-label">产品类型</text>
              <view class="type-chips">
                <view
                  v-for="opt in TYPE_OPTIONS"
                  :key="opt.value"
                  class="chip"
                  :class="ocrForm.debtType === opt.value ? 'chip-active' : ''"
                  @tap="ocrForm.debtType = opt.value"
                >{{ opt.label }}</view>
              </view>
            </view>
            <view class="field">
              <text class="field-label">本金（元）</text>
              <input class="field-input" v-model="ocrForm.principal" type="digit" />
            </view>
            <view class="field">
              <text class="field-label">总还款额（元）</text>
              <input class="field-input" v-model="ocrForm.totalRepayment" type="digit" />
            </view>
            <view class="field">
              <text class="field-label">借款天数</text>
              <input class="field-input" v-model="ocrForm.loanDays" type="number" />
            </view>
          </view>
        </scroll-view>
        <view class="sheet-footer">
          <button class="btn-cancel" @tap="cancelOcrConfirm">取消</button>
          <button class="btn-confirm" @tap="submitOcrConfirm" :loading="submitting">确认添加</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted, onUnmounted } from 'vue'
import FunnelNavBar from '../../components/FunnelNavBar.vue'
import { useDebtStore } from '../../stores/debt.js'
import { useProfileStore } from '../../stores/profile.js'
import { useFunnelStore } from '../../stores/funnel.js'
import { calculateApr } from '../../api/engine.js'

const debtStore = useDebtStore()
const profileStore = useProfileStore()
const funnelStore = useFunnelStore()

// ---- 格式化工具 ----
function formatMoney(v) {
  const n = Number(v)
  if (isNaN(n)) return '¥0.00'
  return '¥' + new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(n)
}
function formatMoneyInt(v) {
  const n = Math.round(Number(v))
  return isNaN(n) ? '¥0' : '¥' + new Intl.NumberFormat('zh-CN').format(n)
}
function formatRate(v) {
  const n = Number(v)
  if (isNaN(n)) return '0.0%'
  return new Intl.NumberFormat('zh-CN', { minimumFractionDigits: 1, maximumFractionDigits: 1 }).format(n) + '%'
}

// ---- 常量 ----
const TYPE_OPTIONS = [
  { value: 'CREDIT_CARD', label: '信用卡' },
  { value: 'CONSUMER_LOAN', label: '消费贷' },
  { value: 'BUSINESS_LOAN', label: '经营贷' },
  { value: 'MORTGAGE', label: '房贷' },
  { value: 'OTHER', label: '其他' },
]
const TEMPLATES = [
  { creditor: '花呗', debtType: 'CONSUMER_LOAN', typeLabel: '消费贷' },
  { creditor: '借呗', debtType: 'CONSUMER_LOAN', typeLabel: '消费贷' },
  { creditor: '微粒贷', debtType: 'CONSUMER_LOAN', typeLabel: '消费贷' },
  { creditor: '京东白条', debtType: 'CONSUMER_LOAN', typeLabel: '消费贷' },
  { creditor: '招商银行信用卡', debtType: 'CREDIT_CARD', typeLabel: '信用卡' },
  { creditor: '工商银行信用卡', debtType: 'CREDIT_CARD', typeLabel: '信用卡' },
  { creditor: '建设银行信用卡', debtType: 'CREDIT_CARD', typeLabel: '信用卡' },
]

function debtTypeLabel(t) { return TYPE_OPTIONS.find(o => o.value === t)?.label || '其他' }
function debtTypeClass(t) {
  return { CREDIT_CARD: 'badge-blue', CONSUMER_LOAN: 'badge-orange', BUSINESS_LOAN: 'badge-green', MORTGAGE: 'badge-teal', OTHER: 'badge-gray' }[t] || 'badge-gray'
}
function statusLabel(s) {
  return { DRAFT: '草稿', SUBMITTED: '已提交', OCR_PROCESSING: '识别中', PENDING_CONFIRM: '待确认', CONFIRMED: '✓ 已确认', IN_PROFILE: '✓ 已分析' }[s] || s
}

// ---- OCR 状态 ----
const ocrStatus = computed(() => debtStore.ocrTask?.status)

// ---- 手动录入表单 ----
const showForm = ref(false)
const showTpl = ref(false)
const showOcrConfirm = ref(false)
const formTitle = ref('手动录入债务')
const submitting = ref(false)
const aprPreview = ref(null)
let aprTimer = null

const form = reactive({ creditor: '', debtType: '', principal: '', totalRepayment: '', startDate: '', loanDays: '' })
const errs = reactive({ creditor: '', debtType: '', principal: '', totalRepayment: '', loanDays: '' })

const ocrForm = reactive({ creditor: '', debtType: 'CONSUMER_LOAN', principal: '', totalRepayment: '', loanDays: '', confidence: 0 })
let currentOcrTaskId = null

function openManualForm() {
  formTitle.value = '手动录入债务'
  Object.assign(form, { creditor: '', debtType: '', principal: '', totalRepayment: '', startDate: '', loanDays: '' })
  aprPreview.value = null
  showForm.value = true
}

function applyTpl(tpl) {
  formTitle.value = `录入 ${tpl.creditor}`
  Object.assign(form, { creditor: tpl.creditor, debtType: tpl.debtType, principal: '', totalRepayment: '', startDate: '', loanDays: '' })
  aprPreview.value = null
  showTpl.value = false
  showForm.value = true
}

// 实时 APR 试算（debounce 500ms）
watch([() => form.principal, () => form.totalRepayment, () => form.loanDays], () => {
  if (aprTimer) clearTimeout(aprTimer)
  if (!form.principal || !form.totalRepayment || !form.loanDays) { aprPreview.value = null; return }
  aprTimer = setTimeout(async () => {
    try {
      const r = await calculateApr(Number(form.principal), Number(form.totalRepayment), Number(form.loanDays))
      aprPreview.value = r.apr
    } catch (_) { aprPreview.value = null }
  }, 500)
})

function validate() {
  Object.keys(errs).forEach(k => errs[k] = '')
  let ok = true
  if (!form.creditor.trim()) { errs.creditor = '请填写债权方名称'; ok = false }
  if (!form.debtType) { errs.debtType = '请选择产品类型'; ok = false }
  if (!form.principal || Number(form.principal) <= 0) { errs.principal = '请填写有效本金'; ok = false }
  if (!form.totalRepayment || Number(form.totalRepayment) < Number(form.principal)) { errs.totalRepayment = '总还款额不能小于本金'; ok = false }
  if (!form.loanDays || Number(form.loanDays) < 1) { errs.loanDays = '请填写有效的借款天数'; ok = false }
  return ok
}

async function submitForm() {
  if (!validate()) return
  submitting.value = true
  try {
    await debtStore.addDebt({
      creditor: form.creditor.trim(),
      debtType: form.debtType,
      principal: Number(form.principal),
      totalRepayment: Number(form.totalRepayment),
      loanDays: Number(form.loanDays),
      ...(form.startDate ? { startDate: form.startDate } : {}),
    })
    showForm.value = false
    uni.showToast({ title: '录入成功', icon: 'success' })
  } catch (_) {
    // 全局 toast 已由统一请求层弹出
  } finally {
    submitting.value = false
  }
}

async function handleDelete(debt) {
  const id = (debt.name || '').split('/').pop() || debt.id
  uni.showModal({
    title: '确认删除',
    content: `删除"${debt.creditor}"？`,
    success: async (res) => {
      if (!res.confirm) return
      try {
        await debtStore.removeDebt(id)
        uni.showToast({ title: '已删除', icon: 'success' })
      } catch (e) {
        uni.showToast({ title: e.message || '删除失败', icon: 'none' })
      }
    },
  })
}

// OCR 拍照
async function handleOcr() {
  try {
    const recognized = await debtStore.startOcr()
    Object.assign(ocrForm, {
      creditor: recognized?.creditor || '',
      debtType: recognized?.debtType || 'CONSUMER_LOAN',
      principal: String(recognized?.principal || ''),
      totalRepayment: String(recognized?.totalRepayment || ''),
      loanDays: String(recognized?.loanDays || ''),
      confidence: recognized?.confidenceScore || 0,
    })
    currentOcrTaskId = debtStore.ocrTask?.taskId
    showOcrConfirm.value = true
  } catch (_) {
    // 全局 toast 已由统一请求层弹出
  }
}

async function submitOcrConfirm() {
  if (!currentOcrTaskId) return
  submitting.value = true
  try {
    await debtStore.confirmOcr(currentOcrTaskId, {
      creditor: ocrForm.creditor,
      debtType: ocrForm.debtType,
      principal: Number(ocrForm.principal),
      totalRepayment: Number(ocrForm.totalRepayment),
      loanDays: Number(ocrForm.loanDays),
    })
    showOcrConfirm.value = false
    uni.showToast({ title: '识别录入成功', icon: 'success' })
  } catch (_) {
    // 全局 toast 已由统一请求层弹出
  } finally {
    submitting.value = false
  }
}

function cancelOcrConfirm() {
  showOcrConfirm.value = false
  debtStore.cancelOcr()
}

async function handleGoReport() {
  if (debtStore.confirmedCount === 0) {
    uni.showToast({ title: '请先确认至少 1 笔债务', icon: 'none' })
    return
  }
  uni.showLoading({ title: '分析中...' })
  try {
    await profileStore.triggerCalculation()
    uni.hideLoading()
    funnelStore.advanceStep(4)
    uni.navigateTo({ url: '/pages/page4-loss-report/index' })
  } catch (_) {
    uni.hideLoading()
    // 全局 toast 已由统一请求层弹出
  }
}

// 初始化：在页面挂载时加载，避免模块导入时即发请求
onMounted(() => {
  debtStore.loadDebts()
})

// 离开页面时清理 OCR 轮询定时器，防止内存泄漏
onUnmounted(() => {
  debtStore.cancelOcr()
})
</script>

<style scoped>
/* ===== 页面 ===== */
.page { min-height: 100vh; background: #F8FAFE; display: flex; flex-direction: column; }

/* 顶部 */
.page-header { padding: 48rpx 32rpx 24rpx; background: #fff; }
.header-title { display: block; font-size: 36rpx; font-weight: 700; color: #1A1A2E; line-height: 1.4; }
.header-sub { display: block; font-size: 26rpx; color: #6B7280; margin-top: 8rpx; }

/* 统计栏 */
.stat-bar { margin: 16rpx 32rpx; padding: 20rpx 24rpx; background: #F0F4F8; border-radius: 12rpx; }
.stat-bar-active { background: #D5E8F0; }
.stat-text { font-size: 28rpx; color: #2E75B6; }
.stat-num { font-weight: 700; color: #2E75B6; }
.stat-money { font-weight: 700; color: #E8852A; }
.stat-text-empty { font-size: 28rpx; color: #6B7280; }

/* 债务列表 */
.debt-list { flex: 1; padding: 0 32rpx; }
.debt-card { background: #fff; border-radius: 16rpx; padding: 24rpx; margin-bottom: 16rpx; display: flex; flex-direction: row; align-items: center; box-shadow: 0 2rpx 8rpx rgba(0,0,0,.06); }
.debt-main { flex: 1; display: flex; flex-direction: row; align-items: center; gap: 16rpx; }
.debt-badge { padding: 4rpx 16rpx; border-radius: 20rpx; font-size: 22rpx; font-weight: 600; }
.badge-blue { background: #D5E8F0; color: #2E75B6; }
.badge-orange { background: #FFF3E8; color: #E8852A; }
.badge-green { background: #E8F8F0; color: #2BAF7E; }
.badge-teal { background: #E0F7F7; color: #0097A7; }
.badge-gray { background: #F3F4F6; color: #6B7280; }
.debt-info { display: flex; flex-direction: column; }
.debt-creditor { font-size: 28rpx; font-weight: 600; color: #1A1A2E; }
.debt-principal { font-size: 24rpx; color: #6B7280; margin-top: 4rpx; }
.debt-right { display: flex; flex-direction: column; align-items: flex-end; gap: 6rpx; }
.debt-apr { font-size: 26rpx; font-weight: 700; color: #E8852A; }
.debt-status { font-size: 22rpx; color: #9CA3AF; }
.s-confirmed,.s-in_profile { color: #2BAF7E; }
.debt-delete { font-size: 40rpx; color: #D1D5DB; padding: 0 8rpx; line-height: 1; margin-left: 8rpx; }

/* 空态 */
.empty-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx; }
.empty-icon { font-size: 80rpx; margin-bottom: 24rpx; }
.empty-title { font-size: 32rpx; color: #6B7280; font-weight: 600; }
.empty-hint { font-size: 26rpx; color: #9CA3AF; margin-top: 8rpx; }

/* OCR 遮罩 */
.ocr-mask { position: fixed; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: center; justify-content: center; z-index: 999; }
.ocr-card { background: #fff; border-radius: 24rpx; padding: 48rpx 64rpx; display: flex; flex-direction: column; align-items: center; gap: 16rpx; }
.spinner { width: 60rpx; height: 60rpx; border: 4rpx solid #D5E8F0; border-top-color: #2E75B6; border-radius: 50%; animation: spin .8s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.ocr-msg { font-size: 28rpx; color: #1A1A2E; }
.ocr-cancel { font-size: 26rpx; color: #9CA3AF; margin-top: 8rpx; }

/* 底部栏 */
.bottom-bar { background: #fff; padding: 24rpx 32rpx; padding-bottom: calc(24rpx + env(safe-area-inset-bottom)); box-shadow: 0 -2rpx 12rpx rgba(0,0,0,.08); }
.methods { display: flex; flex-direction: row; gap: 16rpx; margin-bottom: 20rpx; }
.method-btn { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 20rpx 12rpx; background: #F8FAFE; border: 2rpx solid #E5E7EB; border-radius: 16rpx; gap: 8rpx; }
.method-primary { background: #D5E8F0; border-color: #2E75B6; }
.method-icon { font-size: 36rpx; }
.method-label { font-size: 24rpx; color: #1A1A2E; }
.cta-btn { width: 100%; height: 96rpx; background: linear-gradient(135deg,#2E75B6,#1a5fa0); color: #fff; font-size: 32rpx; font-weight: 700; border-radius: 48rpx; border: none; }
.cta-disabled { background: #D1D5DB; color: #9CA3AF; }
.cta-tip { display: block; text-align: center; font-size: 24rpx; color: #9CA3AF; margin-top: 12rpx; }

/* 弹窗通用 */
.overlay { position: fixed; inset: 0; background: rgba(0,0,0,.5); display: flex; align-items: flex-end; z-index: 1000; }
.sheet { width: 100%; max-height: 90vh; background: #fff; border-radius: 32rpx 32rpx 0 0; display: flex; flex-direction: column; }
.sheet-sm { max-height: 60vh; }
.sheet-header { padding: 32rpx; border-bottom: 2rpx solid #E5E7EB; display: flex; flex-direction: row; align-items: flex-start; justify-content: space-between; }
.sheet-title { font-size: 32rpx; font-weight: 700; color: #1A1A2E; }
.sheet-sub { font-size: 24rpx; color: #6B7280; margin-top: 4rpx; }
.sheet-close { font-size: 48rpx; color: #9CA3AF; line-height: 1; padding: 0 8rpx; }
.sheet-scroll { flex: 1; max-height: 65vh; }
.form-wrap { padding: 24rpx 32rpx; }

/* 表单字段 */
.field { margin-bottom: 28rpx; }
.field-label { display: block; font-size: 26rpx; color: #6B7280; margin-bottom: 12rpx; }
.required { color: #E8852A; }
.field-input { width: 100%; height: 80rpx; background: #F8FAFE; border: 2rpx solid #E5E7EB; border-radius: 12rpx; padding: 0 20rpx; font-size: 28rpx; color: #1A1A2E; box-sizing: border-box; }
.field-err { display: block; font-size: 24rpx; color: #E8852A; margin-top: 8rpx; }
.field-picker { height: 80rpx; background: #F8FAFE; border: 2rpx solid #E5E7EB; border-radius: 12rpx; padding: 0 20rpx; display: flex; align-items: center; }
.picker-val { font-size: 28rpx; color: #1A1A2E; }
.picker-ph { font-size: 28rpx; color: #9CA3AF; }

/* 类型芯片 */
.type-chips { display: flex; flex-direction: row; flex-wrap: wrap; gap: 12rpx; }
.chip { padding: 12rpx 24rpx; background: #F8FAFE; border: 2rpx solid #E5E7EB; border-radius: 40rpx; font-size: 26rpx; color: #6B7280; }
.chip-active { background: #D5E8F0; border-color: #2E75B6; color: #2E75B6; font-weight: 600; }

/* APR 预览 */
.apr-preview { background: #FFF3E8; border-radius: 12rpx; padding: 20rpx 24rpx; display: flex; align-items: center; gap: 12rpx; }
.apr-label { font-size: 26rpx; color: #6B7280; }
.apr-value { font-size: 32rpx; font-weight: 700; color: #E8852A; }
.apr-note { font-size: 22rpx; color: #9CA3AF; flex: 1; text-align: right; }

/* 弹窗底部 */
.sheet-footer { padding: 24rpx 32rpx; padding-bottom: calc(24rpx + env(safe-area-inset-bottom)); border-top: 2rpx solid #E5E7EB; display: flex; gap: 16rpx; }
.btn-cancel { flex: 1; height: 88rpx; background: #F3F4F6; color: #6B7280; font-size: 28rpx; border-radius: 44rpx; border: none; }
.btn-confirm { flex: 2; height: 88rpx; background: linear-gradient(135deg,#2E75B6,#1a5fa0); color: #fff; font-size: 28rpx; font-weight: 700; border-radius: 44rpx; border: none; }

/* 模板列表 */
.tpl-list { padding: 16rpx 32rpx; }
.tpl-item { display: flex; flex-direction: row; align-items: center; justify-content: space-between; padding: 28rpx 0; border-bottom: 2rpx solid #F3F4F6; }
.tpl-name { font-size: 30rpx; font-weight: 600; color: #1A1A2E; }
.tpl-type { font-size: 24rpx; color: #6B7280; }

/* OCR 置信度 */
.confidence-bar { display: flex; flex-direction: row; align-items: center; gap: 12rpx; margin-bottom: 20rpx; padding: 16rpx 20rpx; background: #E8F8F0; border-radius: 12rpx; }
.confidence-label { font-size: 26rpx; color: #6B7280; }
.confidence-val { font-size: 28rpx; font-weight: 700; color: #2BAF7E; }
</style>
