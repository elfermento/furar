// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import type { Locator, Page, Request, Route } from '@playwright/test';
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, test, text } from './fixtures';

/** Axudas de `instalacions.e2e.ts` (D1-25): rede hermética, datas e teclado. */

/** PNG 1×1 para as teselas: o mapa pinta sen pedir nada ao servidor de teselas. */
const TILE = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==',
  'base64',
);
/** Unha imaxe real (o debuxo da pista de pádel): a API decodifícaa e reencódaa coma unha foto. */
export const PHOTO = readFileSync(
  fileURLToPath(new URL('../../api/src/main/resources/drawings/padel_court.jpg', import.meta.url)),
);

/** Reservas que devolve o 409 simulado do bloqueo. */
export const AFFECTED = [
  {
    bookingId: '00000000-0000-4000-8000-0000000000b1',
    holderName: 'Uxía Bouza',
    date: '',
    startMinute: 600,
    endMinute: 660,
    useName: 'Pista completa',
    hasEmail: true,
  },
  {
    bookingId: '00000000-0000-4000-8000-0000000000b2',
    holderName: 'Brais Lema',
    date: '',
    startMinute: 660,
    endMinute: 720,
    useName: 'Pista completa',
    hasEmail: false,
  },
];

/** Hoxe en Europe/Madrid (a zona da API) e días despois, en ISO. */
export function isoDay(offset = 0): string {
  const today = new Intl.DateTimeFormat('en-CA', { timeZone: 'Europe/Madrid' }).format(new Date());
  const date = new Date(`${today}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + offset);
  return date.toISOString().slice(0, 10);
}

export function escape(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

/**
 * Teselas con PNG de proba e garda de rede: calquera petición que non vaia a localhost
 * abórtase e queda anotada (a proba comproba ao final que a lista está baleira).
 */
export async function hermetic(page: Page): Promise<string[]> {
  const outside: string[] = [];
  const base = new URL(test.info().project.use.baseURL ?? 'http://localhost:8080');
  page.on('request', (request: Request) => {
    const url = new URL(request.url());
    if (!['http:', 'https:'].includes(url.protocol)) return;
    if (url.hostname !== base.hostname && url.hostname !== 'localhost' && url.hostname !== '127.0.0.1') {
      outside.push(request.url());
    }
  });
  await page.route(
    (url) => url.hostname !== base.hostname && url.hostname !== 'localhost' && url.hostname !== '127.0.0.1',
    (route: Route) => route.abort('blockedbyclient'),
  );
  await page.route(/\/tiles\/\d+\/\d+\/\d+\.png(\?.*)?$/, (route: Route) =>
    route.fulfill({ status: 200, contentType: 'image/png', body: TILE }),
  );
  return outside;
}

/** Axe e (en gl-mobile) reflow a 320 px sen scroll horizontal. */
export async function checkStep(page: Page): Promise<void> {
  // Con diálogos que entran con transición, axe mediría o contraste a medio camiño.
  await page.waitForFunction(() =>
    document.getAnimations().every((a) => a.playState !== 'running' || a.effect?.getTiming().iterations === Infinity),
  );
  await expectNoA11yViolations(page);
  await expectNoHorizontalScroll(page);
}

/**
 * Nome do botón dun campo con panel («Data, 24/12/2026»), co «(obrigatorio)» que leva o nome se o
 * campo é obrigatorio (docs/deseno.md P1-3): «Data (obrigatorio), 24/12/2026».
 */
export function fieldButtonName(label: string, lang: 'gl' | 'es'): RegExp {
  return new RegExp(`^${escape(label)}( \\(${escape(text(lang, 'ui.form.required'))}\\))?,`);
}

/** Escolle unha data no `furar-date-field` cuxo rótulo é `label` (avanza meses se fai falta). */
export async function pickDate(
  page: Page,
  scope: Locator,
  label: string,
  iso: string,
  lang: 'gl' | 'es',
): Promise<void> {
  await scope.getByRole('button', { name: fieldButtonName(label, lang) }).click();
  const panel = page.getByRole('dialog', { name: text(lang, 'ui.dateField.dialog', { label }) });
  await expect(panel).toBeVisible();
  // Vaise mes a mes ata o da data, esperando a que se pinte cada mes antes de seguir.
  const shown = panel.locator('td[data-date]').first();
  const target = iso.slice(0, 7);
  for (let i = 0; i < 24; i++) {
    const month = ((await shown.getAttribute('data-date')) ?? '').slice(0, 7);
    if (month === target) break;
    const step = month < target ? 'ui.calendar.nextMonth' : 'ui.calendar.previousMonth';
    await panel.getByRole('button', { name: text(lang, step) }).click();
    await expect(shown).not.toHaveAttribute('data-date', new RegExp(`^${month}`));
  }
  await panel.locator(`td[data-date="${iso}"]`).click();
  await expect(panel).toBeHidden();
}

/** Tab ata que o foco chega a `target` (como faría quen só usa teclado). */
export async function tabTo(page: Page, target: Locator, max = 40): Promise<void> {
  for (let i = 0; i < max; i++) {
    if (await target.evaluate((el) => el === document.activeElement).catch(() => false)) return;
    await page.keyboard.press('Tab');
  }
  await expect(target, 'alcánzase con Tab').toBeFocused();
}

/**
 * No asistente, desde o paso «Ubicación» dun borrador ao que só lle falta o enderezo: o enderezo
 * co buscador (`fixed`), «Seguinte» ou «Saltar» ata «Revisar» (sen nada pendente) e «Publicar».
 */
export async function publishFromAddress(page: Page, lang: 'gl' | 'es'): Promise<void> {
  await page.getByRole('combobox', { name: text(lang, 'facility.location.search') }).fill('Castela');
  await page.getByRole('option', { name: /Estrada de Castela/ }).click();
  const street = new RegExp(`^${escape(text(lang, 'facility.location.field.street'))}`);
  await expect(page.getByRole('textbox', { name: street })).toHaveValue('Estrada de Castela');
  await checkStep(page);
  // Ubicación → Contacto e servizos → Fotos → Usos → Horario → Normas → Revisar
  for (const action of ['next', 'skip', 'skip', 'next', 'next', 'skip']) {
    await page.locator(`[data-${action}]`).click();
  }
  await expect(page.locator('#wizard-missing')).toHaveCount(0);
  await checkStep(page);
  await page.locator('[data-publish]').click();
}
