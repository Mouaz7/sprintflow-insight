import { defineConfig } from 'vitest/config';
import react, { reactCompilerPreset } from '@vitejs/plugin-react';
import babel from '@rolldown/plugin-babel';
import { VitePWA } from 'vite-plugin-pwa';

export default defineConfig({
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset({ target: '19' })] }),
    VitePWA({
      injectRegister: null,
      registerType: 'autoUpdate',
      manifest: {
        id: '/',
        name: 'SprintFlow Insight',
        short_name: 'SprintFlow',
        description: 'Plan and follow work with your team.',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        theme_color: '#181f39',
        background_color: '#f2f4fa',
        icons: [
          {
            src: '/icons/sprintflow-192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: '/icons/sprintflow-512.png',
            sizes: '512x512',
            type: 'image/png',
          },
          {
            src: '/icons/sprintflow-maskable-512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'maskable',
          },
        ],
      },
      workbox: {
        inlineWorkboxRuntime: true,
        navigateFallback: '',
        directoryIndex: '',
        globPatterns: ['**/*.{js,css,html,woff2,png,webmanifest}'],
        runtimeCaching: [
          {
            urlPattern: ({ request, url, sameOrigin }) =>
              sameOrigin &&
              request.mode === 'navigate' &&
              !/^\/(api|ws|actuator|report|reset-password|email-preferences)(\/|$)/.test(
                url.pathname,
              ),
            handler: 'NetworkOnly',
            options: {
              precacheFallback: { fallbackURL: '/index.html' },
            },
          },
        ],
      },
    }),
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
        headers: { origin: 'http://localhost:8081' },
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
