// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext, Page } from '@playwright/test';
import {
  DEMO_PASSWORD,
  demoEmail,
  demoTokenForm,
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  managerHeaders,
  test,
  text,
} from './fixtures';
import type { DemoUser } from './fixtures';

/**
 * «Buscar xente para xogar» (ADR-0040) coa demo de Narón
 * (`R__demo_naron_open_matches.sql`): fútbol sala de vecino-naron sen aprobación (4 de 10) e pádel
 * dunha persoa ficticia con aprobación (3 de 4, cunha solicitude pendente). Vese na portada, a
 * páxina pide entrar e volve, pídese unirse e retírase; quen organiza acepta unha solicitude; ábrese
 * e péchase unha partida desde «As miñas reservas»; a xestión oculta e volve mostrar. Cada proba
 * deixa os datos coma estaban (a partida aberta desde a reserva queda pechada: ao repetir, reábrese).
 * Con axe en cada pantalla.
 */

const BASE = '/api/v1/municipalities/naron';
const FUTSAL_BOOKING = '019a0b6e-0000-7000-8000-000000000418';
const FUTSAL = '019a0b6e-0000-7000-8000-000000000a01';
const PADEL = '019a0b6e-0000-7000-8000-000000000a02';

/** Cabeceira cun token de demo (cliente furar-e2e) para a persoa `user`. */
async function headersOf(request: APIRequestContext, user: DemoUser): Promise<Record<string, string>> {
  const token = await request.post('/auth/realms/furar/protocol/openid-connect/token', { form: demoTokenForm(user) });
  expect(token.ok(), `token de ${user}`).toBe(true);
  const { access_token: accessToken } = (await token.json()) as { access_token: string };
  return { Authorization: `Bearer ${accessToken}` };
}

interface MyMatch {
  knownCount: number;
  minimum: number;
  maximum: number;
  level?: string;
  title?: string;
  description?: string;
  approval: boolean;
  version: number;
  status: string;
}

/** Cambia «Con aprobación» da partida de fútbol sala (organiza vecino-naron) coa versión que hai. */
async function setFutsalApproval(request: APIRequestContext, approval: boolean): Promise<void> {
  const headers = await headersOf(request, 'vecino-naron');
  const current = (await (await request.get(`${BASE}/me/open-matches/${FUTSAL}`, { headers })).json()) as MyMatch;
  if (current.approval === approval && current.status === 'OPEN') return;
  const { knownCount, minimum, maximum, level, title, description } = current;
  const saved = await request.put(`${BASE}/me/bookings/${FUTSAL_BOOKING}/open-match`, {
    headers: { ...headers, 'If-Match': `"${current.version}"` },
    data: { knownCount, minimum, maximum, level, title, description, approval },
  });
  expect(saved.ok(), 'gardar a partida de fútbol sala').toBe(true);
}

/** Entra polo formulario de Keycloak despois de que a páxina mandase a entrar, e volve. */
async function fillLogin(page: Page, user: DemoUser): Promise<void> {
  await page.waitForURL(/\/auth\/realms\/furar\//);
  await page.locator('#username').fill(demoEmail(user));
  await page.locator('#password').fill(DEMO_PASSWORD);
  await page.locator('#kc-login').click();
  await page.waitForURL((url) => !url.pathname.startsWith('/auth/') && !url.search.includes('code='));
}

test.describe('buscar xente para xogar', () => {
  test('portada → páxina (noindex) → «Pedir unirme» entra e volve → pendente en «As miñas reservas» → retirar', async ({
    page,
    request,
    lang,
  }) => {
    const vecino = await headersOf(request, 'vecino-naron');
    try {
      await page.goto('/naron');
      const section = page.locator('[data-open-matches]');
      await expect(section.getByRole('heading', { name: text(lang, 'openMatch.section') })).toBeVisible();
      const card = section.locator(`[data-open-match="${PADEL}"]`);
      // No carrusel, sen quen organiza; no pé, as prazas libres (3 de 4: a última).
      await expect(card.locator('[data-seats]')).toHaveText(text(lang, 'ui.seats.lastOne'));
      await expect(card).not.toContainText(text(lang, 'openMatch.organizer', { name: '' }).trim());
      await expect(section.locator(`[data-open-match="${FUTSAL}"]`)).toBeVisible();
      await expectNoA11yViolations(page);
      await expectNoHorizontalScroll(page);

      await card.locator('.f-media-card__link').click();
      await expect(page).toHaveURL(new RegExp(`/naron/partidas/${PADEL}$`));
      await expect(page.locator('meta[name="robots"]')).toHaveAttribute('content', 'noindex');
      await expect(page.locator('[data-match-counts]')).toContainText('3');
      await expect(page.locator('[data-match-organizer]')).not.toBeEmpty();
      // Sen sesión, ningún nome das persoas que van.
      await expect(page.locator('[data-match-people]')).toHaveCount(0);
      const join = page.locator('[data-match-join]');
      await expect(join).toHaveText(text(lang, 'openMatch.page.ask'));
      await expectNoA11yViolations(page);
      await expectNoHorizontalScroll(page);

      // «Pedir unirme» sen sesión leva a entrar e, ao volver á partida, a solicitude vai soa.
      await join.click();
      await fillLogin(page, 'vecino-naron');
      await expect(page).toHaveURL(new RegExp(`/naron/partidas/${PADEL}$`));
      await expect(page.locator('furar-toast')).toContainText(text(lang, 'openMatch.page.askedToast'));
      await expect(page.locator('[data-match-action="pending"]')).toContainText(
        text(lang, 'openMatch.page.pendingTitle'),
      );
      await expect(page.locator('[data-match-leave]')).toHaveText(new RegExp(text(lang, 'openMatch.page.withdraw')));
      await expectNoA11yViolations(page);

      await page.goto('/naron/reservas');
      const mine = page.locator(`[data-my-open-match="${PADEL}"]`);
      await expect(mine.locator('[data-chip="PENDING"]')).toHaveText(text(lang, 'openMatch.mine.PENDING'));
      await expectNoA11yViolations(page);
      await mine.locator('.f-status-card__open').click();
      const detail = page.getByRole('dialog');
      await expect(detail.locator('furar-action-item')).toHaveCount(2);
      await expectNoA11yViolations(page);
      await detail.locator('[data-my-open-match-leave]').click();
      await expect(detail).toBeHidden();
      await expect(mine).toHaveCount(0);
      await expect(page.locator('furar-toast')).toContainText(text(lang, 'openMatch.page.withdrawnToast'));
    } finally {
      // Se quedou a medias, a solicitude fóra.
      await request.delete(`${BASE}/me/open-matches/${PADEL}/players/me`, { headers: vecino });
    }
  });

  test('quen organiza acepta unha solicitude: «Rexeitar» e «Aceptar» cheos; quen entrou sae', async ({
    page,
    request,
    lang,
  }) => {
    // Unha segunda persoa sen rol (persoal desactivado: xa non é conta de traballo) pide unirse.
    const other = await headersOf(request, 'ex-recepcion-naron');
    try {
      await setFutsalApproval(request, true);
      const asked = await request.post(`${BASE}/me/open-matches/${FUTSAL}/players`, { headers: other });
      expect([200, 201], 'pedir unirse').toContain(asked.status());
      const { me } = (await asked.json()) as { me: { role: string } };
      expect(me.role).toBe('PENDING');

      await page.goto('/naron');
      await loginAs(page, 'vecino-naron', lang);
      await page.goto(`/naron/partidas/${FUTSAL}`);
      await expect(page.locator('[data-match-organizer]')).toHaveText(text(lang, 'openMatch.page.you'));
      // «Deixar de buscar» no lateral (sen premelo: a demo segue igual).
      await expect(page.locator('[data-match-stop]')).toHaveText(text(lang, 'openMatch.detail.stop'));
      const requests = page.locator('[data-match-requests] [data-request]');
      await expect(requests).toHaveCount(1);
      await expect(requests.locator('[data-request-reject]')).toHaveAttribute('data-variant', 'danger-fill');
      await expect(requests.locator('[data-request-accept]')).toHaveAttribute('data-variant', 'primary');
      const people = page.locator('[data-match-people] li');
      const before = await people.count();
      await expectNoA11yViolations(page);
      await expectNoHorizontalScroll(page);

      await requests.locator('[data-request-accept]').click();
      await expect(page.locator('[data-match-requests]')).toHaveCount(0);
      await expect(people).toHaveCount(before + 1);

      // Sae (pola API: a súa conta non ten máis papel na proba) e a lista volve ao de antes.
      const left = await request.delete(`${BASE}/me/open-matches/${FUTSAL}/players/me`, { headers: other });
      expect(left.status(), 'saír').toBe(204);
      await page.reload();
      await expect(people).toHaveCount(before);
    } finally {
      await request.delete(`${BASE}/me/open-matches/${FUTSAL}/players/me`, { headers: other });
      await setFutsalApproval(request, false);
    }
  });

  test('desde «As miñas reservas»: buscar xente para xogar e deixar de buscar', async ({ page, request, lang }) => {
    const vecino = await headersOf(request, 'vecino-naron');
    const list = await request.get(`${BASE}/me/bookings?scope=UPCOMING&page=0&size=50`, { headers: vecino });
    const { items } = (await list.json()) as {
      items: {
        id: string;
        start: string;
        canCancel: boolean;
        rentalMode: string;
        openMatch?: { status: string };
      }[];
    };
    // Unha reserva propia de pista enteira ou por partes, que aínda se pode cancelar e lonxe do prazo.
    const target = items.find(
      (b) =>
        b.canCancel &&
        b.rentalMode !== 'SEATS' &&
        (!b.openMatch || b.openMatch.status === 'CLOSED') &&
        Date.parse(b.start) - 24 * 3_600_000 > Date.now(),
    );
    expect(target, 'na demo, vecino-naron ten unha reserva futura onde buscar xente').toBeTruthy();
    const id = target!.id;

    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto('/naron/reservas');
    const card = page.locator(`[data-booking="${id}"]`);
    await card.locator('.f-status-card__open').click();
    const detail = page.getByRole('dialog');
    await detail.locator('[data-detail-action="match-open"]').click();

    const form = page.getByRole('dialog').filter({ has: page.locator('[data-open-match-form]') });
    // O formulario ábrese despois de pechar o detalle: espérase antes de contar nada nel e de pasar axe.
    await expect(form).toBeVisible();
    await expect(form.locator('[data-match-notice]')).toHaveCount(0);
    await expect(form.locator('input[type="checkbox"]')).toHaveCount(0);
    await expectNoA11yViolations(page);
    await form.locator('#match-known').fill('1');
    await form.locator('#match-minimum').fill('4');
    await form.locator('#match-maximum').fill('6');
    await form.locator('[data-match-save]').click();
    await expect(form).toBeHidden();
    const chip = card.locator('[data-chip="searching"]');
    await expect(chip).toHaveText(text(lang, 'openMatch.chip'));
    await expect(chip).toHaveAttribute('data-tone', 'info');
    await expect(card.locator('[data-chip="searching"] + .f-status-card__note')).toHaveText(
      text(lang, 'openMatch.ofTotal', { total: 1, maximum: 6 }),
    );
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // Deixar de buscar: pregunta e a reserva segue, sen o chip.
    await card.locator('.f-status-card__open').click();
    await page.getByRole('dialog').locator('[data-detail-action="match-stop"]').click();
    const confirm = page.getByRole('alertdialog');
    await expect(confirm).toContainText(text(lang, 'openMatch.stop.title'));
    await expectNoA11yViolations(page);
    await confirm.locator('[data-confirm-accept]').click();
    await expect(confirm).toBeHidden();
    await expect(chip).toHaveCount(0);
    await expect(card).toBeVisible();
  });

  test('a xestión oculta a partida no detalle da reserva e volve mostrala', async ({ page, request, lang }) => {
    const headers = await managerHeaders(request, 'naron');
    const listed = async () => {
      const list = await request.get(`${BASE}/public/open-matches?page=0&size=50`);
      return ((await list.json()) as { items: { id: string }[] }).items.map((m) => m.id);
    };
    try {
      expect(await listed()).toContain(FUTSAL);
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await page.goto(`/naron/xestion/reservas?reserva=${FUTSAL_BOOKING}`);
      const line = page.locator('[data-detail-match]');
      await expect(line).toContainText(text(lang, 'openMatch.manage.line'));
      await expect(page.locator('[data-value="match"]')).toContainText(
        text(lang, 'openMatch.manage.minimum', { count: 6 }),
      );
      await expectNoA11yViolations(page);

      await line.locator('furar-chip-menu button').click();
      await page.getByRole('menuitemradio', { name: text(lang, 'openMatch.manage.hidden') }).click();
      await expect(page.locator('furar-toast')).toContainText(text(lang, 'openMatch.manage.hiddenToast'));
      await expect(line.locator('furar-chip-menu button')).toContainText(text(lang, 'openMatch.manage.hidden'));
      expect(await listed()).not.toContain(FUTSAL);

      await line.locator('furar-chip-menu button').click();
      await page.getByRole('menuitemradio', { name: text(lang, 'openMatch.manage.shown') }).click();
      await expect(line.locator('furar-chip-menu button')).toContainText(text(lang, 'openMatch.manage.shown'));
      expect(await listed()).toContain(FUTSAL);
    } finally {
      await request.put(`${BASE}/bookings/${FUTSAL_BOOKING}/open-match/hidden`, { headers, data: { hidden: false } });
    }
  });
});
