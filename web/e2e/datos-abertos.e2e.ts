// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext } from '@playwright/test';
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, managerHeaders, test, text } from './fixtures';

/** Acende ou apaga os datos abertos dun concello pola API, co If-Match do concello. */
async function setOpenData(request: APIRequestContext, slug: string, enabled: boolean): Promise<void> {
  const headers = await managerHeaders(request, slug);
  const current = await request.get(`/api/v1/municipalities/${slug}`, { headers });
  expect(current.ok()).toBe(true);
  // A versión do corpo: detrás do proxy, o ETag comprimido leva «-gzip» e non vale para If-Match.
  const { version } = (await current.json()) as { version: number };
  const saved = await request.put(`/api/v1/municipalities/${slug}/open-data-publication`, {
    headers: { ...headers, 'If-Match': `"${version}"` },
    data: { enabled },
  });
  expect(saved.ok(), `open-data ${slug}: ${saved.status()} ${await saved.text()}`).toBe(true);
}

test.describe('datos abertos (ADR-0034)', () => {
  test('desde o pé: pestanas, pregables co teclado, diálogo e descarga; accesible e sen scroll horizontal', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    await page
      .getByRole('contentinfo')
      .getByRole('link', { name: text(lang, 'footer.openData') })
      .click();
    await expect(page).toHaveURL(/\/naron\/datos-abertos$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(text(lang, 'openData.title'));
    const tabs = page.getByRole('tablist', { name: text(lang, 'openData.tabs.label') });
    await expect(tabs.getByRole('tab')).toHaveText([
      text(lang, 'openData.tabs.facilities'),
      text(lang, 'openData.tabs.closures'),
      text(lang, 'openData.tabs.occupancy'),
    ]);

    // Os pregables, pechados e independentes, ábrense co teclado.
    const details = page.getByRole('button', { name: text(lang, 'openData.fold.details') });
    const columns = page.getByRole('button', { name: text(lang, 'openData.fold.columns') });
    await expect(details).toHaveAttribute('aria-expanded', 'false');
    await details.focus();
    await page.keyboard.press('Enter');
    await expect(details).toHaveAttribute('aria-expanded', 'true');
    await expect(columns).toHaveAttribute('aria-expanded', 'false');
    await columns.focus();
    await page.keyboard.press('Space');
    await expect(page.getByRole('table').first()).toBeVisible();
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // Descargar: diálogo co CSV marcado; o ficheiro chega co seu nome.
    await page
      .getByRole('button', { name: text(lang, 'openData.download') })
      .first()
      .click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expect(dialog.getByRole('radio', { name: 'CSV' })).toBeChecked();
    await expectNoA11yViolations(page);
    const download = page.waitForEvent('download');
    await dialog.getByRole('button', { name: text(lang, 'openData.download') }).click();
    expect((await download).suggestedFilename()).toBe('naron-instalacions.csv');
    await expect(dialog).toBeHidden();

    // Ocupación: as datas na tarxeta, por defecto o último mes enteiro.
    await tabs.getByRole('tab', { name: text(lang, 'openData.tabs.occupancy') }).click();
    await expect(page).toHaveURL(/conxunto=ocupacion/);
    await expect(page.locator('[data-date-chip]')).toBeVisible();
    await page
      .getByRole('button', { name: text(lang, 'openData.download') })
      .first()
      .click();
    await expect(page.getByRole('dialog')).toContainText(text(lang, 'openData.tabs.occupancy'));
    await page.keyboard.press('Escape');
    await expect(page.getByRole('dialog')).toBeHidden();
  });

  test('a de todos, xunto á portada común, coa columna «concello»', async ({ page, lang }) => {
    // Desde o pé da portada común (varios concellos).
    await page.goto('/');
    await page
      .getByRole('contentinfo')
      .getByRole('link', { name: text(lang, 'footer.openData') })
      .click();
    await expect(page).toHaveURL(/\/datos-abertos$/);
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(text(lang, 'openData.title'));
    await page.getByRole('button', { name: text(lang, 'openData.fold.columns') }).click();
    await expect(page.getByRole('table').first()).toContainText('concello');
    await expectNoA11yViolations(page);
  });

  test('apagado: sen ligazón no pé e a páxina dá 404; os ficheiros tamén', async ({ page, request, lang }) => {
    await setOpenData(request, 'ferrol', false);
    try {
      await page.goto('/ferrol');
      await expect(
        page.getByRole('contentinfo').getByRole('link', { name: text(lang, 'footer.openData') }),
      ).toHaveCount(0);
      const file = await request.get('/api/v1/municipalities/ferrol/open-data/facilities.csv');
      expect(file.status()).toBe(404);
      expect(file.headers()['access-control-allow-origin']).toBe('*');
      await page.goto('/ferrol/datos-abertos');
      await expect(page).toHaveURL(/\/ferrol\/datos-abertos$/);
      await expect(page.getByRole('heading', { level: 1 })).not.toHaveText(text(lang, 'openData.title'));
    } finally {
      await setOpenData(request, 'ferrol', true);
    }
  });

  test('detrás do proxy, con compresión: o mesmo ficheiro co seu ETag dá 304', async ({ request }) => {
    const url = '/api/v1/municipalities/naron/open-data/facilities.csv';
    const first = await request.get(url, { headers: { 'Accept-Encoding': 'gzip' } });
    expect(first.status()).toBe(200);
    const etag = first.headers()['etag'];
    expect(etag).toBeTruthy();
    const again = await request.get(url, { headers: { 'Accept-Encoding': 'gzip', 'If-None-Match': etag } });
    expect(again.status()).toBe(304);
  });
});
