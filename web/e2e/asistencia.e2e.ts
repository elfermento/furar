// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext, Page } from '@playwright/test';
import { expect, loginAs, openManageLink, test, text } from './fixtures';
import { checkStep, hermetic, isoDay } from './instalacions-axudas';
import {
  FAR,
  PADEL,
  PAVILION,
  book,
  bookingDay,
  cancelLeftovers,
  firstSlotStart,
  tokenFor,
  waitForMail,
} from './reservas-axudas';

/**
 * Validar o QR e non presentados de punta a punta (ADR-0032): Recepción
 * valida por código (fóra de prazo, co motivo obrigatorio; en prazo, soa) e desfaino; marca e quita
 * unha falta na ficha dunha reserva xa pasada, e o correo da falta chega a Mailpit; e a persoa
 * bloqueada ve por que e ata cando, sen «Confirmar reserva». Axe en cada paso.
 *
 * A reserva de fóra de prazo faina a persoa da demo pola API, lonxe de hoxe (`FAR`); as demais son
 * reservas da demo (a que está en marcha e unha xa rematada), que quedan sen marca ao rematar. O
 * bloqueo depende do paso do tempo (só contan as faltas posteriores a acender a penalización), así
 * que aquí a resposta de `…/me/booking-block` vai simulada: a regra enteira próbaa `NoShowPenaltyIT`.
 */
test.describe.configure({ mode: 'serial' });

const API = '/api/v1/municipalities/naron';
const MARGIN_MS = 45 * 60_000;
const FAULT_MAIL = /^(Non te presentaches|No te presentaste): /;

interface Managed {
  id: string;
  code: string;
  start: string;
  end: string;
  status: string;
  attendance: string;
  done: boolean;
  version: number;
}

/** As reservas do concello que empezan entre eses días (os dous incluídos), como as ve Recepción. */
async function bookingsBetween(request: APIRequestContext, from: string, to: string): Promise<Managed[]> {
  const staff = await tokenFor(request, 'recepcion-naron');
  const list = await request.get(`${API}/bookings`, {
    headers: staff.headers,
    params: { from, to, sort: 'START', size: 100 },
  });
  expect(list.ok(), 'reservas do concello').toBe(true);
  return ((await list.json()) as { items: Managed[] }).items;
}

/** Deixa sen marca de asistencia as reservas que tocaron as probas (tamén as dunha execución cortada). */
async function clearMarks(request: APIRequestContext, ids: readonly string[]): Promise<void> {
  const staff = await tokenFor(request, 'recepcion-naron');
  for (const id of ids) {
    const found = await request.get(`${API}/bookings/${id}`, { headers: staff.headers });
    if (!found.ok()) continue;
    const booking = (await found.json()) as Managed;
    if (booking.attendance === 'NONE') continue;
    const undone = await request.delete(`${API}/bookings/${id}/attendance`, {
      headers: { ...staff.headers, 'If-Match': `"${booking.version}"` },
    });
    expect(undone.status(), `quitar a marca de ${booking.code}`).toBe(200);
  }
}

async function openValidate(page: Page, lang: 'gl' | 'es'): Promise<void> {
  await page.goto('/naron');
  await loginAs(page, 'recepcion-naron', lang);
  await openManageLink(page, lang, 'nav.validate');
  await expect(page.getByRole('heading', { level: 1, name: text(lang, 'validate.title') })).toBeVisible();
}

test.describe('validar o QR e non presentados', () => {
  /** As reservas ás que as probas lles poñen marca. */
  const touched: string[] = [];

  test.beforeAll(async ({ request }) => {
    await cancelLeftovers(request, 'vecino-naron', [PAVILION.id]);
  });

  test.afterAll(async ({ request }) => {
    await clearMarks(request, touched);
    await cancelLeftovers(request, 'vecino-naron', [PAVILION.id]);
  });

  test('validar por código fóra de prazo: aviso, motivo obrigatorio, «Validar igualmente» e «Desfacer»', async ({
    page,
    request,
    lang,
  }, testInfo) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const day = bookingDay(FAR + 12, testInfo.project.name);
    const booking = await book(
      request,
      await tokenFor(request, 'vecino-naron'),
      PAVILION.id,
      PAVILION.use,
      // Lonxe de hoxe (fóra da marxe de validación); o primeiro día con ese oco desde o do proxecto.
      await firstSlotStart(request, PAVILION.id, PAVILION.use, day, 780),
    );
    touched.push(booking.id);

    await openValidate(page, lang);
    await checkStep(page);
    const code = page.locator('[data-validate-code]');

    // Un código que non é de ningunha reserva.
    await code.fill('FUR-0000');
    await expect(page.locator('[data-validate-outcome="NOT_FOUND"]')).toContainText(
      text(lang, 'validate.result.notFound'),
    );

    // Tecleado en minúsculas: ao completalo, lese só; fóra de prazo non se valida soa.
    await code.fill(booking.code.toLowerCase());
    await expect(code).toHaveValue(booking.code);
    const warning = page.locator('[data-validate-outcome="OUT_OF_WINDOW"]');
    await expect(warning).toContainText(text(lang, 'validate.result.outOfWindow.title'));
    await expect(page.locator('[data-validate-facility]')).toContainText(PAVILION.name);
    await checkStep(page);

    // Sen motivo non valida: o erro debaixo do campo e o foco nel.
    await page.locator('[data-validate-force]').click();
    await expect(page.locator('#validate-reason-error')).toHaveText(text(lang, 'validate.reason.required'));
    await expect(page.locator('[data-validate-reason]')).toBeFocused();
    await checkStep(page);

    await page.locator('[data-validate-reason]').fill('Proba e2e: chegou antes');
    await page.locator('[data-validate-force]').click();
    await expect(page.locator('[data-validate-outcome="VALIDATED"]')).toContainText(
      text(lang, 'validate.result.validated'),
    );
    await checkStep(page);

    // Lela outra vez non cambia nada: xa estaba validada.
    await page.locator('[data-validate-next]').click();
    await expect(code).toHaveValue('');
    await code.fill(booking.code);
    await expect(page.locator('[data-validate-outcome="ALREADY_VALIDATED"]')).toContainText(
      text(lang, 'validate.result.already.title'),
    );

    // «Desfacer» pola API da ficha deixa a reserva sen marca: a persoa pode cancelala de novo.
    await clearMarks(request, [booking.id]);
    const staff = await tokenFor(request, 'recepcion-naron');
    const saved = await request.get(`${API}/bookings/${booking.id}`, { headers: staff.headers });
    expect(await saved.json()).toMatchObject({ status: 'CONFIRMED', attendance: 'NONE' });
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('validar por código en prazo: valida soa, con «Validar outra» e «Desfacer»', async ({
    page,
    request,
    lang,
  }, testInfo) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const now = Date.now();
    const today = await bookingsBetween(request, isoDay(-1), isoDay(1));
    const open = today.filter((b) => b.status === 'CONFIRMED' && b.attendance === 'NONE');
    // A reserva da demo que está en marcha (ou a 45 minutos de empezar ou de rematar), se a hai.
    const live = open.find(
      (b) => now >= Date.parse(b.start) - MARGIN_MS + 60_000 && now <= Date.parse(b.end) + MARGIN_MS - 60_000,
    );
    await openValidate(page, lang);
    const code = page.locator('[data-validate-code]');

    if (!live) {
      // Fóra das horas da demo ninguén está en prazo: compróbase que a seguinte non se valida soa.
      testInfo.annotations.push({
        type: 'asistencia',
        description: 'sen ningunha reserva da demo en prazo a esta hora',
      });
      const next = open.find((b) => Date.parse(b.start) - MARGIN_MS > now + 60_000);
      expect(next, 'unha reserva da demo por vir').toBeDefined();
      await code.fill(next!.code);
      await expect(page.locator('[data-validate-outcome="OUT_OF_WINDOW"]')).toBeVisible();
      await expect(page.locator('[data-validate-force]')).toBeVisible();
      await checkStep(page);
      expect(outside, 'peticións fóra de localhost').toEqual([]);
      return;
    }

    touched.push(live.id);
    await code.fill(live.code);
    await expect(page.locator('[data-validate-outcome="VALIDATED"]')).toContainText(
      text(lang, 'validate.result.validated'),
    );
    await expect(page.locator('[data-validate-reason]')).toHaveCount(0);
    await expect(page.locator('[data-validate-next]')).toHaveText(text(lang, 'validate.next'));
    await checkStep(page);

    // «Desfacer»: a reserva queda sen marca e a pantalla, lista para outro código.
    await page.locator('[data-validate-undo]').click();
    await expect(page.locator('[data-validate-outcome]')).toHaveCount(0);
    await expect(code).toHaveValue('');
    const staff = await tokenFor(request, 'recepcion-naron');
    const saved = await request.get(`${API}/bookings/${live.id}`, { headers: staff.headers });
    expect(await saved.json()).toMatchObject({ status: 'CONFIRMED', attendance: 'NONE' });
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('ficha dunha reserva pasada: «Non presentado», chip, correo da falta e «Quitar a falta»', async ({
    page,
    request,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    // Unha reserva da demo xa rematada, confirmada e sen marca.
    const past = (await bookingsBetween(request, isoDay(-6), isoDay(-1))).find(
      (b) => b.status === 'CONFIRMED' && b.done && b.attendance === 'NONE',
    );
    expect(past, 'unha reserva da demo xa rematada').toBeDefined();
    const booking = past!;
    touched.push(booking.id);

    await page.goto('/naron');
    await loginAs(page, 'recepcion-naron', lang);
    await page.goto(`/naron/xestion/reservas?reserva=${booking.id}`);
    const detail = page.locator(`[data-booking-detail="${booking.id}"]`);
    await expect(detail).toContainText(booking.code);
    await expect(detail.locator('[data-attendance]')).toHaveCount(0);
    const mark = detail.locator('[data-detail-no-show-mark]');
    await expect(mark).toHaveText(text(lang, 'booking.manage.noShow.action'));
    await checkStep(page);

    await mark.click();
    await expect(detail.locator('[data-attendance="NO_SHOW"]')).toHaveText(text(lang, 'booking.attendance.NO_SHOW'));
    await expect(mark).toHaveCount(0);
    const remove = detail.locator('[data-detail-attendance-undo]');
    await expect(remove).toHaveText(text(lang, 'booking.manage.noShow.remove'));
    await checkStep(page);

    // A persoa recibe o correo da falta, sen QR nin arquivo para o calendario.
    const mail = await waitForMail(request, booking.code, FAULT_MAIL);
    expect(mail.HTML).toContain(booking.code);
    expect(mail.Attachments).toEqual([]);

    // Ao reabrir a ficha segue marcada; «Quitar a falta» déixaa como estaba.
    await page.goto(`/naron/xestion/reservas?reserva=${booking.id}`);
    await expect(detail.locator('[data-attendance="NO_SHOW"]')).toBeVisible();
    await remove.click();
    await expect(detail.locator('[data-attendance]')).toHaveCount(0);
    await expect(mark).toBeVisible();
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('persoa bloqueada por faltas: o aviso coa data, o motivo e o teléfono, sen «Confirmar reserva»', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    // O bloqueo real precisa días (ver arriba): a resposta do bloqueo vai simulada.
    await page.route('**/api/v1/municipalities/naron/me/booking-block', (route) =>
      route.fulfill({ json: { blocked: true, until: isoDay(15), faults: 3, windowDays: 30 } }),
    );
    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto(`/naron/instalacions/${PADEL.id}/reservar`);
    const notice = page.locator('[data-reserve-blocked]');
    await expect(notice).toBeVisible();
    await expect(notice).toContainText(
      text(lang, 'booking.reserve.blocked.body', { faults: 3, days: 30, phone: '' }).split(':')[0]!,
    );
    await expect(notice).toContainText('981 000 101');
    await expect(page.locator('[data-reserve-field="time"]')).toBeVisible();
    await expect(page.locator('[data-reserve-go]')).toHaveCount(0);
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
});
