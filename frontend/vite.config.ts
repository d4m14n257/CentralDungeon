import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import react from '@vitejs/plugin-react'
import { defineConfig } from 'vitest/config'

export default defineConfig({
  // Tailwind 4 is a Vite plugin: there is no tailwind.config.ts, the theme lives
  // in src/styles/globals.css and content detection is automatic.
  plugins: [react(), tailwindcss()],
  resolve: {
    alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) },
  },
  test: {
    environment: 'jsdom',
    globals: true,
    setupFiles: './src/test/setup.ts',
    // Playwright owns e2e/; Vitest must not try to run those specs.
    exclude: ['**/node_modules/**', '**/dist/**', 'e2e/**'],
  },
})
