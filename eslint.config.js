// ESLint flat config (ESLint 9) for the frontend (JavaScript + JSX, React 19).
// Lives at the repo root because the JS tooling is hoisted here (see package.json).
// Rules are intentionally lenient for gradual adoption on the existing ~188-file
// codebase: most issues are `warn`, so pre-commit lint-staged (--fix) improves
// files as they are touched without blocking legacy code.
import js from '@eslint/js';
import globals from 'globals';
import react from 'eslint-plugin-react';
import reactHooks from 'eslint-plugin-react-hooks';
import reactRefresh from 'eslint-plugin-react-refresh';
import prettier from 'eslint-config-prettier';

export default [
  {
    ignores: [
      '**/node_modules/**',
      '**/dist/**',
      '**/coverage/**',
      '**/target/**',
      '**/build/**',
      '**/*.config.js',
      '**/*.config.mjs',
      // one-off dev scripts (repo debt, not app code)
      'frontend/checkDuplicates.js',
      'frontend/check_duplicates.js',
      'frontend/cleanupPatients.js',
    ],
  },
  {
    files: ['frontend/**/*.{js,jsx}'],
    languageOptions: {
      ecmaVersion: 2022,
      sourceType: 'module',
      globals: { ...globals.browser, ...globals.node },
      parserOptions: { ecmaFeatures: { jsx: true } },
    },
    // Pinned (not 'detect') because React lives in frontend/node_modules, not the
    // root where this shared config resolves — avoids a "react not installed" warning.
    settings: { react: { version: '19.0' } },
    plugins: {
      react,
      'react-hooks': reactHooks,
      'react-refresh': reactRefresh,
    },
    rules: {
      ...js.configs.recommended.rules,
      ...react.configs.flat.recommended.rules,
      ...reactHooks.configs.recommended.rules,
      // React 19 / automatic JSX runtime — no need to import React in scope.
      'react/react-in-jsx-scope': 'off',
      'react/prop-types': 'off',
      'react-refresh/only-export-components': ['warn', { allowConstantExport: true }],
      'no-unused-vars': ['warn', { argsIgnorePattern: '^_', varsIgnorePattern: '^_' }],
      'no-empty': ['warn', { allowEmptyCatch: true }],
    },
  },
  // Must be last: disables ESLint stylistic rules that conflict with Prettier.
  prettier,
];
