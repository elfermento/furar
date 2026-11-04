// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Page } from '@playwright/test';
import { expect, loginAs, openManageLink, openSectionTab, test, text, userMenu } from './fixtures';
import { darkTheme } from './indicadores-axudas';
import { checkStep, hermetic, tabTo } from './instalacions-axudas';
import { backFromKeycloak, bearer, keycloakLogin, otherPerson, staffList } from './persoal-axudas';

/**
 * Accesos á xestión (plan do dominio 4, D4-17): o xestor entra só co contrasinal (sen segundo
 * factor, decisión do 2026-11-02) e ve o seu último acceso; a plataforma ve só «Persoal» do
 * concello (as mesmas accións ca o xestor, revisión do 2026-11-02) e o seu rexistro, nin reservas nin indicadores; e a sección
 * «Concello» (maqueta 49): ligazón da barra, pestanas co teclado, a pregunta ao saír cun cambio e,
 * no móbil, as pestanas nunha fila que se despraza dentro da súa franxa.
 *
 * Axe en cada paso (en gl-mobile, 320 px sen scroll horizontal), cos dous temas onde se indica; sen
 * peticións fóra de localhost nin violacións de CSP.
 */
test.describe('accesos á xestión', () => {
  test('xestor-naron: só contrasinal e o aviso do último acceso «dd/mm/aaaa, hh:mm»', async ({
    page,
    browser,
    lang,
  }, info) => {
    test.setTimeout(150_000);
    const outside = await hermetic(page);
    // Un acceso anterior noutro navegador (o token por contrasinal non leva auth_time): así a web
    // ensina sempre o aviso, tamén na primeira execución sobre unha base nova.
    const earlier = await otherPerson(browser, info);
    try {
      await earlier.page.goto('/naron/instalacions');
      await signInWithPassword(earlier.page, lang, false);
      expect(earlier.outside, 'peticións fóra de localhost (acceso anterior)').toEqual([]);
      expect(earlier.csp, 'violacións de CSP (acceso anterior)').toEqual([]);
    } finally {
      await earlier.context.close();
    }

    await page.goto('/naron/instalacions');
    await signInWithPassword(page, lang, true);
    await openManageLink(page, lang, 'nav.facilities');
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions/);
    const notice = page.locator('[data-last-sign-in]');
    await expect(notice).toBeVisible();
    const date = /(\d{2})\/(\d{2})\/(\d{4}), (\d{2}):(\d{2})/.exec(await notice.innerText());
    expect(date, `formato do último acceso: ${await notice.innerText()}`).not.toBeNull();
    const [, day, month, year] = date!;
    expect(Number(day)).toBeLessThanOrEqual(31);
    expect(Number(month)).toBeLessThanOrEqual(12);
    expect(Number(year)).toBeGreaterThanOrEqual(2026);
    await expect(notice).toHaveText(text(lang, 'session.lastSignIn', { date: date![0] }));
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('a plataforma: configura o concello («Instalacións» e «Concello»: datos, persoal, rexistro, campos) e o seu rexistro; nin reservas nin indicadores', async ({
    page,
    lang,
    request,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const platform = await bearer(request, 'admin-plataforma');
    for (const path of ['bookings', 'indicators', 'export']) {
      const response = await request.get(`/api/v1/municipalities/naron/${path}`, { headers: platform });
      expect(response.status(), `${path} da plataforma`).toBe(403);
    }
    // Configura o concello (ADR-0028, revisión do 2026-11-03): instalacións e rexistro do concello.
    for (const path of ['facilities', 'audit-events']) {
      const response = await request.get(`/api/v1/municipalities/naron/${path}`, { headers: platform });
      expect(response.status(), `${path} da plataforma`).toBe(200);
    }
    const listed = await staffList(request, platform);
    const managers = listed.filter((m) => m.role === 'MANAGER' && m.status !== 'DEACTIVATED');
    expect(managers.length).toBeGreaterThan(0);

    await page.goto('/naron');
    await loginAs(page, 'admin-plataforma', lang);
    const nav = page.getByRole('navigation', { name: text(lang, 'app.manageNav') }).first();
    await expect(nav.getByRole('link', { name: text(lang, 'nav.facilities'), exact: true })).toBeVisible();
    for (const key of ['nav.bookings', 'nav.indicators']) {
      await expect(page.getByRole('link', { name: text(lang, key), exact: true })).toHaveCount(0);
    }
    // «Concello»: as sete pestanas, tamén «Legal» (os datos legais, para deixar o concello listo).
    await openSectionTab(page, lang, 'legal');
    await expect(page.locator('a[data-section-tab]')).toHaveCount(7);
    await expect(page.getByRole('region', { name: text(lang, 'settings.legal.required.title') })).toBeVisible();
    await checkStep(page);
    // «Rexistro» do concello: sen «Exportar bloqueados» (só o xestor).
    await openSectionTab(page, lang, 'activity');
    await expect(page.getByRole('heading', { level: 2, name: text(lang, 'activity.title') })).toHaveCount(1);
    await expect(page.locator('[data-export-blocked]')).toHaveCount(0);
    await openSectionTab(page, lang, 'staff');
    await expect(page.locator('[data-invite]')).toBeVisible();
    // Fai co persoal o mesmo ca o xestor (revisión do 2026-11-02): cada fila en uso, coas súas accións.
    const rows = page.locator('[data-group="current"] [data-member]');
    await expect(rows.first()).toBeVisible();
    for (const member of listed.filter((m) => m.status !== 'DEACTIVATED')) {
      const actions = page.locator(`[data-group="current"] [data-member="${member.id}"] [data-actions]`);
      await expect(actions, `accións de ${member.email}`).toHaveCount(1);
    }
    const manager = managers.find((m) => m.status === 'ACTIVE')!;
    await page.locator(`[data-actions="${manager.id}"]`).click();
    const items = page.locator('[role="menu"] [data-action]');
    await expect(items).toHaveCount(2);
    await expect(items.nth(0)).toHaveAttribute('data-action', 'role');
    await expect(items.nth(1)).toHaveAttribute('data-action', 'deactivate');
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(page.locator(`[data-actions="${manager.id}"]`)).toBeFocused();
    // «Invitar»: o mesmo selector de rol ca o xestor.
    await page.locator('[data-invite]').click();
    const invite = page.getByRole('dialog', { name: text(lang, 'staff.members.invite.title') });
    await expect(invite.locator('furar-radio-cards').getByRole('radio')).toHaveCount(2);
    await checkStep(page);
    await page.keyboard.press('Escape');
    await expect(invite).toBeHidden();

    for (const path of ['reservas', 'indicadores']) {
      await page.goto(`/naron/xestion/${path}`);
      await expect(page.getByRole('heading', { level: 1, name: text(lang, 'errorPage.403.title') })).toBeVisible();
    }
    await checkStep(page);

    await page.goto('/naron');
    await userMenu(page).click();
    // Sen «As miñas reservas»: a plataforma non reserva.
    await expect(page.getByRole('menuitem', { name: text(lang, 'session.myBookings') })).toHaveCount(0);
    await page.getByRole('menuitem', { name: text(lang, 'session.platformActivity') }).click();
    await expect(page).toHaveURL(/\/plataforma\/rexistro$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'activity.platformTitle') })).toBeVisible();
    await expect(page.locator('[data-area]')).toHaveCount(0);
    await expect(page.locator('[data-activity-table], [data-activity-empty]').first()).toBeVisible();
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('«Concello»: ligazón da barra e pestanas co teclado; saír cun cambio pregunta; no móbil, unha fila', async ({
    page,
    lang,
  }) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    // Desde unha páxina sen animacións que esperar (a portada ten as súas)
    await page.goto('/naron/instalacions');
    await loginAs(page, 'xestor-naron', lang);
    const nav = page.getByRole('navigation', { name: text(lang, 'app.manageNav') }).locator('visible=true');
    // Sen despregable: «Concello» é unha ligazón coma as outras (maqueta 49).
    await expect(nav.locator('button[data-more]')).toHaveCount(0);
    const concello = nav.getByRole('link', { name: text(lang, 'nav.municipality'), exact: true });
    await tabTo(page, concello, 60);
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/xeral$/);
    await expect(concello).toHaveAttribute('aria-current', 'page');
    const tabs = page.getByRole('navigation', { name: text(lang, 'section.title'), exact: true });
    const tab = (key: string) => tabs.locator(`a[data-section-tab="${key}"]`);
    await expect(tab('general')).toHaveAttribute('aria-current', 'page');
    await checkStep(page);

    // Frechas: moven o foco entre as pestanas sen navegar; Intro navega e o foco queda na pestana.
    await tab('general').focus();
    await page.keyboard.press('ArrowRight');
    await expect(tab('appearance')).toBeFocused();
    await page.keyboard.press('End');
    await expect(tab('activity')).toBeFocused();
    await expect(page).toHaveURL(/\/concello\/xeral$/);
    await page.keyboard.press('Home');
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('Enter');
    await expect(page).toHaveURL(/\/concello\/aparencia$/);
    await expect(tab('appearance')).toHaveAttribute('aria-current', 'page');
    await expect(tab('appearance')).toBeFocused();

    // Un cambio pon o punto; saír pregunta e «Cancelar» deixa onde estaba; «Descartar» sae.
    await page.locator('input[data-color="accent"]').evaluate((input: HTMLInputElement) => {
      input.value = '#5a2d00';
      input.dispatchEvent(new Event('input', { bubbles: true }));
    });
    await expect(tab('appearance').locator('[data-dirty]')).toBeVisible();
    await tab('service').click();
    const leave = page.getByRole('alertdialog');
    await expect(leave).toContainText(
      text(lang, 'ui.unsavedChoice.title', { tab: text(lang, 'section.tab.appearance') }),
    );
    await expect(leave.locator('[data-choice="save"]')).toBeFocused();
    await checkStep(page);
    await leave.locator('[data-choice="cancel"]').click();
    await expect(page).toHaveURL(/\/concello\/aparencia$/);
    await tab('service').click();
    await page.getByRole('alertdialog').locator('[data-choice="discard"]').click();
    await expect(page).toHaveURL(/\/concello\/servizo$/);
    await expect(tab('appearance').locator('[data-dirty]')).toHaveCount(0);

    // Os enderezos de antes levan ao seu sitio novo, cos filtros.
    await page.goto('/naron/xestion/rexistro?fallidos=1');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/rexistro\?fallidos=1$/);
    await expect(tab('activity')).toHaveAttribute('aria-current', 'page');

    // No móbil (320 px): unha fila que se despraza dentro da franxa, coa activa á vista; a páxina non.
    await page.setViewportSize({ width: 320, height: 700 });
    await page.goto('/naron/xestion/concello/campos');
    const strip = tabs.locator('.f-tabs__list');
    await expect(tab('customFields')).toBeInViewport();
    const box = await strip.evaluate((el) => ({
      scroll: el.scrollWidth,
      client: el.clientWidth,
      top: new Set([...el.children].map((c) => (c as HTMLElement).offsetTop)).size,
    }));
    expect(box.scroll, 'as pestanas non caben: desprázanse').toBeGreaterThan(box.client);
    expect(box.top, 'nunha soa fila').toBe(1);
    await expect(strip).toHaveAttribute('data-fade', /l/);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
    await checkStep(page);

    // Co tema escuro do concello, as mesmas pestanas
    await darkTheme(page);
    await page.reload();
    await expect(tab('customFields')).toHaveAttribute('aria-current', 'page');
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
});

/** «Entrar» → correo e contrasinal → de volta na web, sen ningún paso máis. */
async function signInWithPassword(page: Page, lang: 'gl' | 'es', check: boolean): Promise<void> {
  await page.getByRole('button', { name: text(lang, 'session.login') }).click();
  await page.waitForURL(/\/auth\/realms\/furar\//);
  if (check) await checkStep(page);
  await keycloakLogin(page, 'xestor-naron');
  await backFromKeycloak(page);
  await expect(page.locator('#otp')).toHaveCount(0);
  await expect(userMenu(page)).toBeVisible();
}
