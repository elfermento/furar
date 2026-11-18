// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext, Page } from '@playwright/test';
import { expect, loginAs, test, text } from './fixtures';
import { checkStep, hermetic } from './instalacions-axudas';
import {
  FAR,
  PADEL,
  SLUG,
  bookingDay,
  cancelCounterBookings,
  cancelLeftovers,
  hhmm,
  openDay,
  slotStart,
  tokenFor,
} from './reservas-axudas';

/**
 * Lista de espera e ocos parecidos de punta a punta (ADR-0033): a hora ocupada
 * sae en gris no selector → «Avísame se se libera» → a espera vese en «As miñas reservas» → ao
 * cancelarse a reserva que a ocupaba chega o correo «Quedou libre» a Mailpit, e a súa ligazón abre
 * a páxina de reservar coa hora posta → «Deixar de esperar». E elixir un oco parecido e reservalo.
 * Axe en cada paso (e 320 px sen scroll horizontal en gl-mobile).
 *
 * A hora ocupada faina a recepción pola API, no mostrador e lonxe de hoxe (`FAR`), a nome dun
 * correo de proba; ao empezar e ao rematar cancélanse esas reservas, as da persoa e as súas esperas.
 */
test.describe.configure({ mode: 'serial' });

const API = `/api/v1/municipalities/${SLUG}`;
const MAILPIT = process.env['FURAR_E2E_MAILPIT_URL'] ?? 'http://localhost:8025';
/** O correo das reservas do mostrador que fan estas probas. */
const COUNTER = 'e2e-espera';
const FREED_MAIL = /^(Quedou libre|Ha quedado libre): /;
/** As 18:30 e as 20:00, na grella de 90 minutos do pádel. */
const BUSY = 1110;
const LATER = 1200;

interface Counter {
  id: string;
  version: number;
}

/** A recepción reserva no mostrador: a hora queda ocupada para a veciñanza. */
async function occupy(request: APIRequestContext, day: string, minute: number, project: string): Promise<Counter> {
  const token = await tokenFor(request, 'recepcion-naron');
  const start = await slotStart(request, PADEL.id, PADEL.use, day, minute);
  const response = await request.post(`${API}/bookings`, {
    headers: { ...token.headers, 'Idempotency-Key': crypto.randomUUID() },
    data: {
      facilityId: PADEL.id,
      useId: PADEL.use,
      start,
      slots: 1,
      holder: { name: 'E2E Espera', email: `${COUNTER}-${project}@example.org` },
      language: 'gl',
    },
  });
  expect(response.status(), `reservar no mostrador: ${await response.text()}`).toBe(201);
  return (await response.json()) as Counter;
}

/** A persoa deixa de esperar por todo. */
async function clearWaits(request: APIRequestContext): Promise<void> {
  const token = await tokenFor(request, 'vecino-naron');
  const list = await request.get(`${API}/me/booking-waits`, { headers: token.headers });
  expect(list.ok(), 'esperas').toBe(true);
  for (const wait of (await list.json()) as { id: string }[]) {
    const left = await request.delete(`${API}/me/booking-waits/${wait.id}`, { headers: token.headers });
    expect([204, 404], 'deixar de esperar').toContain(left.status());
  }
}

async function cleanUp(request: APIRequestContext): Promise<void> {
  await clearWaits(request);
  await cancelLeftovers(request, 'vecino-naron', [PADEL.id]);
  await cancelCounterBookings(request, 'xestor-naron', SLUG, COUNTER);
}

/** Abre o panel «Hora» da páxina de reservar. */
async function openTime(page: Page, lang: 'gl' | 'es') {
  const panel = page.getByRole('dialog', { name: text(lang, 'booking.reserve.time') });
  if (!(await panel.isVisible())) {
    await page.locator('[data-reserve-field="time"] button[aria-haspopup="dialog"]').click();
  }
  await expect(panel).toBeVisible();
  return panel;
}

/** O correo «Quedou libre» desa hora (a súa ligazón leva o día e a hora), enteiro. */
async function freedMail(request: APIRequestContext, day: string, time: string) {
  const link = `data=${day}&hora=${encodeURIComponent(time)}`;
  let found: { Subject: string; Text: string; HTML: string; Attachments: unknown[] } | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT}/api/v1/search`, {
          params: { query: 'to:vecino-naron@example.org libre' },
        });
        if (!response.ok()) return false;
        const { messages } = (await response.json()) as { messages: { ID: string; Subject: string }[] };
        for (const summary of messages.filter((m) => FREED_MAIL.test(m.Subject))) {
          const detail = await request.get(`${MAILPIT}/api/v1/message/${summary.ID}`);
          if (!detail.ok()) continue;
          const mail = (await detail.json()) as NonNullable<typeof found>;
          if (mail.Text.includes(link)) {
            found = mail;
            return true;
          }
        }
        return false;
      },
      { message: `correo «Quedou libre» do ${day} ás ${time} en Mailpit`, timeout: 30_000 },
    )
    .toBe(true);
  return found!;
}

test.describe('lista de espera e ocos parecidos', () => {
  test.beforeAll(async ({ request }) => cleanUp(request));
  test.afterAll(async ({ request }) => cleanUp(request));

  test('hora ocupada → «Avísame» → «En espera» nas miñas reservas → correo ao liberarse → deixar de esperar', async ({
    page,
    request,
    lang,
  }, testInfo) => {
    test.setTimeout(180_000);
    const outside = await hermetic(page);
    const day = await openDay(request, PADEL.id, PADEL.use, bookingDay(FAR + 20, testInfo.project.name), [BUSY, LATER]);
    const time = hhmm(BUSY);
    const taken = await occupy(request, day, BUSY, testInfo.project.name);

    // 1. Coa sesión aberta, a páxina de reservar dese día: a hora ocupada sae en gris con «Ocupada»
    await page.goto('/naron/instalacions');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto(`/naron/instalacions/${PADEL.id}/reservar?uso=${PADEL.use}&data=${day}`);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.title') })).toBeVisible();
    const timePanel = await openTime(page, lang);
    const busy = timePanel.locator('.f-reserve__slot[data-busy]', { hasText: time });
    await expect(busy).toContainText(text(lang, 'booking.reserve.busy'));
    // O nome accesible é o que se ve (WCAG 2.5.3): a hora e «Ocupada».
    await expect(busy).toHaveAccessibleName(`${time} ${text(lang, 'booking.reserve.busy')}`);
    await expect(timePanel.locator('[data-reserve-slot]', { hasText: hhmm(LATER) })).toBeVisible();
    await checkStep(page);

    // 2. Ao elixila: «· ocupada», sen «Continuar», con «Avísame se se libera» e os ocos parecidos
    await busy.click();
    await expect(timePanel).toBeHidden();
    await expect(page.locator('[data-reserve-field="time"]')).toContainText(`${time}–${hhmm(BUSY + 90)}`);
    await expect(page.locator('[data-reserve-field="time"]')).toContainText('· ocupada');
    await expect(page.locator('[data-reserve-go]')).toHaveCount(0);
    const add = page.locator('[data-wait-add]');
    await expect(add).toHaveText(text(lang, 'booking.reserve.wait.add'));
    await expect(
      page.getByRole('heading', { level: 2, name: text(lang, 'booking.reserve.similar.title') }),
    ).toBeVisible();
    await expect(page.locator('[data-similar-slot]').first()).toBeVisible();
    await checkStep(page);

    // 3. «Avísame»: o aviso verde e «Deixar de esperar»
    await add.click();
    const on = page.locator('[data-wait-on]');
    await expect(on).toContainText(text(lang, 'booking.reserve.wait.onTitle'));
    await expect(on).toContainText(text(lang, 'booking.reserve.wait.onText'));
    await expect(page.locator('[data-wait-remove]')).toHaveText(text(lang, 'booking.reserve.wait.remove'));
    await expect(add).toHaveCount(0);
    await checkStep(page);

    // 4. «As miñas reservas → Próximas»: a tarxeta «En espera» e, no detalle, só «Deixar de esperar»
    await page.goto('/naron/reservas');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.mine.title') })).toBeVisible();
    const card = page.locator('furar-booking-card[data-wait]');
    await expect(card).toHaveCount(1);
    await expect(card).toContainText(PADEL.name);
    await expect(card).toContainText(time);
    await expect(card.locator('furar-tag')).toHaveText(text(lang, 'booking.mine.wait.chip'));
    await expect(card.locator('button, a')).toHaveCount(1);
    await card.locator('.f-status-card__open').click();
    const detail = page.getByRole('dialog', { name: PADEL.name });
    await expect(detail.locator('[data-wait-leave]')).toHaveText(text(lang, 'booking.mine.wait.remove'));
    await expect(detail.locator('furar-action-item')).toHaveCount(1);
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(detail).toBeHidden();

    // 5. A xestión cancela a reserva que ocupaba a hora: chega «Quedou libre», sen adxuntos
    const manager = await tokenFor(request, 'xestor-naron');
    const cancelled = await request.post(`${API}/bookings/${taken.id}/cancel`, {
      headers: { ...manager.headers, 'If-Match': `"${taken.version}"` },
      data: { channels: [] },
    });
    expect([200, 204], 'cancelar a reserva do mostrador').toContain(cancelled.status());
    const mail = await freedMail(request, day, time);
    expect(mail.Subject).toContain(PADEL.name);
    expect(mail.Text).toContain(PADEL.useName);
    expect(mail.Text).toContain(`${time}–${hhmm(BUSY + 90)}`);
    expect(mail.Attachments).toHaveLength(0);

    // 6. A ligazón do correo abre a páxina de reservar coa hora posta, agora libre
    const link = /https?:\/\/\S+\/reservar\?\S+/.exec(mail.Text)?.[0];
    expect(link, 'ligazón «Reservar esta hora»').toBeDefined();
    const target = new URL(link!);
    await page.goto(`${target.pathname}${target.search}`);
    await expect(page.locator('[data-reserve-field="time"]')).toContainText(time);
    await expect(page.locator('[data-reserve-field="time"]')).not.toContainText('· ocupada');
    await expect(page.locator('[data-reserve-go]')).toBeEnabled();
    await expect(page.locator('[data-wait-add]')).toHaveCount(0);

    // 7. «Deixar de esperar» desde as miñas reservas: a tarxeta desaparece sen preguntar
    await page.goto('/naron/reservas');
    await expect(card).toHaveCount(1);
    await card.locator('.f-status-card__open').click();
    await detail.locator('[data-wait-leave]').click();
    await expect(detail).toBeHidden();
    await expect(card).toHaveCount(0);
    await checkStep(page);
    expect(outside).toEqual([]);
  });

  test('elixir un oco parecido e reservalo', async ({ page, request, lang }, testInfo) => {
    test.setTimeout(120_000);
    const day = await openDay(request, PADEL.id, PADEL.use, bookingDay(FAR + 24, testInfo.project.name), [BUSY, LATER]);
    await occupy(request, day, BUSY, testInfo.project.name);

    await page.goto('/naron/instalacions');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto(
      `/naron/instalacions/${PADEL.id}/reservar?uso=${PADEL.use}&data=${day}&hora=${encodeURIComponent(hhmm(BUSY))}`,
    );
    // A hora do URL está ocupada: queda elixida como ocupada, cos ocos parecidos debaixo
    await expect(page.locator('[data-reserve-field="time"]')).toContainText('· ocupada');
    const rows = page.locator('[data-similar-slot]');
    await expect(rows.first()).toBeVisible();
    // A mesma hora na outra pista de pádel da demo (o mesmo uso, tamén por internet), máis tarde
    // nesta instalación e a semana seguinte (as regras enteiras próbaas SimilarSlotsIT).
    await expect(
      rows.filter({ hasText: `${hhmm(BUSY)}–` }).filter({ hasText: 'Pista de pádel 2 de O Alto' }),
    ).toHaveCount(1);
    const later = rows
      .filter({ hasText: `${hhmm(LATER)}–` })
      .filter({ hasText: text(lang, 'booking.reserve.similar.here') });
    await expect(later).toHaveCount(1);
    await checkStep(page);

    // Pulsar a fila déixaa elixida: a hora cambia e volve «Confirmar reserva»
    await later.getByRole('link').click();
    await expect(page.locator('[data-reserve-field="time"]')).toContainText(hhmm(LATER));
    await expect(page.locator('[data-reserve-field="time"]')).not.toContainText('· ocupada');
    await expect(rows).toHaveCount(0);
    const go = page.locator('[data-reserve-go]');
    await expect(go).toHaveText(text(lang, 'booking.reserve.confirm'));
    await checkStep(page);

    await go.click();
    await expect(page).toHaveURL(/[?&]reserva=[0-9a-f-]{36}/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.done.title') })).toBeVisible();
  });
});
