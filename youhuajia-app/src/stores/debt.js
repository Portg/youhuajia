/**
 * 债务状态管理 Store (Pinia)
 * 管理 Page 3 的债务列表、OCR 状态、录入统计
 */
import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import { listDebts, createDebt, deleteDebt, confirmDebt, createOcrTask, getOcrTask, confirmOcrTask } from '../api/debt.js'
import { calculateApr } from '../api/engine.js'

export const useDebtStore = defineStore('debt', () => {
  const debts = ref([])
  const loading = ref(false)
  const ocrTask = ref(null) // { status, taskId, recognizedDebt }
  let ocrPollingTimer = null
  const error = ref('')

  // 已确认债务数量
  const confirmedCount = computed(() =>
    debts.value.filter(d => d.status === 'CONFIRMED' || d.status === 'IN_PROFILE').length
  )

  const totalCount = computed(() => debts.value.length)

  // 估算潜在节省（基于超额利息，APR 市场均值 18%）
  const estimatedSaving = computed(() => {
    const MARKET_RATE = 18
    let total = 0
    debts.value.forEach(d => {
      if (d.apr && Number(d.apr) > MARKET_RATE && d.principal) {
        const excessRate = (Number(d.apr) - MARKET_RATE) / 100
        total += Number(d.principal) * excessRate * 3
      }
    })
    return Math.round(total)
  })

  // 请求去重
  let _loadPromise = null

  async function loadDebts() {
    if (_loadPromise) return _loadPromise
    loading.value = true
    error.value = ''
    _loadPromise = listDebts()
      .then(res => { debts.value = res.debts || [] })
      .catch(e => { error.value = e.message || '加载失败' })
      .finally(() => { loading.value = false; _loadPromise = null })
    return _loadPromise
  }

  async function addDebt(debtData) {
    const requestId = generateUuid()
    const res = await createDebt(debtData, requestId)
    const newDebt = res.debt || res
    // 试算 APR
    try {
      const aprRes = await calculateApr(newDebt.principal, newDebt.totalRepayment, newDebt.loanDays)
      newDebt.apr = aprRes.apr
    } catch (_) {}
    debts.value.push(newDebt)
    return newDebt
  }

  async function removeDebt(debtId) {
    await deleteDebt(debtId)
    debts.value = debts.value.filter(d => getDebtId(d) !== debtId)
  }

  async function doConfirmDebt(debtId) {
    const res = await confirmDebt(debtId)
    const idx = debts.value.findIndex(d => getDebtId(d) === debtId)
    if (idx >= 0) {
      debts.value[idx] = { ...debts.value[idx], ...(res.debt || res), status: 'CONFIRMED' }
    }
  }

  // OCR 拍照录入
  async function startOcr() {
    return new Promise((resolve, reject) => {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['camera'],
        success: async (res) => {
          const filePath = res.tempFilePaths[0]
          try {
            ocrTask.value = { status: 'UPLOADING', taskId: null, recognizedDebt: null }
            const taskRes = await createOcrTask(filePath)
            const taskId = taskRes.taskId || (taskRes.name || '').split('/').pop()
            ocrTask.value = { status: 'PROCESSING', taskId, recognizedDebt: null }
            pollOcr(taskId, resolve, reject)
          } catch (e) {
            ocrTask.value = null
            reject(e)
          }
        },
        fail: (err) => reject({ message: err.errMsg || '相机调用失败' }),
      })
    })
  }

  function pollOcr(taskId, resolve, reject) {
    let attempts = 0
    const MAX = 30
    const poll = async () => {
      try {
        const res = await getOcrTask(taskId)
        if (res.status === 'COMPLETED') {
          ocrTask.value = { status: 'COMPLETED', taskId, recognizedDebt: res.recognizedDebt }
          resolve(res.recognizedDebt)
        } else if (res.status === 'FAILED') {
          ocrTask.value = null
          reject({ message: '识别失败，请手动录入' })
        } else {
          attempts++
          if (attempts >= MAX) {
            ocrTask.value = null
            reject({ message: '识别超时，请手动录入' })
            return
          }
          ocrPollingTimer = setTimeout(poll, 2000)
        }
      } catch (e) {
        ocrTask.value = null
        reject(e)
      }
    }
    poll()
  }

  async function confirmOcr(taskId, debtData) {
    const res = await confirmOcrTask(taskId, debtData)
    const newDebt = res.debt || res
    debts.value.push(newDebt)
    ocrTask.value = null
    return newDebt
  }

  function cancelOcr() {
    if (ocrPollingTimer) { clearTimeout(ocrPollingTimer); ocrPollingTimer = null }
    ocrTask.value = null
  }

  return {
    debts, loading, ocrTask, error,
    confirmedCount, totalCount, estimatedSaving,
    loadDebts, addDebt, removeDebt, doConfirmDebt,
    startOcr, confirmOcr, cancelOcr,
  }
})

function getDebtId(debt) {
  return (debt.name || '').split('/').pop() || debt.id
}

function generateUuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const r = Math.random() * 16 | 0
    return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16)
  })
}
