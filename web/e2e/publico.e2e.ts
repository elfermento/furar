// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { randomUUID } from 'node:crypto';
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, test, text, userMenu } from './fixtures';
import { backFromKeycloak, deleteMailsTo, freshEmail, takeMail } from './persoal-axudas';

test.describe('parte pública', () => {
  test('portada: escoller concello, accesible e sen scroll horizontal', async ({ page, lang }) => {
    await page.goto('/');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'picker.title') })).toBeVisible();
    await expect(page.locator('html')).toHaveAttribute('lang', lang);
    await expect(page).toHaveTitle(`${text(lang, 'titles.home')} · ${text(lang, 'app.name')}`);
    const list = page.getByRole('list', { name: text(lang, 'picker.listLabel') });
    // Narón e Ferrol, e os oito concellos da comarca de Verín (db/demo).
    const names = [
      'Narón',
      'Ferrol',
      'Verín',
      'Vilardevós',
      'Riós',
      'Castrelo do Val',
      'Cualedro',
      'Laza',
      'Monterrei',
      'Oímbra',
    ];
    for (const name of names) {
      await expect(list.getByRole('link', { name, exact: true })).toBeVisible();
    }
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
  });

  test('máis contraste (prefers-contrast: more): bordos de 2 px e texto secundario na tinta', async ({ page }) => {
    await page.emulateMedia({ contrast: 'more' });
    await page.goto('/');
    const list = page.locator('ul.f-row-list');
    await expect(list).toBeVisible();
    const styles = await list.evaluate((el) => {
      const root = getComputedStyle(document.documentElement);
      return {
        border: root.getPropertyValue('--f-border').trim(),
        listBorder: getComputedStyle(el).borderTopWidth,
        inkSoft: root.getPropertyValue('--f-ink-soft').trim(),
        ink: root.getPropertyValue('--f-ink').trim(),
      };
    });
    expect(styles.border).toBe('2px');
    expect(styles.listBorder).toBe('2px');
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
    await page.getByRole('button', { name: text(other, `lang.endonym.${other}`) }).click();
    await expect(page.locator('html')).toHaveAttribute('lang', other);
    await expect(page.getByRole('button', { name: text(other, 'session.login') })).toBeVisible();
    await page.reload();
    await expect(page.locator('html')).toHaveAttribute('lang', other);
    await expect(page.getByRole('button', { name: text(other, `lang.endonym.${other}`) })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
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
    // RGPD art. 13 (AP2-02): responsable e DPD saen dos datos legais do concello (ADR-0017,
    // revisión do 2026-11-03: sen FURAR_PRIVACY_*); a demo de Narón tráeos.
    const { legal } = (await (await page.request.get('/api/v1/municipalities/naron')).json()) as {
      legal: { legalEntityName: string; dpoEmail: string };
    };
    await expect(page.getByText(legal.legalEntityName).first()).toBeVisible();
    await expect(page.getByRole('link', { name: legal.dpoEmail }).first()).toBeVisible();
    await expect(page.getByText(text(lang, 'privacy.retention'))).toBeVisible();
    await expectNoA11yViolations(page);
  });

  test('privacidade #persoal (a ligazón do correo da invitación) leva á sección do persoal', async ({ page, lang }) => {
    await page.goto('/naron/privacidade#persoal');
    const staff = page.getByRole('heading', { level: 2, name: text(lang, 'privacy.staff.heading') });
    await expect(staff).toBeFocused();
    await expect(staff).toBeInViewport();
    await expect(staff).toHaveAttribute('id', 'persoal');
  });

  test('a marca do pé, sen «Furar», leva á sección «Software» do aviso legal', async ({ page, lang }) => {
    await page.goto('/naron');
    const brand = page.locator('footer .f-footer__brand a');
    await expect(brand).toHaveText(text(lang, 'app.footer'));
    await expect(page.locator('footer')).not.toContainText('Furar');
    await brand.click();
    await expect(page).toHaveURL(/\/naron\/aviso-legal#software$/);
    const software = page.getByRole('heading', { level: 2, name: text(lang, 'legalNotice.softwareTitle') });
    await expect(software).toHaveAttribute('id', 'software');
    await expect(software).toBeInViewport();
  });

  test('ancho: a barra, o contido e o pé cos mesmos bordos; o texto legal, centrado', async ({ page }) => {
    await page.setViewportSize({ width: 1440, height: 900 });
    const box = async (selector: string) => (await page.locator(selector).first().boundingBox())!;
    await page.goto('/naron/instalacions');
    await expect(page.locator('.f-plist__title')).toBeVisible();
    const bar = await box('.f-topbar__bar');
    const footer = await box('.f-footer__bar');
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
    expect(notices).toContain('Package: @fontsource/atkinson-hyperlegible-next');
    expect(notices).toContain('SIL OPEN FONT LICENSE Version 1.1');
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
      await page.getByRole('button', { name: text(lang, 'session.login') }).click();
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
      // O botón leva o URL da acción; o texto, a ligazón curta (maqueta 55), que redirixe a el.
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
