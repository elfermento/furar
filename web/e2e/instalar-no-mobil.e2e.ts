// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, test, text } from './fixtures';

/**
 * Aviso para instalar Furar no móbil, na portada do concello e na común. Cun Safari de iPhone (o UA
 * no contexto): no móbil (gl-mobile, con dedo) sae na portada e non noutras páxinas; «Instalar» abre
 * a folla cos dous pasos; pechalo lémbrase ao recargar. Na portada común sae sempre e pechalo alí
 * vale tamén para a do concello (a mesma clave). Con Chrome de iPhone, a mesma folla cos pasos de
 * Chrome. No escritorio (gl, es) nunca sae, aínda co mesmo UA: sen dedo non é un móbil. Con axe.
 * A portada común precisa varios concellos (db/demo).
 */
const IPHONE_SAFARI =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Mobile/15E148 Safari/604.1';
const IPHONE_CHROME =
  'Mozilla/5.0 (iPhone; CPU iPhone OS 18_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) CriOS/130.0.6723.90 Mobile/15E148 Safari/604.1';

test.describe('aviso para instalar no móbil', () => {
  test.use({ userAgent: IPHONE_SAFARI });

  test('iPhone: na portada e só nela; folla cos pasos; pechado non volve. Escritorio: nunca', async ({
    page,
    lang,
  }, testInfo) => {
    const hint = page.locator('[data-install-hint]');
    await page.goto('/naron');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    if (testInfo.project.name !== 'gl-mobile') {
      await expect(hint).toHaveCount(0);
      await expectNoA11yViolations(page);
      return;
    }

    await expect(hint).toBeVisible();
    await expect(hint).toHaveAttribute('data-install-hint', 'ios');
    await expect(hint).toContainText(text(lang, 'ui.installHint.text'));
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // «Instalar»: a folla cos dous pasos de Safari; pecha con Esc.
    await hint.getByRole('button', { name: text(lang, 'ui.installHint.install') }).click();
    const sheet = page.getByRole('dialog', { name: text(lang, 'ui.installHint.ios.title') });
    await expect(sheet).toBeVisible();
    await expect(sheet.locator('[data-install-steps] > li')).toHaveCount(2);
    await expect(sheet).toContainText(text(lang, 'ui.installHint.ios.share'));
    await expect(sheet).toContainText(text(lang, 'ui.installHint.ios.addToHome'));
    await expectNoA11yViolations(page);
    await page.keyboard.press('Escape');
    await expect(sheet).toHaveCount(0);
    // Quen viu os pasos non volve ver o aviso: o navegador de iPhone non sabe se a instalou.
    await expect(hint, 'vistos os pasos, o aviso marcha').toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem('furar.installHint.dismissed'))).toBe('1');
    await page.reload();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(hint).toHaveCount(0);

    // Noutra páxina do concello, nunca (tampouco sen pechalo).
    await page.evaluate(() => localStorage.removeItem('furar.installHint.dismissed'));
    await page.goto('/naron/instalacions');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(hint).toHaveCount(0);

    // Pechar (✕) lémbrase neste navegador: ao recargar a portada xa non sae.
    await page.goto('/naron');
    await hint.getByRole('button', { name: text(lang, 'ui.installHint.close') }).click();
    await expect(hint).toHaveCount(0);
    expect(await page.evaluate(() => localStorage.getItem('furar.installHint.dismissed'))).toBe('1');
    await page.reload();
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(hint).toHaveCount(0);
  });

  test('portada común: sae sempre no móbil; pechalo alí vale tamén para a portada do concello', async ({
    page,
    lang,
  }, testInfo) => {
    const hint = page.locator('[data-install-hint]');
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();

    if (testInfo.project.name !== 'gl-mobile') {
      await expect(hint).toHaveCount(0);
      return;
    }

    await expect(hint).toBeVisible();
    await expect(hint).toHaveAttribute('data-install-hint', 'ios');
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    await hint.getByRole('button', { name: text(lang, 'ui.installHint.close') }).click();
    await expect(hint).toHaveCount(0);
    await page.goto('/naron');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await expect(hint, 'a mesma clave de «pechado» en todo o sitio').toHaveCount(0);
  });
});

test.describe('aviso para instalar no móbil: Chrome de iPhone', () => {
  test.use({ userAgent: IPHONE_CHROME });

  test('iPhone: a mesma folla, cos pasos de Chrome (Compartir na barra de enderezos). Escritorio: nunca', async ({
    page,
    lang,
  }, testInfo) => {
    const hint = page.locator('[data-install-hint]');
    await page.goto('/naron');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    if (testInfo.project.name !== 'gl-mobile') {
      await expect(hint).toHaveCount(0);
      return;
    }
    await expect(hint).toHaveAttribute('data-install-hint', 'ios-chrome');
    await hint.getByRole('button', { name: text(lang, 'ui.installHint.install') }).click();
    const sheet = page.getByRole('dialog', { name: text(lang, 'ui.installHint.ios.title') });
    await expect(sheet.locator('[data-install-steps="chrome"] > li')).toHaveCount(2);
    await expect(sheet).toContainText(text(lang, 'ui.installHint.ios.inChrome'));
    await expect(sheet).toContainText(text(lang, 'ui.installHint.ios.addToHome'));
    await expectNoA11yViolations(page);
  });
});
