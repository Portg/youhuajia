<template>
  <view class="page">
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
          <text class="debt-apr" v-if="debt.apr">APR {{ formatRate(debt.apr) }}</text>
          <text class="debt-status" :class="'s-' + (debt.status || '').toLowerCase()">
            {{ statusLabel(debt.status) }}
          </text>
        </view>
        <text class="debt-delete" @tap="handleDelete(debt)">×</text>
      </view>
    </scroll-view>

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
              <text class="apr-label">预计年化利率：</text>
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
import { ref, reactive, computed, watch } from 'vue'
import { useDebtStore } from '../../src/stores/debt.js'
import { useProfileStore } from '../../src/stores/profile.js'
import { calculateApr } from '../../src/api/engine.js'

const debtStore = useDebtStore()
const profileStore = useProfileStore()

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
  } catch (e) {
    uni.showToast({ title: e.message || '录入失败', icon: 'none' })
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
        uni.showToast({ title: '删除失败', icon: 'none' })
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
  } catch (e) {
    uni.showToast({ title: e.message || '识别失败，请手动录入', icon: 'none' })
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
  } catch (e) {
    uni.showToast({ title: e.message || '录入失败', icon: 'none' })
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
    uni.navigateTo({ url: '/pages/page4-loss-report/index' })
  } catch (e) {
    uni.hideLoading()
    uni.showToast({ title: e.message || '分析失败，请稍后重试', icon: 'none' })
  }
}

// 初始化
debtStore.loadDebts()
</script>

<style scoped>
/* ===== 页面 — 2026 Edition ===== */
.page { min-height: 100vh; background: linear-gradient(168deg, #F0F4FA 0%, #FAF8F5 35%, #F5F7FA 100%); display: flex; flex-direction: column; }

/* 顶部 */
.page-header { padding: 48rpx 40rpx 24rpx; background: rgba(255,255,255,0.72); backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px); }
.header-title { display: block; font-size: 40rpx; font-weight: 900; color: #0F172A; line-height: 1.4; letter-spacing: -1rpx; }
.header-sub { display: block; font-size: 26rpx; color: #64748B; margin-top: 8rpx; }

/* 统计栏 */
.stat-bar { margin: 16rpx 40rpx; padding: 20rpx 24rpx; background: #F5F7FA; border-radius: 20rpx; border: 1rpx solid #E8ECF1; }
.stat-bar-active { background: rgba(27,109,178,0.08); border-color: rgba(27,109,178,0.12); }
.stat-text { font-size: 28rpx; color: #1B6DB2; }
.stat-num { font-weight: 700; color: #1B6DB2; }
.stat-money { font-weight: 900; color: #D97B1A; }
.stat-text-empty { font-size: 28rpx; color: #64748B; }

/* 债务列表 */
.debt-list { flex: 1; padding: 0 40rpx; }
.debt-card { background: #fff; border-radius: 20rpx; padding: 24rpx; margin-bottom: 16rpx; display: flex; flex-direction: row; align-items: center; box-shadow: 0 2rpx 8rpx rgba(15,23,42,.04), 0 4rpx 16rpx rgba(15,23,42,.02); }
.debt-main { flex: 1; display: flex; flex-direction: row; align-items: center; gap: 16rpx; }
.debt-badge { padding: 6rpx 18rpx; border-radius: 200rpx; font-size: 22rpx; font-weight: 600; }
.badge-blue { background: #E3F0FA; color: #1B6DB2; }
.badge-orange { background: #FEF3E2; color: #D97B1A; }
.badge-green { background: #E6F9F0; color: #0FA968; }
.badge-teal { background: #E0F7F7; color: #0097A7; }
.badge-gray { background: #F1F5F9; color: #64748B; }
.debt-info { display: flex; flex-direction: column; }
.debt-creditor { font-size: 30rpx; font-weight: 600; color: #0F172A; }
.debt-principal { font-size: 24rpx; color: #64748B; margin-top: 4rpx; }
.debt-right { display: flex; flex-direction: column; align-items: flex-end; gap: 6rpx; }
.debt-apr { font-size: 26rpx; font-weight: 900; color: #D97B1A; }
.debt-status { font-size: 22rpx; color: #94A3B8; }
.s-confirmed,.s-in_profile { color: #0FA968; }
.debt-delete { font-size: 40rpx; color: #CBD5E1; padding: 0 8rpx; line-height: 1; margin-left: 8rpx; }

/* 空态 */
.empty-wrap { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx; }
.empty-icon { font-size: 80rpx; margin-bottom: 24rpx; }
.empty-title { font-size: 34rpx; color: #64748B; font-weight: 700; }
.empty-hint { font-size: 26rpx; color: #94A3B8; margin-top: 8rpx; }

/* OCR 遮罩 */
.ocr-mask { position: fixed; inset: 0; background: rgba(15,23,42,.45); display: flex; align-items: center; justify-content: center; z-index: 999; backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); }
.ocr-card { background: #fff; border-radius: 28rpx; padding: 48rpx 64rpx; display: flex; flex-direction: column; align-items: center; gap: 16rpx; box-shadow: 0 12rpx 36rpx rgba(15,23,42,.08), 0 24rpx 64rpx rgba(15,23,42,.10); }
.spinner { width: 56rpx; height: 56rpx; border: 3rpx solid #E8ECF1; border-top-color: #1B6DB2; border-radius: 50%; animation: spin .7s linear infinite; }
@keyframes spin { to { transform: rotate(360deg); } }
.ocr-msg { font-size: 30rpx; color: #0F172A; font-weight: 500; }
.ocr-cancel { font-size: 26rpx; color: #94A3B8; margin-top: 8rpx; }

/* 底部栏 */
.bottom-bar { background: rgba(255,255,255,0.72); backdrop-filter: blur(40px); -webkit-backdrop-filter: blur(40px); padding: 24rpx 40rpx; padding-bottom: calc(24rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #F1F5F9; }
.methods { display: flex; flex-direction: row; gap: 16rpx; margin-bottom: 20rpx; }
.method-btn { flex: 1; display: flex; flex-direction: column; align-items: center; padding: 20rpx 12rpx; background: #F5F7FA; border: 2rpx solid #E8ECF1; border-radius: 20rpx; gap: 8rpx; transition: transform .15s cubic-bezier(.25,.46,.45,.94), opacity .15s; }
.method-btn:active { transform: scale(0.97); opacity: 0.88; }
.method-primary { background: rgba(27,109,178,0.08); border-color: rgba(27,109,178,0.2); }
.method-icon { font-size: 36rpx; }
.method-label { font-size: 24rpx; color: #0F172A; font-weight: 500; }
.cta-btn { width: 100%; height: 100rpx; background: linear-gradient(135deg, #3A9BDC 0%, #1B6DB2 50%, #134E82 100%); color: #fff; font-size: 34rpx; font-weight: 700; border-radius: 200rpx; border: none; box-shadow: 0 8rpx 32rpx rgba(27,109,178,.28); }
.cta-disabled { background: #CBD5E1; color: #94A3B8; box-shadow: none; }
.cta-tip { display: block; text-align: center; font-size: 22rpx; color: #94A3B8; margin-top: 12rpx; }

/* 弹窗通用 */
.overlay { position: fixed; inset: 0; background: rgba(15,23,42,.45); display: flex; align-items: flex-end; z-index: 1000; backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px); }
.sheet { width: 100%; max-height: 90vh; background: #fff; border-radius: 36rpx 36rpx 0 0; display: flex; flex-direction: column; }
.sheet-sm { max-height: 60vh; }
.sheet-header { padding: 32rpx 40rpx; border-bottom: 1rpx solid #F1F5F9; display: flex; flex-direction: row; align-items: flex-start; justify-content: space-between; }
.sheet-title { font-size: 34rpx; font-weight: 700; color: #0F172A; }
.sheet-sub { font-size: 24rpx; color: #64748B; margin-top: 4rpx; }
.sheet-close { font-size: 48rpx; color: #94A3B8; line-height: 1; padding: 0 8rpx; }
.sheet-scroll { flex: 1; max-height: 65vh; }
.form-wrap { padding: 24rpx 40rpx; }

/* 表单字段 */
.field { margin-bottom: 28rpx; }
.field-label { display: block; font-size: 26rpx; color: #64748B; margin-bottom: 12rpx; font-weight: 500; }
.required { color: #D97B1A; }
.field-input { width: 100%; height: 88rpx; background: #F5F7FA; border: 2rpx solid #E8ECF1; border-radius: 20rpx; padding: 0 24rpx; font-size: 30rpx; color: #0F172A; box-sizing: border-box; transition: border-color .15s, box-shadow .15s; }
.field-err { display: block; font-size: 22rpx; color: #D97B1A; margin-top: 8rpx; }
.field-picker { height: 88rpx; background: #F5F7FA; border: 2rpx solid #E8ECF1; border-radius: 20rpx; padding: 0 24rpx; display: flex; align-items: center; }
.picker-val { font-size: 30rpx; color: #0F172A; }
.picker-ph { font-size: 30rpx; color: #94A3B8; }

/* 类型芯片 */
.type-chips { display: flex; flex-direction: row; flex-wrap: wrap; gap: 12rpx; }
.chip { padding: 14rpx 28rpx; background: #F5F7FA; border: 2rpx solid #E8ECF1; border-radius: 200rpx; font-size: 26rpx; color: #64748B; transition: all .15s; }
.chip:active { transform: scale(0.97); }
.chip-active { background: #E3F0FA; border-color: #1B6DB2; color: #1B6DB2; font-weight: 600; box-shadow: 0 0 0 3rpx rgba(27,109,178,0.08); }

/* APR 预览 */
.apr-preview { background: rgba(217,123,26,0.06); border: 1rpx solid rgba(217,123,26,0.1); border-radius: 20rpx; padding: 20rpx 24rpx; display: flex; align-items: center; gap: 12rpx; }
.apr-label { font-size: 26rpx; color: #64748B; }
.apr-value { font-size: 34rpx; font-weight: 900; color: #D97B1A; }
.apr-note { font-size: 22rpx; color: #94A3B8; flex: 1; text-align: right; }

/* 弹窗底部 */
.sheet-footer { padding: 24rpx 40rpx; padding-bottom: calc(24rpx + env(safe-area-inset-bottom)); border-top: 1rpx solid #F1F5F9; display: flex; gap: 16rpx; }
.btn-cancel { flex: 1; height: 92rpx; background: #F1F5F9; color: #64748B; font-size: 30rpx; border-radius: 200rpx; border: none; font-weight: 500; }
.btn-confirm { flex: 2; height: 92rpx; background: linear-gradient(135deg, #3A9BDC 0%, #1B6DB2 50%, #134E82 100%); color: #fff; font-size: 30rpx; font-weight: 700; border-radius: 200rpx; border: none; box-shadow: 0 8rpx 32rpx rgba(27,109,178,.28); }

/* 模板列表 */
.tpl-list { padding: 16rpx 40rpx; }
.tpl-item { display: flex; flex-direction: row; align-items: center; justify-content: space-between; padding: 28rpx 0; border-bottom: 1rpx solid #F1F5F9; }
.tpl-item:active { opacity: 0.7; }
.tpl-name { font-size: 30rpx; font-weight: 600; color: #0F172A; }
.tpl-type { font-size: 24rpx; color: #64748B; }

/* OCR 置信度 */
.confidence-bar { display: flex; flex-direction: row; align-items: center; gap: 12rpx; margin-bottom: 20rpx; padding: 16rpx 20rpx; background: rgba(15,169,104,0.06); border: 1rpx solid rgba(15,169,104,0.1); border-radius: 20rpx; }
.confidence-label { font-size: 26rpx; color: #64748B; }
.confidence-val { font-size: 30rpx; font-weight: 900; color: #0FA968; }
</style>
