// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, loginAs, test, text, userMenu } from './fixtures';
import { bearer } from './persoal-axudas';

/**
 * Portada do concello (maqueta 02-portada): barra de cristal, buscador con suxestións e día,
 * tarxetas das instalacións publicadas e «Así de fácil». En gl, es e móbil (320 px), con axe.
 */
test.describe('portada pública do concello', () => {
  test('título, buscador, tarxetas e pasos; accesible e sen scroll lateral', async ({ page, lang, request }) => {
    const published = (await (
      await request.get('/api/v1/municipalities/naron/public/facilities', { headers: { 'Accept-Language': lang } })
    ).json()) as { name: string }[];
    expect(published.length).toBeGreaterThan(0);

    await page.goto('/naron');
    const title = page.getByRole('heading', { level: 1 });
    await expect(title).toHaveText(`${text(lang, 'home.title.line1')} ${text(lang, 'home.title.line2')} Narón`);
    await expect(page.getByRole('search', { name: text(lang, 'home.search.label') })).toBeVisible();
    const cards = page.locator('furar-facility-card');
    await expect(cards).toHaveCount(published.length);
    await expect(cards.first().getByRole('heading', { level: 3 })).toHaveText(published[0].name);
    // Cada tarxeta coa súa portada (foto ou debuxo suxerido), que carga.
    for (const img of await cards.locator('img').all()) {
      await img.scrollIntoViewIfNeeded(); // carga perezosa (no móbil, na tira)
      await expect.poll(() => img.evaluate((el: HTMLImageElement) => el.naturalWidth)).toBeGreaterThan(0);
      await expect(img).not.toHaveAttribute('alt', '');
    }
    const pavilion = cards.filter({ hasText: 'Pavillón de Piñeiros' });
    if ((await pavilion.count()) > 0) {
      await expect(pavilion.locator('img').first()).toHaveAttribute('src', /\/drawings\/sports_hall\//);
    }
    // Cada tarxeta leva á ficha pública.
    await expect(cards.first().getByRole('link').first()).toHaveAttribute(
      'href',
      /^\/naron\/instalacions\/[0-9a-f-]{36}$/,
    );
    // Nin «Reservar» nin «As miñas reservas»: aínda non existen.
    await expect(page.getByRole('heading', { level: 2, name: text(lang, 'home.facilities.title') })).toBeVisible();
    await expect(page.locator('.f-step')).toHaveCount(3);
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
  });

  test('suxestións con teclado e día con «Mañá»', async ({ page, lang }) => {
    await page.goto('/naron');
    const field = page.getByRole('combobox', { name: text(lang, 'home.search.what') });
    await field.fill('pad');
    const listbox = page.getByRole('listbox', { name: text(lang, 'home.search.suggestions') });
    await expect(listbox).toBeVisible();
    await expect(listbox.getByRole('group', { name: text(lang, 'home.search.sports') })).toBeVisible();
    await expect(listbox.getByRole('group', { name: text(lang, 'home.search.facilities') })).toBeVisible();
    await expectNoA11yViolations(page);
    await field.press('ArrowDown');
    await field.press('Enter');
    await expect(field).toHaveValue(/p[aá]del/i);
    await expect(field).toHaveAttribute('aria-expanded', 'false');

    const day = page.getByRole('button', {
      name: `${text(lang, 'home.search.day')}, ${text(lang, 'home.search.today')}`,
    });
    await day.click();
    const dialog = page.getByRole('dialog');
    await expect(dialog).toBeVisible();
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: text(lang, 'home.search.tomorrow') }).click();
    await expect(
      page.getByRole('button', { name: `${text(lang, 'home.search.day')}, ${text(lang, 'home.search.tomorrow')}` }),
    ).toBeFocused();
  });

  test('a barra escóndese ao baixar e volve co foco', async ({ page, lang }) => {
    await page.goto('/naron');
    const bar = page.locator('furar-site-bar header');
    await expect(bar).toBeVisible();
    await expect(page.locator('furar-facility-card').first()).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, 200));
    await page.evaluate(() => window.scrollTo(0, 700));
    await expect(bar).toHaveAttribute('data-hidden', 'true');
    await page
      .locator('furar-site-bar')
      .getByRole('button', { name: text(lang, 'session.login') })
      .focus();
    await expect(bar).not.toHaveAttribute('data-hidden');
    await expect(bar).toBeInViewport();
  });

  test('co persoal, unha soa barra coa xestión e o menú da persoa', async ({ page, lang }) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    const bar = page.locator('furar-site-bar');
    await expect(bar.locator('nav')).toHaveCount(1);
    const manage = page.getByRole('navigation', { name: text(lang, 'app.manageNav') });
    // «Concello» é unha ligazón máis (maqueta 49): sen despregable nin «Máis».
    await expect(manage.locator('button[data-more]')).toHaveCount(0);
    await expect(
      manage.getByRole('link', { name: text(lang, 'nav.municipality'), exact: true }).first(),
    ).toHaveAttribute('href', '/naron/xestion/concello');
    await expect(bar.getByRole('link', { name: /Narón/ }).first()).toHaveAttribute('href', '/naron');
    const trigger = userMenu(page);
    await trigger.click();
    const menu = page.getByRole('menu');
    await expect(menu).toBeVisible();
    await expect(menu.getByRole('menuitem', { name: text(lang, 'session.account') })).toHaveAttribute(
      'href',
      /\/auth\/realms\/furar\/account$/,
    );
    // Na web pública, a entrada á xestión (maqueta 56) e non «Ver a web pública».
    await expect(menu.getByRole('menuitem', { name: text(lang, 'session.manage') })).toBeVisible();
    await expect(menu.getByRole('menuitem', { name: text(lang, 'session.publicSite') })).toHaveCount(0);
    await expect(page.locator('.f-usermenu__panel')).toContainText(text(lang, 'session.role.MANAGER'));
    await expectNoA11yViolations(page);
    await page.keyboard.press('Escape');
    await expect(menu).toBeHidden();
    await expect(trigger).toBeFocused();
    await expectNoHorizontalScroll(page);
  });

  test('listado: o buscador leva a el, os filtros van no URL e o mapa ten a lista como alternativa', async ({
    page,
    lang,
  }) => {
    await page.route(/\/tiles\/\d+\/\d+\/\d+\.png(\?.*)?$/, (route) => route.fulfill({ status: 204 }));
    await page.goto('/naron');
    await page
      .getByRole('search', { name: text(lang, 'home.search.label') })
      .getByRole('button', { name: text(lang, 'home.search.submit') })
      .click();
    await expect(page).toHaveURL(/\/naron\/instalacions$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.publicList.title') })).toBeVisible();
    const cards = page.locator('furar-public-list-card');
    await expect(cards.first()).toBeVisible();
    const total = await cards.count();
    // Un chip de deporte filtra e queda no URL; volver a picalo quítao.
    const filters = page.getByRole('group', { name: text(lang, 'facility.publicList.filters') });
    const chip = filters.getByRole('button').first();
    await chip.click();
    await expect(chip).toHaveAttribute('aria-pressed', 'true');
    await expect(page).toHaveURL(/[?&]deporte=/);
    expect(await cards.count()).toBeLessThanOrEqual(total);
    // As horas libres levan á páxina de reservar con día e hora.
    const time = page.locator('.f-slot-links a').first();
    if ((await time.count()) > 0) {
      await expect(time).toHaveAttribute(
        'href',
        /\/naron\/instalacions\/[0-9a-f-]{36}\/reservar\?data=\d{4}-\d{2}-\d{2}&hora=\d{2}:\d{2}$/,
      );
    }
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await chip.click();
    await expect(page).toHaveURL(/\/naron\/instalacions$/);
    await expect(cards).toHaveCount(total);
    // Mapa: unha marca por instalación con punto; a lista segue a un clic.
    const views = page.getByRole('group', { name: text(lang, 'facility.publicList.view.label') });
    await views.getByRole('button', { name: text(lang, 'facility.publicList.view.map') }).click();
    await expect(page).toHaveURL(/[?&]vista=mapa/);
    await expect(page.locator('.f-plist__map .f-map__marker')).toHaveCount(total);
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await views.getByRole('button', { name: text(lang, 'facility.publicList.view.list') }).click();
    await expect(cards).toHaveCount(total);
  });

  test('movemento reducido: os tres pasos un debaixo doutro', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.goto('/naron');
    const steps = page.locator('.f-step');
    await expect(steps).toHaveCount(3);
    const boxes = await steps.evaluateAll((els) => els.map((e) => e.getBoundingClientRect().top));
    expect(boxes[1]).toBeGreaterThan(boxes[0]);
    expect(boxes[2]).toBeGreaterThan(boxes[1]);
  });

  test('ficha pública: imaxe, usos, servizos, mapa e tarxeta con horario e contacto', async ({ page, lang }) => {
    await page.route(/\/tiles\/\d+\/\d+\/\d+\.png(\?.*)?$/, (route) => route.fulfill({ status: 204 }));
    await page.goto('/naron');
    const card = page.locator('furar-facility-card').filter({ hasText: 'Campo de fútbol 1 de O Alto' });
    await card.getByRole('link').first().click();
    await expect(page).toHaveURL(/\/naron\/instalacions\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { level: 1, name: 'Campo de fútbol 1 de O Alto' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: text(lang, 'facility.public.uses') })).toBeVisible();
    await expect(page.locator('[data-public-uses] li').first()).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: text(lang, 'facility.public.howToGet') })).toBeVisible();
    // O recoñecemento do Mapa base do IGN (o mapa por defecto do despregue), á vista e con ligazón.
    await expect(page.locator('.f-map__attribution a')).toHaveAttribute('href', 'https://www.scne.es');
    await expect(page.locator('.f-map__attribution a')).toContainText('CC BY 4.0 scne.es');
    const side = page.getByRole('complementary', { name: text(lang, 'facility.public.card') });
    await expect(side.getByRole('heading', { name: text(lang, 'facility.public.schedule') })).toBeVisible();
    await expect(side.locator('[data-public-week] dt').first()).toContainText(
      text(lang, 'facility.public.today', { day: '' }).trim().replace(/,$/, ''),
    );
    await expect(side.locator('a[href^="tel:"]')).toBeVisible();
    // Dominio 2: «Reservar» leva á páxina de reservar da instalación.
    await expect(page.locator('[data-public-reserve]')).toHaveAttribute(
      'href',
      /\/naron\/instalacions\/[0-9a-f-]{36}\/reservar$/,
    );
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await page
      .locator('#contido')
      .getByRole('link', { name: text(lang, 'facility.public.back') })
      .click();
    await expect(page).toHaveURL(/\/naron\/instalacions$/);
  });

  test('sen mapa no despregue (FURAR_MAP=off): sen teselas nin Leaflet; lista e enderezo', async ({ page, lang }) => {
    // O despregue di que non hai mapa; calquera tesela pedida faría fallar a proba.
    await page.route('**/api/v1/deployment-settings', (route) =>
      route.fulfill({ json: { map: { enabled: false, attribution: '' } } }),
    );
    const tiles: string[] = [];
    await page.route(/\/tiles\//, (route) => {
      tiles.push(route.request().url());
      return route.fulfill({ status: 404 });
    });
    const leaflet: string[] = [];
    page.on('request', (request) => {
      if (/leaflet/i.test(request.url())) leaflet.push(request.url());
    });
    await page.goto('/naron/instalacions?vista=mapa');
    await expect(page.locator('furar-public-list-card').first()).toBeVisible();
    await expect(page.getByRole('group', { name: text(lang, 'facility.publicList.view.label') })).toHaveCount(0);
    await page.getByRole('link', { name: 'Campo de fútbol 1 de O Alto' }).first().click();
    await expect(page.getByRole('heading', { level: 1, name: 'Campo de fútbol 1 de O Alto' })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: text(lang, 'facility.public.howToGet') })).toHaveCount(0);
    await expect(page.locator('[data-public-address]')).toBeVisible();
    await expect(page.locator('furar-map, .leaflet-container')).toHaveCount(0);
    expect(tiles).toEqual([]);
    expect(leaflet).toEqual([]);
    await expectNoA11yViolations(page);
  });

  test('concello sen instalacións publicadas: exemplos coa etiqueta e sen ligazón (maqueta 46)', async ({
    page,
    lang,
    request,
  }, testInfo) => {
    // Un concello propio de cada proxecto, sen instalacións: créase a primeira vez (despois, 409).
    const slug = `e2e-exemplos-${testInfo.project.name}`;
    const created = await request.post('/api/v1/municipalities', {
      headers: await bearer(request, 'admin-plataforma'),
      data: { slug, name: 'Exemplos', languages: ['gl', 'es'], defaultLanguage: 'gl' },
    });
    expect([201, 409], await created.text()).toContain(created.status());
    await page.goto(`/${slug}`);
    const rail = page.getByRole('list', { name: text(lang, 'home.examples.rail') });
    await expect(rail.locator('furar-example-card')).toHaveCount(4);
    await expect(rail.getByText(text(lang, 'home.examples.badge'), { exact: true })).toHaveCount(4);
    await expect(rail.getByRole('link')).toHaveCount(0);
    await expect(page.getByRole('heading', { level: 1 })).toContainText('Exemplos');
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
  });
});
