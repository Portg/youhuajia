import { defineConfig } from 'vite'
import uni from '@dcloudio/vite-plugin-uni'

export default defineConfig({
  plugins: [uni()],
  build: {
    // 分包：vendor（vue/pinia）独立缓存，业务代码按需加载
    rollupOptions: {
      output: {
        manualChunks(id) {
          if (id.includes('node_modules')) {
            if (id.includes('vue') || id.includes('pinia')) return 'vendor'
            if (id.includes('ucharts') || id.includes('qiun')) return 'charts'
          }
        },
      },
    },
    // 压缩
    minify: 'terser',
    terserOptions: {
      compress: { drop_console: true, drop_debugger: true },
    },
    // chunk 大小警告阈值
    chunkSizeWarningLimit: 500,
  },
  css: {
    // scss 预处理：避免每个组件重复编译变量
    preprocessorOptions: {
      scss: {
        silenceDeprecations: ['legacy-js-api'],
      },
    },
  },
})
