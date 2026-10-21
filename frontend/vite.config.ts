import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '')
  return { plugins: [vue()], server: { port: 5173, proxy: { '/api': { target: env.API_PROXY_TARGET || 'http://127.0.0.1:18080', changeOrigin: true } } }, build: { chunkSizeWarningLimit: 1200, rollupOptions: { output: { manualChunks: { vue: ['vue', 'vue-router', 'pinia'], element: ['element-plus'], charts: ['echarts'] } } } } }
})
