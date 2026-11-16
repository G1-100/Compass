/// <reference types="vitest/config" />
import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';

// In development, the browser calls /api/... on the Vite server and Vite forwards it to Tomcat.
// Set VITE_BACKEND_URL in .env.local if your Tomcat context path is not "/" (e.g. http://localhost:8080/compass-api).
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), '');
  const backend = env.VITE_BACKEND_URL || 'http://localhost:8080';
  const backendUrl = new URL(backend);
  const contextPath = backendUrl.pathname.replace(/\/$/, '');

  return {
    plugins: [react()],
    server: {
      port: 5173,
      proxy: {
        '/api': {
          target: backendUrl.origin,
          changeOrigin: true,
          rewrite: (path) => contextPath + path,
        },
      },
    },
    test: {
      environment: 'jsdom',
      globals: true,
      setupFiles: ['./src/test/setup.ts'],
    },
  };
});
