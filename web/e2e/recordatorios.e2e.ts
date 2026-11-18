// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, loginAs, test, text } from './fixtures';
import { SLUG, tokenFor } from './reservas-axudas';

/**
 * Recordatorios por correo (ADR-0035) e preferencias de accesibilidade na conta: o interruptor
 * «Recordatorios por correo» ao final de «As miñas reservas» (nome á esquerda, interruptor á
 * dereita) garda na conta; a ligazón «Non quero recordatorios» sen proba válida di que non serve; e
 * cun axuste de accesibilidade escollido con sesión, a API ten os mesmos valores. O envío do
 * recordatorio é dunha tarefa programada: próbano as probas da API (ReminderIT).
 */
test.describe.configure({ mode: 'serial' });

const API = `/api/v1/municipalities/${SLUG}`;

test.afterEach(async ({ request }) => {
  // Deixa a demo coma estaba: recordatorios acesos.
  const token = await tokenFor(request, 'vecino-naron');
  await request.put(`${API}/me/reminders`, { headers: token.headers, data: { enabled: true } });
});

test('«Recordatorios por correo» en «As miñas reservas»: apagar e acender gárdase na conta', async ({
  page,
  request,
  lang,
}) => {
  await page.goto(`/${SLUG}`);
  await loginAs(page, 'vecino-naron', lang);
  await page.goto(`/${SLUG}/reservas`);
  const control = page.getByRole('switch', { name: text(lang, 'booking.mine.reminders.label') });
  await expect(control).toBeVisible();
  await expect(control).toHaveAttribute('aria-checked', 'true');
  // O nome á esquerda e o interruptor á dereita, na mesma fila. O rótulo vai en dous anacos (a
  // última palabra, xunto ao «?» se o hai): o último, o máis á dereita.
  const label = page.locator('[data-mine-reminders] label').last();
  const [labelBox, controlBox] = await Promise.all([label.boundingBox(), control.boundingBox()]);
  expect(labelBox && controlBox && labelBox.x < controlBox.x).toBe(true);

  const saved = page.waitForResponse((r) => r.url().endsWith('/me/reminders') && r.request().method() === 'PUT');
  await control.click();
  expect((await saved).status()).toBe(204);
  await expect(control).toHaveAttribute('aria-checked', 'false');
  const token = await tokenFor(request, 'vecino-naron');
  const off = await request.get(`${API}/me/reminders`, { headers: token.headers });
  expect(await off.json()).toEqual({ enabled: false });

  await page.reload();
  await expect(control).toHaveAttribute('aria-checked', 'false');
  await control.click();
  await expect(control).toHaveAttribute('aria-checked', 'true');
  await expectNoA11yViolations(page);
  await expectNoHorizontalScroll(page);
});

test('a ligazón «Non quero recordatorios» manipulada di que non serve, sen sesión', async ({ page, lang }) => {
  await page.goto(`/${SLUG}/recordatorios/baixa?t=v1.manipulada.1.AAAA`);
  await expect(page.getByRole('heading', { level: 1 })).toHaveText(text(lang, 'booking.reminders.optOut.title'));
  await expect(page.locator('[data-opt-out-error]')).toContainText(text(lang, 'errors.booking.reminder-link-invalid'));
  await expectNoA11yViolations(page);
  await expectNoHorizontalScroll(page);
});

test('con sesión, un axuste de accesibilidade queda gardado na conta', async ({ page, request, lang }) => {
  await page.goto(`/${SLUG}`);
  await loginAs(page, 'vecino-naron', lang);
  await page.goto(`/${SLUG}`);
  await page
    .locator('furar-site-bar')
    .getByRole('button', { name: text(lang, 'ui.displayPrefs.button') })
    .click();
  const panel = page.getByRole('dialog', { name: text(lang, 'ui.displayPrefs.title') });
  await expect(panel).toContainText(text(lang, 'ui.displayPrefs.introAccount'));
  const saved = page.waitForResponse(
    (r) => r.url().endsWith('/api/v1/me/display-preferences') && r.request().method() === 'PUT',
  );
  await panel.getByRole('switch', { name: text(lang, 'ui.displayPrefs.contrast') }).click();
  expect((await saved).status()).toBe(204);
  const token = await tokenFor(request, 'vecino-naron');
  const stored = await request.get('/api/v1/me/display-preferences', { headers: token.headers });
  expect(stored.status()).toBe(200);
  expect((await stored.json()) as { highContrast: boolean | null }).toMatchObject({ highContrast: true });
  // Deixa a conta sen axustes de contraste para as outras probas.
  await request.put('/api/v1/me/display-preferences', {
    headers: token.headers,
    data: { textSize: 'normal', highContrast: null, reducedMotion: null },
  });
});
