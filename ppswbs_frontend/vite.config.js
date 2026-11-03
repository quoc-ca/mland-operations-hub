import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// Frontend foundation (002 T088): build with Vite, test with Vitest + Testing Library on jsdom.
export default defineConfig({
  plugins: [react()],
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
});
