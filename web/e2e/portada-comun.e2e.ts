// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, test, text } from './fixtures';

/**
 * Portada común (ADR-0030): «Preto de min» dentro do mapa do botón «Mapa» (no escritorio, ao lado
 * da grella; no móbil, a toda a pantalla), marcas agrupadas, carruseis por concello e a folla
 * «Filtros».
 * Precisa varios concellos (db/demo).
 */
test.describe('portada común: preto de min, mapa e filtros', () => {
  test('«Preto de min» ordena pola distancia e centra o mapa; a posición non sae do navegador', async ({
    page,
    context,
    lang,
  }) => {
    // Ao lado da Gándara (Narón): nos resultados (cun filtro), as máis cerca primeiro.
    await context.grantPermissions(['geolocation']);
    await context.setGeolocation({ latitude: 43.5167, longitude: -8.1903 });
    const api: string[] = [];
    await page.goto('/?concello=naron');
    page.on('request', (request) => {
      if (request.url().includes('/api/')) api.push(request.url());
    });
    // O mapa ábrese co botón «Mapa»: no escritorio, ao lado da grella; no móbil, a toda a pantalla.
    await page.locator('[data-map-toggle]').click();
    const map = page.locator('.f-plist__map, [data-home-map]');
    const near = map.getByRole('button', { name: text(lang, 'facility.catalog.near') });
    await expect(near).toBeVisible();
    await expect(near).toHaveAttribute('aria-pressed', 'false');
    await near.click();
    await expect(near).toHaveAttribute('aria-pressed', 'true');
    const firstPlace = page.locator('furar-facility-card .f-media-card__place').first();
    await expect(firstPlace).toContainText(/^a \d/);
    await expect(firstPlace).toContainText('Narón (demo) · ');
    await expect(map.locator('.f-map__here')).toHaveCount(1);
    // Nin a posición nin nada novo vai á API.
    expect(api.filter((url) => /latitude|longitude|lat=|lon=/.test(url))).toEqual([]);
    await expectNoA11yViolations(page);
    await near.click();
    await expect(near).toHaveAttribute('aria-pressed', 'false');
    await expect(firstPlace).not.toContainText(/^a \d/);
  });

  test('«Mapa»: grella e mapa (no móbil, a toda a pantalla); sen permiso, aviso que se pecha co foco en «Preto de min»', async ({
    page,
    lang,
  }) => {
    await page.goto('/');
    const toggle = page.locator('[data-map-toggle]');
    await expect(toggle).toHaveText(text(lang, 'facility.publicList.view.map'));
    await toggle.click();
    const map = page.locator('.f-plist__map, [data-home-map]');
    await expect(map.locator('.f-map__marker').first()).toBeVisible();
    await expect(toggle).toHaveText(text(lang, 'facility.publicList.view.list'));
    const near = map.getByRole('button', { name: text(lang, 'facility.catalog.near') });
    // Sen grantPermissions, o navegador denega a posición.
    await near.click();
    const notice = page.locator('[data-near-notice]');
    await expect(notice).toContainText(
      `${text(lang, 'facility.catalog.nearDenied.lead')} ${text(lang, 'facility.catalog.nearDenied.text')}`,
    );
    await expect(near).toHaveAttribute('aria-pressed', 'false');
    // O texto do aviso vai centrado coa icona (unha liña: os dous co mesmo centro vertical).
    const [icon, body] = await Promise.all([
      notice.locator('.f-callout__icon').boundingBox(),
      notice.locator('.f-callout__body p').boundingBox(),
    ]);
    if (icon && body && body.height < 2 * icon.height) {
      expect(Math.abs(icon.y + icon.height / 2 - (body.y + body.height / 2))).toBeLessThan(3);
    }
    await expectNoA11yViolations(page);
    await notice.getByRole('button', { name: text(lang, 'ui.callout.close') }).click();
    await expect(notice).toHaveCount(0);
    await expect(near).toBeFocused();
    await expectNoHorizontalScroll(page);
    // «Lista» volve aos carruseis.
    await toggle.click();
    await expect(map).toHaveCount(0);
    await expect(page.locator('[data-rail]').first()).toBeVisible();
  });

  test('cada tarxeta das portadas con ocos leva as súas horas (os carruseis, os resultados e a portada do concello)', async ({
    page,
  }) => {
    for (const url of ['/', '/?deporte=football', '/naron']) {
      await page.goto(url);
      const cards = page.locator('furar-facility-card');
      await expect(cards.first()).toBeVisible();
      // A tarxeta co chip «N ocos» ten as horas debaixo (ata tres), cada unha a reservar con esa hora.
      for (const card of await cards.filter({ has: page.locator('furar-tag[cardBadge]') }).all()) {
        const times = card.locator('.f-slot-links a');
        expect(await times.count()).toBeGreaterThan(0);
        expect(await times.count()).toBeLessThanOrEqual(3);
        await expect(times.first()).toHaveAttribute('href', /\/reservar\?data=\d{4}-\d{2}-\d{2}&hora=\d{2}:\d{2}/);
      }
    }
  });

  test('marcas cercanas agrupadas cun número; picar o grupo sepáraas', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 900 });
    await page.goto('/');
    await page.locator('[data-map-toggle]').click();
    // No escritorio, o mapa vai á dereita da grella con todas.
    const map = page.locator('.f-plist__split > .f-plist__map');
    await expect(page.locator('furar-facility-card').first()).toBeVisible();
    const group = map.locator('.f-map__marker--cluster').first();
    await expect(group).toBeVisible();
    const count = Number((await group.textContent())?.trim());
    expect(count).toBeGreaterThan(1);
    const pins = map.locator('.f-map__marker:not(.f-map__marker--cluster)');
    const before = await pins.count();
    await group.click();
    await expect.poll(() => pins.count()).toBeGreaterThan(before);
    // Unha marca soa leva á ficha da instalación.
    await pins.first().click();
    await expect(page).toHaveURL(/\/[a-z0-9-]+\/instalacions\/[0-9a-f-]{36}/);
  });

  test('móbil: co mapa aberto asoma a folla do listado; estendida, «Mapa» vólvea pregar', async ({ page, lang }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    const toggle = page.locator('[data-map-toggle]');
    await toggle.click();
    const head = page.locator('[data-home-map] [data-peek-toggle]');
    await expect(head).toBeVisible();
    await expect(head).toHaveAttribute('aria-expanded', 'false');
    // «Lista» xusto enriba da folla.
    const [pill, peek] = [(await toggle.boundingBox())!, (await head.boundingBox())!];
    expect(pill.y + pill.height).toBeLessThanOrEqual(peek.y);
    await head.click();
    await expect(head).toHaveAttribute('aria-expanded', 'true');
    await expect(page.locator('.f-peek [data-rail]').first()).toBeVisible();
    await expect(toggle).toHaveText(text(lang, 'facility.publicList.view.map'));
    await expectNoA11yViolations(page);
    // «Atrás» do navegador prega a folla sen saír da portada.
    await page.goBack();
    await expect(head).toHaveAttribute('aria-expanded', 'false');
    await expect(page).toHaveURL(/\/$/);
  });

  test('móbil: carruseis e «Mapa» flotante, sen «Lista / Mapa»; «Buscar» abre a folla de busca', async ({
    page,
    lang,
  }, testInfo) => {
    await page.setViewportSize({ width: 320, height: 640 });
    await page.goto('/');
    await expect(page.locator('[data-rail] furar-facility-card').first()).toBeVisible();
    await expect(page.getByRole('group', { name: text(lang, 'facility.publicList.view.label') })).toHaveCount(0);
    // O botón «Mapa», abaixo no medio da pantalla.
    const toggle = await page.locator('[data-map-toggle]').boundingBox();
    expect(toggle && Math.abs(toggle.x + toggle.width / 2 - 160) < 2 && toggle.y + toggle.height < 640).toBe(true);
    // Sen barra de busca: «Buscar» despois do título, do tamaño de «Mapa».
    await expect(page.getByRole('search')).toBeHidden();
    const open = page.locator('[data-open-search]');
    const [buscar, mapa] = [(await open.boundingBox())!, (await page.locator('[data-map-toggle]').boundingBox())!];
    expect(Math.round(buscar.height)).toBe(Math.round(mapa.height));
    await expectNoHorizontalScroll(page);

    await open.click();
    const sheet = page.getByRole('dialog', { name: text(lang, 'home.search.submit') });
    // A folla vai fixa na pantalla: o contedor do diálogo non ten caixa propia.
    await expect(sheet.locator('.f-dialog')).toBeVisible();
    // «Onde» aberto, co foco no texto (cun dedo, non: a persoa toca o campo); o resto, filas pechadas.
    const field = sheet.locator('.f-ssheet__input');
    if (testInfo.project.use.hasTouch) await expect(field).not.toBeFocused();
    else await expect(field).toBeFocused();
    await expect(field).toHaveAttribute('placeholder', text(lang, 'home.search.where'));
    await expect(sheet.locator('[data-section="when"]')).toHaveAttribute('aria-expanded', 'false');
    await field.fill('p');
    await expect(sheet.locator('.f-ssheet__options button').first()).toBeVisible();
    await expectNoA11yViolations(page);
    // «Cando»: mañá; pasa ao seguinte apartado.
    await field.fill('');
    await sheet.locator('[data-section="when"]').click();
    await sheet.locator('.f-date-field__shortcut').nth(1).click();
    await expect(sheet.locator('[data-section="when"]')).toContainText(text(lang, 'home.search.tomorrow'));
    // «Deporte»: o primeiro.
    await sheet.locator('[data-section="sport"]').click();
    await sheet.getByRole('option').first().click();
    await sheet.locator('[data-search-apply]').click();
    await expect(sheet).toHaveCount(0);
    await expect(page).toHaveURL(/deporte=.*data=|data=.*deporte=/);
    // Cun filtro, a cabeceira (e o seu «Buscar») escóndese: o foco, na pílula co resumo.
    const summary = page.locator('[data-search-summary]');
    await expect(open).toBeHidden();
    await expect(summary).toBeFocused();

    // A pílula volve abrir a folla; Esc pecha sen cambiar nada.
    const url = page.url();
    await summary.click();
    await expect(sheet.locator('.f-dialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(sheet).toHaveCount(0);
    expect(page.url()).toBe(url);
    await expectNoHorizontalScroll(page);
  });
});
