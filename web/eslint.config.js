// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Lint da web (ADR-0014): TypeScript e modelos HTML (tamén inline), accesibilidade,
// OnPush, signals, tamaño de compoñentes, fronteiras entre zonas (furar/zones) e texto
// solto nos modelos (furar/no-loose-text, mesma lóxica ca i18n:check).
import { readFileSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import js from '@eslint/js';
import angular from 'angular-eslint';
import { defineConfig } from 'eslint/config';
import tseslint from 'typescript-eslint';
import { findLooseText } from './scripts/template-text.mjs';

const root = dirname(fileURLToPath(import.meta.url));
const appDir = resolve(root, 'src/app');

/** Zona dun ficheiro de src/app: core, shared, api, features/<nome> ou app (raíz). */
function zoneOf(file) {
  const path = relative(appDir, file).split('\\').join('/');
  if (path.startsWith('..')) return null;
  const [first, second] = path.split('/');
  if (first === 'features') return { zone: 'features', feature: second };
  return { zone: ['core', 'shared', 'api'].includes(first) ? first : 'app' };
}

/**
 * Fronteiras (ADR-0014): unha feature non importa outra; shared e core non importan
 * features; o cliente xerado (api/) só se usa desde *.store.ts e core/. HTTP á man
 * (@angular/common/http, HttpClient e os seus backends, aínda reexportados desde
 * outro sitio, e rxjs/ajax e rxjs/fetch, que fan XHR e fetch por dentro: QA P1) só en core/ e api/: se unha feature chamase á API sen o cliente xerado,
 * un cambio da API non rompería o build (ADR-0009, QA N13). Os specs quedan fóra.
 */
const HTTP_MODULES = new Set(['@angular/common/http', 'rxjs/ajax', 'rxjs/fetch']);
const HTTP_NAMES = new Set(['HttpClient', 'HttpBackend', 'HttpHandler', 'HttpXhrBackend', 'FetchBackend']);
const isTestFile = (file) => /\.spec\.ts$/.test(file);
const zones = {
  meta: {
    type: 'problem',
    docs: { description: 'Fronteiras entre core, shared, api e features' },
    messages: {
      crossFeature: 'A feature «{{from}}» non pode importar a feature «{{to}}»: usa core/ ou shared/.',
      intoFeatures: '{{from}}/ non pode importar features/ (só app.routes.ts as carga).',
      apiOutsideStore: 'O cliente xerado (api/) só se usa desde *.store.ts ou core/; aquí usa o store da feature.',
      rawHttp:
        'HTTP á man en {{from}}/ ({{what}}): chama á API só co cliente xerado (api/) desde o *.store.ts, así un cambio da API rompe o build (ADR-0009).',
    },
    schema: [],
  },
  create(context) {
    const from = zoneOf(context.filename);
    if (!from) return {};
    const rawHttpForbidden = (from.zone === 'features' || from.zone === 'shared') && !isTestFile(context.filename);
    const checkHttp = (node) => {
      if (!rawHttpForbidden) return;
      const source = node.source?.value;
      if (HTTP_MODULES.has(source)) {
        context.report({ node, messageId: 'rawHttp', data: { from: from.zone, what: source } });
        return;
      }
      for (const spec of node.specifiers ?? []) {
        const name = spec.imported?.name ?? spec.local?.name;
        if (spec.type !== 'ImportDefaultSpecifier' && HTTP_NAMES.has(name)) {
          context.report({ node: spec, messageId: 'rawHttp', data: { from: from.zone, what: name } });
        }
      }
    };
    const check = (node, source) => {
      // Reexportar HttpClient desde core/ abriría unha porta traseira cun alias (Cliente…).
      const isReexport = node.type === 'ExportNamedDeclaration' || node.type === 'ExportAllDeclaration';
      if (HTTP_MODULES.has(source) && isReexport && from.zone !== 'api') {
        context.report({ node, messageId: 'rawHttp', data: { from: from.zone, what: `reexport de ${source}` } });
        return;
      }
      if (HTTP_MODULES.has(source) && rawHttpForbidden) {
        context.report({ node, messageId: 'rawHttp', data: { from: from.zone, what: source } });
        return;
      }
      if (typeof source !== 'string' || !source.startsWith('.')) return;
      const to = zoneOf(resolve(dirname(context.filename), source));
      if (!to) return;
      if (from.zone === 'features' && to.zone === 'features' && from.feature !== to.feature) {
        context.report({ node, messageId: 'crossFeature', data: { from: from.feature, to: to.feature } });
      }
      if ((from.zone === 'shared' || from.zone === 'core') && to.zone === 'features') {
        context.report({ node, messageId: 'intoFeatures', data: { from: from.zone } });
      }
      const isStore = context.filename.endsWith('.store.ts');
      if (to.zone === 'api' && from.zone !== 'api' && from.zone !== 'core' && from.zone !== 'app' && !isStore) {
        context.report({ node, messageId: 'apiOutsideStore' });
      }
    };
    return {
      ImportDeclaration: (node) => {
        checkHttp(node);
        if (!HTTP_MODULES.has(node.source.value)) check(node, node.source.value);
      },
      ExportNamedDeclaration: (node) => node.source && check(node, node.source.value),
      ExportAllDeclaration: (node) => check(node, node.source.value),
      ImportExpression: (node) => node.source.type === 'Literal' && check(node, node.source.value),
    };
  },
};

/** Claves de gl.json (fonte de verdade das claves; i18n:check comproba a paridade con es). */
function translationKeys() {
  const keys = new Set();
  const walk = (node, prefix) => {
    for (const [key, value] of Object.entries(node)) {
      const path = prefix ? `${prefix}.${key}` : key;
      if (value !== null && typeof value === 'object') walk(value, path);
      else keys.add(path);
    }
  };
  walk(JSON.parse(readFileSync(resolve(root, 'src/i18n/gl.json'), 'utf8')), '');
  return keys;
}
const keys = translationKeys();
const isKey = (literal) => keys.has(literal) || (literal.endsWith('.') && [...keys].some((k) => k.startsWith(literal)));

/** Texto solto nos modelos (D25): a mesma detección ca i18n:check, con posición no editor. */
const noLooseText = {
  meta: {
    type: 'problem',
    docs: { description: 'Sen texto literal nos modelos: todo pasa por Transloco' },
    schema: [],
  },
  create(context) {
    return {
      Program(program) {
        findLooseText(program.templateNodes ?? [], isKey, (message, span) =>
          context.report({
            message,
            loc: {
              start: { line: span.start.line + 1, column: span.start.col },
              end: { line: span.end.line + 1, column: span.end.col },
            },
          }),
        );
      },
    };
  },
};

/** CSP sen style-src 'unsafe-inline' (ADR-0007). */
const NO_COMPONENT_STYLES = {
  selector: "Decorator[expression.callee.name='Component'] Property[key.name=/^style(s|Url|Urls)$/]",
  message: 'Sen estilos de compoñente: a CSP non permite <style> en liña (ADR-0007). Usa clases de styles.css.',
};

/**
 * Texto escrito en TypeScript e pintado con {{ }} (D25, T10): i18n:check só mira os modelos, así
 * que un literal con palabras nunha propiedade dun @Component (`qaText = 'Benvido ao concello'`,
 * tamén dentro de signal(...) ou computed(...)) fai fallar o lint. Detecta dúas palabras
 * separadas por espazo ou calquera letra acentuada; claves, ids e selectores non levan nin unha
 * cousa nin a outra. Lista branca: constantes fóra da clase ou `// eslint-disable-next-line`
 * xustificado (p. ex. nomes propios como «Furar»).
 */
const LOOSE_TEXT = String.raw`/[A-Za-zÀ-ÿ]\s+[A-Za-zÀ-ÿ]|[À-ÿ]/`;
// Nun template literal as palabras quedan partidas polos ${...}: abonda unha letra xunto a un espazo.
const LOOSE_TEMPLATE_TEXT = String.raw`/[A-Za-zÀ-ÿ]\s|\s[A-Za-zÀ-ÿ]|[À-ÿ]/`;
const COMPONENT_PROPERTY = "ClassDeclaration[decorators.0.expression.callee.name='Component'] PropertyDefinition";
// T14: Vitest e Playwright saen con código 0 cun test omitido, e un .only executa só ese test.
// Prohíbense todas as formas (tamén condicionais): un test que non se pode executar bórrase ou arránxase.
const SKIP_MODIFIERS = '/^(skip|only|fixme|fail|todo|skipIf|runIf)$/';
const TEST_FUNCTIONS = '/^(it|test|describe|suite)$/';
const SKIP_MESSAGE =
  'Sen tests omitidos nin enfocados (T14, D26/D34): nada de .skip/.only/.fixme/.fail/.todo nin xit/fit/xdescribe.';
const NO_SKIPPED_TESTS = [
  // it.skip(...), test.only(...), describe.skipIf(...)(...), test.fixme(), test.skip(cond, ...)
  {
    selector: `MemberExpression[object.name=${TEST_FUNCTIONS}][property.name=${SKIP_MODIFIERS}]`,
    message: SKIP_MESSAGE,
  },
  // test.describe.skip(...), test.describe.only(...), test.describe.fixme(...)
  {
    selector: `MemberExpression[object.type='MemberExpression'][object.property.name='describe'][property.name=${SKIP_MODIFIERS}]`,
    message: SKIP_MESSAGE,
  },
  { selector: 'CallExpression[callee.name=/^(xit|fit|xtest|xdescribe|fdescribe)$/]', message: SKIP_MESSAGE },
];

const NO_TS_TEXT = [
  {
    selector: `${COMPONENT_PROPERTY} Literal[value=${LOOSE_TEXT}]`,
    message: 'Texto visible en TypeScript (D25): pon a clave en gl.json/es.json e tradúcea con Transloco.',
  },
  {
    selector: `${COMPONENT_PROPERTY} TemplateElement[value.raw=${LOOSE_TEMPLATE_TEXT}]`,
    message: 'Texto visible en TypeScript (D25): pon a clave en gl.json/es.json e tradúcea con Transloco.',
  },
];

/**
 * Gráficos (ADR-0026): ECharts e zrender só se importan dentro de shared/ui/chart/, e alí só
 * echarts-setup.ts os carga de verdade (chega por `import()`, nun chunk diferido); os demais
 * ficheiros da carpeta só collen tipos. Así ECharts nunca entra na carga inicial nin nunha feature.
 */
const ECHARTS_MODULES = ['echarts', 'echarts/*', 'zrender', 'zrender/*'];
const CHART_DIR = 'src/app/shared/ui/chart';

export default defineConfig(
  {
    ignores: [
      'dist/',
      'coverage/',
      '.angular/',
      'node_modules/',
      'src/app/api/',
      'playwright-report/',
      'test-results/',
    ],
  },
  {
    files: ['**/*.ts'],
    extends: [
      js.configs.recommended,
      tseslint.configs.strict,
      tseslint.configs.stylistic,
      angular.configs.tsRecommended,
    ],
    processor: angular.processInlineTemplates,
    languageOptions: {
      parserOptions: {
        project: ['./tsconfig.app.json', './tsconfig.spec.json', './tsconfig.e2e.json'],
        tsconfigRootDir: root,
      },
    },
    plugins: { furar: { rules: { zones } } },
    rules: {
      '@angular-eslint/component-selector': ['error', { type: 'element', prefix: 'furar', style: 'kebab-case' }],
      '@angular-eslint/directive-selector': ['error', { type: 'attribute', prefix: 'furar', style: 'camelCase' }],
      '@angular-eslint/prefer-on-push-component-change-detection': 'error',
      '@angular-eslint/prefer-signals': 'error',
      '@angular-eslint/prefer-standalone': 'error',
      '@angular-eslint/prefer-output-readonly': 'error',
      '@angular-eslint/no-input-rename': 'error',
      '@angular-eslint/use-lifecycle-interface': 'error',
      '@angular-eslint/no-uncalled-signals': 'error',
      '@angular-eslint/computed-must-return': 'error',
      '@typescript-eslint/no-explicit-any': 'error',
      '@typescript-eslint/no-non-null-assertion': 'error',
      '@typescript-eslint/no-floating-promises': 'error',
      '@typescript-eslint/no-misused-promises': ['error', { checksVoidReturn: { attributes: false } }],
      '@typescript-eslint/consistent-type-imports': [
        'error',
        { prefer: 'type-imports', fixStyle: 'separate-type-imports' },
      ],
      '@typescript-eslint/no-extraneous-class': ['error', { allowWithDecorator: true }],
      'furar/zones': 'error',
      // CSP sen style-src 'unsafe-inline' (ADR-0007): os estilos de compoñente Angular insíreos como
      // <style> en tempo de execución e a CSP bloquearíanos. Estilos só en styles.css (Tailwind).
      'no-restricted-syntax': ['error', NO_COMPONENT_STYLES],
      'max-lines': ['error', { max: 250, skipBlankLines: true, skipComments: true }],
      'no-console': ['error', { allow: ['error', 'warn'] }],
      eqeqeq: 'error',
    },
  },
  {
    files: ['src/app/**/*.ts'],
    ignores: ['**/*.spec.ts'],
    rules: { 'no-restricted-syntax': ['error', NO_COMPONENT_STYLES, ...NO_TS_TEXT] },
  },
  {
    // ADR-0009 (QA N13): tampouco fetch nin XMLHttpRequest á man fóra de core/ e api/.
    files: ['src/app/features/**/*.ts', 'src/app/shared/**/*.ts'],
    ignores: ['**/*.spec.ts'],
    rules: {
      'no-restricted-globals': [
        'error',
        ...['fetch', 'XMLHttpRequest', 'EventSource'].map((name) => ({
          name,
          message: `HTTP á man (${name}): chama á API só co cliente xerado (api/) desde o *.store.ts (ADR-0009).`,
        })),
      ],
      'no-restricted-properties': [
        'error',
        ...['window', 'globalThis', 'self'].flatMap((object) =>
          ['fetch', 'XMLHttpRequest'].map((property) => ({
            object,
            property,
            message: 'HTTP á man: chama á API só co cliente xerado (api/) desde o *.store.ts (ADR-0009).',
          })),
        ),
      ],
    },
  },
  {
    files: ['**/*.ts'],
    ignores: [`${CHART_DIR}/**`],
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ECHARTS_MODULES,
              message: 'ECharts só se importa en shared/ui/chart/ (ADR-0026): usa furar-chart e os seus construtores.',
            },
          ],
        },
      ],
    },
  },
  {
    files: [`${CHART_DIR}/**/*.ts`],
    ignores: [`${CHART_DIR}/echarts-setup.ts`],
    rules: {
      '@typescript-eslint/no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: ECHARTS_MODULES,
              allowTypeImports: true,
              message: 'Só echarts-setup.ts carga ECharts (por import(), ADR-0026); aquí, só `import type`.',
            },
          ],
        },
      ],
    },
  },
  {
    files: ['**/*.spec.ts', 'e2e/**/*.ts', 'src/testing/**/*.ts'],
    rules: {
      'no-restricted-syntax': ['error', NO_COMPONENT_STYLES, ...NO_SKIPPED_TESTS],
      'max-lines': ['error', { max: 400, skipBlankLines: true, skipComments: true }],
      '@typescript-eslint/no-non-null-assertion': 'off',
    },
  },
  {
    files: ['e2e/**/*.ts', 'playwright.config.ts'],
    rules: { 'no-empty-pattern': 'off', 'no-console': 'off' },
  },
  {
    files: ['**/*.html'],
    extends: [angular.configs.templateRecommended, angular.configs.templateAccessibility],
    rules: {
      '@angular-eslint/template/button-has-type': 'error',
      '@angular-eslint/template/no-any': 'error',
      '@angular-eslint/template/no-positive-tabindex': 'error',
      '@angular-eslint/template/no-inline-styles': ['error', { allowBindToStyle: true }],
      '@angular-eslint/template/prefer-control-flow': 'error',
      '@angular-eslint/template/prefer-self-closing-tags': 'error',
      '@angular-eslint/template/eqeqeq': 'error',
      'max-lines': ['error', { max: 200 }],
    },
  },
  {
    // Modelos da app (.html e templates inline); index.html e os specs quedan fóra, como en i18n:check.
    files: ['src/app/**/*.html'],
    ignores: ['**/*.spec.ts/**'],
    plugins: { furar: { rules: { 'no-loose-text': noLooseText } } },
    rules: { 'furar/no-loose-text': 'error' },
  },
  {
    files: ['scripts/**/*.mjs', 'eslint.config.js'],
    extends: [js.configs.recommended],
    languageOptions: { globals: { process: 'readonly', console: 'readonly', URL: 'readonly' } },
  },
);
