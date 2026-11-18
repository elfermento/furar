// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  loginEntry,
  navName,
  openHomeSearch,
  test,
  text,
  userMenu,
} from './fixtures';
import { bearer } from './persoal-axudas';

/**
 * Portada do concello: barra de cristal, buscador con suxestións e día,
 * tarxetas das instalacións publicadas e «Así de fácil». En gl, es e móbil (320 px), con axe.
 */
test.describe('portada pública do concello', () => {
  test('título, buscador, tarxetas e pasos; accesible e sen scroll lateral', async ({
    page,
    lang,
    request,
  }, testInfo) => {
    const published = (await (
      await request.get('/api/v1/municipalities/naron/public/facilities', { headers: { 'Accept-Language': lang } })
    ).json()) as { name: string }[];
    expect(published.length).toBeGreaterThan(0);

    await page.goto('/naron');
    const title = page.getByRole('heading', { level: 1 });
    await expect(title).toHaveText(`${text(lang, 'home.title.line1')} ${text(lang, 'home.title.line2')} Narón (demo)`);
    // «Buscar» despois do nome: no escritorio saca a barra arriba; no móbil abre a folla de busca.
    await expect(page.locator('[data-open-search]')).toBeVisible();
    if (!testInfo.project.name.endsWith('mobile')) {
      await openHomeSearch(page);
      await expect(page.getByRole('search', { name: text(lang, 'home.search.label') })).toBeVisible();
    }
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

  test('suxestións con teclado e día con «Mañá»', async ({ page, lang }, testInfo) => {
    await page.goto('/naron');
    if (testInfo.project.name.endsWith('mobile')) {
      // No móbil, na folla de busca: as suxestións dentro de «Onde»; escoller unha pasa a «Cando».
      await page.locator('[data-open-search]').click();
      const sheet = page.getByRole('dialog', { name: text(lang, 'home.search.submit') });
      const input = sheet.locator('.f-ssheet__input');
      // Cun dedo, sen foco ao abrir (sacaría o teclado): a persoa toca o campo.
      await expect(input).toBeVisible();
      await expect(input).not.toBeFocused();
      await input.fill('pad');
      await expect(sheet.locator('.f-combobox__group-label').first()).toHaveText(text(lang, 'home.search.sports'));
      await expectNoA11yViolations(page);
      await sheet.locator('.f-ssheet__options button').first().click();
      await expect(sheet.locator('[data-section="where"]')).toContainText(/p[aá]del/i);
      await expect(sheet.locator('[data-section="when"]')).toHaveAttribute('aria-expanded', 'true');
      return;
    }
    await openHomeSearch(page);
    const field = page.getByRole('combobox', { name: text(lang, 'home.search.what') });
    // «Buscar» deixa o foco no texto.
    await expect(field).toBeFocused();
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
    await loginEntry(page, lang).focus();
    await expect(bar).not.toHaveAttribute('data-hidden');
    await expect(bar).toBeInViewport();
  });

  test('co persoal, unha soa barra coa xestión e o menú da persoa', async ({ page, lang }) => {
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    const bar = page.locator('furar-site-bar');
    await expect(bar.locator('nav')).toHaveCount(1);
    const manage = page.getByRole('navigation', { name: text(lang, 'app.manageNav') });
    // «Concello» é unha ligazón máis: sen despregable nin «Máis», tamén na barra
    // inferior do móbil (catro apartados e o botón do QR).
    await expect(manage.locator('button[data-more]')).toHaveCount(0);
    await expect(bar.locator('button[data-more]')).toHaveCount(0);
    await expect(manage.getByRole('link', { name: navName(lang, 'nav.municipality') }).first()).toHaveAttribute(
      'href',
      '/naron/xestion/concello',
    );
    // O logotipo leva á portada de todos os concellos (/).
    await expect(bar.getByRole('link', { name: /Narón/ }).first()).toHaveAttribute('href', '/');
    const trigger = userMenu(page);
    await trigger.click();
    const menu = page.getByRole('menu');
    await expect(menu).toBeVisible();
    await expect(menu.getByRole('menuitem', { name: text(lang, 'session.account'), exact: true })).toHaveAttribute(
      'href',
      '/naron/conta',
    );
    // Na web pública, a entrada á xestión.
    await expect(menu.getByRole('menuitem', { name: text(lang, 'session.manage') })).toBeVisible();
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
  }, testInfo) => {
    await page.route(/\/tiles\/\d+\/\d+\/\d+\.png(\?.*)?$/, (route) => route.fulfill({ status: 204 }));
    await page.goto('/naron');
    if (testInfo.project.name.endsWith('mobile')) {
      // No móbil, a folla de busca cun texto leva ao listado con el; sen texto, ao listado enteiro.
      await page.goto('/naron/instalacions');
    } else {
      await openHomeSearch(page);
      await page
        .getByRole('search', { name: text(lang, 'home.search.label') })
        .getByRole('button', { name: text(lang, 'home.search.submit') })
        .click();
    }
    await expect(page).toHaveURL(/\/naron\/instalacions$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.publicList.title') })).toBeVisible();
    const cards = page.locator('furar-facility-card');
    await expect(cards.first()).toBeVisible();
    const total = await cards.count();
    // O primeiro deporte da folla «Filtros» (a mesma no móbil e no escritorio) filtra e queda no
    // URL; volver a marcalo quítao.
    const search = page.getByRole('search', { name: text(lang, 'home.search.label') });
    const toggleFirstSport = async () => {
      await search.getByRole('button', { name: new RegExp(`^${text(lang, 'home.search.filters')}`) }).click();
      const sheet = page.getByRole('dialog', { name: text(lang, 'facility.catalog.filters.title') });
      // «Deporte»: o despregable de sempre; a lista segue aberta ao marcar e Esc pecha só ela.
      await sheet.locator('[data-filter="sport"] .f-select__button').click();
      await page.getByRole('option').first().click();
      await page.keyboard.press('Escape');
      await sheet.locator('[data-filters-apply]').click();
      await expect(sheet).toBeHidden();
    };
    await toggleFirstSport();
    await expect(page).toHaveURL(/[?&]deporte=/);
    expect(await cards.count()).toBeLessThanOrEqual(total);
    // As horas libres levan á páxina de reservar con día, hora e o deporte buscado.
    const time = page.locator('.f-slot-links a').first();
    if ((await time.count()) > 0) {
      await expect(time).toHaveAttribute(
        'href',
        /\/naron\/instalacions\/[0-9a-f-]{36}\/reservar\?data=\d{4}-\d{2}-\d{2}&hora=\d{2}:\d{2}(&deporte=[\w-]+)+$/,
      );
    }
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await toggleFirstSport();
    await expect(page).toHaveURL(/\/naron\/instalacions$/);
    await expect(cards).toHaveCount(total);
    // «Mapa» flotante, coma nas portadas: no escritorio ao lado da grella; no móbil, a toda a
    // pantalla coa folla do listado asomando. Non vai no URL; «Lista» volve.
    const toggle = page.locator('[data-map-toggle]');
    await toggle.click();
    await expect(page).not.toHaveURL(/vista=/);
    await expect(page.locator('.f-plist__map, [data-home-map]').locator('.f-map__marker').first()).toBeVisible();
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await toggle.click();
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

  test('móbil: «Así de fácil» coa mesma baralla fixa e animada, sen que a páxina salte ao cambiar de paso', async ({
    page,
  }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/naron');
    const section = page.locator('.f-steps');
    const steps = page.locator('.f-step');
    await expect(steps).toHaveCount(3);
    // As seccións de enriba (instalacións, partidas) chegan da API: mídese coa páxina xa asentada.
    await page.waitForLoadState('networkidle');
    await expect(section).not.toHaveAttribute('data-deck', 'static');
    // As tres na mesma cela (o alto da máis alta) e enteiras.
    const layout = await steps.evaluateAll((els) =>
      els.map((e) => ({
        top: (e as HTMLElement).offsetTop,
        height: (e as HTMLElement).offsetHeight,
        whole: e.scrollHeight <= e.clientHeight + 1,
      })),
    );
    expect(new Set(layout.map((step) => step.top)).size).toBe(1);
    expect(new Set(layout.map((step) => step.height)).size).toBe(1);
    expect(layout.every((step) => step.whole)).toBe(true);
    // Percorrer a sección: a páxina non cambia de alto e ao final está á vista o paso 3 co seu botón.
    const height = () => page.evaluate(() => document.documentElement.scrollHeight);
    const before = await height();
    const travel = await section.evaluate((el) => ({
      top: el.getBoundingClientRect().top + window.scrollY,
      length: (el as HTMLElement).offsetHeight - window.innerHeight,
    }));
    for (const part of [0, 0.25, 0.5, 0.75, 1]) {
      await page.evaluate((y) => window.scrollTo(0, y), travel.top + travel.length * part);
      expect(await height()).toBe(before);
    }
    // Un scroll brusco non fai pasar os pasos de golpe: van dun en dun, co seu tempo.
    expect(await section.getAttribute('data-active')).not.toBe('2');
    await expect(section).toHaveAttribute('data-active', '2');
    const button = page.locator('.f-step[data-step="3"] .f-shot__button');
    await expect
      .poll(async () => {
        const box = await button.boundingBox();
        const nav = await page.locator('.f-bottomnav').boundingBox();
        return !!box && box.x >= 0 && box.x + box.width <= 390 && box.y + box.height <= (nav?.y ?? 844);
      })
      .toBe(true);
    await expectNoHorizontalScroll(page);
  });

  test('móbil a 320, 360 e 390 px, tamén co texto moi grande: cada paso enteiro e o seu botón á vista', async ({
    page,
  }) => {
    for (const textSize of ['normal', 'x-large'] as const) {
      await page.addInitScript((value) => {
        localStorage.setItem('furar.display', JSON.stringify({ origin: 'device', textSize: value }));
      }, textSize);
      for (const [width, height] of [
        [320, 568],
        [360, 640],
        [390, 844],
      ] as const) {
        await page.setViewportSize({ width, height });
        await page.goto('/naron');
        await page.waitForLoadState('networkidle');
        const steps = await page.locator('.f-step').evaluateAll((els) =>
          els.map((e) => {
            const shot = e.querySelector<HTMLElement>('.f-shot')!;
            const button = e.querySelector<HTMLElement>('.f-shot__button[data-primary]');
            const text = e.querySelector<HTMLElement>('.f-shot__ticket > div');
            return {
              whole: e.scrollHeight <= e.clientHeight + 1 && shot.scrollHeight <= shot.clientHeight + 1,
              // O botón do paso 3 dentro da súa captura, e o texto do tique sen esmagar.
              button: !button || button.offsetTop + button.offsetHeight <= shot.scrollHeight,
              ticket: !text || text.offsetWidth >= 100,
            };
          }),
        );
        const where = `${width} px, texto ${textSize}`;
        expect(steps, where).toEqual(Array(3).fill({ whole: true, button: true, ticket: true }));
        // Fixa só se cabe: o seu contido non sae da zona fixa.
        const pinned = await page
          .locator('.f-steps')
          .evaluate(
            (el) =>
              el.getAttribute('data-deck') !== 'static' &&
              getComputedStyle(el.querySelector('.f-steps__pin')!).position === 'sticky',
          );
        if (pinned) {
          const fits = await page.locator('.f-steps__pin').evaluate((pin) => {
            const style = getComputedStyle(pin);
            const title = pin.querySelector<HTMLElement>('.f-steps__title')!;
            const deck = pin.querySelector<HTMLElement>('.f-steps__deck')!;
            const room = pin.clientHeight - parseFloat(style.paddingTop) - parseFloat(style.paddingBottom);
            return (
              title.offsetHeight + parseFloat(getComputedStyle(title).marginBottom) + deck.offsetHeight <= room + 1
            );
          });
          expect(fits, where).toBe(true);
        }
        await expectNoHorizontalScroll(page);
      }
    }
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
    // «Reservar» leva á páxina de reservar da instalación.
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
    await expect(page.locator('furar-facility-card').first()).toBeVisible();
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

  test('concello sen instalacións publicadas: exemplos coa etiqueta e sen ligazón', async ({
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
