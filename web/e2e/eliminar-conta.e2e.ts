// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Page } from '@playwright/test';
import { DEMO_PASSWORD, demoEmail, expect, expectNoA11yViolations, loginAs, test, text, userMenu } from './fixtures';

/** Os textos da páxina de Keycloak (tema furar, messages_gl e messages_es). */
const KEYCLOAK = {
  gl: {
    blockedTitle: 'Aínda non podes eliminar a túa conta',
    back: 'Volver á web',
  },
  es: {
    blockedTitle: 'Todavía no puedes eliminar tu cuenta',
    back: 'Volver a la web',
  },
} as const;

/**
 * Eliminar a propia conta (ADR-0043): a última fila de «A miña conta», en vermello. A web
 * pregunta antes á API; se queda algo pendente, un diálogo lístao coas ligazóns e non se vai a
 * Keycloak. Se a web deixase pasar (aquí, a resposta da API falseada), Keycloak volve preguntar á
 * API antes de mostrar a confirmación: o veciño de demo ten reservas futuras en Narón, así que
 * Keycloak non deixa e só ofrece volver á web, coa sesión. Ningunha proba elimina unha conta de
 * demo.
 */
test.describe('eliminar a miña conta', () => {
  test('con reservas pendentes, o diálogo lístaas coa ligazón a «As miñas reservas»; pasa axe', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await openAccount(page, lang);
    await expectNoA11yViolations(page);
    const item = page.getByRole('button', { name: text(lang, 'session.deleteAccount') });
    await expect(page.locator('ul.f-action-list > li').last()).toContainText(text(lang, 'session.deleteAccount'));
    await item.click();

    const dialog = page.getByRole('dialog', { name: text(lang, 'accountDeletion.title') });
    await expect(dialog).toBeVisible();
    const bookings = dialog.locator('a[href="/naron/reservas"]').first();
    await expect(bookings).toBeVisible();
    await expectNoA11yViolations(page);
    await bookings.click();
    await expect(page).toHaveURL(/\/naron\/reservas$/);
    await expect(dialog).toBeHidden();
  });

  test('Keycloak volve preguntar á API: sen o seu si, non se elimina nada e volve á web coa sesión', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    // Keycloak mide en segundos enteiros canto hai que se entrou: no mesmo segundo do acceso non
    // pide entrar outra vez (unha persoa nunca chega tan rápido). Agarda ao seguinte.
    const nextSecond = (Math.floor(Date.now() / 1000) + 1) * 1000 + 250;
    await page.waitForFunction((at) => Date.now() >= at, nextSecond);
    // A web cre que se pode: Keycloak ten que comprobalo pola súa conta.
    await page.route('**/api/v1/me/account-deletion', (route) =>
      route.fulfill({ json: { allowed: true, blockers: [] } }),
    );
    await openAccount(page, lang);
    await page.getByRole('button', { name: text(lang, 'session.deleteAccount') }).click();

    // Antes de nada, entrar outra vez: Keycloak mostra o correo como texto e pide só o contrasinal.
    await page.waitForURL(/\/auth\/realms\/furar\//);
    const password = page.locator('#password');
    await expect(password).toBeVisible();
    // Keycloak usa o último idioma que gardou a conta antes ca o da web (ui_locales); outra proba
    // desta mesma conta pode telo cambiado: volve ao desta.
    if ((await page.locator('html').getAttribute('lang')) !== lang) {
      const option = await page
        .locator('#login-select-toggle option')
        .evaluateAll(
          (options, l) => options.map((o) => (o as HTMLOptionElement).value).find((v) => v.includes(`kc_locale=${l}`)),
          lang,
        );
      await page.locator('#login-select-toggle').selectOption(option!);
      await expect(page.locator('html')).toHaveAttribute('lang', lang);
    }
    await password.fill(DEMO_PASSWORD);
    if (await page.locator('#username').count()) {
      await page.locator('#username').fill(demoEmail('vecino-naron'));
    }
    await page.locator('#kc-login').click();

    await expect(page.getByRole('heading', { name: KEYCLOAK[lang].blockedTitle })).toBeVisible();
    await expect(page.locator('#furar-delete-account-confirm')).toHaveCount(0);
    await expectNoA11yViolations(page);
    await page.getByRole('button', { name: KEYCLOAK[lang].back }).click();

    await expect(page).toHaveURL(/\/naron\/conta$/);
    await expect(userMenu(page)).toBeVisible();
  });
});

/** «A miña conta» desde o menú da persoa. */
async function openAccount(page: Page, lang: 'gl' | 'es'): Promise<void> {
  await userMenu(page).click();
  await page.getByRole('menuitem', { name: text(lang, 'session.account'), exact: true }).click();
  await expect(page).toHaveURL(/\/naron\/conta$/);
  await expect(page.getByRole('heading', { level: 1, name: text(lang, 'account.title') })).toBeVisible();
}
