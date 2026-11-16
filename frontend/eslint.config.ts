import js from '@eslint/js';
import tseslint from 'typescript-eslint';
import reactHooks from 'eslint-plugin-react-hooks';
import jsxA11y from 'eslint-plugin-jsx-a11y';
import boundaries from 'eslint-plugin-boundaries';
import prettier from 'eslint-config-prettier';
import globals from 'globals';

const policies = [
  {
    from: { element: { type: 'entry' } },
    allow: { to: { element: { types: ['app'] } } },
  },
  {
    from: { element: { type: 'app' } },
    allow: {
      to: { element: { types: ['app', 'feature', 'shared', 'api'] } },
    },
  },
  {
    from: { element: { type: 'feature' } },
    allow: { to: { element: { types: ['shared', 'api'] } } },
  },
  {
    from: { element: { type: 'feature' } },
    allow: {
      to: {
        element: {
          type: 'feature',
          captured: { feature: '{{from.element.captured.feature}}' },
        },
      },
    },
  },
  {
    from: { element: { type: 'shared' } },
    allow: { to: { element: { types: ['shared', 'api'] } } },
  },
  {
    from: { element: { type: 'api' } },
    allow: { to: { element: { type: 'api' } } },
  },
];

// Test helpers in src/test may be imported by tests only; they compose the
// application providers, so they may import shared and api code.
const testPolicies = [
  ...policies,
  {
    from: { element: { types: ['app', 'feature', 'shared', 'api'] } },
    allow: { to: { element: { type: 'test-setup' } } },
  },
  {
    from: { element: { type: 'test-setup' } },
    allow: { to: { element: { types: ['test-setup', 'shared', 'api'] } } },
  },
];

export default tseslint.config(
  {
    ignores: [
      'dist/**',
      'src/shared/i18n/icu.generated.js',
      'coverage/**',
      'node_modules/**',
      'e2e/playwright-report/**',
      'e2e/test-results/**',
      'e2e/lighthouse-report/**',
    ],
  },
  js.configs.recommended,
  ...tseslint.configs.strict,
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: { globals: globals.browser },
    plugins: { 'react-hooks': reactHooks, 'jsx-a11y': jsxA11y, boundaries },
    settings: {
      'import/resolver': {
        node: { extensions: ['.js', '.jsx', '.ts', '.tsx'] },
      },
      'jsx-a11y/components': { DateInput: 'input' },
      'boundaries/elements': [
        { type: 'app', pattern: 'src/app' },
        { type: 'feature', pattern: 'src/features/*', capture: ['feature'] },
        { type: 'shared', pattern: 'src/shared/*' },
        { type: 'api', pattern: 'src/api' },
        { type: 'test-setup', pattern: 'src/test' },
      ],
    },
    rules: {
      '@typescript-eslint/no-unused-vars': [
        'error',
        { argsIgnorePattern: '^_', varsIgnorePattern: '^_' },
      ],
      ...reactHooks.configs['recommended-latest'].rules,
      'react-hooks/exhaustive-deps': 'error',
      ...jsxA11y.flatConfigs.recommended.rules,
      'jsx-a11y/label-has-associated-control': [
        'error',
        { controlComponents: ['DateInput'] },
      ],
      'boundaries/dependencies': ['error', { default: 'disallow', policies }],
    },
  },
  {
    files: [
      '**/*.test.{ts,tsx}',
      'src/test/**',
      'src/app/testWorkspace.tsx',
      'src/app/appTestFixtures.ts',
    ],
    rules: {
      'boundaries/dependencies': [
        'error',
        { default: 'disallow', policies: testPolicies },
      ],
      '@typescript-eslint/no-explicit-any': 'off',
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },
  {
    files: ['scripts/**/*.{ts,mjs}', '*.config.ts'],
    languageOptions: { globals: globals.node },
  },
  prettier,
);
