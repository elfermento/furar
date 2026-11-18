// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, loginAs, test, text } from './fixtures';
import { card, openIndicators, panelReady } from './indicadores-axudas';
import { checkStep, hermetic, tabTo } from './instalacions-axudas';
import { tokenFor } from './reservas-axudas';

/**
 * Indicadores do concello, filtros e contorno: período, instalación e
 * uso só co teclado (o anuncio `role="status"`, o URL e «atrás»); o chip do período co calendario de
 * rango (frechas, máis de 366 días, Esc); 320 px e zoom do 200 % (o mapa de calor desprázase dentro
 * da súa rexión) e a barra inferior; un concello sen reservas; sen permiso (veciño e recepción).
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

    // Período: Trimestre, desde o chip de data: atallo e «Aplicar»
    const chip = page.locator('[data-period-filter] .f-mdate__chip-open');
    const monthChip = (await chip.innerText()).trim();
    await tabTo(page, chip);
    await page.keyboard.press('Enter');
    const quarter = page.locator('[data-period-filter] [data-preset="trimestre"]');
    await tabTo(page, quarter);
    await page.keyboard.press('Enter');
    await expect(quarter).toHaveAttribute('aria-pressed', 'true');
    await tabTo(page, page.locator('[data-period-filter] [data-date-apply]'));
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/[?&]periodo=trimestre/);
    await expect(chip).toHaveText(text(lang, 'ui.periodFilter.trimestre'));
    await expect(chip).toBeFocused();
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
    await expect(chip).toHaveText(monthChip);
    await panelReady(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('rango no chip co teclado: frechas, inicio e fin, Aplicar; máis de 366 días non; Esc conserva o período', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    await openIndicators(page, lang);
    const chip = page.locator('[data-period-filter] .f-mdate__chip-open');
    const monthChip = (await chip.innerText()).trim();
    const dates = page.locator('[data-period-filter] [data-date-panel]');
    const apply = dates.locator('[data-date-apply]');
    const focusedDay = async (): Promise<string> =>
      (await page.evaluate(() => document.activeElement?.getAttribute('data-date'))) ?? '';
    /** Unha tecla do calendario: o foco pásase ao día novo despois de pintar, así que espérase. */
    const move = async (key: string, days: number): Promise<string> => {
      const target = shift(await focusedDay(), days);
      await page.keyboard.press(key);
      await expect.poll(focusedDay).toBe(target);
      return target;
    };

    // Esc sen escoller: o foco volve ao chip e segue o mes
    await tabTo(page, chip);
    await page.keyboard.press('Enter');
    await expect(dates).toBeVisible();
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(dates).toBeHidden();
    await expect(chip).toBeFocused();
    await expect(chip).toHaveText(monthChip);
    await expect(page).not.toHaveURL(/periodo=/);

    // Inicio: hoxe − 10 días; fin: unha semana despois
    await page.keyboard.press('Enter');
    await expect(dates).toBeVisible();
    await tabTo(page, dates.locator('td[data-date][tabindex="0"]'));
    await move('ArrowUp', -7);
    let from = '';
    for (let i = 0; i < 3; i++) from = await move('ArrowLeft', -1);
    await page.keyboard.press('Enter');
    await expect(dates.locator(`td[data-date="${from}"]`)).toHaveAttribute('aria-selected', 'true');
    const to = await move('ArrowDown', 7);
    await page.keyboard.press('Enter');
    await expect(dates.locator(`td[data-date="${to}"]`)).toHaveAttribute('aria-selected', 'true');
    await expect(apply).toBeEnabled();
    await checkStep(page);
    await tabTo(page, apply);
    await page.keyboard.press('Enter');
    await expect(dates).toBeHidden();
    await expect(page).toHaveURL(new RegExp(`periodo=datas&desde=${from}&ata=${to}`));
    await expect(page.locator('[data-indicators-status]')).not.toHaveText('');
    await panelReady(page, false);

    // Máis de 366 días: «Aplicar» desactivado e aviso; Esc pecha e o período segue sendo o de antes
    await chip.focus();
    await page.keyboard.press('Enter');
    await expect(dates).toBeVisible();
    await tabTo(page, dates.locator('td[data-date][tabindex="0"]'));
    const start = await focusedDay();
    await page.keyboard.press('Enter');
    await page.keyboard.press('Shift+PageDown');
    await expect.poll(focusedDay).toBe(`${Number(start.slice(0, 4)) + 1}${start.slice(4)}`);
    await move('ArrowDown', 7);
    await page.keyboard.press('Enter');
    await expect(apply).toBeEnabled();
    await expect(dates.locator('[data-date-too-long]')).toHaveText(text(lang, 'ui.dateFilter.tooLong', { max: 366 }));
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(dates).toBeHidden();
    await expect(chip).toBeFocused();
    await expect(page).toHaveURL(new RegExp(`periodo=datas&desde=${from}&ata=${to}`));
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('320 px e zoom do 200 %: sen scroll horizontal, mapa de calor desprazable e a barra inferior co teclado', async ({
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

    // Barra inferior: Instalacións · Reservas · [QR] · Indicadores · Concello, sen
    // «Máis»; «Indicadores» marcada; «Concello» leva á sección.
    const bottom = page.locator('furar-bottom-nav');
    await expect(bottom.locator('button[data-more]')).toHaveCount(0);
    await expect(bottom.getByRole('link', { name: text(lang, 'nav.indicators') })).toHaveAttribute(
      'aria-current',
      'page',
    );
    const municipality = bottom.getByRole('link', { name: text(lang, 'nav.municipalityShort') });
    await tabTo(page, municipality, 140);
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

  // Monterrei: a demo non lle pon reservas e ningunha proba reserva alí. Ferrol non vale: as probas
  // que reservan alí deixan reservas e cancelacións (as reservas non se borran), así que o baleiro
  // dependería da orde e de cantas veces corresen. Reservar en Monterrei rompería esta proba.
  test('concello sen reservas: estado baleiro e o anuncio «Sen reservas neste período»', async ({ page, lang }) => {
    test.setTimeout(90_000);
    const outside = await hermetic(page);
    await page.goto('/monterrei');
    await loginAs(page, 'xestor-monterrei', lang);
    await page.goto('/monterrei/xestion/indicadores');
    await panelReady(page, false);
    const empty = page.locator('[data-indicators-empty]');
    await expect(empty).toContainText(text(lang, 'indicators.conclusion.empty'));
    await expect(page.locator('furar-chart-card')).toHaveCount(0);
    await checkStep(page);
    await page.locator('[data-period-filter] .f-mdate__chip-open').click();
    await page.locator('[data-period-filter] [data-preset="ano"]').click();
    await page.locator('[data-period-filter] [data-date-apply]').click();
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
