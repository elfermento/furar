// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Page } from '@playwright/test';
import { expect, expectNoA11yViolations, loginAs, test, text } from './fixtures';

/**
 * Menú da xestión en dous niveis e botón do QR. No móbil, a barra inferior do xestor
 * (Instalacións · Reservas · [QR] · Indicadores · Concello) e a de Recepción (Reservas · [QR] ·
 * Incidencias):
 * ningún nome se parte nin se corta a 320 e a 360 px, tamén co texto «Moi grande» (mídese: unha
 * liña e sen desbordar o seu sitio); o QR é un botón redondo de 56 px que sobresae da barra, co
 * anel na súa páxina, e na páxina o último contido non queda debaixo del. No escritorio, «Validar
 * QR» é unha pestana de «Reservas» e o título da páxina vai só para lectores de pantalla.
 */

/** Cada nome da barra inferior: o seu texto, se cabe no seu sitio e en cantas liñas vai. */
async function labels(page: Page) {
  return page.locator('nav.f-bottomnav > ul > li > a:not(.f-bottomnav__qr)').evaluateAll((links) =>
    links.map((link) => {
      const range = document.createRange();
      const label = [...link.childNodes].find((node) => node.nodeType === Node.TEXT_NODE && node.textContent?.trim());
      if (label) range.selectNodeContents(label);
      const lines = label ? new Set([...range.getClientRects()].map((rect) => Math.round(rect.top))).size : 0;
      return {
        // Só o nome: o contador (as incidencias novas) vai á parte.
        text: label?.textContent?.trim() ?? '',
        fits: link.scrollWidth <= link.clientWidth + 1,
        lines,
      };
    }),
  );
}

async function remember(page: Page, textSize: 'normal' | 'x-large'): Promise<void> {
  await page.addInitScript((value) => {
    localStorage.setItem('furar.display', JSON.stringify({ origin: 'device', textSize: value }));
  }, textSize);
}

test.describe('menú da xestión: barra inferior e botón do QR', () => {
  for (const textSize of ['normal', 'x-large'] as const) {
    for (const width of [320, 360]) {
      test(`xestor a ${width} px (texto ${textSize}): os nomes enteiros nunha liña`, async ({ page, lang }) => {
        await remember(page, textSize);
        await page.setViewportSize({ width, height: 720 });
        await page.goto('/naron');
        await loginAs(page, 'xestor-naron', lang);
        await page.goto('/naron/xestion/reservas');
        const nav = page.locator('nav.f-bottomnav');
        await expect(nav.locator('[data-bottom-qr]')).toBeVisible();
        const shown = await labels(page);
        // Na barra inferior, o nome curto do apartado do concello (es: «Municipio»).
        expect(shown.map((label) => label.text)).toEqual(
          ['nav.facilities', 'nav.bookings', 'nav.indicators', 'nav.municipalityShort'].map((key) => text(lang, key)),
        );
        for (const label of shown) {
          expect(label.fits, `«${label.text}» cabe no seu sitio`).toBe(true);
          expect(label.lines, `«${label.text}» nunha liña`).toBe(1);
        }
        // A barra non desborda a pantalla.
        const overflow = await nav.evaluate((el) => el.scrollWidth - el.clientWidth);
        expect(overflow).toBeLessThanOrEqual(0);
      });
    }
  }

  test('móbil: o QR redondo de 56 px sobresae, co anel na súa páxina; sen pestana «Validar QR»', async ({
    page,
    lang,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/reservas');
    const nav = page.locator('nav.f-bottomnav');
    const qr = nav.locator('[data-bottom-qr]');
    await expect(qr).toHaveAccessibleName(text(lang, 'nav.validate'));
    const [box, bar] = await Promise.all([qr.boundingBox(), nav.boundingBox()]);
    expect(Math.round(box!.width)).toBe(56);
    expect(Math.round(box!.height)).toBe(56);
    // Sobresae uns 12 px por riba da barra.
    expect(bar!.y - box!.y).toBeGreaterThanOrEqual(10);
    // A franxa leva Reservas e Incidencias, sen «Validar QR» (é o botón do medio).
    const strip = page.locator('[data-manage-tabs]');
    await expect(strip.getByRole('link', { name: text(lang, 'nav.validate') })).toBeHidden();
    await expect(strip.getByRole('link', { name: new RegExp(text(lang, 'nav.incidents')) })).toBeVisible();
    await expect(qr).not.toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: text(lang, 'nav.bookings') })).toHaveAttribute('aria-current', 'page');

    await qr.click();
    await expect(page).toHaveURL(/\/naron\/xestion\/validar$/);
    await expect(qr).toHaveAttribute('aria-current', 'page');
    await expect(nav.getByRole('link', { name: text(lang, 'nav.bookings') })).not.toHaveAttribute('aria-current');
    // O pé (o último da páxina) remata por riba do botón ao baixar de todo.
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
    const footer = await page.locator('footer.f-footer nav').boundingBox();
    const qrNow = await qr.boundingBox();
    expect(footer!.y + footer!.height).toBeLessThanOrEqual(qrNow!.y);
    await expectNoA11yViolations(page);
  });

  for (const width of [320, 360]) {
    test(`Recepción no móbil a ${width} px: Reservas · [QR] · Incidencias, nomes enteiros nunha liña`, async ({
      page,
      lang,
    }) => {
      await page.setViewportSize({ width, height: 720 });
      await page.goto('/naron');
      await loginAs(page, 'recepcion-naron', lang);
      await page.goto('/naron/xestion/reservas');
      const items = page.locator('nav.f-bottomnav > ul > li > a');
      await expect(items).toHaveCount(3);
      await expect(items.nth(0)).toHaveText(text(lang, 'nav.bookings'));
      await expect(items.nth(1)).toHaveAccessibleName(text(lang, 'nav.validate'));
      await expect(items.nth(2)).toContainText(text(lang, 'nav.incidents'));
      for (const label of await labels(page)) {
        expect(label.fits, `«${label.text}» cabe no seu sitio`).toBe(true);
        expect(label.lines, `«${label.text}» nunha liña`).toBe(1);
      }
    });
  }

  test('escritorio: «Reservas» con pestanas Reservas · Validar QR; título só para lectores; Recepción sen pestanas', async ({
    page,
    lang,
  }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/reservas');
    const bar = page.locator('furar-site-bar nav');
    // toContainText: «Reservas» leva o número de incidencias novas.
    await expect(bar.getByRole('link')).toContainText(
      ['nav.facilities', 'nav.bookings', 'nav.indicators', 'nav.municipality'].map((key) => text(lang, key)),
    );
    const tabs = page.locator('[data-manage-tabs]');
    await expect(tabs.getByRole('link')).toContainText([
      text(lang, 'nav.bookings'),
      text(lang, 'nav.incidents'),
      text(lang, 'nav.validate'),
    ]);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.manage.title') })).toHaveClass(
      /sr-only/,
    );
    // Na barra de Reservas, Filtros e Calendario dentro da busca; Descargar CSV e Nova reserva arriba.
    const head = page.locator('furar-manage-heading');
    await expect(head.locator('[data-manage-export]')).toBeVisible();
    await expect(head.locator('[data-manage-new]')).toBeVisible();
    const search = page.locator('.f-manage__search');
    await expect(search.locator('[data-manage-filters]')).toHaveAttribute(
      'title',
      text(lang, 'booking.manage.filters.title'),
    );
    await expect(search.locator('[data-manage-calendar]')).toHaveAccessibleName(text(lang, 'booking.manage.calendar'));

    await tabs.getByRole('link', { name: text(lang, 'nav.validate') }).click();
    await expect(page).toHaveURL(/\/naron\/xestion\/validar$/);
    await expect(tabs.getByRole('link', { name: text(lang, 'nav.validate') })).toHaveAttribute('aria-current', 'page');
    await expect(bar.getByRole('link', { name: text(lang, 'nav.bookings') })).toHaveAttribute('aria-current', 'page');
    await expectNoA11yViolations(page);
  });
});
