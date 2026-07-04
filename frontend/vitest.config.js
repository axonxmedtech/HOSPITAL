import { defineConfig } from 'vitest/config'
import react from '@vitejs/plugin-react'

// Test config is kept separate from vite.config.js so the production build stays
// untouched. CI runs `vitest run --coverage` and ingests coverage/lcov.info.
export default defineConfig({
    plugins: [react()],
    test: {
        environment: 'jsdom',
        globals: true,
        setupFiles: './src/test/setup.js',
        css: false,
        coverage: {
            provider: 'v8',
            reporter: ['text', 'lcov', 'json-summary'],
            reportsDirectory: './coverage',
        },
    },
})
