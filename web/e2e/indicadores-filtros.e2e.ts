// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, loginAs, test, text } from './fixtures';
import { card, openIndicators, panelReady } from './indicadores-axudas';
import { checkStep, hermetic, tabTo } from './instalacions-axudas';
import { tokenFor } from './reservas-axudas';

/**
 * Indicadores do concello (plan do dominio 3, D3-10), filtros e contorno: período, instalación e
 * uso só co teclado (o anuncio `role="status"`, o URL e «atrás»); «Datas…» co calendario de rango
 * (frechas, máis de 366 días, Esc); 320 px e zoom do 200 % (o mapa de calor desprázase dentro da
 * súa rexión) e «Máis» da barra inferior; Ferrol sen reservas; sen permiso (veciño e recepción).
 * Axe en cada paso, sen peticións fóra de localhost nin violacións de CSP.
 */
/** `iso` máis `days` días (ISO, sen zona). */
function shift(iso: string, days: number): string {
  const date = new Date(`${iso}T12:00:00Z`);
  date.setUTCDate(date.getUTCDate() + days);
  return date.toISOString().slice(0, 10);
}

test.describe('indicadores do concello: filtros, móbil e permisos', () => {
  test('filtros só co teclado: período, instalación e uso; anuncio, URL e «atrás»', async ({ page, lang }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    await openIndicators(page, lang);
    const status = page.locator('[data-indicators-status]');
    await expect(status).toHaveAttribute('role', 'status');
    await expect(status).toHaveText('');
    const ready = (fragment: string) =>
      new RegExp(`^${text(lang, 'indicators.status.ready').split(':')[0]}:.*${fragment}`);

    // Período: Trimestre
    const quarter = page.locator('[data-period="trimestre"]');
    await tabTo(page, quarter);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/[?&]periodo=trimestre/);
    await expect(quarter).toHaveAttribute('aria-pressed', 'true');
    await expect(quarter).toBeFocused();
    await expect(status).toHaveText(ready(''));
    const afterPeriod = await status.innerText();
    await panelReady(page, false);
    await checkStep(page);

    // Instalación: o segundo do despregable (o primeiro é «Todas»)
    const facility = page.locator('[data-facility] button.f-select__button');
    await tabTo(page, facility);
    await page.keyboard.press('Enter');
    const facilityList = page.getByRole('listbox', { name: text(lang, 'indicators.filters.facility') });
    await expect(facilityList).toBeVisible();
    await checkStep(page);
    await page.keyboard.press('ArrowDown');
    const chosen = (await facilityList.locator('[role="option"]').nth(1).innerText()).trim();
    await page.keyboard.press('Enter');
    await expect(facilityList).toBeHidden();
    await expect(facility).toBeFocused();
    await expect(page).toHaveURL(/[?&]instalacion=[0-9a-f-]{36}/);
    await expect(facility).toHaveAccessibleName(`${text(lang, 'indicators.filters.facility')}, ${chosen}`);
    await expect(status).toContainText(chosen);
    expect(await status.innerText()).not.toBe(afterPeriod);
    await panelReady(page, false);

    // Uso: o primeiro uso desa instalación
    const use = page.locator('[data-use] button.f-select__button');
    await tabTo(page, use);
    await page.keyboard.press('ArrowDown');
    const useList = page.getByRole('listbox', { name: text(lang, 'indicators.filters.use') });
    await expect(useList).toBeVisible();
    await page.keyboard.press('ArrowDown');
    const useName = (await useList.locator('[role="option"]').nth(1).innerText()).trim();
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(new RegExp(`[?&]uso=${encodeURIComponent(useName).replace(/%20/g, '(%20|\\+)')}`));
    await expect(status).toContainText(useName);
    await panelReady(page, false);
    await checkStep(page);

    // «Atrás» restaura cada paso
    await page.goBack();
    await expect(page).not.toHaveURL(/[?&]uso=/);
    await expect(use).toHaveAccessibleName(
      `${text(lang, 'indicators.filters.use')}, ${text(lang, 'indicators.filters.allUses')}`,
    );
    await page.goBack();
    await expect(page).not.toHaveURL(/[?&]instalacion=/);
    await expect(facility).toHaveAccessibleName(
      `${text(lang, 'indicators.filters.facility')}, ${text(lang, 'indicators.filters.allFacilities')}`,
    );
    await page.goBack();
    await expect(page).not.toHaveURL(/periodo=/);
    await expect(page.locator('[data-period="mes"]')).toHaveAttribute('aria-pressed', 'true');
    await panelReady(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('«Datas…» co teclado: frechas, inicio e fin, Aplicar; máis de 366 días non; Esc conserva o período', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    await openIndicators(page, lang);
    const datesButton = page.locator('[data-period="datas"]');
    const panelOf = () => page.getByRole('dialog', { name: text(lang, 'indicators.filters.dates') });
    const focusedDay = async (): Promise<string> =>
      (await page.evaluate(() => document.activeElement?.getAttribute('data-date'))) ?? '';
    /** Unha tecla do calendario: o foco pásase ao día novo despois de pintar, así que espérase. */
    const move = async (key: string, days: number): Promise<string> => {
      const target = shift(await focusedDay(), days);
      await page.keyboard.press(key);
      await expect.poll(focusedDay).toBe(target);
      return target;
    };

    // Esc sen escoller: volve o foco a «Datas…» e segue o mes
    await tabTo(page, datesButton);
    await page.keyboard.press('Enter');
    await expect(panelOf()).toBeVisible();
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(panelOf()).toBeHidden();
    await expect(datesButton).toBeFocused();
    await expect(page.locator('[data-period="mes"]')).toHaveAttribute('aria-pressed', 'true');
    await expect(page).not.toHaveURL(/periodo=/);

    // Inicio: hoxe − 10 días; fin: unha semana despois
    await page.keyboard.press('Enter');
    const dates = panelOf();
    await expect(dates).toBeVisible();
    await tabTo(page, dates.locator('td[data-date][tabindex="0"]'));
    await move('ArrowUp', -7);
    let from = '';
    for (let i = 0; i < 3; i++) from = await move('ArrowLeft', -1);
    await page.keyboard.press('Enter');
    await expect(dates.locator(`td[data-date="${from}"]`)).toHaveAttribute('aria-selected', 'true');
    await expect(dates.locator('.f-indicators__dates-hint')).toHaveText(
      text(lang, 'indicators.filters.datesLast', { max: 366 }),
    );
    const to = await move('ArrowDown', 7);
    await page.keyboard.press('Enter');
    await expect(dates.locator(`td[data-date="${to}"]`)).toHaveAttribute('aria-selected', 'true');
    // Os días do medio só se ven se o rango non cruza de mes (o panel queda no mes do fin).
    if (from.slice(0, 7) === to.slice(0, 7)) await expect(dates.locator('[data-in-range]').first()).toBeAttached();
    const apply = dates.locator('[data-dates-apply]');
    await expect(apply).toBeEnabled();
    await checkStep(page);
    await tabTo(page, apply);
    await page.keyboard.press('Enter');
    await expect(dates).toBeHidden();
    await expect(page).toHaveURL(new RegExp(`periodo=datas&desde=${from}&ata=${to}`));
    await expect(page.locator('[data-indicators-status]')).not.toHaveText('');
    await panelReady(page, false);

    // Máis de 366 días: «Aplicar» desactivado e aviso; Esc pecha e o período segue sendo o de antes
    const field = page.locator('[data-dates] button[aria-haspopup="dialog"]');
    await field.focus();
    await page.keyboard.press('Enter');
    await expect(dates).toBeVisible();
    await tabTo(page, dates.locator('td[data-date][tabindex="0"]'));
    const start = await focusedDay();
    await page.keyboard.press('Enter');
    // Escoller pinta de novo o calendario: espérase antes da tecla seguinte
    await expect(dates.locator('.f-indicators__dates-hint')).toHaveText(
      text(lang, 'indicators.filters.datesLast', { max: 366 }),
    );
    await page.keyboard.press('Shift+PageDown');
    await expect.poll(focusedDay).toBe(`${Number(start.slice(0, 4)) + 1}${start.slice(4)}`);
    await move('ArrowDown', 7);
    await page.keyboard.press('Enter');
    await expect(apply).toBeDisabled();
    await expect(dates.locator('.f-indicators__dates-hint')).toHaveText(
      text(lang, 'indicators.filters.tooLong', { max: 366 }),
    );
    await expect(dates.locator('.f-indicators__dates-hint')).toHaveAttribute('data-error', /.*/);
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(dates).toBeHidden();
    await expect(field).toBeFocused();
    await expect(page).toHaveURL(new RegExp(`periodo=datas&desde=${from}&ata=${to}`));
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('320 px e zoom do 200 %: sen scroll horizontal, mapa de calor desprazable e «Máis» co teclado', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    await page.setViewportSize({ width: 320, height: 640 });
    await openIndicators(page, lang);
    await checkStep(page);
    await expectNoHorizontalScroll(page);

    // A táboa do mapa de calor non cabe: rexión enfocable co caption de nome
    const peak = card(page, 'peak');
    const title = await peak.getByRole('heading', { level: 2 }).innerText();
    await peak.locator('.f-chart-card__toggle').click();
    const region = peak.getByRole('region', { name: title });
    await expect(region).toHaveAttribute('tabindex', '0');
    await region.focus();
    await expect(region).toBeFocused();
    await page.keyboard.press('ArrowRight');
    await expect.poll(() => region.evaluate((el) => el.scrollLeft)).toBeGreaterThan(0);
    await expectNoHorizontalScroll(page);
    await checkStep(page);

    // Barra inferior: catro destinos, sen «Máis»; Indicadores é a páxina actual e «Concello» leva á sección.
    const bottom = page.locator('furar-bottom-nav');
    await expect(bottom.getByRole('link', { name: text(lang, 'nav.indicators') })).toHaveAttribute(
      'aria-current',
      'page',
    );
    await expect(bottom.locator('button[data-more]')).toHaveCount(0);
    const municipality = bottom.getByRole('link', { name: text(lang, 'nav.municipality') });
    await tabTo(page, municipality, 120);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/xeral$/);
    await expect(municipality).toHaveAttribute('aria-current', 'page');
    await checkStep(page);

    // Zoom do 200 % a 1280 px (640 px CSS): o panel sen scroll horizontal (1.4.4, 1.4.10)
    await page.setViewportSize({ width: 640, height: 400 });
    await page.goto('/naron/xestion/indicadores');
    await panelReady(page);
    await expectNoHorizontalScroll(page);
    await expectNoA11yViolations(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('Ferrol sen reservas: estado baleiro e o anuncio «Sen reservas neste período»', async ({ page, lang }) => {
    test.setTimeout(90_000);
    const outside = await hermetic(page);
    await page.goto('/ferrol');
    await loginAs(page, 'xestor-ferrol', lang);
    await page.goto('/ferrol/xestion/indicadores');
    await panelReady(page, false);
    const empty = page.locator('[data-indicators-empty]');
    await expect(empty).toContainText(text(lang, 'indicators.conclusion.empty'));
    await expect(page.locator('furar-chart-card')).toHaveCount(0);
    await checkStep(page);
    const year = page.locator('[data-period="ano"]');
    await tabTo(page, year);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/periodo=ano/);
    const status = page.getByRole('status').and(page.locator('[data-indicators-status]'));
    await expect(status).toContainText(text(lang, 'indicators.conclusion.empty'));
    await expect(empty).toBeVisible();
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  for (const user of ['vecino-naron', 'recepcion-naron'] as const) {
    test(`sen permiso (${user}): páxina de prohibido e 403 na API`, async ({ page, lang, request }) => {
      const outside = await hermetic(page);
      const token = await tokenFor(request, user);
      const api = await request.get('/api/v1/municipalities/naron/indicators', { headers: token.headers });
      expect(api.status()).toBe(403);
      await page.goto('/naron');
      await loginAs(page, user, lang);
      // Sen entrada no menú
      await expect(page.getByRole('link', { name: text(lang, 'nav.indicators') })).toHaveCount(0);
      await page.goto('/naron/xestion/indicadores');
      await expect(page.getByRole('heading', { level: 1, name: text(lang, 'errorPage.403.title') })).toBeVisible();
      await expect(page.locator('furar-stat-tile')).toHaveCount(0);
      await expectNoA11yViolations(page);
      expect(outside, 'peticións fóra de localhost').toEqual([]);
    });
  }
});
