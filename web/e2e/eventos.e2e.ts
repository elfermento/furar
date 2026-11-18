// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext } from '@playwright/test';
import {
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  managerHeaders,
  test,
  text,
} from './fixtures';

/**
 * Eventos do concello (ADR-0038): a veciñanza ve «Eventos e actividades» na
 * portada (co chip das prazas no pé), apúntase na ficha, veo en «As miñas reservas» e desapúntase;
 * «Con aprobación» pide praza e a xestión acepta e marca o pago; un evento completo non deixa
 * apuntarse (sen aprobación, só entrar na lista de espera; con aprobación, pedir praza); a xestión crea un evento co
 * formulario; no móbil (320 px) nada desborda. Con axe en cada pantalla. Na demo, Narón.
 */

const BASE = '/api/v1/municipalities/naron/events';

/** Un evento novo pola API da xestión (publicado, noutro sitio), cun nome propio da proba. */
async function createEvent(
  request: APIRequestContext,
  name: string,
  extra: Record<string, unknown> = {},
): Promise<string> {
  const headers = await managerHeaders(request, 'naron');
  const startsAt = new Date(Date.now() + 20 * 86_400_000);
  startsAt.setUTCHours(10, 0, 0, 0);
  const created = await request.post(`${BASE}/manage`, {
    headers,
    data: {
      nameGl: name,
      nameEs: name,
      descriptionGl: 'Proba automática.',
      descriptionEs: 'Prueba automática.',
      startsAt: startsAt.toISOString(),
      place: 'Praza do concello',
      capacity: 10,
      published: true,
      approval: false,
      ...extra,
    },
  });
  expect(created.status(), 'alta do evento').toBe(201);
  return ((await created.json()) as { id: string }).id;
}

/** Cancela (ou elimina, se non ten ninguén) os eventos da proba que queden: os que empezan por `prefix`. */
async function removeEvents(request: APIRequestContext, prefix: string): Promise<void> {
  const headers = await managerHeaders(request, 'naron');
  const list = await request.get(`${BASE}/manage`, { headers });
  const events = (await list.json()) as {
    id: string;
    name: { gl: string };
    cancelledAt?: string;
    confirmed: number;
    pending: number;
    version: number;
  }[];
  for (const event of events.filter((e) => e.name.gl.startsWith(prefix) && !e.cancelledAt)) {
    if (event.confirmed + event.pending > 0) {
      await request.post(`${BASE}/manage/${event.id}/cancel`, {
        headers: { ...headers, 'If-Match': `"${event.version}"` },
      });
    } else {
      await request.delete(`${BASE}/manage/${event.id}`, { headers });
    }
  }
}

test.describe('eventos do concello', () => {
  test('a veciñanza apúntase desde a portada e desapúntase en «As miñas reservas»', async ({
    page,
    request,
    lang,
  }, info) => {
    const name = `E2E ${info.project.name} apuntarse ${Date.now()}`;
    const id = await createEvent(request, name);
    try {
      await page.goto('/naron');
      await loginAs(page, 'vecino-naron', lang);
      await page.goto('/naron');
      const section = page.locator('[data-home-events]');
      await expect(section.getByRole('heading', { name: text(lang, 'event.public.upcoming') })).toBeVisible();
      await expect(section.locator(`[data-event-card="${id}"] [data-seats]`)).toHaveText(
        text(lang, 'ui.seats.free', { count: 10 }),
      );
      await section.locator(`[data-event-card="${id}"] a`).click();
      await expect(page).toHaveURL(new RegExp(`/naron/eventos/${id}$`));
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(name);
      await expect(page.locator('[data-event-seats]')).toContainText('10');
      await expectNoA11yViolations(page);

      await page.locator('[data-event-join]').click();
      await expect(page.locator('[data-band="joined"]')).toContainText(text(lang, 'event.band.joined'));
      await expect(page.locator('[data-event-leave]')).toHaveText(text(lang, 'event.band.leave'));

      await page.goto('/naron/reservas');
      const card = page.locator(`[data-my-event="${id}"]`);
      await expect(card.locator('[data-chip="CONFIRMED"]')).toHaveText(text(lang, 'event.mine.CONFIRMED'));
      // A tarxeta abre o detalle: a banda con «Darme de baixa» e «Ver o evento».
      await expect(card.locator('button, a')).toHaveCount(1);
      await expectNoA11yViolations(page);
      await card.locator('.f-status-card__open').click();
      const detail = page.getByRole('dialog', { name });
      await expect(detail.locator('furar-action-item')).toHaveCount(1);
      await expectNoA11yViolations(page);
      await detail.locator('[data-event-leave]').click();
      await expect(detail).toBeHidden();
      await expect(card).toHaveCount(0);
      await expect(page.locator('furar-toast')).toContainText(text(lang, 'event.public.leftToast'));
    } finally {
      await removeEvents(request, name);
    }
  });

  test('con aprobación: pide praza, a xestión acepta e marca o pago', async ({ page, request, lang }, info) => {
    const name = `E2E ${info.project.name} aprobación ${Date.now()}`;
    const id = await createEvent(request, name, { approval: true, priceCents: 500 });
    try {
      await page.goto(`/naron/eventos/${id}`);
      await loginAs(page, 'vecino-naron', lang);
      await page.goto(`/naron/eventos/${id}`);
      await expect(page.locator('[data-event-price]')).toContainText('5,00');
      await expect(page.locator('[data-event-join]')).toHaveText(text(lang, 'event.public.ask'));
      await page.locator('[data-event-join]').click();
      await expect(page.locator('[data-band="pending"]')).toContainText(text(lang, 'event.public.pendingTitle'));
      await expectNoA11yViolations(page);

      await page.context().clearCookies();
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto('/naron/xestion/eventos');
      const row = page.locator(`[data-event-row="${id}"]`);
      await expect(row.locator('[data-event-pending]')).toBeVisible();
      await expectNoA11yViolations(page);
      await row.locator('button').first().click();

      // O detalle (diálogo grande): as persoas, na súa pestana.
      const dialog = page.getByRole('dialog');
      await dialog.locator('[data-detail-tab="people"]').click();
      const request1 = dialog.locator('[data-request]').first();
      await expect(request1.locator('[data-request-reject]')).toHaveText(new RegExp(text(lang, 'event.people.reject')));
      await expectNoA11yViolations(page);
      await request1.locator('[data-request-accept]').click();
      await expect(dialog.locator('[data-request]')).toHaveCount(0);

      const person = dialog.locator('[data-people-table] [data-person]').first();
      await person.locator('[data-person-payment] button').click();
      await page.getByRole('menuitemradio', { name: text(lang, 'event.payment.PAID') }).click();
      await expect(person.locator('[data-person-payment]')).toContainText(text(lang, 'event.payment.PAID'));
    } finally {
      await removeEvents(request, name);
    }
  });

  test('un evento completo: «Completa»; sen aprobación, a lista de espera; con aprobación, «Pedir praza»', async ({
    page,
    request,
    lang,
  }) => {
    const list = await request.get(BASE);
    const full = ((await list.json()) as { id: string; free?: number; approval: boolean }[]).find((e) => e.free === 0);
    expect(full, 'na demo hai un evento completo (Torneo 3x3)').toBeTruthy();
    await page.goto(`/naron/eventos/${full!.id}`);
    await expect(page.locator('[data-event-seats] [data-seats="full"]')).toHaveText(text(lang, 'ui.seats.full'));
    await expect(page.locator('[data-event-join]')).toHaveCount(full!.approval ? 1 : 0);
    await expect(page.locator('[data-event-wait]')).toHaveCount(full!.approval ? 0 : 1);
    await expectNoA11yViolations(page);
  });

  test('a xestión crea un evento co formulario', async ({ page, request, lang }, info) => {
    const name = `E2E ${info.project.name} formulario ${Date.now()}`;
    try {
      await page.setViewportSize({ width: 1280, height: 800 });
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await page.goto('/naron/xestion/eventos');
      await expect(
        page.locator('[data-manage-tabs]').getByRole('link', { name: new RegExp(text(lang, 'nav.events')) }),
      ).toHaveAttribute('aria-current', 'page');
      await page.locator('[data-event-new]').click();
      const dialog = page.getByRole('dialog', { name: text(lang, 'event.form.newEvent') });
      await expect(dialog).toBeVisible();
      await expectNoA11yViolations(page);

      const nameField = dialog.locator('.f-bilingual').first();
      for (const l of ['gl', 'es'] as const) {
        await nameField.locator(`[data-lang="${l}"]`).click();
        // O mesmo campo cambia de idioma: escríbese cando xa mostra o novo.
        await expect(nameField.locator(`[data-lang="${l}"]`)).toHaveAttribute('aria-pressed', 'true');
        await nameField.locator('input').fill(name);
        await expect(nameField.locator('input')).toHaveValue(name);
      }
      await dialog.locator('[data-event-date] button').click();
      await page.getByRole('button', { name: text(lang, 'ui.calendar.nextMonth') }).click();
      const next = new Date();
      next.setMonth(next.getMonth() + 1, 15);
      const iso = `${next.getFullYear()}-${String(next.getMonth() + 1).padStart(2, '0')}-15`;
      await page.locator(`[data-date="${iso}"]`).click();
      await dialog.locator('[data-place-kind="other"]').click();
      await dialog.locator('[data-event-place]').fill('Praza do concello');
      await dialog.locator('[data-event-capacity]').fill('30');
      // «Apuntarse ata» só aparece co seu interruptor aceso.
      await expect(dialog.locator('[data-event-deadline]')).toHaveCount(0);
      await dialog.locator('[data-event-published] button[role="switch"]').click();
      await dialog.locator('[data-event-save]').click();
      await expect(dialog).toBeHidden();
      await expect(page.locator('furar-toast')).toContainText(name);
      await expect(page.locator('[data-events-table]')).toContainText(name);
    } finally {
      await removeEvents(request, name);
    }
  });

  test('móbil a 320 px: lista en tarxetas e ficha sen desbordar', async ({ page, request, lang }, info) => {
    const name = `E2E ${info.project.name} móbil ${Date.now()}`;
    const id = await createEvent(request, name, { priceCents: 300 });
    try {
      await page.setViewportSize({ width: 320, height: 720 });
      await page.goto(`/naron/eventos/${id}`);
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(name);
      await expectNoHorizontalScroll(page);
      await expectNoA11yViolations(page);

      await loginAs(page, 'xestor-naron', lang);
      await page.goto('/naron/xestion/eventos');
      await expect(page.locator(`[data-event-item="${id}"]`)).toBeVisible();
      await expectNoHorizontalScroll(page);
      await page.locator(`[data-event-item="${id}"]`).click();
      await expect(page.getByRole('dialog')).toBeVisible();
      await expectNoHorizontalScroll(page);
      await expectNoA11yViolations(page);
    } finally {
      await removeEvents(request, name);
    }
  });
});
