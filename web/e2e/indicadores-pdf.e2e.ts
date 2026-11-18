// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, test, text } from './fixtures';
import {
  card,
  chooseExport,
  finishPrint,
  lastPrint,
  manyFacilities,
  openIndicators,
  panelReady,
  stubPrint,
} from './indicadores-axudas';
import { hermetic } from './instalacions-axudas';
import { pdfPages, squash } from './pdf-texto';

/**
 * PDF dos indicadores dun concello grande (ADR-0026): 45 instalacións de nome longo na ocupación
 * e nos ingresos. As barras deitadas medran coas filas e a súa imaxe pasaba do alto da páxina: o
 * navegador deixaba a primeira páxina só co título, a imaxe cortada na seguinte e o resto pintado
 * enriba. Agora cada PDF (o dun gráfico e o do panel) non ten páxinas sen texto, a tarxeta empeza
 * na primeira e a táboa parte entre páxinas coa súa cabeceira en cada unha.
 */
test.describe('indicadores do concello: PDF cun concello grande', () => {
  test('45 instalacións de nome longo: sen páxinas en branco e a táboa repite a cabeceira', async ({ page, lang }) => {
    test.setTimeout(150_000);
    const outside = await hermetic(page);
    await openIndicators(page, lang);
    const names = await manyFacilities(page);
    await page.reload();
    await panelReady(page);
    await expect(card(page, 'occupancy').locator('tbody th[scope="row"]')).toHaveCount(names.length);
    await stubPrint(page);

    // Un PDF: ningunha páxina sen texto e, onde segue a táboa, a súa cabeceira outra vez.
    const print = async (head?: string) => {
      await page.emulateMedia({ media: 'print' });
      const pages = pdfPages(await page.pdf({ format: 'A4', printBackground: true }));
      await page.emulateMedia({ media: 'screen' });
      await finishPrint(page);
      pages.forEach((words, i) => expect(words, `páxina ${i + 1} con texto`).not.toBe(''));
      if (head) {
        const rows = pages.filter((words) => names.some((name) => words.includes(squash(name))));
        expect(rows.length, 'a táboa parte en máis dunha páxina').toBeGreaterThan(1);
        rows.forEach((words, i) => expect(words, `táboa, páxina ${i + 1}: coa cabeceira`).toContain(head));
      }
      return pages;
    };
    const facility = text(lang, 'indicators.column.facility');
    for (const [id, column] of [
      ['occupancy', 'indicators.column.occupancy'],
      ['revenue', 'indicators.revenue.paid'],
    ] as const) {
      const title = await card(page, id).getByRole('heading', { level: 2 }).innerText();
      await chooseExport(page, card(page, id).locator('button.f-export-menu__trigger'), 'pdf');
      expect((await lastPrint(page)).targets).toEqual([id]);
      const pages = await print(squash(facility + text(lang, column)));
      expect(pages[0], `${id}: a tarxeta empeza na primeira páxina`).toContain(squash(title));
      for (const name of names) expect(pages.join(''), `${id}: ${name}`).toContain(squash(name));
    }

    // O panel enteiro: tamén sen páxinas en branco.
    await chooseExport(
      page,
      page.locator('furar-indicator-filters furar-export-menu button.f-export-menu__trigger'),
      'pdf',
    );
    expect((await lastPrint(page)).scope).toBe('panel');
    await print();
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
});
