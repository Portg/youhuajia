/**
 * 金额格式化工具
 * 禁止使用 toFixed，使用 Intl.NumberFormat 保证精度
 */

/**
 * 格式化金额，带人民币符号
 * @param {number|string} amount
 * @param {number} decimals 小数位数，默认2
 * @returns {string} 如 ¥82,400.00
 */
export function formatMoney(amount, decimals = 2) {
  const num = Number(amount);
  if (isNaN(num)) return '¥0.00';
  return '¥' + new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  }).format(num);
}

/**
 * 格式化金额（不带小数）
 * @param {number|string} amount
 * @returns {string} 如 ¥82,400
 */
export function formatMoneyInteger(amount) {
  const num = Math.round(Number(amount));
  if (isNaN(num)) return '¥0';
  return '¥' + new Intl.NumberFormat('zh-CN').format(num);
}

/**
 * 格式化百分比
 * @param {number|string} rate 小数形式（如 0.24）或百分比形式（如 24）
 * @param {boolean} isPercent 是否已经是百分比形式
 * @returns {string} 如 24.0%
 */
export function formatRate(rate, isPercent = true) {
  const num = Number(rate);
  if (isNaN(num)) return '0.0%';
  const pct = isPercent ? num : num * 100;
  return new Intl.NumberFormat('zh-CN', {
    minimumFractionDigits: 1,
    maximumFractionDigits: 1,
  }).format(pct) + '%';
}

/**
 * 格式化大金额为简短形式
 * @param {number|string} amount
 * @returns {string} 如 8.24万 或 82,400
 */
export function formatMoneyShort(amount) {
  const num = Number(amount);
  if (isNaN(num)) return '0';
  if (Math.abs(num) >= 10000) {
    return (num / 10000).toFixed(2) + '万';
  }
  return new Intl.NumberFormat('zh-CN').format(Math.round(num));
}

/**
 * 格式化日期为 YYYY-MM-DD
 * @param {Date|string} date
 * @returns {string}
 */
export function formatDate(date) {
  if (!date) return '';
  const d = new Date(date);
  if (isNaN(d.getTime())) return '';
  const year = d.getFullYear();
  const month = String(d.getMonth() + 1).padStart(2, '0');
  const day = String(d.getDate()).padStart(2, '0');
  return `${year}-${month}-${day}`;
}
