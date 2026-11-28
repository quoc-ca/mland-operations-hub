import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// Frontend foundation (002 T088): build with Vite, test with Vitest + Testing Library on jsdom.
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  server: {
    host: true,
    allowedHosts: ['.trycloudflare.com'],
    proxy: {
      '/api': {
        target: process.env.BACKEND_URL
          || loadEnv(mode, process.cwd(), 'BACKEND_URL').BACKEND_URL
          || process.env.VITE_PROXY_BACKEND_DEFAULT
          || 'http://localhost:8080',
        changeOrigin: true,
      },
      '/__/auth': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
      '/__/firebase': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
    },
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
    },
    watch: {
      usePolling: process.env.USE_POLLING === 'true',
    },
  },
  preview: {
    headers: {
      'Cross-Origin-Opener-Policy': 'same-origin-allow-popups',
    },
    proxy: {
      '/__/auth': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
      '/__/firebase': {
        target: 'https://mland-operation-hub.firebaseapp.com',
        changeOrigin: true,
      },
    },
  },
  // The existing local .env came from Firebase's generated snippet and uses
  // unprefixed public config names. Keep VITE_* as the canonical format while
  // exposing only these known Firebase Web config keys for compatibility.
  envPrefix: [
    'VITE_',
    'apiKey',
    'authDomain',
    'projectId',
    'storageBucket',
    'messagingSenderId',
    'appId',
    'measurementId',
  ],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/test/setup.js'],
    css: false,
    restoreMocks: true,
  },
}));
