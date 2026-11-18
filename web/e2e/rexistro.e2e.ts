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
 * Rexistro de actividade do concello (ADR-0020): as
 * accións do persoal (invitar, aceptar, desactivar e entrar) coa persoa e o resultado, «Cargar máis»
 * co foco na primeira fila nova, os filtros das cabeceiras (data, acción, persoa e resultado) só co
 * teclado e no URL, «atrás», o detalle nunha capa, 320 px sen scroll horizontal e a persoa
 * anonimizada se a hai. Axe en cada paso, co tema claro e o escuro do concello; sen peticións fóra
 * de localhost nin violacións de CSP.
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

/**
 * Un reenvío da xestora que falla (412) agora: un fallo desta semana para os filtros. Coa demo, o
 * rexistro xa ten dúas páxinas e `ensureTwoPages` pode non crear ningún.
 */
async function failedResend(request: APIRequestContext): Promise<void> {
  const response = await request.post(`${STAFF_API}/${EXPIRED_MEMBER}/invitation`, {
    headers: { ...(await bearer(request, 'xestor-naron')), 'If-Match': '"999999"' },
  });
  expect(response.status(), 'reenvío cunha versión vella').toBe(412);
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
/** O botón da cabeceira dunha columna (`date`, `person`, `action`, `outcome`…). */
const header = (page: Page, column: string) => page.locator(`thead [data-filter="${column}"] button`).first();

/** Abre co teclado o filtro dunha columna e devolve o seu panel. */
async function openFilter(page: Page, lang: 'gl' | 'es', column: string) {
  await tabTo(page, header(page, column), 80);
  await page.keyboard.press('Enter');
  const panel = page.getByRole('dialog', { name: text(lang, `activity.col.${column}`) });
  await expect(panel).toBeVisible();
  return panel;
}

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
    await expect(page.locator('[data-announcement]')).toHaveText(text(lang, 'activity.status.count', { count: PAGE }));
    await checkStep(page);

    // «Cargar máis» co teclado: o foco vai á fila da primeira acción nova e anúnciase
    const more = page.locator('.f-activity__more [data-more]');
    for (let i = 0; i < 20 && (await more.count()); i++) {
      await tabTo(page, more, 400);
      await page.keyboard.press('Enter');
      await expect(page.locator('[data-activity-table] [data-open]:focus')).toHaveCount(1);
      const key = (await more.count()) ? 'activity.status.more' : 'activity.status.end';
      await expect(page.locator('[data-announcement]')).toHaveText(new RegExp(text(lang, key, { count: '\\d+' })));
      if (i === 0) await checkStep(page);
    }
    await expect(more).toHaveCount(0);

    // As accións do persoal: invitar, aceptar, desactivar e entrar (as da demo e as das probas)
    const actions = (await page.locator('[data-activity-table] [data-cell="action"]').allInnerTexts()).map((a) =>
      a.trim(),
    );
    for (const code of ['invite', 'accept', 'deactivate', 'sign-in']) {
      expect(actions, code).toContain(text(lang, `activity.done.staff.${code}`));
    }
    // Os fallos do persoal, «Intentou …» e «Fallou»
    expect(events.some((e) => e.outcome === 'FAILURE')).toBe(true);
    await expect(page.locator('[data-activity-table] tr[data-outcome="FAILURE"]').first()).toBeVisible();
    // A persoa anonimizada (sen nome nin correo), se a hai
    const anonymized = events.some((e) => e.actor.kind === 'MEMBER' && !e.actor.name && !e.actor.email);
    if (anonymized) {
      await expect(
        page
          .locator('[data-cell="person"]')
          .getByText(text(lang, 'activity.actor.anonymized'), { exact: true })
          .first(),
      ).toBeVisible();
    }

    // O detalle dunha fila, co teclado: capa coa persoa, o rol e a petición; Esc pecha
    await rows(page).first().locator('[data-open]').focus();
    await page.keyboard.press('Enter');
    const detail = page.locator('[data-activity-detail]');
    await expect(detail).toBeVisible();
    await expect(detail.locator('[data-request]')).not.toHaveText('');
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(detail).toBeHidden();

    // Tema escuro do concello
    await darkTheme(page);
    await page.reload();
    await expect(rows(page).first()).toBeVisible();
    await checkStep(page);

    // 320 px: tarxetas de dúas liñas e os filtros en «Filtros» (folla), sen scroll horizontal
    await page.setViewportSize({ width: 320, height: 640 });
    await expect(rows(page).first()).toBeVisible();
    await page.locator('[data-filters-open]').click();
    await expect(page.locator('[data-filters-sheet] [data-filter="action"]')).toBeVisible();
    await expectNoA11yViolations(page);
    await page.keyboard.press('Escape');
    await expect(page.locator('[data-filters-sheet]')).toBeHidden();
    await expectNoHorizontalScroll(page);
    await expectNoA11yViolations(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('filtros das cabeceiras só co teclado e no URL: data, acción, persoa e resultado; «atrás»', async ({
    page,
    lang,
    request,
  }) => {
    test.setTimeout(180_000);
    const outside = await hermetic(page);
    await ensureTwoPages(request);
    await failedResend(request);
    await openActivity(page, lang);
    const status = page.locator('[data-announcement]');

    // Data: «Últimos 7 días» pecha o panel e leva o período ao URL
    let panel = await openFilter(page, lang, 'date');
    await checkStep(page);
    await tabTo(page, panel.locator('[data-option="week"]'));
    await page.keyboard.press('Enter');
    await expect(panel).toBeHidden();
    await expect(page).toHaveURL(new RegExp(`[?&]desde=${isoDay(-6)}&ata=${isoDay(0)}`));
    await expect(page.locator('[data-active-filters]')).toContainText(text(lang, 'activity.filters.presets.week'));
    await expect(rows(page).first()).toBeVisible();

    // Acción: busca «invit…» e escolle «Invitar ao persoal»; o panel queda aberto (pódese sumar outra)
    panel = await openFilter(page, lang, 'action');
    await tabTo(page, panel.locator('[data-column-search]'));
    await page.keyboard.type(text(lang, 'activity.actions.staff.invite').slice(0, 6));
    const invite = panel.locator('[data-option="staff.invite"]');
    await tabTo(page, invite);
    await page.keyboard.press('Enter');
    await expect(invite).toHaveAttribute('aria-pressed', 'true');
    await expect(page).toHaveURL(/[?&]accion=staff\.invite/);
    // … e súmase «Reenviar unha invitación» (os fallos de failedResend) desde a mesma busca.
    const search = panel.locator('[data-column-search]');
    for (let i = 0; i < 40 && !(await search.evaluate((el) => el === document.activeElement)); i++) {
      await page.keyboard.press('Shift+Tab');
    }
    await expect(search).toBeFocused();
    await page.keyboard.press('ControlOrMeta+A');
    await page.keyboard.type(text(lang, 'activity.actions.staff.invite-resend').slice(0, 6));
    const resend = panel.locator('[data-option="staff.invite-resend"]');
    await tabTo(page, resend);
    await page.keyboard.press('Enter');
    await expect(resend).toHaveAttribute('aria-pressed', 'true');
    await expect(page).toHaveURL(/[?&]accion=staff\.invite-resend(&|$)/);
    await page.keyboard.press('Escape');
    await expect(header(page, 'action')).toBeFocused();
    await expect(header(page, 'action').locator('.f-field-popover__dot')).toHaveCount(1);
    await checkStep(page);

    // Persoa: a xestora (a opción e a columna levan primeiro o correo)
    panel = await openFilter(page, lang, 'person');
    const manager = panel.locator('[data-option]').filter({ hasText: /^xestor-naron@example\.org/ });
    await tabTo(page, manager);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/[?&]persoa=[0-9a-f-]{36}/);
    await page.keyboard.press('Escape');
    const people$ = page.locator('[data-activity-table] [data-cell="person"]');
    await expect
      .poll(async () => {
        const who = await people$.allInnerTexts();
        return who.every((w) => w.includes('xestor-naron@example.org'));
      })
      .toBe(true);

    // Resultado: «Fallou»
    panel = await openFilter(page, lang, 'outcome');
    await tabTo(page, panel.locator('[data-option="FAILURE"]'));
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/[?&]resultado=fallou/);
    await page.keyboard.press('Escape');
    await expect(page.locator('[data-activity-table] tr[data-outcome="SUCCESS"]')).toHaveCount(0);
    await expect(status).not.toHaveText('');
    await checkStep(page);

    // «Atrás» desfai cada filtro; «Borrar filtros» volve ao último ano
    await page.goBack();
    await expect(page).not.toHaveURL(/resultado=/);
    await page.goBack();
    await expect(page).not.toHaveURL(/persoa=/);
    const reset = page.locator('[data-clear-filters]');
    await tabTo(page, reset, 80);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/rexistro$/);
    await expect(page.locator('[data-active-filters]')).toHaveCount(0);

    // O URL cos filtros ábrese tal cal (ligazón compartida)
    await page.goto(`/naron/xestion/concello/rexistro?accion=staff.invite-resend&resultado=fallou`);
    await expect(page.locator('[data-active-filters] .f-chip__label')).toHaveCount(2);
    await expect(header(page, 'outcome')).toHaveAccessibleName(
      `${text(lang, 'activity.col.outcome')}, ${text(lang, 'ui.columnFilter.active')}`,
    );
    await expect(rows(page).first()).toBeVisible();
    await expect(page.locator('[data-activity-table] tr[data-outcome="SUCCESS"]')).toHaveCount(0);
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('o detalle dunha acción sobre unha reserva leva a «Reservas» co seu detalle aberto', async ({
    page,
    lang,
    request,
  }, testInfo) => {
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
      await page.goto('/naron/xestion/concello/rexistro?accion=booking.payment');
      await rows(page).first().locator('[data-open]').click();
      const detail = page.locator('[data-activity-detail]');
      await expect(detail).toContainText(text(lang, 'activity.fields.booking.payment'));
      const link = detail.locator(`a[href$="reserva=${booking.id}"]`);
      await expect(link).toBeVisible();
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
