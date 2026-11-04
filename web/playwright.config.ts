// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { defineConfig, devices } from '@playwright/test';

/**
 * E2E contra o perfil `full` de compose (Caddy en :8080), ADR-0014 e D34.
 * Proxectos: gl, es (o navegador pide castelán) e gl-mobile (320×640, WCAG 1.4.10). Só Chromium.
 */
const baseURL = process.env['FURAR_E2E_BASE_URL'] ?? 'http://localhost:8080';

export default defineConfig({
  testDir: 'e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  workers: 1,
  // T14: tamén en local; un .only faría que D34 pasase cun só test.
  forbidOnly: true,
  // Sen reintentos tampouco na CI: un test intermitente ten que saír vermello, non agocharse.
  retries: 0,
  timeout: 60_000,
  expect: { timeout: 10_000 },
  // O json leno scripts/e2e-web.sh (D34): 0 omitidos e tantos esperados como lista --list.
  reporter: [['list'], ['html', { open: 'never' }], ['json', { outputFile: 'test-results-json/resultados.json' }]],
  use: {
    baseURL,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    // O service worker probaríase coa caché entre probas; a PWA compróbase á parte.
    serviceWorkers: 'block',
  },
  projects: [
    { name: 'gl', use: { ...devices['Desktop Chrome'], locale: 'gl-ES' } },
    { name: 'es', use: { ...devices['Desktop Chrome'], locale: 'es-ES' } },
    {
      name: 'gl-mobile',
      use: { ...devices['Desktop Chrome'], locale: 'gl-ES', viewport: { width: 320, height: 640 }, hasTouch: true },
    },
  ],
});
