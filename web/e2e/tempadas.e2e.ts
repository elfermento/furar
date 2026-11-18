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
 * Tempadas das entidades (ADR-0039) coa demo de Narón (`R__demo_naron_seasons.sql`):
 * «Club Demo Baloncesto» (responsable: vecino-naron) cunha tempada no Pavillón de Piñeiros e
 * «Club Demo Patinaxe» cunha solicitude pendente. A xestión ve «Entidades» co número de
 * solicitudes, revisa a solicitude (sen decidila: a demo queda igual para os outros proxectos),
 * dá de alta, edita e elimina unha entidade propia da proba, ve «Tempadas» en Reservas co seu
 * detalle e «Nova reserva › Tempada»; a persoa responsable ve «Eu · Club Demo Baloncesto», as
 * sesións, «Solicitar tempada» e «Reservar como». Con axe e sen scroll de lado en cada pantalla.
 */

const BASE = '/api/v1/municipalities/naron';
const BALONCESTO = '019a0b6e-0000-7000-8000-000000000b01';
const SOLICITUDE = '019a0b6e-0000-7000-8000-000000000b21';
const TEMPADA = '019a0b6e-0000-7000-8000-000000000b11';
const PAVILLON = '019a0b6e-0000-7000-8000-000000000204';
/** «Pistas transversais» (por partes): «Reservar como» non sae nos usos por prazas. */
const TRANSVERSAIS = '019a0b6e-0000-7000-8000-000000000237';

/** Borra as entidades que deixase a proba deste proxecto (unha execución interrompida). */
async function deleteE2eOrganizations(request: APIRequestContext, prefix: string): Promise<void> {
  const headers = await managerHeaders(request, 'naron');
  const list = await request.get(`${BASE}/organizations?archived=false`, { headers });
  expect(list.ok(), 'lista de entidades').toBe(true);
  for (const org of (await list.json()) as { id: string; name: string }[]) {
    if (!org.name.startsWith(prefix)) continue;
    const one = await request.get(`${BASE}/organizations/${org.id}`, { headers });
    const etag = one.headers()['etag'] ?? '';
    await request.delete(`${BASE}/organizations/${org.id}`, { headers: { ...headers, 'If-Match': etag } });
  }
}

test.describe('tempadas das entidades', () => {
  test('xestión: «Entidades» co número, a solicitude en tarxeta e a revisión cos choques', async ({ page, lang }) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/entidades');
    const tab = page.locator('[data-manage-tab="nav.organizations"]');
    if (await tab.isVisible()) {
      await expect(tab.locator('[data-count]')).not.toHaveText('');
      // O lector non le «Entidades 1»: o número vai aria-hidden e o nome di que conta.
      await expect(tab).toHaveAccessibleName(/^.+, \d+ .+$/);
    }
    const card = page.locator(`[data-series-request="${SOLICITUDE}"]`);
    await expect(card.locator('.f-status-card__name')).toHaveText('Club Demo Patinaxe');
    await expect(card.locator('[data-chip="PENDING"]')).toHaveText(text(lang, 'booking.seasons.request.PENDING'));
    await expect(card.locator('[data-line="place"]')).toContainText('Pavillón de Piñeiros');
    // A táboa no escritorio e as filas no móbil: a que se vexa.
    await expect(
      page
        .locator(`[data-organization-row="${BALONCESTO}"] [data-status], [data-organization-item="${BALONCESTO}"]`)
        .filter({ visible: true }),
    ).toHaveCount(1);
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    await card.locator('.f-status-card__open').click();
    await expect(page).toHaveURL(new RegExp(`/naron/xestion/entidades/solicitudes/${SOLICITUDE}$`));
    await expect(page.locator('[data-request-from]')).toContainText('Club Demo Patinaxe');
    // A vista previa chega soa: «Aprobar e crear N sesións».
    await expect(page.locator('[data-request-approve]')).toContainText(/\d/);
    await expect(page.locator('[data-request-reject]')).toHaveText(text(lang, 'booking.seasons.review.reject'));
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // Rexeitar pide a mensaxe; pechar sen escribir non decide nada.
    await page.locator('[data-request-reject]').click();
    const reject = page.getByRole('dialog');
    await reject.locator('[data-reject-confirm]').click();
    await expect(reject).toContainText(text(lang, 'errors.booking.series-request-message-required'));
    await expectNoA11yViolations(page);
    await reject.locator('[data-reject-keep]').click();
    await expect(reject).toBeHidden();
  });

  test('xestión: dar de alta, editar e eliminar unha entidade (o correo é obrigatorio)', async ({
    page,
    request,
    lang,
  }, testInfo) => {
    const prefix = `E2E ${testInfo.project.name}`;
    const name = `${prefix} ${Date.now().toString(36)}`;
    await deleteE2eOrganizations(request, prefix);
    try {
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await page.goto('/naron/xestion/entidades');
      await page.locator('[data-organization-new]').click();
      const form = page.getByRole('dialog');
      await form.locator('[data-organization-name]').fill(name);
      await form.locator('[data-organization-save]').click();
      await expect(form.locator('[data-organization-email]')).toHaveAttribute('aria-invalid', 'true');
      await expect(form.locator('[data-organization-email]')).toBeFocused();
      await form.locator('[data-organization-email]').fill('e2e-entidade@example.org');
      await form.locator('[data-organization-phone]').fill('600 000 999');
      await form.locator('[data-organization-manager]').fill('e2e-responsable@example.org');
      await form.locator('[data-organization-manager]').press('Enter');
      await expect(form.locator('.f-chip__label')).toHaveText(['e2e-responsable@example.org']);
      await expectNoA11yViolations(page);
      await form.locator('[data-organization-save]').click();
      await expect(form).toBeHidden();

      const row = page
        .locator('[data-organization-row], [data-organization-item]')
        .filter({ hasText: name })
        .filter({ visible: true })
        .first();
      await expect(row).toContainText(text(lang, 'booking.seasons.organizations.status.NONE'));
      await row.click();
      const edit = page.getByRole('dialog');
      await expect(edit.locator('[data-organization-name]')).toHaveValue(name);
      await edit.locator('[data-organization-contact]').fill('Persoa de proba');
      await edit.locator('[data-organization-save]').click();
      await expect(edit).toBeHidden();

      await row.click();
      await page.getByRole('dialog').locator('[data-organization-delete] button').click();
      await page.locator('[data-confirm-accept]').click();
      await expect(
        page.locator('[data-organization-row], [data-organization-item]').filter({ hasText: name }),
      ).toHaveCount(0);
    } finally {
      await deleteE2eOrganizations(request, prefix);
    }
  });

  test('xestión: «Tempadas» en Reservas e o seu detalle (pago, sesións e «Rematar a tempada…»)', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/reservas');
    const section = page.locator('[data-section="series"]');
    await expect(section.getByRole('heading', { name: text(lang, 'booking.seasons.section') })).toBeVisible();
    const row = section.locator(`[data-series="${TEMPADA}"]`);
    await expect(row.locator('.f-mrow__name')).toHaveText('Club Demo Baloncesto');
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    await row.click();
    const detail = page.getByRole('dialog');
    await expect(detail.locator(`[data-series-detail="${TEMPADA}"]`)).toBeVisible();
    await expect(detail.locator('[data-series-payment]')).toContainText(text(lang, 'booking.seasons.detail.payment'));
    await detail.locator('[data-series-sessions] button').first().click();
    await expect(detail.locator('[data-session]').first()).toContainText(/\d{2}:\d{2}–\d{2}:\d{2}/);
    await expect(detail.locator('[data-series-end]')).toHaveText(text(lang, 'booking.seasons.detail.end'));
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
  });

  test('xestión: «Nova reserva › Tempada» cos campos da tempada', async ({ page, lang }) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/reservas/nova');
    await page.locator('[data-book-for-kind] [data-kind="season"]').click();
    await expect(page).toHaveURL(/tipo=tempada/);
    const season = page.locator('[data-book-for-season]');
    for (const field of ['organization', 'facility', 'use', 'days', 'hours', 'dates']) {
      await expect(season.locator(`[data-season-${field}]`)).toBeVisible();
    }
    await expect(season.locator('[data-season-create]')).toHaveText(text(lang, 'booking.seasons.create'));
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await page.locator('[data-book-for-kind] [data-kind="once"]').click();
    await expect(page.locator('[data-book-for-go]')).toBeVisible();
  });

  test('a persoa responsable: «Eu · Club», as sesións, «Solicitar tempada» e «Reservar como»', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto('/naron/reservas');
    const holders = page.locator('[data-mine-holders]');
    await expect(holders.locator('[data-mine-holder="me"]')).toHaveText(text(lang, 'booking.who.me'));
    await holders.locator(`[data-mine-holder="${BALONCESTO}"]`).click();
    const sessions = page.locator('[data-org-bookings] furar-booking-card');
    await expect(sessions.first()).toContainText(text(lang, 'booking.seasons.session'));
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // O detalle dunha sesión: sen «Cambiar», con «Cancelar» (sen cancelala: a demo queda igual).
    await sessions.first().locator('.f-status-card__open').click();
    const detail = page.getByRole('dialog');
    await expect(detail.locator('[data-booking-change]')).toHaveCount(0);
    await expect(detail.locator('[data-booking-cancel]')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(detail).toBeHidden();

    await page.locator('[data-season-ask]').click();
    const ask = page.getByRole('dialog');
    await expect(ask).toContainText('Club Demo Baloncesto');
    await expect(ask.locator('[data-season-organization]')).toHaveCount(0);
    await ask.locator('[data-ask-send]').click();
    await expect(ask.locator('[aria-invalid="true"]').first()).toBeFocused();
    await expectNoA11yViolations(page);
    await ask.locator('[data-ask-cancel]').click();
    await expect(ask).toBeHidden();

    // «Reservar como» na páxina de reservar dunha instalación (sen reservar).
    await page.goto(`/naron/instalacions/${PAVILLON}/reservar?uso=${TRANSVERSAIS}`);
    const as = page.locator('[data-reserve-as]');
    await expect(as).toBeVisible();
    await as.click();
    await expect(page.getByRole('option', { name: 'Club Demo Baloncesto' })).toBeVisible();
    await expectNoA11yViolations(page);
    await page.keyboard.press('Escape');
    await expectNoHorizontalScroll(page);
  });
});
