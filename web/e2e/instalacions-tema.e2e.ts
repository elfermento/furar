// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, loginAs, test, text } from './fixtures';
import { checkStep, hermetic } from './instalacions-axudas';

/**
 * Instalacións (D1-25), o que non depende da instalación creada en `instalacions.e2e.ts`: o tema
 * de Ferrol (o segundo tema), zoom do 200 % a 1280 px (1.4.4 e 1.4.10) e sen permiso.
 */
test.describe('instalacións: tema, zoom e permisos', () => {
  test('tema de Ferrol: listado e ficha accesibles', async ({ page, lang }) => {
    const outside = await hermetic(page);
    await page.goto('/ferrol');
    await loginAs(page, 'xestor-ferrol', lang);
    await page
      .getByRole('navigation', { name: text(lang, 'app.manageNav') })
      .getByRole('link', { name: text(lang, 'nav.facilities') })
      .click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.list.title') })).toBeVisible();
    const first = page.locator('[data-facility]').first();
    await expect(first).toBeVisible();
    await checkStep(page);
    await first.getByRole('link').first().click();
    await expect(page).toHaveURL(/\/ferrol\/xestion\/instalacions\/[0-9a-f-]{36}$/);
    await expect(page.locator('#facility-uses-title')).toBeVisible();
    await expect(page.locator('.f-page-header__intro')).toBeVisible();
    // Horario cargado (semana ou estado baleiro) antes de medir.
    await expect(
      page.locator('furar-facility-schedule').locator('furar-facility-week-rows li, [data-schedule-empty]').first(),
    ).toBeVisible();
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('sen permiso: o veciño non entra nas instalacións', async ({ page, lang }) => {
    await hermetic(page);
    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto('/naron/xestion/instalacions');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'errorPage.403.title') })).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('zoom do 200 % a 1280 px: listado e ficha sen scroll horizontal', async ({ page, lang }) => {
    const outside = await hermetic(page);
    // 1280 px ao 200 % = 640 px CSS de ancho (WCAG 1.4.4 e 1.4.10).
    await page.setViewportSize({ width: 640, height: 400 });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/instalacions');
    await expect(page.locator('[data-facility]').first()).toBeVisible();
    await expectNoHorizontalScroll(page);
    await page.locator('[data-facility]').first().getByRole('link').first().click();
    await expect(page.locator('#facility-uses-title')).toBeVisible();
    await expect(page.locator('.f-page-header__intro')).toBeVisible();
    // Horario cargado (semana ou estado baleiro) antes de medir.
    await expect(
      page.locator('furar-facility-schedule').locator('furar-facility-week-rows li, [data-schedule-empty]').first(),
    ).toBeVisible();
    await expectNoHorizontalScroll(page);
    await expectNoA11yViolations(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('listado a 390 px: tarxetas con imaxe nunha columna, chip debaixo do enderezo e «+» na liña do título', async ({
    page,
    lang,
  }) => {
    await hermetic(page);
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/instalacions');
    const cards = page.locator('[data-facility]');
    const card = cards.first();
    await expect(card).toBeVisible();
    const box = async (selector: string) => {
      const b = await card.locator(selector).first().boundingBox();
      expect(b, selector).not.toBeNull();
      return b!;
    };
    const image = await box('.f-media-card__image');
    const name = await box('.f-media-card__title');
    const place = await box('.f-media-card__place');
    const chip = await box('furar-chip-menu');
    // Imaxe arriba, e o chip de estado debaixo do enderezo.
    expect(image.y).toBeLessThan(name.y);
    expect(chip.y).toBeGreaterThanOrEqual(place.y + place.height);
    // Unha columna: a segunda tarxeta vai debaixo da primeira.
    const second = await cards.nth(1).boundingBox();
    expect(second!.y).toBeGreaterThan(image.y + image.height);
    // O botón «Nova instalación» (só a icona) na mesma liña ca o título.
    const title = await page.getByRole('heading', { level: 1 }).boundingBox();
    const add = await page.locator('[data-new]').boundingBox();
    expect(add!.y).toBeLessThan(title!.y + title!.height);
    expect(add!.x).toBeGreaterThan(title!.x);
    await expectNoHorizontalScroll(page);
  });
});
