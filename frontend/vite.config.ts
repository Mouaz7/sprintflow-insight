import { defineConfig } from 'vitest/config';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import babel from '@rolldown/plugin-babel';

export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset({ target: '19' })] }),
  ],
  html: { cspNonce: '__SPRINTFLOW_CSP_NONCE__' },
  server: {
    port: 5173,
    proxy: {
      '/api': 'http://localhost:8081',
      // The backend only accepts WebSocket handshakes from its public origin.
      '/ws': {
        target: 'ws://localhost:8081',
        ws: true,
        configure: (proxy) =>
          proxy.on('proxyReqWs', (request) =>
            request.setHeader('origin', 'http://localhost:8081'),
          ),
      },
    },
  },
  test: {
    environment: 'jsdom',
    maxWorkers: 4,
    globals: true,
    setupFiles: './src/test/setup.ts',
  },
});
