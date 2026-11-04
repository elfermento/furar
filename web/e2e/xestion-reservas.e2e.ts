// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Locator, Page } from '@playwright/test';
import { expect, loginAs, test, text } from './fixtures';
import { checkStep, hermetic, isoDay, pickDate } from './instalacions-axudas';
import {
  BLOCK_REASON,
  FAR,
  PAVILION,
  book,
  bookingDay,
  cancelLeftovers,
  deleteE2eBlocks,
  expectBookingMail,
  slotStart,
  tokenFor,
  waitForMail,
} from './reservas-axudas';

/**
 * Reservas do concello de punta a punta (plan do dominio 2, D2-17): cobrar no mostrador
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
  });

  test.afterAll(async ({ request }) => {
    await cancelLeftovers(request, 'vecino-naron', [PAVILION.id]);
    await deleteE2eBlocks(request, PAVILION.id);
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
      await slotStart(request, PAVILION.id, PAVILION.use, day, 720),
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
    await expect(cancel.locator('[data-channel="EMAIL"]')).toBeChecked();
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
    const count = page.locator('[data-count]').first();
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
    const day = bookingDay(FAR + 12, testInfo.project.name);
    const vecino = await tokenFor(request, 'vecino-naron');
    const booking = await book(
      request,
      vecino,
      PAVILION.id,
      PAVILION.use,
      await slotStart(request, PAVILION.id, PAVILION.use, day, 660),
    );

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
      affected.getByRole('checkbox', { name: text(lang, 'facility.affectedBookings.channel.EMAIL') }),
    ).toBeChecked();
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
});
