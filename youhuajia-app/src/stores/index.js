/**
 * Pinia Store 统一入口
 */
import { createPinia } from 'pinia'
import { createUnistorage } from 'pinia-plugin-unistorage'

const pinia = createPinia()
pinia.use(createUnistorage())

export default pinia
