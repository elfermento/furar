// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext, Page } from '@playwright/test';
import {
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  openSectionTab,
  test,
  text,
} from './fixtures';
import { darkTheme } from './indicadores-axudas';
import { checkStep, hermetic, isoDay, tabTo } from './instalacions-axudas';
import { STAFF_API, bearer } from './persoal-axudas';
import { FAR, PAVILION, book, bookingDay, cancelLeftovers, firstSlotStart, tokenFor } from './reservas-axudas';

/**
 * Rexistro de actividade do concello (plan do dominio 4, D4-17; ADR-0020): as accións do persoal
 * (invitar, aceptar, desactivar e entrar) coa persoa e o resultado, «Cargar máis» co foco na
 * primeira fila nova, os filtros (período, área, persoa e «Só fallidos») só co teclado e no URL,
 * «atrás», 320 px sen scroll horizontal e a persoa anonimizada se a hai. Axe en cada paso, co tema
 * claro e o escuro do concello; sen peticións fóra de localhost nin violacións de CSP.
 *
 * A demo trae poucos eventos (unha páxina son 50): se faltan, créanse fallos de verdade (reenviar a
 * invitación caducada da demo cunha versión vella: 412, rexistrado como «Fallou» e sen correo nin
 * envío que conte para os límites).
 */

const API = '/api/v1/municipalities/naron/audit-events';
const PAGE = 50;
/** Invitación caducada da demo (`R__demo_staff.sql`). */
const EXPIRED_MEMBER = '019a0b6e-0000-7000-8000-000000000917';

interface ActivityEvent {
  action: string;
  outcome: 'SUCCESS' | 'FAILURE';
  actor: { kind: 'MEMBER' | 'PLATFORM' | 'OTHER'; name?: string; email?: string };
}

/** Todos os eventos do último ano (o período por defecto), pola API do xestor. */
async function allEvents(request: APIRequestContext, headers: Record<string, string>): Promise<ActivityEvent[]> {
  const events: ActivityEvent[] = [];
  let cursor: string | undefined;
  do {
    const response = await request.get(API, { headers, params: cursor ? { cursor } : {} });
    expect(response.ok(), 'rexistro pola API').toBe(true);
    const page = (await response.json()) as { items: ActivityEvent[]; nextCursor?: string };
    events.push(...page.items);
    cursor = page.nextCursor;
  } while (cursor);
  return events;
}

/** Polo menos dúas páxinas: se faltan, fallos de verdade (412) ata pasar de 50. */
async function ensureTwoPages(request: APIRequestContext): Promise<ActivityEvent[]> {
  const headers = await bearer(request, 'xestor-naron');
  let events = await allEvents(request, headers);
  for (let i = events.length; i <= PAGE + 2; i++) {
    const response = await request.post(`${STAFF_API}/${EXPIRED_MEMBER}/invitation`, {
      headers: { ...headers, 'If-Match': '"999999"' },
    });
    expect(response.status(), 'reenvío cunha versión vella').toBe(412);
  }
  if (events.length <= PAGE + 2) events = await allEvents(request, headers);
  return events;
}

/** Entra como xestor-naron e abre o rexistro: «Concello» › «Rexistro». */
async function openActivity(page: Page, lang: 'gl' | 'es'): Promise<void> {
  await page.goto('/naron/instalacions');
  await loginAs(page, 'xestor-naron', lang);
  await openSectionTab(page, lang, 'activity');
  await expect(page).toHaveURL(/\/naron\/xestion\/concello\/rexistro$/);
  await expect(page.getByRole('heading', { level: 2, name: text(lang, 'activity.title') })).toHaveCount(1);
  await expect(page.locator('[data-activity-table] tbody tr').first()).toBeVisible();
}

const rows = (page: Page) => page.locator('[data-activity-table] tbody tr');

test.describe('rexistro de actividade', () => {
  test('as accións do persoal; «Cargar máis» co foco na primeira fila nova; tema escuro; 320 px', async ({
    page,
    lang,
    request,
  }) => {
    test.setTimeout(180_000);
    const outside = await hermetic(page);
    const events = await ensureTwoPages(request);
    await openActivity(page, lang);
    await expect(rows(page)).toHaveCount(PAGE);
    await expect(page.locator('[data-announcement]')).toHaveText(text(lang, 'activity.status.count', { count: PAGE }));
    await checkStep(page);

    // «Cargar máis» co teclado: o foco vai á primeira fila nova e anúnciase
    const more = page.locator('.f-activity__more [data-more]');
    let loaded = PAGE;
    for (let i = 0; i < 20 && (await more.count()); i++) {
      await tabTo(page, more, 400);
      await page.keyboard.press('Enter');
      await expect(page.locator(`[data-activity-table] tbody tr[data-row="${loaded}"]`)).toBeFocused();
      const now = await rows(page).count();
      expect(now).toBeGreaterThan(loaded);
      const key = (await more.count()) ? 'activity.status.more' : 'activity.status.end';
      await expect(page.locator('[data-announcement]')).toHaveText(text(lang, key, { count: now - loaded }));
      if (i === 0) await checkStep(page);
      loaded = now;
    }
    await expect(more).toHaveCount(0);
    // Todas as da API (e o acceso de agora, que entra despois de lelas)
    expect(loaded).toBeGreaterThanOrEqual(events.length);

    // As accións do persoal: invitar, aceptar, desactivar e entrar (as da demo e as das probas)
    const actions = (await page.locator('[data-activity-table] [data-cell="action"]').allInnerTexts()).map((a) =>
      a.trim(),
    );
    for (const code of ['invite', 'accept', 'deactivate', 'sign-in']) {
      expect(actions, code).toContain(text(lang, `activity.actions.staff.${code}`));
    }
    // Cada fila co seu resultado; os fallos, «Fallou»
    const failed = events.filter((e) => e.outcome === 'FAILURE').length;
    await expect(page.locator('[data-activity-table] tr[data-outcome="FAILURE"]')).toHaveCount(failed);
    // A persoa anonimizada (sen nome nin correo), se a hai
    const anonymized = events.filter((e) => e.actor.kind === 'MEMBER' && !e.actor.name && !e.actor.email).length;
    await expect(
      page.locator('[data-cell="person"]').getByText(text(lang, 'activity.actor.anonymized'), { exact: true }),
    ).toHaveCount(anonymized);

    // Tema escuro do concello
    await darkTheme(page);
    await page.reload();
    await expect(rows(page)).toHaveCount(PAGE);
    await checkStep(page);

    // 320 px: cada fila apilada, sen scroll horizontal
    await page.setViewportSize({ width: 320, height: 640 });
    await expect(rows(page).first()).toBeVisible();
    await expectNoHorizontalScroll(page);
    await expectNoA11yViolations(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('filtros só co teclado e no URL: período, área, persoa e «Só fallidos»; «atrás»', async ({
    page,
    lang,
    request,
  }) => {
    test.setTimeout(180_000);
    const outside = await hermetic(page);
    await ensureTwoPages(request);
    await openActivity(page, lang);
    const status = page.locator('[data-announcement]');

    // Período: os últimos sete días co calendario de rango
    const period = page.locator('#activity-period');
    await tabTo(page, period, 60);
    await page.keyboard.press('Enter');
    const dates = page.getByRole('dialog', { name: text(lang, 'activity.filters.period') });
    await expect(dates).toBeVisible();
    const focusedDay = async (): Promise<string> =>
      (await page.evaluate(() => document.activeElement?.getAttribute('data-date'))) ?? '';
    await tabTo(page, dates.locator('td[data-date][tabindex="0"]'));
    const today = isoDay(0);
    const from = isoDay(-7);
    while ((await focusedDay()) !== today) {
      const before = await focusedDay();
      await page.keyboard.press(before < today ? 'ArrowRight' : 'ArrowLeft');
      await expect.poll(focusedDay).not.toBe(before);
    }
    await page.keyboard.press('ArrowUp');
    await expect.poll(focusedDay).toBe(from);
    await page.keyboard.press('Enter');
    await expect(dates.locator('.f-activity__dates-hint')).toHaveText(text(lang, 'activity.filters.datesLast'));
    await page.keyboard.press('ArrowDown');
    await expect.poll(focusedDay).toBe(today);
    await page.keyboard.press('Enter');
    await expect(dates.locator('.f-activity__dates-hint')).toHaveText(text(lang, 'activity.filters.datesReady'));
    await checkStep(page);
    await tabTo(page, dates.locator('[data-period-apply]'));
    await page.keyboard.press('Enter');
    await expect(dates).toBeHidden();
    await expect(period).toBeFocused();
    await expect(page).toHaveURL(new RegExp(`[?&]desde=${from}&ata=${today}`));
    await expect(rows(page).first()).toBeVisible();

    // Área: «Persoal»
    const area = page.locator('[data-area] button.f-select__button');
    await tabTo(page, area);
    await page.keyboard.press('Enter');
    const areas = page.getByRole('listbox', { name: text(lang, 'activity.filters.area') });
    await expect(areas).toBeVisible();
    await checkStep(page);
    const staffOption = areas.getByRole('option', { name: text(lang, 'activity.areas.staff'), exact: true });
    while (!(await isActive(staffOption))) await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await expect(areas).toBeHidden();
    await expect(area).toBeFocused();
    await expect(page).toHaveURL(/[?&]area=staff/);
    await expect(rows(page).first()).toBeVisible();
    for (const action of await page.locator('[data-activity-table] [data-cell="action"]').allInnerTexts()) {
      expect(Object.values(staffActions(lang))).toContain(action.trim());
    }

    // Persoa: a xestora
    const person = page.locator('[data-person] button.f-select__button');
    await tabTo(page, person);
    await page.keyboard.press('Enter');
    const people = page.getByRole('listbox', { name: text(lang, 'activity.filters.person') });
    await expect(people).toBeVisible();
    const manager = people.getByRole('option', { name: /^Xestora Narón$/ });
    while (!(await isActive(manager))) await page.keyboard.press('ArrowDown');
    await page.keyboard.press('Enter');
    await expect(person).toBeFocused();
    await expect(page).toHaveURL(/[?&]persoa=[0-9a-f-]{36}/);
    // A táboa cambia despois do URL: agarda a que só queden as filas da xestora (sen ler as de antes).
    const people$ = page.locator('[data-activity-table] [data-cell="person"]');
    await expect
      .poll(async () => {
        const who = await people$.allInnerTexts();
        return who.length > 0 && who.every((w) => w.includes('Xestora Narón'));
      })
      .toBe(true);

    // «Só fallidos» con Espazo: aria-pressed, URL e só filas «Fallou»
    const failedOnly = page.locator('[data-failed]');
    await tabTo(page, failedOnly);
    await page.keyboard.press('Space');
    await expect(failedOnly).toHaveAttribute('aria-pressed', 'true');
    await expect(page).toHaveURL(/[?&]fallidos=1/);
    await expect(rows(page).first()).toBeVisible();
    await expect(page.locator('[data-activity-table] tr[data-outcome="SUCCESS"]')).toHaveCount(0);
    await expect(status).not.toHaveText('');
    await checkStep(page);

    // «Atrás» desfai cada filtro; «Quitar os filtros» volve ao último ano
    await page.goBack();
    await expect(page).not.toHaveURL(/fallidos=/);
    await expect(failedOnly).toHaveAttribute('aria-pressed', 'false');
    await page.goBack();
    await expect(page).not.toHaveURL(/persoa=/);
    const reset = page.locator('[data-reset]');
    await tabTo(page, reset, 60);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/rexistro$/);
    await expect(rows(page)).toHaveCount(PAGE);

    // O URL cos filtros ábrese tal cal (ligazón compartida)
    await page.goto(`/naron/xestion/concello/rexistro?area=staff&fallidos=1`);
    await expect(page.locator('[data-failed]')).toHaveAttribute('aria-pressed', 'true');
    await expect(area).toHaveAccessibleName(
      `${text(lang, 'activity.filters.area')}, ${text(lang, 'activity.areas.staff')}`,
    );
    await expect(rows(page).first()).toBeVisible();
    await expect(page.locator('[data-activity-table] tr[data-outcome="SUCCESS"]')).toHaveCount(0);
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('a reserva dunha acción leva a «Reservas» co seu detalle aberto', async ({ page, lang, request }, testInfo) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    await cancelLeftovers(request, 'vecino-naron', [PAVILION.id]);
    try {
      const day = bookingDay(FAR + 15, testInfo.project.name);
      const vecino = await tokenFor(request, 'vecino-naron');
      const booking = await book(
        request,
        vecino,
        PAVILION.id,
        PAVILION.use,
        await firstSlotStart(request, PAVILION.id, PAVILION.use, day, 720),
      );
      // Unha acción da xestión sobre a reserva: marcala como pagada (booking.payment).
      const xestor = await tokenFor(request, 'xestor-naron');
      const paid = await request.put(`/api/v1/municipalities/naron/bookings/${booking.id}/payment`, {
        headers: { ...xestor.headers, 'If-Match': `"${booking.version}"` },
        data: { status: 'PAID' },
      });
      expect(paid.status(), `marcar como pagada: ${await paid.text()}`).toBe(200);

      await openActivity(page, lang);
      await page.goto('/naron/xestion/concello/rexistro?area=booking');
      const link = page.locator(`[data-cell="target"] a[href$="reserva=${booking.id}"]`).first();
      await expect(link).toHaveText(text(lang, 'activity.target.BOOKING'));
      await checkStep(page);
      await link.click();
      await expect(page).toHaveURL(/\/naron\/xestion\/reservas$/);
      await expect(page.locator(`[data-booking-detail="${booking.id}"]`)).toContainText(booking.code);
      await checkStep(page);
      expect(outside, 'peticións fóra de localhost').toEqual([]);
    } finally {
      await cancelLeftovers(request, 'vecino-naron', [PAVILION.id]);
    }
  });
});

/** A opción activa do listbox (a que marca `aria-activedescendant` ou ten o foco). */
async function isActive(option: ReturnType<Page['locator']>): Promise<boolean> {
  return option.evaluate((el) => {
    const owner = document.querySelector(`[aria-activedescendant="${el.id}"]`);
    return Boolean(el.id && owner) || el === document.activeElement || el.hasAttribute('data-active');
  });
}

/** Os textos das accións do persoal. */
function staffActions(lang: 'gl' | 'es'): Record<string, string> {
  const codes = ['invite', 'invite-resend', 'role-change', 'deactivate', 'reactivate', 'accept', 'sign-in'];
  return Object.fromEntries(codes.map((c) => [c, text(lang, `activity.actions.staff.${c}`)]));
}
