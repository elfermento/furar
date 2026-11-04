// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DEMO_PASSWORD, expect, expectNoA11yViolations, test, text, userMenu } from './fixtures';

/**
 * Autenticación pública (AP2-03, ADR-0019): a veciña entra en Furar con «Cl@ve simulada», un IdP
 * SAML 2.0 de demo (realm clave-simulada). É o mesmo conector que usará a pasarela Cl@ve ou
 * Chave365 en produción; a adhesión fírmaa o concello. O NIF que envía o IdP non chega a Furar
 * (compróbao D52, scripts/proba-identidade-keycloak.sh).
 */
const NIF = '00000000T';

test.describe('Cl@ve simulada (SAML 2.0)', () => {
  test('entrar con Cl@ve desde a web e volver con sesión', async ({ page, lang }) => {
    await page.goto('/naron');
    await page.getByRole('button', { name: text(lang, 'session.login') }).click();
    await page.waitForURL(/\/auth\/realms\/furar\//);
    const clave = page.locator('#social-clave-simulada');
    await expect(clave).toBeVisible();
    await expectNoA11yViolations(page);
    await clave.click();
    await page.waitForURL(/\/auth\/realms\/clave-simulada\//);
    await page.locator('#username').fill(NIF);
    await page.locator('#password').fill(DEMO_PASSWORD);
    await page.locator('#kc-login').click();
    await page.waitForURL((url) => url.pathname.startsWith('/naron') && !url.search.includes('code='));
    await expect(userMenu(page)).toBeVisible();
    await expect(page.getByText(NIF)).toHaveCount(0);
  });
});
