// Central ESLint flat config for all client workspace packages.
//
// Each package runs `eslint src` from its own directory (see the lint scripts
// in packages/*/package.json); ESLint discovers this file by walking up to
// client/, so all files patterns are rooted here.
//
// Vue rules use the 'essential' tier (error-catching, no style noise); TS
// rules use the non-type-checked 'recommended' preset to keep lint fast.
import js from '@eslint/js'
import { defineConfigWithVueTs, vueTsConfigs } from '@vue/eslint-config-typescript'
import pluginVue from 'eslint-plugin-vue'

export default defineConfigWithVueTs(
  {
    name: 'nimbus/files-to-ignore',
    ignores: ['**/dist/**', '**/node_modules/**', '**/coverage/**'],
  },

  js.configs.recommended,
  pluginVue.configs['flat/essential'],
  vueTsConfigs.recommended,

  {
    name: 'nimbus/adopted-conventions',
    rules: {
      // The codebase deliberately uses `any` in dynamic serialization and
      // interop code (1000+ occurrences); narrowing them all is a follow-up,
      // not a lint-blocking concern.
      '@typescript-eslint/no-explicit-any': 'off',
      // Namespaces are an established scoping pattern here (logger, utils).
      '@typescript-eslint/no-namespace': 'off',
      // Existing debt is surfaced as warnings instead of errors, so lint can
      // run in CI. Intentionally unused parameters can be prefixed with `_`.
      '@typescript-eslint/no-unused-vars': [
        'warn',
        {
          argsIgnorePattern: '^_',
          varsIgnorePattern: '^_',
          caughtErrorsIgnorePattern: '^_',
        },
      ],
    },
  },
)
