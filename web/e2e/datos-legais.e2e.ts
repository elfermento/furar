// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  openSectionTab,
  resetLegalPages,
  test,
  text,
} from './fixtures';

/**
 * Concello › Legal (ADR-0017 punto 8, maquetas 33 e 49): o xestor publica as páxinas legais nos
 * catro modos e a web pública pinta cada unha segundo o seu. Os datos que usan os textos legais
 * van enriba (o aviso dos que faltan, en vivo); cada páxina é unha subpestana co seu URL. Páxina
 * desta web cos datos do formulario e a súa vista previa; páxina propia con enderezo https (o erro
 * marca a pestana e o foco vai ao campo) e o pé leva a ela; texto propio co editor, que arranca co
 * texto de Furar, e un `<script>` pegado non se executa nin no editor nin na web; «Sen páxina»
 * avisa, garda e o pé xa non a liga. Ao rematar, todo volve ao de Furar.
 */
test.describe.configure({ mode: 'serial' });

test.describe('datos legais do concello', () => {
  test.beforeEach(async ({ request }) => resetLegalPages(request));
  test.afterAll(async ({ request }) => resetLegalPages(request));

  test('o xestor publica as páxinas legais nos tres modos', async ({ page, lang }) => {
    const dialogs: string[] = [];
    page.on('dialog', (dialog) => {
      dialogs.push(dialog.message());
      void dialog.dismiss();
    });
    const tab = (page_: string) => page.locator(`a[data-legal-tab="${page_}"]`);
    const mode = (value: string) => page.locator(`[data-legal-panel] [data-mode="${value}"]`);
    const save = page.getByRole('button', { name: text(lang, 'common.save'), exact: true });

    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'legal');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/legal\/privacidade$/);
    const required = page.getByRole('region', { name: text(lang, 'settings.legal.required.title') });
    await expect(required.locator('#settings-legal-nif')).toBeVisible();
    const legal = page.getByRole('region', { name: text(lang, 'settings.legal.pages') });
    await expect(legal.getByRole('navigation', { name: text(lang, 'settings.legal.pages') })).toBeVisible();
    await expect(tab('privacy')).toHaveAttribute('aria-current', 'page');
    await expect(legal.getByRole('radio', { name: text(lang, 'settings.legal.mode.FURAR') })).toBeChecked();
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // 1. Páxina de Furar (Aviso legal): os seus datos e «Ver a páxina» cos do formulario.
    // Os datos de enriba: se falta algún, un aviso dío en vivo e a pestana «Legal» leva un triángulo.
    const legalTab = page.locator('a[data-section-tab="legal"]');
    await page.locator('#settings-legal-nif').fill('');
    await expect(page.locator('[data-legal-missing]')).toContainText(text(lang, 'settings.legal.missingName.nif'));
    await expect(legalTab.locator('[data-legal-warning]')).toBeVisible();
    await page.locator('#settings-legal-nif').fill('P1505400J');
    await page.locator('#settings-legal-address').fill('Praza de Galicia, 1. 15570 Narón');
    await page.locator('#settings-legal-generalEmail').fill('concello@naron.example');
    await tab('legalNotice').click();
    await expect(page).toHaveURL(/\/concello\/legal\/aviso-legal$/);
    await expect(tab('legalNotice')).toBeFocused();
    await page.locator('[data-legal-view]').click();
    const preview = page.getByRole('dialog');
    await expect(preview).toContainText(text(lang, 'legalNotice.nif', { value: 'P1505400J' }));
    // As obrigas do persoal van no texto de Furar do aviso legal (sen nada que aceptar)
    await expect(preview.getByRole('heading', { level: 2, name: text(lang, 'legalNotice.staffTitle') })).toBeVisible();
    await expect(preview.getByRole('link', { name: 'concello@naron.example' })).toHaveAttribute(
      'href',
      'mailto:concello@naron.example',
    );
    // A capa entra con transición: axe mediría o contraste a medio camiño.
    await page.waitForFunction(() =>
      document.getAnimations().every((a) => a.playState !== 'running' || a.effect?.getTiming().iterations === Infinity),
    );
    await expectNoA11yViolations(page);
    await page.keyboard.press('Escape');
    await expect(preview).toHaveCount(0);

    // 2. Páxina propia (Privacidade): sen enderezo non garda; o erro marca a pestana e o foco vai ao campo.
    await tab('privacy').click();
    await mode('URL').click();
    await save.click();
    await expect(page.locator('#settings-legal-privacyUrl')).toBeFocused();
    await expect(tab('privacy')).toHaveAccessibleName(new RegExp(text(lang, 'settings.legal.withErrors').trim()));
    await expect(page.locator('[data-save-status]')).toHaveText(
      text(lang, 'settings.legal.errorsIn', { pages: text(lang, 'settings.legal.tab.privacy') }),
    );
    await page.locator('#settings-legal-privacyUrl').fill('http://naron.example/privacidade');
    await expect(page.locator('#settings-legal-privacyUrl-error')).toHaveText(text(lang, 'errors.legal.https'));
    await page.locator('#settings-legal-privacyUrl').fill('https://naron.example/privacidade');

    // 3. Texto propio (Aviso legal): arranca co texto de Furar nos dous idiomas.
    await tab('legalNotice').click();
    await mode('TEXT').click();
    const area = page.locator('#settings-legal-legalNotice-text-gl');
    await expect(area.getByRole('heading', { level: 2, name: text('gl', 'legalNotice.ownerTitle') })).toBeVisible();
    await expect(area).toContainText('NIF: P1505400J');
    await expect(page.locator('[data-legal-panel] [role="toolbar"]')).toHaveAttribute(
      'aria-label',
      text(lang, 'ui.richText.toolbar'),
    );
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // Pegar: só o texto. O <script> queda coma texto e non se executa.
    // Texto novo desde cero: o cursor queda nun sitio coñecido.
    await area.click();
    await page.keyboard.press('ControlOrMeta+a');
    await page.keyboard.press('Delete');
    await expect(area).toHaveText('');
    await area.evaluate((node) => {
      const data = new DataTransfer();
      data.setData(
        'text/html',
        '<p>Pegado</p><script>window.__pwned = true</script><img src="x" onerror="window.__pwned = true">',
      );
      data.setData('text/plain', 'Texto pegado <script>window.__pwned = true</script>');
      node.dispatchEvent(new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true }));
    });
    await expect(area).toContainText('Texto pegado <script>window.__pwned = true</script>');
    await expect(area.locator('script, img')).toHaveCount(0);
    // Barra: título co botón «T» grande; ligazón co atallo Ctrl/⌘+K e o campo baixo a barra.
    await page.keyboard.press('Enter');
    await page.locator('[data-legal-panel] [data-cmd="h2"]').click();
    await page.keyboard.type('Contacto');
    await expect(area.getByRole('heading', { level: 2, name: 'Contacto' })).toBeVisible();
    await page.keyboard.press('Enter');
    await page.keyboard.press('ControlOrMeta+k');
    const href = page.locator('#settings-legal-legalNotice-text-gl-href');
    await expect(href).toBeFocused();
    await href.fill('javascript:alert(1)');
    await href.press('Enter');
    await expect(page.locator('#settings-legal-legalNotice-text-gl-href-error')).toHaveText(
      text(lang, 'ui.richText.linkInvalid'),
    );
    await href.fill('https://sede.naron.example');
    await href.press('Enter');
    await expect(area.getByRole('link', { name: 'https://sede.naron.example' })).toBeVisible();

    await save.click();
    await expect(page.getByText(text(lang, 'settings.saved'))).toBeVisible();

    // 4. Sen páxina (Accesibilidade): avisa da obriga legal, deixa gardar e a pestana o marca.
    await tab('accessibility').click();
    await mode('NONE').click();
    await expect(page.locator('[data-legal-none]')).toContainText(text(lang, 'settings.legal.none.accessibility'));
    await expect(legalTab.locator('[data-legal-warning]')).toBeVisible();
    await expectNoA11yViolations(page);
    // O aviso «Gardado» do paso 3 aínda pode estar á vista: agárdase a resposta deste.
    const stored = page.waitForResponse(
      (r) => r.request().method() === 'PUT' && /\/municipalities\/naron$/.test(r.url()) && r.ok(),
    );
    await save.click();
    await stored;
    await expect(page.getByText(text(lang, 'settings.saved')).last()).toBeVisible();

    // O enderezo de antes (`/xestion#aviso-legal`) leva á súa pestana.
    await page.goto('/naron/xestion#aviso-legal');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/legal\/aviso-legal/);

    // 5. Web pública: o texto propio co estilo de Furar, sen nada executable.
    await page.goto('/naron/aviso-legal');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'legalNotice.title') })).toBeVisible();
    // A web en castelán non ten texto propio en castelán editado aquí: pinta o castelán de Furar.
    const body = page.locator('[data-legal-body]');
    if (lang === 'gl') {
      await expect(body).toContainText('Texto pegado <script>window.__pwned = true</script>');
      await expect(body.getByRole('link', { name: 'https://sede.naron.example' })).toHaveAttribute(
        'rel',
        'noopener noreferrer',
      );
    } else {
      await expect(body).toContainText(text('es', 'legalNotice.software'));
    }
    await expect(page.locator('main script, main img')).toHaveCount(0);
    expect(await page.evaluate(() => (window as unknown as { __pwned?: boolean }).__pwned)).toBeUndefined();
    expect(dialogs).toEqual([]);
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // 6. Páxina propia: o pé leva á do concello e a de Furar deixa a ligazón; sen páxina, sen ligazón.
    const footer = page.getByRole('navigation', { name: text(lang, 'footer.nav') });
    await expect(footer.getByRole('link', { name: new RegExp(text(lang, 'footer.accessibility')) })).toHaveCount(0);
    await page.goto('/naron/accesibilidade');
    await expect(page.locator('[data-legal-none]')).toHaveText(text(lang, 'legal.none'));
    await expect(footer.getByRole('link', { name: new RegExp(text(lang, 'footer.privacy')) })).toHaveAttribute(
      'href',
      'https://naron.example/privacidade',
    );
    await page.goto('/naron/privacidade');
    await expect(page.locator('article a[href="https://naron.example/privacidade"]')).toBeVisible();
    await expectNoA11yViolations(page);
  });
});
