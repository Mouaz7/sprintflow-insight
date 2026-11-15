import { defineConfig } from 'vitest/config';
import react from '@vitejs/plugin-react';

export default defineConfig({
  plugins: [react()],
  html: { cspNonce: '__SPRINTFLOW_CSP_NONCE__' },
  server: { port: 5173, proxy: { '/api': 'http://localhost:8081' } },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/test/setup.ts' }
});
