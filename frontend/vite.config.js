import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url))
    },
  },
  server: {
    allowedHosts: ['aware-guest-workers-creatures.trycloudflare.com','7ac505e4.r9.cpolar.top'],
    proxy: {
      '/api': {//只要前端请求地址以 /api 开头，就交给代理处理。
        target: 'http://localhost:8080',//代理目标是后端服务器。
        changeOrigin: true
      }
    }
  }
})
