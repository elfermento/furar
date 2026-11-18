// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { randomUUID } from 'node:crypto';
import {
  chooseLanguage,
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginEntry,
  openHomeSearch,
  startLogin,
  test,
  text,
  userMenu,
} from './fixtures';
import { backFromKeycloak, deleteMailsTo, freshEmail, takeMail } from './persoal-axudas';

test.describe('parte pública', () => {
  test('portada común: as instalacións de todos os concellos, accesible e sen scroll horizontal', async ({
    page,
    lang,
  }) => {
    await page.goto('/');
    // Con varios concellos (db/demo) xa non se escolle concello: «Reserva a túa pista» e o buscador.
    await expect(page.getByRole('heading', { level: 1 })).toHaveText(
      `${text(lang, 'home.title.line1')} ${text(lang, 'home.showcase.place')}`,
    );
    await expect(page.locator('html')).toHaveAttribute('lang', lang);
    await expect(page).toHaveTitle(`${text(lang, 'titles.home')} · ${text(lang, 'app.name')}`);
    // No escritorio, a barra sae arriba ao pulsar «Buscar» despois do título, co foco no texto, con
    // «Filtros» (tipo e deportes) ao final; no móbil, «Buscar» abre a folla (portada-comun.e2e.ts).
    const search = page.getByRole('search');
    await expect(search).toBeHidden();
    if ((page.viewportSize()?.width ?? 1280) >= 640) {
      await openHomeSearch(page);
      await expect(search.getByRole('combobox')).toBeFocused();
      await expect(search.getByRole('combobox')).toHaveAttribute('placeholder', text(lang, 'home.search.whatCatalog'));
      await expect(search.locator('furar-multi-select')).toHaveCount(0);
      await expect(search.getByRole('button', { name: text(lang, 'home.search.filters') })).toBeVisible();
      await expect(page.getByRole('listbox')).toHaveCount(0);
    }
    // Sen filtros, un carrusel por concello co seu nome; cada tarxeta entra no seu concello.
    const rails = page.locator('[data-rail]');
    await expect(rails.first()).toBeVisible();
    expect(await rails.count()).toBeGreaterThan(1);
    const naron = page.locator('[data-rail="naron"]');
    await expect(naron.getByRole('heading', { level: 3, name: 'Narón (demo)', exact: true })).toBeVisible();
    await expect(naron.locator('furar-facility-card').first().getByRole('link').first()).toHaveAttribute(
      'href',
      /^\/naron\/instalacions\/[0-9a-f-]+$/,
    );
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
  });

  test('máis contraste (prefers-contrast: more): bordos de 2 px e texto secundario na tinta', async ({ page }) => {
    await page.emulateMedia({ contrast: 'more' });
    // O listado leva a barra en todos os anchos.
    await page.goto('/naron/instalacions');
    const search = page.locator('form.f-search');
    await expect(search).toBeVisible();
    const styles = await search.evaluate((el) => {
      const root = getComputedStyle(document.documentElement);
      return {
        border: root.getPropertyValue('--f-border').trim(),
        searchBorder: getComputedStyle(el).borderTopWidth,
        inkSoft: root.getPropertyValue('--f-ink-soft').trim(),
        ink: root.getPropertyValue('--f-ink').trim(),
      };
    });
    expect(styles.border).toBe('2px');
    expect(styles.searchBorder).toBe('2px');
    expect(styles.inkSoft).toBe(styles.ink);
  });

  for (const slug of ['naron', 'ferrol', 'verin']) {
    test(`concello ${slug}: tema propio antes de pintar e accesible`, async ({ page, request }) => {
      const theme = (await (await request.get(`/api/v1/municipalities/${slug}/theme`)).json()) as { primary: string };
      await page.goto(`/${slug}`);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      const primary = await page.evaluate(() => document.documentElement.style.getPropertyValue('--furar-primary'));
      expect(primary).toBe(theme.primary);
      await expectNoA11yViolations(page);
      await expectNoHorizontalScroll(page);
    });
  }

  test('ruta descoñecida e concello inexistente: páxina «non existe» sen cambiar o URL', async ({ page, lang }) => {
    await page.goto('/naron/non-existe');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'errorPage.404.title') })).toBeVisible();
    await expect(page).toHaveURL(/\/naron\/non-existe$/);
    await expectNoA11yViolations(page);
    await page.goto('/non-existe');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'errorPage.404.title') })).toBeVisible();
    await expect(page).toHaveURL(/\/non-existe$/);
  });

  test('cambio de idioma: textos, <html lang> e lembrado ao recargar', async ({ page, lang }) => {
    const other = lang === 'gl' ? 'es' : 'gl';
    await page.goto('/naron');
    await chooseLanguage(page, lang, other);
    await expect(page.locator('html')).toHaveAttribute('lang', other);
    await expect(loginEntry(page, other)).toBeVisible();
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('lang', other);
    // O idioma vai no menú da persoa en todos os anchos: o escollido, marcado e co seu `lang`; a
    // ruta non cambia e Esc pecha o menú.
    await expect(page.locator('furar-site-bar furar-language-switcher')).toHaveCount(0);
    await loginEntry(page, other).click();
    await page.locator('[data-item="language"]').click();
    const chosen = page.getByRole('menuitemradio', { name: text(other, `lang.endonym.${other}`) });
    await expect(chosen).toHaveAttribute('aria-checked', 'true');
    await expect(chosen).toHaveAttribute('lang', other);
    await expect(page.getByRole('menuitemradio', { name: text(lang, `lang.endonym.${lang}`) })).toHaveAttribute(
      'lang',
      lang,
    );
    await page.keyboard.press('Escape');
    await expect(chosen).toBeHidden();
    await expect(loginEntry(page, other)).toBeFocused();
    await expect(page).toHaveURL(/\/naron$/);
  });

  test('teclado: o primeiro Tab é «Saltar ao contido» e leva o foco ao contido', async ({ page, lang }) => {
    await page.goto('/naron');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    await page.keyboard.press('Tab');
    const skip = page.getByRole('link', { name: text(lang, 'app.skipToContent') });
    await expect(skip).toBeFocused();
    await expect(skip).toBeInViewport();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('heading', { level: 1 })).toBeFocused();
  });

  test('pé: declaración de accesibilidade e privacidade do concello, accesibles', async ({ page, lang }) => {
    await page.goto('/naron');
    const footer = page.getByRole('navigation', { name: text(lang, 'footer.nav') });
    await footer.getByRole('link', { name: text(lang, 'footer.accessibility') }).click();
    await expect(page).toHaveURL(/\/naron\/accesibilidade$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'accessibility.title') })).toBeVisible();
    await expect(page.getByText(text(lang, 'accessibility.status'))).toBeVisible();
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await footer.getByRole('link', { name: text(lang, 'footer.privacy') }).click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'privacy.title') })).toBeVisible();
    // RGPD art. 13: responsable e DPD saen dos datos legais do concello (ADR-0017,
    // revisión do 2026-11-03: sen FURAR_PRIVACY_*); a demo de Narón tráeos.
    const { legal } = (await (await page.request.get('/api/v1/municipalities/naron')).json()) as {
      legal: { legalEntityName: string; dpoEmail: string };
    };
    await expect(page.getByText(legal.legalEntityName).first()).toBeVisible();
    await expect(page.getByRole('link', { name: legal.dpoEmail }).first()).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: text(lang, 'privacy.retentionTitle') })).toBeVisible();
    await expect(page.getByText(text(lang, 'privacy.residents.minors'))).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('pé: «Cookies» leva á súa sección de Privacidade, sen banner', async ({ page, lang }) => {
    await page.goto('/naron');
    const footer = page.getByRole('navigation', { name: text(lang, 'footer.nav') });
    await footer.getByRole('link', { name: text(lang, 'footer.cookies') }).click();
    await expect(page).toHaveURL(/\/naron\/privacidade#cookies$/);
    const cookies = page.getByRole('heading', { level: 2, name: text(lang, 'privacy.cookies.title') });
    await expect(cookies).toHaveAttribute('id', 'cookies');
    await expect(cookies).toBeInViewport();
    await expect(page.getByText(text(lang, 'privacy.cookies.none'))).toBeVisible();
  });

  test('privacidade #persoal (a ligazón do correo da invitación) leva á sección do persoal', async ({ page, lang }) => {
    await page.goto('/naron/privacidade#persoal');
    const staff = page.getByRole('heading', { level: 2, name: text(lang, 'privacy.staff.heading') });
    await expect(staff).toBeFocused();
    await expect(staff).toBeInViewport();
    await expect(staff).toHaveAttribute('id', 'persoal');
  });

  test('o pé, sen «Furar»: o nome do servizo como texto e «Código e licenzas» á sección «Software» do aviso legal', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    // O nome do servizo, como texto: xa non é ligazón.
    await expect(page.locator('footer .f-footer__brand > span')).toHaveText(text(lang, 'app.footer').split(' · '));
    await expect(page.locator('footer .f-footer__brand a')).toHaveCount(0);
    await expect(page.locator('footer')).not.toContainText('Furar');
    const software = page.getByRole('navigation', { name: text(lang, 'footer.nav') }).getByRole('link', {
      name: text(lang, 'footer.software'),
    });
    await expect(software).toHaveAttribute('href', '/naron/aviso-legal#software');
    await software.click();
    await expect(page).toHaveURL(/\/naron\/aviso-legal#software$/);
    const heading = page.getByRole('heading', { level: 2, name: text(lang, 'legalNotice.softwareTitle') });
    await expect(heading).toHaveAttribute('id', 'software');
    await expect(heading).toBeInViewport();
    // A sección enlaza o código, a licenza e as licenzas de terceiros (xa non van soltas no pé).
    for (const [key, href] of [
      ['legalNotice.softwareSource', '/codigo-fonte'],
      ['legalNotice.softwareLicense', '/LICENSE.txt'],
      ['legalNotice.softwareThirdParty', '/3rdpartylicenses.txt'],
    ]) {
      await expect(page.getByRole('link', { name: text(lang, key), exact: true })).toHaveAttribute('href', href);
    }
  });

  test('ancho: a barra, o contido e o pé cos mesmos bordos; o texto legal, centrado', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    const box = async (selector: string) => (await page.locator(selector).first().boundingBox())!;
    await page.goto('/naron/instalacions');
    await expect(page.locator('.f-plist__title')).toBeVisible();
    const bar = await box('.f-topbar__bar');
    // O pé é unha franxa a todo o ancho; o seu contido (a liña de ligazóns), cos bordos da barra.
    const footer = await box('footer nav');
    const list = await box('.f-plist');
    // Un só contedor (docs/deseno.md §7): o listado vai de bordo a bordo da barra superior.
    for (const edge of [list, footer]) {
      expect(Math.abs(edge.x - bar.x)).toBeLessThanOrEqual(1);
      expect(Math.abs(edge.x + edge.width - (bar.x + bar.width))).toBeLessThanOrEqual(1);
    }
    await page.goto('/naron/aviso-legal');
    const article = await box('main article');
    expect(article.width).toBeLessThan(bar.width);
    expect(Math.abs(article.x - bar.x - (bar.x + bar.width - article.x - article.width))).toBeLessThanOrEqual(1);
  });

  test('pé: enteiro na portada e nas páxinas legais; nas interiores, unha liña coas ligazóns legais', async ({
    page,
    lang,
  }) => {
    const footer = page.getByRole('navigation', { name: text(lang, 'footer.nav') });
    await page.goto('/naron');
    await expect(page.locator('footer[data-variant="full"] .f-footer__brand')).toBeVisible();
    await expect(footer.getByRole('link', { name: text(lang, 'footer.cookies') })).toBeVisible();
    await page.goto('/naron/accesibilidade');
    await expect(page.locator('footer')).toHaveAttribute('data-variant', 'full');
    await page.goto('/naron/instalacions');
    await expect(page.locator('footer')).toHaveAttribute('data-variant', 'line');
    await expect(page.locator('footer .f-footer__brand')).toHaveCount(0);
    for (const key of ['footer.legalNotice', 'footer.privacy', 'footer.cookies', 'footer.accessibility']) {
      await expect(footer.getByRole('link', { name: text(lang, key) })).toBeVisible();
    }
    // No móbil, enriba da barra inferior; cada ligazón cos 44 px táctiles e nunha liña.
    await page.setViewportSize({ width: 320, height: 640 });
    for (const link of await footer.getByRole('link').all()) {
      const box = (await link.boundingBox())!;
      expect(box.height).toBeGreaterThanOrEqual(43);
      expect(box.height).toBeLessThan(60);
    }
    // A lista segue cargando e a páxina crece: baixar ao final e medir ata que estea quieta.
    await expect(async () => {
      await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));
      const last = (await footer.boundingBox())!;
      const nav = (await page.locator('nav.f-bottomnav').boundingBox())!;
      expect(last.y + last.height).toBeLessThanOrEqual(nav.y);
    }).toPass();
    await expectNoHorizontalScroll(page);
  });

  test('avisos de licenza e código fonte (EUPL art. 5, OFL-1.1 §2) servidos pola imaxe', async ({ page, request }) => {
    await page.goto('/');
    await expect(page.locator('footer nav a[href="/codigo-fonte"]')).toBeVisible();
    const source = await request.get('/codigo-fonte', { maxRedirects: 0 });
    expect(source.status()).toBe(302);
    expect(source.headers()['location']).toMatch(/^https:\/\//);
    const license = await request.get('/LICENSE.txt');
    expect(license.ok()).toBe(true);
    expect(await license.text()).toContain('EUROPEAN UNION PUBLIC LICENCE v. 1.2');
    const notices = await (await request.get('/3rdpartylicenses.txt')).text();
    expect(notices).toContain('Package: @angular/core');
    expect(notices).toContain('Package: inter-ui');
    expect(notices).toContain('SIL OPEN FONT LICENSE Version 1.1');
  });

  test('letra: Inter servida desde Furar, co cero con barra e o ollo axustado, na web e en Keycloak', async ({
    page,
    lang,
  }) => {
    const bodyFont = () =>
      page.evaluate(async () => {
        await document.fonts.ready;
        const style = getComputedStyle(document.body);
        const loaded = [...document.fonts].filter(
          (f) => f.family.replace(/["']/g, '') === 'Inter' && f.status === 'loaded',
        );
        return {
          family: style.fontFamily,
          features: style.fontFeatureSettings,
          adjust: style.fontSizeAdjust,
          weights: loaded.map((f) => f.weight),
        };
      });
    await page.goto('/naron');
    await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
    const web = await bodyFont();
    expect(web.family).toMatch(/^"?Inter"?,/);
    expect(web.features).toMatch(/"zero"( 1)?/);
    expect(web.adjust).toMatch(/0\.5$/);
    // Na portada, o texto (400) e os títulos (700); o 600 dos rótulos chega coas follas dos formularios.
    expect(web.weights).toEqual(expect.arrayContaining(['400', '700']));

    await startLogin(page, lang);
    await page.waitForURL(/\/auth\/realms\/furar\//);
    const keycloak = await bodyFont();
    expect(keycloak.family).toMatch(/^"?Inter"?,/);
    expect(keycloak.features).toMatch(/"zero"( 1)?/);
    expect(keycloak.adjust).toMatch(/0\.5$/);
    expect(keycloak.weights).toEqual(expect.arrayContaining(['400', '700']));
  });

  test('PWA básica: manifesto e service worker sen datos da API', async ({ request }) => {
    const manifest = await request.get('/manifest.json');
    expect(manifest.ok()).toBe(true);
    expect((await manifest.json()) as { lang: string }).toMatchObject({ lang: 'gl', display: 'standalone' });
    const ngsw = (await (await request.get('/ngsw.json')).json()) as {
      dataGroups?: unknown[];
      navigationUrls: { regex: string }[];
    };
    expect(ngsw.dataGroups ?? []).toEqual([]);
    expect(JSON.stringify(ngsw.navigationUrls)).toContain('auth');
  });

  test('crear a conta e verificar o correo noutra lapela: as dúas lapelas quedan coa sesión iniciada', async ({
    page,
    context,
    lang,
    request,
  }) => {
    const email = freshEmail('e2e-verificar');
    const password = `E2e-${randomUUID()}`;
    try {
      await page.goto('/naron');
      await startLogin(page, lang);
      await page.waitForURL(/\/auth\/realms\/furar\//);
      await page.locator('#kc-registration a').click();
      const form = page.locator('#kc-register-form');
      await form.locator('#email').fill(email);
      await form.locator('#firstName').fill('Veciña');
      await form.locator('#lastName').fill('Proba');
      await form.locator('#password').fill(password);
      await form.locator('#password-confirm').fill(password);
      await form.locator('[type="submit"]').click();
      await expect(page.locator('#furar-verify-email-sent')).toBeVisible();

      // A ligazón do correo, noutra lapela (sen o sessionStorage desta, coma desde o correo web).
      const mail = await takeMail(request, email, /^(Verifica o teu correo|Verifica tu correo)$/);
      // O botón leva o URL da acción; o texto, a ligazón curta, que redirixe a el.
      const button = /href="(https?:\/\/[^"]+\/auth\/realms\/furar\/login-actions\/action-token\?[^"]+)"/.exec(
        mail.HTML,
      );
      expect(button, 'botón coa ligazón de verificación').not.toBeNull();
      const short =
        /https?:\/\/\S+\/auth\/realms\/furar\/l\/[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}-[2-9A-HJKMNP-Z]{4}/.exec(
          mail.Text,
        );
      expect(short, 'ligazón curta no correo').not.toBeNull();
      expect(mail.HTML, 'ligazón curta baixo o botón').toContain(`>${short![0]}</a>`);
      const shortUrl = new URL(short![0]);
      const redirect = await request.get(shortUrl.pathname, { maxRedirects: 0 });
      expect(redirect.status(), 'a ligazón curta redirixe').toBe(302);
      const action = redirect.headers()['location'] ?? '';
      expect(action, 'redirixe ao URL do botón').toBe(button![1].replaceAll('&amp;', '&'));
      const link = new URL(action);
      const other = await context.newPage();
      await other.goto(link.pathname + link.search);
      // Keycloak remata o acceso nesa lapela; a web non ten alí o state, así que entra en silencio.
      await backFromKeycloak(other);
      await expect(userMenu(other)).toBeVisible();

      // A lapela do rexistro ve a sesión, Keycloak devólvea con authentication_expired e a web
      // entra en silencio e volve á páxina de onde saíu.
      await backFromKeycloak(page);
      await expect(userMenu(page)).toBeVisible();
      await expect(page).toHaveURL(/\/naron$/);
      await other.close();
    } finally {
      await deleteMailsTo(request, email);
    }
  });
});
