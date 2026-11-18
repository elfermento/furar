// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Locator, Page } from '@playwright/test';
import { expect, loginAs, test, text } from './fixtures';
import { checkStep, hermetic, isoDay, pickDate } from './instalacions-axudas';
import {
  ATHLETICS,
  BLOCK_REASON,
  E2E_PERSON,
  FAR,
  PAVILION,
  book,
  bookingDay,
  cancelCounterBookings,
  cancelLeftovers,
  deleteE2eBlocks,
  expectBookingMail,
  firstSlotDay,
  firstSlotStart,
  tokenFor,
  waitForMail,
} from './reservas-axudas';

/**
 * Reservas do concello de punta a punta: cobrar no mostrador
 * (pendente → pagada; cancelar pagada → a devolver → devolta), o calendario (día, semana, mes e a
 * vista rápida) e o bloqueo sobre unha reserva real: o modal de reservas afectadas trae a reserva,
 * cancélase avisando por correo e o correo de cancelación chega a Mailpit. Axe en cada paso.
 *
 * As reservas fainas a persoa da demo pola API (cada proxecto nos seus días, lonxe de hoxe para
 * non ensuciar o mes en curso dos indicadores, no Pavillón de Piñeiros, onde a demo non ten
 * reservas eses días); ao empezar e ao rematar cancélanse as que
 * non son da demo e bórranse os bloqueos das probas.
 */
test.describe.configure({ mode: 'serial' });

const CANCELLED_BY_MUNICIPALITY = /^(O concello cancelou a túa reserva|El ayuntamiento canceló tu reserva): /;

/** Comezo dos correos das reservas que as probas fan no mostrador (para atopalas e cancelalas). */
const COUNTER_EMAIL = 'e2e-mostrador';

/** Detalle da reserva (ao lado no escritorio; diálogo no móbil). */
function detailOf(page: Page, id: string): Locator {
  return page.locator(`[data-booking-detail="${id}"]`);
}

/**
 * Abre a vista rápida. Desprazar a páxina péchaa (e o evento `scroll` chega un cadro despois):
 * primeiro á vista, un cadro de espera e despois o clic, como faría unha persoa.
 */
async function openPeek(page: Page, trigger: Locator): Promise<void> {
  await trigger.scrollIntoViewIfNeeded();
  await page.evaluate(() => new Promise((resolve) => requestAnimationFrame(() => requestAnimationFrame(resolve))));
  await trigger.click();
}

async function openManage(page: Page, lang: 'gl' | 'es'): Promise<void> {
  await page.goto('/naron');
  await loginAs(page, 'xestor-naron', lang);
  await page
    .getByRole('navigation', { name: text(lang, 'app.manageNav') })
    .getByRole('link', { name: text(lang, 'nav.bookings') })
    .click();
  await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.manage.title') })).toBeVisible();
}

test.describe('reservas do concello', () => {
  test.beforeAll(async ({ request }) => {
    await cancelLeftovers(request, 'vecino-naron', [PAVILION.id]);
    await deleteE2eBlocks(request, PAVILION.id);
    await cancelCounterBookings(request, 'xestor-ferrol', ATHLETICS.slug, COUNTER_EMAIL);
  });

  test.afterAll(async ({ request }) => {
    await cancelLeftovers(request, 'vecino-naron', [PAVILION.id]);
    await deleteE2eBlocks(request, PAVILION.id);
    await cancelCounterBookings(request, 'xestor-ferrol', ATHLETICS.slug, COUNTER_EMAIL);
  });

  test('cobros: pendente → pagada; cancelar pagada → a devolver → devolta', async ({
    page,
    request,
    lang,
  }, testInfo) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const day = bookingDay(FAR + 8, testInfo.project.name);
    const vecino = await tokenFor(request, 'vecino-naron');
    const booking = await book(
      request,
      vecino,
      PAVILION.id,
      PAVILION.use,
      // O primeiro día con ese oco desde o do proxecto: un peche ou un festivo da demo pode caer nel.
      await firstSlotStart(request, PAVILION.id, PAVILION.use, day, 720),
    );
    expect(booking.paymentStatus).toBe('PENDING');

    await openManage(page, lang);
    await checkStep(page);

    // Busca polo código → a reserva → o detalle
    await page.getByRole('searchbox', { name: text(lang, 'booking.manage.search') }).fill(booking.code);
    await expect(
      page.getByRole('heading', { level: 2, name: text(lang, 'booking.manage.list.results') }),
    ).toBeVisible();
    await expect(page.locator('[data-manage-count]')).toHaveText(text(lang, 'booking.manage.countOne', { count: 1 }));
    const row = page.locator(`[data-booking="${booking.id}"]`);
    await expect(row).toBeVisible();
    await checkStep(page);
    await row.click();
    const detail = detailOf(page, booking.id);
    await expect(detail).toContainText(booking.code);
    await checkStep(page);

    // Marcar como pagada
    await detail.locator('[data-payment="PAID"]').click();
    await expect(page.locator('[data-toast]')).toContainText(text(lang, 'booking.manage.detail.done.markPaid'));
    await expect(detail.locator('[data-payment="PENDING"]')).toBeVisible();
    await checkStep(page);

    // Cancelar estando pagada: avisa de que quedará «A devolver»; avísase por correo
    await detail.locator('[data-detail-cancel]').click();
    const cancel = page.getByRole('dialog', { name: text(lang, 'booking.manage.cancel.title') });
    await expect(cancel).toBeVisible();
    await expect(cancel.locator('[data-cancel-paid]')).toBeVisible();
    await expect(cancel.getByRole('switch', { name: text(lang, 'booking.manage.cancel.channel.EMAIL') })).toBeChecked();
    await checkStep(page);
    await cancel.locator('[data-cancel-confirm]').click();
    await expect(cancel).toBeHidden();
    await expect(page.locator('[data-toast]')).toContainText(text(lang, 'booking.manage.detail.done.cancelled'));
    await expect(detail.locator('[data-payment="REFUNDED"]')).toBeVisible();
    await checkStep(page);

    // Devolta
    await detail.locator('[data-payment="REFUNDED"]').click();
    await expect(page.locator('[data-toast]')).toContainText(text(lang, 'booking.manage.detail.done.markRefunded'));
    await expect(detail.locator('[data-payment="TO_REFUND"]')).toBeVisible();
    await checkStep(page);

    // A API garda o estado final
    const xestor = await tokenFor(request, 'xestor-naron');
    const saved = await request.get(`/api/v1/municipalities/naron/bookings/${booking.id}`, { headers: xestor.headers });
    expect(await saved.json()).toMatchObject({ status: 'CANCELLED_BY_MUNICIPALITY', paymentStatus: 'REFUNDED' });
    expectBookingMail(await waitForMail(request, booking.code, CANCELLED_BY_MUNICIPALITY), booking.code);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('calendario: día, semana e mes, e a vista rápida', async ({ page, lang }, testInfo) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const mobile = testInfo.project.name === 'gl-mobile';
    await openManage(page, lang);
    await page.locator('[data-manage-calendar]').click();
    await expect(page).toHaveURL(/\/naron\/xestion\/calendario/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.calendar.title') })).toBeVisible();

    // Día de mañá: a reserva da demo (Fútbol 7 ou pádel) cun nome completo
    await page.locator('[data-mode="DAY"]').click();
    await page.locator('[data-period-next]').click();
    const block = page.locator('[data-cal-block]').first();
    await expect(block).toBeVisible();
    await checkStep(page);
    if (!mobile) {
      // A grella empeza á hora de apertura máis temperá (o pádel abre ás 8:00).
      await expect(page.locator('furar-calendar-grid')).toContainText('8:00');
    }
    await block.click();
    const detail = page.locator('[data-booking-detail]');
    await expect(detail).toBeVisible();
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(detail).toBeHidden();

    // Semana: contas por hora e a vista rápida dunha hora
    await page.locator('[data-mode="WEEK"]').click();
    await expect(page.locator('[data-period-label]')).toContainText(
      text(lang, 'booking.calendar.weekSame', { from: '', to: '', month: '' }).split(' ')[0]!,
    );
    // Só as contas do calendario (os contadores do menú tamén levan `data-count`).
    const count = page.locator('furar-calendar-grid [data-count]').first();
    await expect(count).toBeVisible();
    await checkStep(page);
    await openPeek(page, count);
    const peek = page.locator('[data-peek]');
    await expect(peek).toBeVisible();
    await expect(peek.locator('[data-peek-open]').first()).toBeVisible();
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(peek).toBeHidden();

    // Mes: o día de mañá na vista rápida → «Ver o día»
    await page.locator('[data-mode="MONTH"]').click();
    const tomorrow = page.locator(`[data-day="${isoDay(1)}"]`);
    await expect(tomorrow).toBeVisible();
    await checkStep(page);
    await openPeek(page, tomorrow);
    await expect(peek).toBeVisible();
    await checkStep(page);
    await peek.locator('[data-peek-day]').click();
    await expect(page.locator('[data-mode="DAY"]')).toHaveAttribute('aria-checked', 'true');
    await expect(page.locator('[data-cal-block]').first()).toBeVisible();

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('bloqueo sobre unha reserva: modal coa reserva real → cancelar avisando por correo', async ({
    page,
    request,
    lang,
  }, testInfo) => {
    test.setTimeout(150_000);
    const outside = await hermetic(page);
    // O primeiro día con ese oco desde o do proxecto (un peche ou un festivo da demo pode caer nel):
    // a reserva e o bloqueo van os dous nese día.
    const { day, start } = await firstSlotDay(
      request,
      PAVILION.id,
      PAVILION.use,
      bookingDay(FAR + 12, testInfo.project.name),
      660,
    );
    const vecino = await tokenFor(request, 'vecino-naron');
    const booking = await book(request, vecino, PAVILION.id, PAVILION.use, start);

    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto(`/naron/xestion/instalacions/${PAVILION.id}`);
    await expect(page.getByRole('heading', { level: 1, name: PAVILION.name })).toBeVisible();

    // Modo edición → bloqueo novo ese día (9:00–14:00 por defecto) → «Gardar»
    await page.locator('[data-edit]').click();
    const bar = page.locator('furar-action-bar');
    await expect(bar).toBeVisible();
    const blocks = page.locator('furar-facility-blocks');
    await blocks.locator('[data-block-add]').click();
    const form = page.getByRole('dialog', { name: text(lang, 'facility.blocks.addTitle') });
    await expect(form).toBeVisible();
    // O rato quedou onde estaba «Engadir»: se o diálogo pon alí un «?», a súa axuda ábrese co rato enriba e
    // tapa o campo «Día». Unha persoa movería o rato e esperaría a que se pechase; a proba tamén.
    await page.mouse.move(0, 0);
    await expect(page.locator('.f-help-tip__panel')).toHaveCount(0);
    await pickDate(page, form, text(lang, 'facility.blocks.date'), day, lang);
    const reason = form.getByRole('textbox', { name: text(lang, 'facility.blocks.reason') });
    await reason.fill(BLOCK_REASON);
    await reason.press('Enter');
    await expect(form).toBeHidden();
    await bar.locator('[data-edit-save]').click();

    // O modal trae a reserva real da persoa
    const modal = page.getByRole('dialog', { name: text(lang, 'facility.affectedBookings.titleOne') });
    await expect(modal).toBeVisible();
    const affected = modal.locator(`[data-booking="${booking.id}"]`);
    await expect(affected).toBeVisible();
    await expect(affected).toContainText(PAVILION.useName);
    await expect(affected).toContainText('11:00');
    await checkStep(page);

    // Cancelar avisando por correo
    await affected.getByRole('radio', { name: text(lang, 'facility.affectedBookings.cancel') }).click();
    await expect(
      affected.getByRole('switch', { name: text(lang, 'facility.affectedBookings.channel.EMAIL') }),
    ).toHaveAttribute('aria-checked', 'true');
    await checkStep(page);
    await modal.locator('[data-confirm]').click();
    await expect(modal).toBeHidden();
    await expect(bar).toHaveCount(0);
    await expect(blocks).toContainText(BLOCK_REASON);
    await checkStep(page);

    // A reserva queda cancelada polo concello e chega o correo de cancelación
    const xestor = await tokenFor(request, 'xestor-naron');
    const saved = await request.get(`/api/v1/municipalities/naron/bookings/${booking.id}`, { headers: xestor.headers });
    expect(await saved.json()).toMatchObject({ status: 'CANCELLED_BY_MUNICIPALITY' });
    expectBookingMail(await waitForMail(request, booking.code, CANCELLED_BY_MUNICIPALITY), booking.code);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
  /**
   * Nova reserva no mostrador nun uso por prazas (ADR-0031): a reserva é do correo
   * que dá a persoa, os acompañantes escríbense a man e o total leva as prazas; a ficha da reserva
   * di para quen é, co ano, e «2 prazas» xunto ao uso.
   */
  test('mostrador: reserva a nome dun correo cun acompañante e «Para quen» na ficha', async ({
    page,
    lang,
  }, testInfo) => {
    test.setTimeout(120_000);
    const project = testInfo.project.name;
    const companion = `${E2E_PERSON} Icía ${project}`;
    await page.goto(`/${ATHLETICS.slug}`);
    await loginAs(page, 'xestor-ferrol', lang);
    await page.goto(
      `/${ATHLETICS.slug}/xestion/reservas/nova?instalacion=${ATHLETICS.id}&uso=${ATHLETICS.use}&data=${bookingDay(4, project)}`,
    );
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.bookFor.title') })).toBeVisible();
    await page.locator('[data-reserve-field="time"] button[aria-haspopup="dialog"]').click();
    await page
      .getByRole('dialog', { name: text(lang, 'booking.reserve.time') })
      .locator('[data-reserve-slot]')
      .first()
      .click();

    // Sen correo, a persoa non recibe a confirmación; co correo, a reserva queda ao seu nome.
    const seats = page.locator('[data-book-for-seats]');
    await expect(seats).toBeVisible();
    await expect(page.locator('[data-book-for-hint]')).toHaveText(text(lang, 'booking.bookFor.hint.noEmail'));
    await page.locator('[data-book-for-name]').fill(`${E2E_PERSON} Xoán ${project}`);
    await page.locator('[data-book-for-email]').fill(`${COUNTER_EMAIL}-${project}@example.org`);
    await page.locator('[data-book-for-phone]').fill('611 000 000');
    await expect(page.locator('[data-book-for-hint]')).toHaveText(text(lang, 'booking.bookFor.hint.email'));
    // A persoa ocupa praza sempre.
    await expect(seats.getByRole('option').first()).toHaveAttribute('aria-selected', 'true');

    // Un acompañante, só para esta reserva: nome e ano.
    await seats.locator('[data-book-for-companion-add]').click();
    await seats.locator('[data-companion-name]').fill(companion);
    await page.locator('[data-book-for-go]').click();
    await expect(seats.locator('[data-companion-year]')).toHaveAttribute('aria-invalid', 'true');
    await seats.locator('[data-companion-year]').fill('2016');
    await expect(page.locator('[data-book-for-total]')).toContainText(text(lang, 'booking.who.seats', { count: 2 }));
    await checkStep(page);
    await page.locator('[data-book-for-go]').click();

    // Volve ás reservas co detalle aberto: «Para quen» co ano e as prazas xunto ao uso.
    await page.waitForURL(/\/xestion\/reservas\?reserva=/);
    const id = new URL(page.url()).searchParams.get('reserva') ?? '';
    const detail = detailOf(page, id);
    await expect(detail.locator('[data-value="who"]')).toContainText(`${companion} (2016)`);
    await expect(detail.locator('[data-value="use"]')).toContainText(text(lang, 'booking.who.seats', { count: 2 }));
    await checkStep(page);
  });
});
