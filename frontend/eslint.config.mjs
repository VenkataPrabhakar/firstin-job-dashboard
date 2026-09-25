import js from '@eslint/js';
import react from 'eslint-plugin-react';
import tseslint from 'typescript-eslint';

// dangerouslySetInnerHTML is banned (docs/UI-UX.md: hostile HTML must render
// as inert text). react/no-danger fails the build if it ever appears.
export default tseslint.config(
  { ignores: ['dist', 'node_modules'] },
  js.configs.recommended,
  ...tseslint.configs.recommended,
  {
    // Node scripts (test shim): allow Node globals.
    files: ['scripts/**/*.mjs'],
    languageOptions: {
      globals: {
        process: 'readonly',
        URL: 'readonly',
      },
    },
  },
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { react },
    settings: { react: { version: 'detect' } },
    rules: {
      'react/no-danger': 'error',
      'react/react-in-jsx-scope': 'off',
      'react/jsx-uses-react': 'off',
    },
  },
);
