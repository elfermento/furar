// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, loginAs, test, text } from './fixtures';

/**
 * /:concello/@route@ (xerado por scripts/novo-modulo.sh): lista pública, alta e edición con
 * teclado, erros ligados ao campo e axe en cada pantalla. Corre nos proxectos gl, es e
 * gl-mobile (320 px) de playwright.config.ts contra o perfil full.
 * Entra como xestor-naron porque o xerado dálle @NAME@_MANAGE só ao xestor do concello; se o
 * dás a outro rol, proba tamén con el.
 */
test.describe('@names@', () => {
  test('lista pública accesible e sen scroll horizontal', async ({ page, lang }) => {
    await page.goto('/naron/@route@');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, '@name@.title') })).toBeVisible();
    await expect(page.getByRole('link', { name: text(lang, '@name@.new') })).toHaveCount(0);
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
  });

  test('alta e edición (If-Match) accesibles e só con teclado', async ({ page, lang }, testInfo) => {
    const name = `E2E ${testInfo.project.name} ${Date.now().toString(36)}`;
    await page.goto('/naron/@route@');
    await loginAs(page, 'xestor-naron', lang);

    // Alta: enviar baleiro marca o erro, ligado co aria-describedby, e leva o foco ao campo.
    await page.getByRole('link', { name: text(lang, '@name@.new') }).click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, '@name@.newTitle') })).toBeVisible();
    const field = page.locator('#@name-kebab@-name');
    await field.focus();
    await page.keyboard.press('Enter');
    await expect(field).toBeFocused();
    await expect(field).toHaveAttribute('aria-invalid', 'true');
    await expect(field).toHaveAttribute('aria-describedby', '@name-kebab@-name-error');
    await expect(page.locator('#@name-kebab@-name-error')).toHaveText(text(lang, 'errors.validation.required'));
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await page.keyboard.type(name);
    const created = page.waitForResponse((r) => r.request().method() === 'POST' && r.url().endsWith('/@names@'));
    await page.keyboard.press('Enter');
    const { id } = (await (await created).json()) as { id: string };
    await expect(page.getByRole('status').filter({ hasText: text(lang, '@name@.saved', { name }) })).toBeVisible();
    await expect(page).toHaveURL(/\/naron\/@route@$/);
    await expectNoA11yViolations(page);

    // Edición (por URL: a lista é paxinada e pode non mostrar o novo na primeira páxina).
    await page.goto(`/naron/@route@/${id}`);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, '@name@.editTitle') })).toBeVisible();
    await expect(field).toHaveValue(name);
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    const edited = `${name} (2)`;
    await field.fill(edited);
    const updated = page.waitForResponse((r) => r.request().method() === 'PUT' && r.url().endsWith(`/@names@/${id}`));
    await page.keyboard.press('Enter');
    const put = await updated;
    expect(put.status(), 'PUT con If-Match').toBe(200);
    expect(put.request().headers()['if-match']).toMatch(/^"\d+"$/);
    await expect(page.getByRole('status').filter({ hasText: text(lang, '@name@.saved', { name: edited }) })).toBeVisible();
  });
});
