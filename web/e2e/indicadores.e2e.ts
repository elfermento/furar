// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, test, text } from './fixtures';
import {
  CHART_IDS,
  KPI_FILES,
  KPI_IDS,
  card,
  chooseExport,
  darkTheme,
  finishPrint,
  lastPrint,
  luminanceOf,
  openIndicators,
  panelReady,
  periodFile,
  readCsv,
  stubPrint,
  tileButton,
} from './indicadores-axudas';
import { checkStep, hermetic, tabTo } from './instalacions-axudas';
import { pdfTags, pdfText, squash } from './pdf-texto';

/**
 * Indicadores do concello (plan do dominio 3, D3-10; ADR-0026), o panel: o xestor entra polo menú
 * (axe en claro e co tema escuro do concello; o rato polos seis gráficos sen violar a CSP); as
 * cinco cifras e o seu modal só co teclado, tamén con movemento reducido; «Táboa» e «Exportar»
 * no modal; os seis gráficos co seu nome e a súa táboa; CSV (evento `download`) e PDF
 * (`window.print` substituído e un `page.pdf` etiquetado) do panel e dun gráfico; Ctrl+P no tema
 * escuro; o período en curso (a variación «ata hoxe» e «(en curso)» na táboa e no CSV, ADR-0026,
 * revisión do 2026-10-29). Os filtros, o móbil e os permisos, en `indicadores-filtros.e2e.ts`.
 *
 * Axe en cada paso (e 320 px sen scroll horizontal en gl-mobile), sen peticións fóra de localhost
 * nin violacións de CSP. Sen cifras fixas da demo (cambia cada día): o esperado sae da resposta
 * da API que pediu a propia páxina.
 */
test.describe('indicadores do concello: panel e exportar', () => {
  test('menú → Indicadores: axe en claro e en escuro; os tooltips dos seis gráficos sen violar a CSP', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const panel = await openIndicators(page, lang);
    await checkStep(page);

    // As cifras saen da resposta da API (non números fixos da demo).
    for (const id of ['bookings', 'people'] as const) {
      const shown = await tileButton(page, id).locator('.f-stat-tile__value').innerText();
      expect(shown.replace(/\D/g, ''), `cifra ${id}`).toBe(String(panel.kpis[id].value));
    }
    // A táboa do primeiro gráfico ten unha fila por instalación da resposta.
    const facilities = card(page, 'occupancy').locator('tbody th[scope="row"]');
    await expect(facilities).toHaveCount(panel.occupancyByFacility.length);
    expect((await facilities.allInnerTexts()).sort()).toEqual(panel.occupancyByFacility.map((f) => f.name).sort());

    // O rato por riba de cada gráfico (tooltip de ECharts): a garda da CSP falla se algo mete estilos en liña.
    for (const id of CHART_IDS) {
      const canvas = card(page, id).locator('.f-chart__canvas');
      await canvas.scrollIntoViewIfNeeded();
      const box = (await canvas.boundingBox())!;
      for (const x of [0.3, 0.5, 0.7]) {
        await page.mouse.move(box.x + box.width * x, box.y + box.height * 0.5, { steps: 3 });
      }
    }
    await page.mouse.move(0, 0);

    // Tema escuro do concello: o mesmo panel, accesible.
    await darkTheme(page);
    await page.reload();
    await panelReady(page);
    const surface = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--f-surface'),
    );
    expect(luminanceOf(surface), 'superficie escura').toBeLessThan(0.1);
    await checkStep(page);
    await tileButton(page, 'bookings').click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toBeHidden();

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  for (const motion of ['no-preference', 'reduce'] as const) {
    test(`as cinco cifras só co teclado (movemento: ${motion}): Intro abre, o foco no título, Tab dentro, Esc volve`, async ({
      page,
      lang,
    }) => {
      test.setTimeout(120_000);
      const outside = await hermetic(page);
      await page.emulateMedia({ reducedMotion: motion });
      await openIndicators(page, lang);
      for (const id of KPI_IDS) {
        const tile = tileButton(page, id);
        await tabTo(page, tile, 60);
        await page.keyboard.press('Enter');
        const dialog = page.getByRole('dialog');
        await expect(dialog).toBeVisible();
        const heading = dialog.getByRole('heading', { level: 2 });
        await expect(heading).toBeFocused();
        await expect(dialog).toHaveAccessibleName(await heading.innerText());
        if (id === 'bookings') await checkStep(page);
        // O Tab non sae do modal.
        for (let i = 0; i < 6; i++) {
          await page.keyboard.press('Tab');
          expect(await dialog.evaluate((d) => d.contains(document.activeElement)), `Tab ${i + 1} dentro`).toBe(true);
        }
        await page.keyboard.press('Escape');
        await expect(dialog).toBeHidden();
        await expect(tile).toBeFocused();
      }
      expect(outside, 'peticións fóra de localhost').toEqual([]);
    });
  }

  test('modal: «Táboa» e «Exportar» co teclado; Esc pecha só o menú; CSV da evolución', async ({ page, lang }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const panel = await openIndicators(page, lang);
    await tabTo(page, tileButton(page, 'bookings'), 60);
    await page.keyboard.press('Enter');
    const dialog = page.getByRole('dialog');
    await expect(dialog.getByRole('heading', { level: 2 })).toBeFocused();
    const title = await dialog.getByRole('heading', { level: 2 }).innerText();

    // «Táboa»: aria-pressed, o foco queda no botón, a táboa co caption e as cabeceiras
    const toggle = dialog.getByRole('button', { name: text(lang, 'ui.chartCard.tableFor', { title }) });
    await tabTo(page, toggle);
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await page.keyboard.press('Enter');
    await expect(toggle).toHaveAttribute('aria-pressed', 'true');
    await expect(toggle).toBeFocused();
    const table = dialog.locator(`#${await toggle.getAttribute('aria-controls')}`);
    await expect(table).toBeVisible();
    await expect(table.locator('caption')).toHaveText(title);
    await expect(table.locator('th[scope="col"]')).toHaveCount(2);
    expect(await table.locator('th[scope="row"]').count()).toBeGreaterThan(0);
    await expect(dialog.locator('figure[role="img"]')).toBeHidden();
    await checkStep(page);

    // «Exportar»: Intro abre o menú co foco no PDF, frechas, Esc pecha só o menú
    const exportButton = dialog.getByRole('button', { name: text(lang, 'ui.exportMenu.named', { subject: title }) });
    await tabTo(page, exportButton);
    await page.keyboard.press('Enter');
    const menu = page.getByRole('menu');
    await expect(menu).toBeVisible();
    await expect(menu.locator('[data-format="pdf"]')).toBeFocused();
    await page.keyboard.press('ArrowDown');
    await expect(menu.locator('[data-format="csv"]')).toBeFocused();
    await page.keyboard.press('ArrowUp');
    await expect(menu.locator('[data-format="pdf"]')).toBeFocused();
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(menu).toBeHidden();
    await expect(dialog).toBeVisible();
    await expect(exportButton).toBeFocused();

    // CSV da evolución: nome co período e a cifra, BOM e o seu contido
    const downloading = page.waitForEvent('download');
    await chooseExport(page, exportButton, 'csv');
    const csv = await readCsv(await downloading);
    expect(csv.name).toBe(`indicadores-naron-${periodFile(panel.period)}-evolucion-${KPI_FILES.bookings}.csv`);
    expect(csv.body).toContain(
      `${text(lang, 'indicators.column.period')};${text(lang, 'indicators.evolution.column.bookings')}`,
    );
    await expect(menu).toBeHidden();
    await expect(dialog).toBeVisible();

    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();
    await expect(tileButton(page, 'bookings')).toBeFocused();
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('os seis gráficos: figura con nome (título e conclusión) e «Táboa» con caption e cabeceiras', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    await openIndicators(page, lang);
    for (const id of CHART_IDS) {
      const chart = card(page, id);
      const title = (await chart.getByRole('heading', { level: 2 }).innerText()).trim();
      const conclusion = (await chart.locator('.f-chart-card__conclusion').innerText()).trim();
      expect(conclusion, `conclusión de ${id}`).not.toBe('');
      const figure = chart.locator('figure[role="img"]');
      const name = (await figure.getAttribute('aria-label')) ?? '';
      expect(name.startsWith(title), `o nome de ${id} empeza polo título`).toBe(true);
      expect(name).toContain(conclusion);

      const toggle = chart.getByRole('button', { name: text(lang, 'ui.chartCard.tableFor', { title }) });
      await expect(toggle).toHaveAttribute('aria-pressed', 'false');
      const tableId = await toggle.getAttribute('aria-controls');
      const region = page.locator(`#${tableId}`);
      await expect(region).toBeHidden();
      await toggle.focus();
      await page.keyboard.press('Enter');
      await expect(toggle).toHaveAttribute('aria-pressed', 'true');
      await expect(toggle).toBeFocused();
      await expect(region).toBeVisible();
      await expect(figure).toBeHidden();
      await expect(region.locator('caption')).toHaveText(title);
      expect(await region.locator('thead th[scope="col"]').count()).toBeGreaterThanOrEqual(2);
      expect(await region.locator('tbody th[scope="row"]').count()).toBeGreaterThan(0);
      await expect(chart).toHaveAttribute('data-view', 'table');
    }
    await checkStep(page);
    // E de volta ao gráfico
    const toggle = card(page, 'occupancy').locator('.f-chart-card__toggle');
    await toggle.focus();
    await page.keyboard.press('Space');
    await expect(toggle).toHaveAttribute('aria-pressed', 'false');
    await expect(card(page, 'occupancy').locator('figure[role="img"]')).toBeVisible();
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('exportar o panel e un gráfico: CSV e PDF (window.print substituído e PDF etiquetado)', async ({
    page,
    lang,
  }) => {
    test.setTimeout(150_000);
    const outside = await hermetic(page);
    const panel = await openIndicators(page, lang);
    const period = periodFile(panel.period);
    const panelExport = page.locator('furar-indicator-filters furar-export-menu button.f-export-menu__trigger');

    // CSV do panel: as cifras coa resposta da API
    let downloading = page.waitForEvent('download');
    await chooseExport(page, panelExport, 'csv');
    const panelCsv = await readCsv(await downloading);
    expect(panelCsv.name).toBe(`indicadores-naron-${period}.csv`);
    const label = text(lang, 'indicators.kpi.bookings');
    // No período en curso: valor, ata hoxe e o mesmo tramo do anterior; nun pechado, valor e anterior.
    const bookings = panel.kpis.bookings;
    const row = panel.comparison.toDate
      ? [label, bookings.value, bookings.valueToDate, bookings.previousToDate]
      : [label, bookings.value, bookings.previous];
    expect(panelCsv.body).toContain(`\r\n${row.join(';')}\r\n`);
    for (const id of CHART_IDS) {
      expect(panelCsv.body).toContain(await card(page, id).getByRole('heading', { level: 2 }).innerText());
    }
    await expect(panelExport).toBeFocused();

    // CSV dun gráfico
    const occupancyExport = card(page, 'occupancy').locator('button.f-export-menu__trigger');
    downloading = page.waitForEvent('download');
    await chooseExport(page, occupancyExport, 'csv');
    const chartCsv = await readCsv(await downloading);
    expect(chartCsv.name).toBe(`indicadores-naron-${period}-ocupacion.csv`);
    for (const facility of panel.occupancyByFacility) expect(chartCsv.body).toContain(facility.name);

    // PDF do panel: alcance, imaxes co alt de cada figura e a cabeceira impresa
    await stubPrint(page);
    await chooseExport(page, panelExport, 'pdf');
    const printed = await lastPrint(page);
    expect(printed.scope).toBe('panel');
    expect(printed.targets).toEqual([]);
    const named = printed.images.filter(Boolean);
    expect(named).toHaveLength(6);
    for (const id of CHART_IDS) {
      const title = await card(page, id).getByRole('heading', { level: 2 }).innerText();
      expect(
        named.some((alt) => alt.startsWith(title)),
        `imaxe de ${title}`,
      ).toBe(true);
    }
    await page.emulateMedia({ media: 'print' });
    const header = page.locator('.f-indicators > .f-print-header');
    await expect(header).toBeVisible();
    await expect(header).toContainText('Narón');
    await expect(page.locator('furar-indicator-filters')).toBeHidden();
    const tagged = await page.pdf({ tagged: true, format: 'A4', printBackground: true });
    const tags = pdfTags(tagged);
    expect(tags.has('Table'), 'PDF con táboas').toBe(true);
    expect(tags.has('Figure'), 'PDF con figuras').toBe(true);
    const words = pdfText(tagged);
    expect(words).toContain(squash(text(lang, 'indicators.title')));
    expect(words).toContain(squash(text(lang, 'indicators.occupancy.title')));
    expect(words).toContain(squash(panel.occupancyByFacility[0]!.name));
    await page.emulateMedia({ media: 'screen' });
    await finishPrint(page);

    // PDF dun gráfico: só a súa tarxeta e a súa imaxe
    await chooseExport(page, occupancyExport, 'pdf');
    const one = await lastPrint(page);
    expect(one.scope).toBe('card');
    expect(one.targets).toEqual(['occupancy']);
    expect(one.images).toHaveLength(1);
    expect(one.images[0]!.startsWith(text(lang, 'indicators.occupancy.title'))).toBe(true);
    await page.emulateMedia({ media: 'print' });
    await expect(card(page, 'occupancy')).toBeVisible();
    await expect(card(page, 'occupancy').locator('.f-chart-card__table')).toBeVisible();
    await expect(card(page, 'people')).toBeHidden();
    await page.emulateMedia({ media: 'screen' });
    await finishPrint(page);
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('Ctrl+P no tema escuro sen «Exportar»: alcance do panel e imaxes en claro', async ({ page, lang }) => {
    test.setTimeout(90_000);
    const outside = await hermetic(page);
    await darkTheme(page);
    await openIndicators(page, lang);
    const screenSurface = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--f-surface'),
    );
    expect(luminanceOf(screenSurface)).toBeLessThan(0.1);

    await page.evaluate(() => window.dispatchEvent(new Event('beforeprint')));
    await expect(page.locator('html')).toHaveAttribute('data-print', 'panel');
    const images = page.locator('img.f-chart__print');
    expect(await images.count()).toBeGreaterThanOrEqual(6);
    const alts = (await images.evaluateAll((all) => all.map((i) => (i as HTMLImageElement).alt))).filter(Boolean);
    expect(alts).toHaveLength(6);
    // A imaxe vai co fondo do tema claro (o papel é branco)
    const corners = await images.evaluateAll((all) =>
      Promise.all(
        all.map(async (img) => {
          const image = img as HTMLImageElement;
          await image.decode();
          const canvas = document.createElement('canvas');
          canvas.width = image.naturalWidth;
          canvas.height = image.naturalHeight;
          const context = canvas.getContext('2d')!;
          context.drawImage(image, 0, 0);
          return [...context.getImageData(1, 1, 1, 1).data];
        }),
      ),
    );
    for (const [r, g, b] of corners) expect(luminanceOf(`rgb(${r}, ${g}, ${b})`)).toBeGreaterThan(0.8);
    // No papel, os tokens do tema claro
    await page.emulateMedia({ media: 'print' });
    const printSurface = await page.evaluate(() =>
      getComputedStyle(document.documentElement).getPropertyValue('--f-surface'),
    );
    expect(luminanceOf(printSurface)).toBeGreaterThan(0.8);
    await expect(page.locator('.f-indicators > .f-print-header')).toBeVisible();
    await page.emulateMedia({ media: 'screen' });
    await finishPrint(page);
    const after = await page.evaluate(() => getComputedStyle(document.documentElement).getPropertyValue('--f-surface'));
    expect(luminanceOf(after)).toBeLessThan(0.1);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('período en curso: a variación compara ata hoxe e o tramo en curso dío en texto; un pechado non', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    // Sen a transición do modal: abrir «Exportar» mentres aínda corre a de peche tumba o renderizador
    // de Chromium (a transición próbana as cinco cifras, arriba).
    await page.emulateMedia({ reducedMotion: 'reduce' });
    // O mes de hoxe (por defecto) está sempre en curso.
    const panel = await openIndicators(page, lang);
    expect(panel.comparison.toDate, 'o mes de hoxe compárase ata hoxe').toBe(true);
    expect(panel.comparison.from).toBe(panel.period.from);
    expect(panel.kpis.bookings.evolution.at(-1)?.partial, 'o último punto da evolución está en curso').toBe(true);
    const inProgress = text(lang, 'indicators.inProgress');
    const toDate = text(lang, 'indicators.kpi.comparisonToDate.MONTH', { label: '' }).trim();
    const change = tileButton(page, 'bookings').locator('.f-stat-tile__change');
    if (panel.kpis.bookings.previousToDate > 0) await expect(change).toContainText(toDate);

    // A táboa da ocupación día a día marca o tramo de hoxe en texto.
    const daily = card(page, 'daily');
    const dailyTitle = (await daily.getByRole('heading', { level: 2 }).innerText()).trim();
    await daily.getByRole('button', { name: text(lang, 'ui.chartCard.tableFor', { title: dailyTitle }) }).click();
    const rows = daily.locator('tbody th[scope="row"]');
    const partial = panel.dailyOccupancy.findIndex((b) => b.partial);
    expect(partial, 'un tramo en curso na desagregación').toBeGreaterThanOrEqual(0);
    await expect(rows.nth(partial)).toContainText(`(${inProgress})`);
    await expect(daily.locator('tbody th[scope="row"]', { hasText: `(${inProgress})` })).toHaveCount(1);
    await checkStep(page);

    // O modal: o último período da táboa leva «(en curso)».
    await tileButton(page, 'bookings').click();
    const dialog = page.getByRole('dialog');
    const title = await dialog.getByRole('heading', { level: 2 }).innerText();
    await dialog.getByRole('button', { name: text(lang, 'ui.chartCard.tableFor', { title }) }).click();
    await expect(dialog.locator('tbody th[scope="row"]').last()).toContainText(`(${inProgress})`);
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(dialog).toBeHidden();

    // O CSV do panel leva os dous tramos comparados na cabeceira das cifras.
    const downloading = page.waitForEvent('download');
    await chooseExport(
      page,
      page.locator('furar-indicator-filters furar-export-menu button.f-export-menu__trigger'),
      'csv',
    );
    const csv = await readCsv(await downloading);
    expect(csv.body).toContain(text(lang, 'indicators.column.valueInProgress', { inProgress }));
    expect(csv.body).toContain(`${text(lang, 'indicators.column.toDate', { span: '' }).split(' (')[0]} (`);

    // Un rango de datas xa pechado: comparación enteira e ningún «en curso».
    const day = (offset: number) => {
      const date = new Date();
      date.setDate(date.getDate() + offset);
      return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
    };
    await page.goto(`/naron/xestion/indicadores?periodo=datas&desde=${day(-40)}&ata=${day(-10)}`);
    await panelReady(page);
    await expect(tileButton(page, 'bookings').locator('.f-stat-tile__change')).not.toContainText(toDate);
    const closedTitle = (await card(page, 'daily').getByRole('heading', { level: 2 }).innerText()).trim();
    await card(page, 'daily')
      .getByRole('button', { name: text(lang, 'ui.chartCard.tableFor', { title: closedTitle }) })
      .click();
    await expect(card(page, 'daily').locator('tbody th[scope="row"]').first()).toBeVisible();
    // (As persoas por mes poden levar o mes de hoxe en curso: son os seis que rematan no do fin.)
    await expect(card(page, 'daily').locator('tbody th[scope="row"]', { hasText: `(${inProgress})` })).toHaveCount(0);
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
});
