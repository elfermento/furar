// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { removeLeftoverE2eFields, expect, loginAs, openSectionTab, test, text } from './fixtures';
import { chooseExport, openIndicators, readCsv, tileButton } from './indicadores-axudas';

/**
 * Accións en curso e transicións (docs/deseno.md §8): mentres corre unha acción, o resto do seu
 * contexto queda bloqueado, e nada se superpón á transición dunha capa.
 *
 * - Regresión: «Exportar» co teclado coa animación de peche do modal dunha cifra aínda en curso
 *   (sen movemento reducido) tumbaba o renderizador de Chromium. Agora a tecla remata a
 *   transición antes de chegar á páxina: o menú abre, o CSV descárgase e a páxina segue viva.
 * - Dobre envío: «Gardar» dúas veces seguidas nos datos do concello fai unha soa petición; o
 *   botón queda ocupado co foco e o nome intactos mentres garda.
 */
test.describe('accións en curso e transicións', () => {
  test.beforeAll(async ({ request }) => removeLeftoverE2eFields(request));

  test('pechar o modal dunha cifra e «Exportar» ao momento: sen caerse, co CSV descargado', async ({ page, lang }) => {
    let crashed = false;
    page.on('crash', () => (crashed = true));
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await openIndicators(page, lang);
    const exportButton = page.locator('furar-indicator-filters furar-export-menu button.f-export-menu__trigger');
    for (let round = 0; round < 3; round++) {
      await tileButton(page, 'bookings').click();
      const dialog = page.getByRole('dialog');
      await expect(dialog).toBeVisible();
      await page.keyboard.press('Escape');
      // Sen esperar a que remate a animación de peche.
      const download = page.waitForEvent('download');
      await chooseExport(page, exportButton, 'csv');
      const { name } = await readCsv(await download);
      expect(name).toMatch(/^indicadores-naron-.*\.csv$/);
      await expect(dialog).toBeHidden();
      await expect(exportButton).toBeFocused();
      await expect(exportButton).not.toHaveAttribute('aria-busy', 'true');
    }
    expect(crashed, 'o renderizador caeu').toBe(false);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'indicators.title') })).toBeVisible();
  });

  test('Concello › Xeral: «Gardar» dúas veces seguidas garda unha vez, ocupado e co foco', async ({ page, lang }) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'general');
    const save = page.getByRole('button', { name: text(lang, 'common.save') });
    await expect(save).toBeVisible();
    // A API responde tarde, para ver o estado ocupado; cóntanse os PUT.
    let puts = 0;
    let release!: () => void;
    const held = new Promise<void>((resolve) => (release = resolve));
    await page.route(/\/api\/v1\/municipalities\/naron$/, async (route) => {
      if (route.request().method() !== 'PUT') return route.fallback();
      puts++;
      await held;
      return route.fallback();
    });
    await save.focus();
    await save.dblclick();
    const busy = page.getByRole('button', { name: text(lang, 'common.saving') });
    await expect(busy).toHaveAttribute('aria-busy', 'true');
    await expect(busy).toHaveAttribute('aria-disabled', 'true');
    // aria-disabled, non disabled: segue na orde do foco.
    expect(await busy.evaluate((b) => (b as HTMLButtonElement).disabled)).toBe(false);
    await expect(busy).toBeFocused();
    await busy.click({ force: true });
    await page.keyboard.press('Enter');
    release();
    await expect(page.getByRole('status').filter({ hasText: text(lang, 'settings.saved') })).toBeVisible();
    await expect(save).not.toHaveAttribute('aria-busy', 'true');
    await expect(save).toBeFocused();
    expect(puts, 'peticións PUT').toBe(1);
  });
});
