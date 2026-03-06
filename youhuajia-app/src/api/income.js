import { requestSilent } from './request'

export function batchCreateIncomes(incomes) {
  return requestSilent({ url: '/incomes:batchCreate', method: 'POST', data: { incomes } })
}
