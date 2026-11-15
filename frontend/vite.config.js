import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// https://vitejs.dev/config/
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  esbuild:
    mode === 'production'
      ? {
          // Patient data must never reach the browser console in a deployed build.
          // console.log/info/debug calls are removed from the bundle; console.warn and
          // console.error stay so real failures remain visible. ESLint (no-console)
          // stops new log calls being written in the first place.
          pure: ['console.log', 'console.info', 'console.debug'],
          drop: ['debugger'],
        }
      : undefined,
}));
