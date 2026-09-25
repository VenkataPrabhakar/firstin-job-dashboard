import react from '@vitejs/plugin-react';
import { defineConfig } from 'vitest/config';

// Dev proxy: /api -> local Spring Boot backend (docs/PHASE-2.md).
export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.ts'],
    // globals: true lets @testing-library/react auto-cleanup between tests.
    globals: true,
  },
});
