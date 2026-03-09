/**
 * 漏斗埋点工具 — 统一事件上报
 *
 * MVP 阶段仅 console.debug 输出，后续接入友盟/自研上报。
 * 所有事件名、属性在此集中定义，避免硬编码分散在各页面。
 */

/** 标准事件名 */
export const TrackEvent = {
  /** 漏斗页展示 */
  STEP_VIEW: 'step_view',
  /** 漏斗步骤完成 */
  STEP_COMPLETE: 'step_complete',
  /** 用户登录 */
  LOGIN: 'login',
  /** 债务录入 */
  DEBT_ADD: 'debt_add',
  /** 画像计算完成 */
  PROFILE_CALCULATED: 'profile_calculated',
  /** 报告生成 */
  REPORT_GENERATE: 'report_generate',
  /** 报告导出 */
  REPORT_EXPORT: 'report_export',
  /** 低分路径进入 */
  LOW_SCORE_ENTER: 'low_score_enter',
  /** 用户登出 */
  LOGOUT: 'logout',
}

/**
 * 上报事件
 * @param {string} event - TrackEvent 枚举值
 * @param {Record<string, any>} [props] - 附加属性
 */
export function track(event, props = {}) {
  const payload = {
    event,
    timestamp: Date.now(),
    ...props,
  }

  // 平台分离：H5 走 console.debug（Vite 开发），非 H5（小程序）走 uni.reportAnalytics
  // 有意设计为两段独立条件编译，H5 不调用 uni.reportAnalytics，小程序不输出 console
  // #ifdef H5
  console.debug('[Track]', payload)
  // #endif
  // #ifndef H5
  // eslint-disable-next-line no-undef
  if (typeof uni !== 'undefined' && uni.reportAnalytics) {
    uni.reportAnalytics(event, props)
  }
  // #endif
}
