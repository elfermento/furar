// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { randomUUID } from 'node:crypto';
import type { Page } from '@playwright/test';
import {
  expect,
  expectNoA11yViolations,
  loginAs,
  openManageLink,
  openSectionTab,
  settleAnimations,
  test,
  text,
  userMenu,
} from './fixtures';
import { darkTheme } from './indicadores-axudas';
import { checkStep, hermetic } from './instalacions-axudas';
import {
  DEMO_EXPIRED,
  backFromKeycloak,
  bearer,
  deactivateByApi,
  deleteMailsTo,
  freshEmail,
  inviteByApi,
  invitationLink,
  keycloakLogin,
  mailCount,
  memberRow,
  otherPerson,
  resendWaitText,
  rowAction,
  STAFF_API,
  staffList,
  takeMail,
} from './persoal-axudas';

/**
 * Persoal do concello (plan do dominio 4, D4-17; ADR-0027): a xestora invita unha persoa nova de Recepción,
 * o correo chega a Mailpit coa ligazón no fragmento, que leva directamente ao rexistro de Keycloak
 * (maqueta 38); a persoa de Recepción crea a conta co contrasinal no mesmo formulario e sen outro correo (a
 * ligazón da invitación xa verifica o correo, ADR-0027) e, ao volver, acepta soa (sen capa nin
 * casiña: as obrigas están no aviso legal) e chega ás súas reservas, e ve «Reservas» pero non
 * «Persoal» nin «Indicadores»; a xestora desactívaa (escribindo «desactivar» e coa casiña) e á
 * seguinte navegación xa non entra; reactívaa e volve entrar co mesmo rol; sóbea a xestora e
 * báixaa outra vez a Recepción escribindo «recepción». Os
 * límites de envío («Reenviar» desactivado coa conta atrás e o 429 da API), a ligazón que xa non
 * vale, outra conta (correo distinto), a invitación caducada da demo e o que ve a plataforma. O rexistro de actividade, en `rexistro.e2e.ts`.
 *
 * Axe en cada paso (e 320 px sen scroll horizontal en gl-mobile): a xestora co tema claro e a
 * persoa invitada co tema escuro do concello, e a lista do persoal nos dous. Sen peticións fóra de
 * localhost nin violacións de CSP en ningún dos dous contextos; os correos lidos bórranse.
 */
/**
 * Contrasinal da conta que crea a proba: aleatorio en cada execución (só nesta base de demo), con
 * máis dos 12 caracteres que pide o realm.
 */
const PASSWORD = `E2e-${randomUUID()}`;

test.describe('persoal do concello', () => {
  test('invitar → correo → crear a conta → aceptar → só reservas → desactivar → sen permiso → reactivar → recepción', async ({
    page,
    browser,
    lang,
    request,
  }, info) => {
    test.setTimeout(240_000);
    const outside = await hermetic(page);
    const email = freshEmail('recepcion');
    const invitee = await otherPerson(browser, info, darkTheme);
    try {
      // A xestora abre «Persoal»: «Concello» › «Persoal»
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await openSectionTab(page, lang, 'staff');
      await expect(page).toHaveURL(/\/naron\/xestion\/concello\/persoal$/);
      // Na sección «Concello» o título da páxina é un h2 (para lectores de pantalla).
      await expect(page.getByRole('heading', { level: 2, name: text(lang, 'staff.members.title') })).toHaveCount(1);
      await expect(page.locator('[data-member]').first()).toBeVisible();
      await checkStep(page);

      // Invitar: correo, rol «Recepción» e o idioma (por defecto, o do concello)
      await page.locator('[data-invite]').click();
      const dialog = page.getByRole('dialog', { name: text(lang, 'staff.members.invite.title') });
      await expect(dialog).toBeVisible();
      await dialog.getByLabel(text(lang, 'staff.members.invite.email')).fill(email);
      // Rol en tarxetas: picar na tarxeta (non só no radio) escolle.
      await dialog.locator('label.f-radio-card', { hasText: text(lang, 'staff.members.role.STAFF') }).click();
      await expect(dialog.getByRole('radio', { name: text(lang, 'staff.members.role.STAFF') })).toBeChecked();
      await expect(dialog.locator('[data-language="gl"]')).toHaveAttribute('aria-checked', 'true');
      await dialog.locator(`[data-language="${lang}"]`).click();
      await expect(dialog.locator(`[data-language="${lang}"]`)).toHaveAttribute('aria-checked', 'true');
      await checkStep(page);
      await dialog.locator('[data-send]').click();
      await expect(dialog).toBeHidden();
      await expect(page.locator('[data-toast]')).toContainText(text(lang, 'staff.members.done.invited', { email }));
      await expect(page.locator('[data-invite]')).toBeFocused();
      const row = memberRow(page, email);
      await expect(row.locator('[data-chip]')).toHaveAttribute('data-chip', 'INVITED');
      await expect(row.locator('[data-chip]')).toHaveText(text(lang, 'staff.members.state.INVITED'));
      await expect(row.locator('[data-access]')).toHaveAttribute('data-access', 'sent');
      await checkStep(page);

      // O correo: asunto no idioma escollido e a ligazón co token só no fragmento
      const invitation = await takeMail(
        request,
        email,
        lang === 'gl'
          ? / invítate a atender as reservas das instalacións deportivas /
          : / te invita a atender las reservas de las instalaciones deportivas /,
      );
      expect(invitation.Subject).toContain('Narón');
      const link = invitationLink(invitation);

      // A persoa invitada (tema escuro): a ligazón leva directamente a crear a conta en Keycloak
      const guest = invitee.page;
      await guest.goto(link.pathname + link.hash);
      await guest.waitForURL(/\/auth\/realms\/furar\//);
      await expect(guest.locator('#kc-register-form')).toBeVisible();
      await expectNoA11yViolations(guest);
      // O correo é o usuario (P13): sen campo de usuario, e o correo invitado xa vén posto (login_hint).
      await expect(guest.locator('#username')).toHaveCount(0);
      await expect(guest.locator('#email')).toHaveValue(email);
      await expect(guest.locator('#email')).toHaveAttribute('autocomplete', 'email');
      // A proba da invitación deixa o correo fixo (só lectura)
      await expect(guest.locator('#email')).toHaveAttribute('readonly', '');
      await registerInKeycloak(guest, PASSWORD);

      // Sen correo de verificación: a ligazón da invitación xa chegou a ese enderezo; volve á web
      await backFromKeycloak(guest);
      expect(await mailCount(request, email), 'sen correo de verificación').toBe(0);

      // Sen capa nin casiña: ao volver con sesión acepta soa (as obrigas están no aviso legal)
      await expect(guest.getByRole('dialog')).toHaveCount(0);
      await expect(guest.getByRole('checkbox')).toHaveCount(0);
      // Recepción: o toast e directamente ás reservas do concello
      await expect(guest.locator('[data-toast]')).toContainText(
        text(lang, 'staff.invitation.accepted', { ofName: 'de Narón' }),
      );
      await expect(guest).toHaveURL(/\/naron\/xestion\/reservas$/);
      await checkStep(guest);

      // Xa é Recepción: «Reservas» na barra; nin «Concello» nin «Indicadores»; no menú da persoa,
      // só o nome no botón e sen «As miñas reservas» nin «Ver a web pública» (maqueta 50)
      await guest.goto('/naron/instalacions');
      await expect(userMenu(guest)).toBeVisible();
      for (const key of ['nav.municipality', 'nav.indicators']) {
        await expect(guest.getByRole('link', { name: text(lang, key), exact: true })).toHaveCount(0);
      }
      await expect(userMenu(guest)).not.toContainText(text(lang, 'session.role.STAFF'));
      await userMenu(guest).click();
      for (const key of ['session.myBookings', 'session.publicSite', 'nav.bookings']) {
        await expect(guest.getByRole('menuitem', { name: text(lang, key), exact: true })).toHaveCount(0);
      }
      await checkStep(guest);
      await guest.keyboard.press('Escape');
      // A barra de arriba no escritorio, a de abaixo no móbil.
      await openManageLink(guest, lang, 'nav.bookings');
      await expect(guest).toHaveURL(/\/naron\/xestion\/reservas$/);
      await expect(guest.getByRole('heading', { level: 1, name: text(lang, 'titles.manageBookings') })).toBeVisible();
      await checkStep(guest);

      // A xestora vea activa e desactívaa: o que perde, escribir «desactivar» e a casiña
      await page.reload();
      const active = memberRow(page, email);
      await expect(active.locator('[data-chip]')).toHaveAttribute('data-chip', 'ACTIVE');
      await expect(active.locator('[data-email]')).toHaveText(email);
      await rowAction(page, active, 'deactivate');
      const confirm = page.getByRole('alertdialog', { name: text(lang, 'staff.members.deactivate.title') });
      await expect(confirm).toBeVisible();
      await expect(confirm).toContainText(text(lang, 'staff.members.deactivate.losesSTAFF'));
      await expect(confirm).toContainText(text(lang, 'staff.members.deactivate.again'));
      const accept = confirm.locator('[data-confirm-accept]');
      await expect(accept).toHaveAttribute('aria-disabled', 'true');
      const phrase = confirm.getByRole('textbox', {
        name: text(lang, 'ui.confirm.typeToConfirm', { phrase: text(lang, 'staff.members.deactivate.phrase') }),
      });
      await expect(phrase).toBeFocused();
      await phrase.fill(text(lang, 'staff.members.deactivate.phrase').toUpperCase());
      await expect(accept).toHaveAttribute('aria-disabled', 'true');
      const ack = confirm.getByRole('checkbox', { name: text(lang, 'staff.members.deactivate.acknowledge') });
      await ack.check();
      await expect(accept).not.toHaveAttribute('aria-disabled');
      await checkStep(page);
      await accept.click();
      await expect(confirm).toBeHidden();
      await expect(page.locator('[data-toast]')).toContainText(
        text(lang, 'staff.members.done.deactivated', { name: email }),
      );
      const deactivated = page
        .locator('[data-group="deactivated"]')
        .locator('[data-member]')
        .filter({ hasText: email });
      await expect(deactivated.locator('[data-chip]')).toHaveAttribute('data-chip', 'DEACTIVATED');
      await checkStep(page);

      // Á seguinte navegación, a persoa de Recepción xa non entra: páxina «Sen permiso» e 403 na API
      await guest.reload();
      await expect(guest.getByRole('heading', { level: 1, name: text(lang, 'errorPage.403.title') })).toBeVisible();
      await checkStep(guest);
      const api = await guest.request.get('/api/v1/municipalities/naron/bookings', {
        headers: await bearer(request, email, PASSWORD),
      });
      expect(api.status()).toBe(403);

      // Reactivala: o mesmo rol, sen invitación nova; volve entrar ás reservas
      await rowAction(page, deactivated, 'reactivate');
      const again = page.getByRole('alertdialog', { name: text(lang, 'staff.members.reactivate.title') });
      await expect(again).toContainText(
        text(lang, 'staff.members.reactivate.returns', { role: text(lang, 'staff.members.role.STAFF') }),
      );
      await checkStep(page);
      await again.locator('[data-confirm-accept]').click();
      await expect(again).toBeHidden();
      await expect(page.locator('[data-toast]')).toContainText(
        text(lang, 'staff.members.done.reactivated', {
          name: email,
          role: text(lang, 'staff.members.role.STAFF'),
        }),
      );
      const back = page.locator('[data-group="current"]').locator('[data-member]').filter({ hasText: email });
      await expect(back.locator('[data-chip]')).toHaveAttribute('data-chip', 'ACTIVE');
      expect(await mailCount(request, email), 'sen invitación nova').toBe(0);
      await guest.goto('/naron/xestion/reservas');
      await expect(guest.getByRole('heading', { level: 1, name: text(lang, 'titles.manageBookings') })).toBeVisible();

      // Sóbea a xestora e báixaa outra vez: para pasar a Recepción hai que escribir «recepción»
      await rowAction(page, back, 'role');
      let role = page.getByRole('dialog', { name: text(lang, 'staff.members.changeRole.title') });
      await role.getByRole('radio', { name: text(lang, 'staff.members.role.MANAGER') }).check();
      await role.locator('[data-save]').click();
      await expect(role).toBeHidden();
      await expect(back.locator('[data-cell="role"]').first()).toHaveText(text(lang, 'staff.members.role.MANAGER'));
      await rowAction(page, back, 'role');
      role = page.getByRole('dialog', { name: text(lang, 'staff.members.changeRole.title') });
      await role.getByRole('radio', { name: text(lang, 'staff.members.role.STAFF') }).check();
      const save = role.locator('[data-save]');
      await expect(save).toHaveAttribute('aria-disabled', 'true');
      const demote = role.getByRole('textbox', {
        name: text(lang, 'ui.confirm.typeToConfirm', { phrase: text(lang, 'staff.members.changeRole.phrase') }),
      });
      await demote.fill(text(lang, 'staff.members.changeRole.phrase'));
      await expect(save).not.toHaveAttribute('aria-disabled');
      await checkStep(page);
      await save.click();
      await expect(role).toBeHidden();
      await expect(page.locator('[data-toast]')).toContainText(
        text(lang, 'staff.members.done.roleChanged', {
          name: email,
          role: text(lang, 'staff.members.role.STAFF'),
        }),
      );

      expect(outside, 'peticións fóra de localhost').toEqual([]);
      expect(invitee.outside, 'peticións fóra de localhost (invitada)').toEqual([]);
      expect(invitee.csp, 'violacións de CSP (invitada)').toEqual([]);
    } finally {
      await invitee.context.close();
      await deleteMailsTo(request, email);
      // Se a proba falla a medias, a persoa non queda no persoal
      const manager = await bearer(request, 'xestor-naron');
      const left = (await staffList(request, manager)).find((m) => m.email === email && m.status !== 'DEACTIVATED');
      if (left) await deactivateByApi(request, manager, left);
    }
  });

  test('reenviar antes de tempo → desactivado coa conta atrás e 429 na API; outra conta → «correo distinto»; a ligazón vella → «non serve»', async ({
    page,
    browser,
    lang,
    request,
  }, info) => {
    test.setTimeout(180_000);
    const outside = await hermetic(page);
    const email = freshEmail('persoal');
    const manager = await bearer(request, 'xestor-naron');
    const member = await inviteByApi(request, manager, email, lang);
    const link = invitationLink(await takeMail(request, email, /Narón/));
    const other = await otherPerson(browser, info);
    try {
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await openSectionTab(page, lang, 'staff');
      const row = memberRow(page, email);
      await expect(row.locator('[data-chip]')).toHaveAttribute('data-chip', 'INVITED');

      // Dentro dos 2 min desde o último envío, «Reenviar» está desactivado coa conta atrás como
      // descrición: picar nel dúas veces non pide nada nin dá un 429 por sorpresa
      const trigger = row.locator('[data-actions]');
      // A lista acaba de cargar: un repintado da fila pecharía o menú xa aberto.
      await page.waitForLoadState('networkidle');
      await settleAnimations(page);
      await trigger.click();
      const item = page.locator('[role="menu"] [data-action="resend"]');
      await expect(item).toBeDisabled();
      await expect(item).toHaveAccessibleName(text(lang, 'staff.members.action.resend'));
      await expect(item).toHaveAccessibleDescription(resendWaitText(lang));
      const wait = page.locator('[role="menu"] [data-resend-wait]');
      await expect(wait).toHaveText(resendWaitText(lang));
      const resends: string[] = [];
      page.on('request', (r) => {
        if (r.method() === 'POST' && r.url().endsWith(`/staff/${member.id}/invitation`)) resends.push(r.url());
      });
      // O clic vai ao elemento, non a unhas coordenadas: o aviso do último acceso pode estar enriba.
      for (let i = 0; i < 2; i++) await item.dispatchEvent('click');
      await checkStep(page);
      // A conta atrás avanza (m:ss) sen que o menú se peche
      const first = await wait.textContent();
      await expect(wait).not.toHaveText(first!, { timeout: 3_000 });
      await expect(wait).toHaveText(resendWaitText(lang));
      await page.keyboard.press('Escape');
      await expect(trigger).toBeFocused();
      expect(resends, 'reenvíos pedidos á API').toEqual([]);
      await expect(page.locator('[data-alert]')).toHaveCount(0);

      // A API segue sendo a rede: o mesmo reenvío directo → 429 co tempo en Retry-After
      const direct = await request.post(`${STAFF_API}/${member.id}/invitation`, {
        headers: { ...manager, 'If-Match': `"${member.version}"` },
      });
      expect(direct.status()).toBe(429);
      expect(((await direct.json()) as { code: string }).code).toBe('staff.invitation-rate-limited');
      expect(Number(direct.headers()['retry-after'])).toBeGreaterThan(0);
      expect(Number(direct.headers()['retry-after'])).toBeLessThanOrEqual(120);

      // Outra conta (vecino-naron) abre a ligazón: o rexistro co correo invitado; «Entrar» con
      // outra conta → «Xa iniciaches sesión con outra conta.», sen ningún correo (maqueta 50), e
      // «Pechar sesión e continuar» volve ao rexistro co correo invitado
      const guest = other.page;
      await guest.goto(link.pathname + link.hash);
      await guest.waitForURL(/\/auth\/realms\/furar\//);
      await expect(guest.locator('#email')).toHaveValue(email);
      await guest.locator('a[href*="login-actions/authenticate"]').first().click();
      await expect(guest.locator('#username')).toHaveValue(email);
      await keycloakLogin(guest, 'vecino-naron');
      await backFromKeycloak(guest);
      await expect(
        guest.getByRole('heading', { level: 1, name: text(lang, 'staff.invitation.otherAccount.title') }),
      ).toBeVisible();
      await expect(guest.locator('main')).not.toContainText('@');
      await expect(guest.getByRole('button', { name: text(lang, 'staff.invitation.otherAccount.back') })).toBeVisible();
      const signOut = guest.getByRole('button', { name: text(lang, 'staff.invitation.otherAccount.action') });
      await expect(signOut).toBeVisible();
      await checkStep(guest);
      await signOut.click();
      // Keycloak pecha a sesión e volve á raíz; a web volve á invitación e de aí ao rexistro.
      await expect(guest.locator('#email')).toHaveValue(email, { timeout: 15_000 });
      await expect(guest).toHaveURL(/\/auth\/realms\/furar\//);
      // A API tampouco deixa aceptar con outra conta (rexístrase como fallo)
      const accept = await request.post('/api/v1/municipalities/naron/staff-invitations/accept', {
        headers: await bearer(request, 'vecino-naron'),
        data: { token: link.hash.slice(1) },
      });
      expect(accept.status()).toBe(403);
      expect(((await accept.json()) as { code: string }).code).toBe('staff.invitation-email-mismatch');

      // A xestora desactívaa: a ligazón vella xa non serve
      const current = (await staffList(request, manager)).find((m) => m.id === member.id)!;
      await deactivateByApi(request, manager, current);
      await guest.goto(link.pathname + link.hash);
      await expect(
        guest.getByRole('heading', { level: 1, name: text(lang, 'staff.invitation.invalid.title') }),
      ).toBeVisible();
      await checkStep(guest);

      expect(outside, 'peticións fóra de localhost').toEqual([]);
      expect(other.outside, 'peticións fóra de localhost (outra conta)').toEqual([]);
      expect(other.csp, 'violacións de CSP (outra conta)').toEqual([]);
    } finally {
      await other.context.close();
      await deleteMailsTo(request, email);
      const left = (await staffList(request, manager)).find((m) => m.id === member.id && m.status !== 'DEACTIVATED');
      if (left) await deactivateByApi(request, manager, left);
    }
  });

  test('invitación caducada da demo: «Pendente», «Invitación caducada» en vermello e «Reenviar» á vista; a lista co tema escuro', async ({
    page,
    lang,
  }) => {
    const outside = await hermetic(page);
    await darkTheme(page);
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'staff');
    const row = memberRow(page, DEMO_EXPIRED);
    await expect(row.locator('[data-chip]')).toHaveAttribute('data-chip', 'INVITED');
    await expect(row.locator('[data-chip]')).toHaveText(text(lang, 'staff.members.state.INVITED'));
    const resend = row.locator('[data-resend]');
    await expect(resend).toBeVisible();
    await expect(resend).toHaveAccessibleName(text(lang, 'staff.members.resendFor', { name: DEMO_EXPIRED }));
    await expect(row.locator('[data-access]')).toHaveAttribute('data-access', 'expired');
    await expect(row.locator('[data-access]')).toHaveText(text(lang, 'staff.members.access.expired'));
    // Quen invitou xa non vai na lista: queda no rexistro de actividade.
    await expect(row).not.toContainText('Xestora Narón');
    await checkStep(page);
    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('a plataforma invita a Recepción: a API non dá datos da súa conta e a lista só mostra o correo', async ({
    page,
    lang,
    request,
  }) => {
    const outside = await hermetic(page);
    const platform = await bearer(request, 'admin-plataforma');
    const email = freshEmail('recepcion-plataforma');
    const member = await inviteByApi(request, platform, email, lang, 'STAFF');
    try {
      // A API di só que foi a plataforma: ningún dato da súa conta.
      expect(member.invitedBy.kind).toBe('PLATFORM');
      expect(member.invitedBy.name ?? null).toBeNull();
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await openSectionTab(page, lang, 'staff');
      const row = memberRow(page, email);
      await expect(row.locator('[data-cell="role"]').first()).toHaveText(text(lang, 'staff.members.role.STAFF'));
      await expect(row.locator('[data-chip]')).toHaveAttribute('data-chip', 'INVITED');
      await expect(row.locator('[data-email]')).toHaveText(email);
      await checkStep(page);
      expect(outside, 'peticións fóra de localhost').toEqual([]);
    } finally {
      const left = (await staffList(request, platform)).find((m) => m.id === member.id && m.status !== 'DEACTIVATED');
      if (left) await deactivateByApi(request, platform, left);
      await deleteMailsTo(request, email);
    }
  });
});

/**
 * Rexistro de Keycloak co correo xa posto (maqueta 36): nome, apelidos e o contrasinal no mesmo
 * formulario (só o mínimo baixo o campo; o erro, ao enviar).
 */
async function registerInKeycloak(page: Page, password: string): Promise<void> {
  const form = page.locator('#kc-register-form');
  await form.locator('#firstName').fill('Persoa');
  await form.locator('#lastName').fill('Proba');
  await form.locator('#password').fill(password);
  await form.locator('#password-confirm').fill(password);
  await form.locator('[type="submit"]').click();
}
