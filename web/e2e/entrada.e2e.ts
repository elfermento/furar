// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, loginAs, logout, openSectionTab, test, text, userMenu } from './fixtures';

/**
 * A entrada despois de iniciar sesión e a entrada á xestión no menú da persoa (maqueta 56): a
 * plataforma sen ningún concello vai á alta; a xestión dun concello con pasos pendentes de «Para
 * abrir as reservas», á pestana «Xeral»; sen pendentes, volve onde estaba. No menú, primeiro, a
 * xestión: «Novo concello» ou «Concellos ›» (a plataforma) e «Xestión do concello» (Xestión e
 * Recepción, fóra da xestión). Na portada, os pendentes pregados para quen configura o concello.
 * Os concellos de demo non teñen pasos pendentes: as probas quitan o NIF e despois volven poñelo.
 */
test.describe('entrada e menú da xestión', () => {
  test('primeira entrada da plataforma: sen ningún concello, directo a «Novo concello»; tamén no menú', async ({
    page,
    lang,
  }) => {
    // Unha instalación sen concellos: a lista pública (só ela) volve baleira.
    await page.route(/\/api\/v1\/municipalities\?/, (route) =>
      route.fulfill({ json: { items: [], page: 0, size: 100, totalItems: 0 } }),
    );
    await page.goto('/');
    await loginAs(page, 'admin-plataforma', lang);
    await expect(page).toHaveURL(/\/plataforma\/concellos\/novo$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'platform.createTitle') })).toBeVisible();

    await userMenu(page).click();
    const items = page.getByRole('menu').getByRole('menuitem');
    await expect(items.first()).toHaveText(text(lang, 'session.newMunicipality'));
    await expect(items.first()).toHaveAttribute('href', '/plataforma/concellos/novo');
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.municipalities') })).toHaveCount(0);
    await expectNoA11yViolations(page);
  });

  test('plataforma con concellos: «Concellos ›» co actual, leva á sección «Concello» e remata en «Novo concello»', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron');
    await loginAs(page, 'admin-plataforma', lang);
    await expect(page).toHaveURL(/\/naron$/);
    await userMenu(page).click();
    const row = page.getByRole('menuitem', { name: `${text(lang, 'session.municipalities')} Narón` });
    await expect(row).toBeVisible();
    await expect(page.getByRole('menu').getByRole('menuitem').first()).toHaveText(
      new RegExp(text(lang, 'session.municipalities')),
    );
    await row.click();
    await expect(page.getByRole('menuitemradio', { name: 'Narón' })).toHaveAttribute('aria-checked', 'true');
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.newMunicipality') })).toHaveAttribute(
      'href',
      '/plataforma/concellos/novo',
    );
    await expectNoA11yViolations(page);
    await page.getByRole('menuitemradio', { name: 'Ferrol' }).click();
    await expect(page).toHaveURL(/\/ferrol\/xestion\/concello\/xeral$/);
  });

  test('Xestión: «Xestión do concello» primeiro na web pública; na xestión, «Ver a web pública»', async ({
    page,
    lang,
  }) => {
    await page.goto('/naron/instalacions');
    await loginAs(page, 'xestor-naron', lang);
    // Sen pasos pendentes, volve onde estaba.
    await expect(page).toHaveURL(/\/naron\/instalacions$/);
    await userMenu(page).click();
    const first = page.getByRole('menu').getByRole('menuitem').first();
    await expect(first).toHaveText(text(lang, 'session.manage'));
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.publicSite') })).toHaveCount(0);
    await expectNoA11yViolations(page);
    await first.click();
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/xeral$/);

    await userMenu(page).click();
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.manage') })).toHaveCount(0);
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.publicSite') })).toBeVisible();
  });

  test('Recepción: «Xestión do concello» leva ás reservas; sen a web pública', async ({ page, lang }) => {
    await page.goto('/naron');
    await loginAs(page, 'recepcion-naron', lang);
    await userMenu(page).click();
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.publicSite') })).toHaveCount(0);
    await page.getByRole('menuitem', { name: text(lang, 'session.manage') }).click();
    await expect(page).toHaveURL(/\/naron\/xestion\/reservas$/);
  });

  test('Xestión cun paso pendente: ao entrar desde a portada, á pestana «Xeral» coa lista', async ({ page, lang }) => {
    const save = page.getByRole('button', { name: text(lang, 'common.save'), exact: true });
    const nif = page.locator('#settings-legal-nif');
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'legal');
    const saved = await nif.inputValue();
    await nif.fill('');
    await save.click();
    await expect(page.getByText(text(lang, 'settings.saved'))).toBeVisible();
    try {
      await logout(page, lang);
      await expect(page.getByRole('button', { name: text(lang, 'session.login') })).toBeVisible();
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await expect(page).toHaveURL(/\/naron\/xestion\/concello\/xeral$/);
      const card = page.getByRole('region', { name: text(lang, 'section.opening.title') });
      await expect(card).toBeVisible();
      await expect(card.locator('[data-item="legal"]')).toHaveAttribute('data-done', 'false');
      await expectNoA11yViolations(page);
    } finally {
      await openSectionTab(page, lang, 'legal');
      await nif.fill(saved || 'P1505400J');
      await save.click();
      await expect(page.getByText(text(lang, 'settings.saved')).last()).toBeVisible();
    }
  });

  test('portada cun paso pendente: Xestión ve a lista pregada, desprégaa e «Ir» leva ao paso; a veciñanza, non', async ({
    page,
    lang,
  }) => {
    const save = page.getByRole('button', { name: text(lang, 'common.save'), exact: true });
    const nif = page.locator('#settings-legal-nif');
    const card = page.getByRole('region', { name: text(lang, 'section.opening.title') });
    const toggle = card.getByRole('button', { name: new RegExp(text(lang, 'section.opening.title')) });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'legal');
    const saved = await nif.inputValue();
    await nif.fill('');
    await save.click();
    await expect(page.getByText(text(lang, 'settings.saved'))).toBeVisible();
    let manager = true;
    try {
      await page.goto('/naron');
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');
      await expect(toggle).toContainText(text(lang, 'section.opening.legalNext'));
      await expect(card.locator('[data-count]')).toHaveText(/1 de 2/);
      await expect(card.locator('[data-item]').first()).toBeHidden();
      await expectNoA11yViolations(page);

      await toggle.click();
      await expect(toggle).toHaveAttribute('aria-expanded', 'true');
      await expect(card.locator('[data-item="legal"]')).toHaveAttribute('data-done', 'false');
      await expect(card.locator('[data-item="facility"]')).toHaveAttribute('data-done', 'true');
      await expect(card.locator('a[data-go]')).toHaveCount(0);
      await expectNoA11yViolations(page);
      await toggle.press('Enter');
      await expect(toggle).toHaveAttribute('aria-expanded', 'false');

      const go = text(lang, 'ui.checklist.goTo', { step: text(lang, 'section.opening.legalNext') });
      await card.getByRole('link', { name: go, exact: true }).click();
      await expect(page).toHaveURL(/\/naron\/xestion\/concello\/legal\/privacidade$/);

      await page.goto('/naron');
      await expect(toggle).toBeVisible();
      // Pechar sesión volve á raíz: a veciña entra desde a portada do concello.
      await logout(page, lang);
      manager = false;
      await page.waitForURL((url) => !url.pathname.startsWith('/auth/'));
      await page.goto('/naron');
      await loginAs(page, 'vecino-naron', lang);
      await expect(page).toHaveURL(/\/naron$/);
      await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
      await expect(card).toHaveCount(0);
    } finally {
      if (!manager) {
        await logout(page, lang);
        await page.waitForURL((url) => !url.pathname.startsWith('/auth/'));
        await page.goto('/naron');
        await loginAs(page, 'xestor-naron', lang);
      }
      await openSectionTab(page, lang, 'legal');
      await nif.fill(saved || 'P1505400J');
      await save.click();
      await expect(page.getByText(text(lang, 'settings.saved')).last()).toBeVisible();
    }
  });
});
