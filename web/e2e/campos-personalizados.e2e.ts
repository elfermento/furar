// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  removeLeftoverE2eFields,
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  logout,
  openSectionTab,
  test,
  text,
  userMenu,
} from './fixtures';
import { FAR, PADEL, bookingDay, cancelLeftovers, firstSlotStart, hhmm, tokenFor } from './reservas-axudas';

/**
 * Fluxo da rebanda vertical (ADR-0011): o xestor define un campo → valídase → gárdase →
 * vese na ficha pública. Tamén: teclado no formulario dinámico, recarga con sesión e
 * desactivado ao final (ten valores: non se elimina, e deixa de contar no límite de 50 activos).
 * Revisión do 2026-11-04 (maqueta 57): un campo, un sitio; un campo da reserva que a veciña enche
 * antes de confirmar e o persoal ve no detalle.
 */
test.describe.configure({ mode: 'serial' });

test.describe('campos personalizados do concello', () => {
  test.beforeAll(async ({ request }) => {
    await removeLeftoverE2eFields(request);
    await cancelLeftovers(request, 'vecino-naron', [PADEL.id]);
  });
  test.afterAll(async ({ request }) => {
    await removeLeftoverE2eFields(request);
    await cancelLeftovers(request, 'vecino-naron', [PADEL.id]);
  });

  test('o xestor crea, enche, ve e desactiva un campo', async ({ page, lang }, testInfo) => {
    const code = `e2e_${testInfo.project.name.replace(/\W/g, '')}_${Date.now().toString(36)}`;
    const label = { gl: `Código RIDEGA ${code}`, es: `Código RIDEGA ${code} (es)` }[lang];
    // Valor propio de cada proxecto: o campo desactivado doutro proxecto segue vendo o seu.
    const value = `GAL-${Date.now().toString(36).slice(-4)}`;

    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await expect(page).toHaveURL(/\/naron$/);

    // 1. Lista de campos (xestión)
    await openSectionTab(page, lang, 'customFields');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/campos$/);
    // Na sección «Concello» o título da páxina é para lectores de pantalla; o foco queda na pestana.
    await expect(page.getByRole('heading', { level: 2, name: text(lang, 'customFields.admin.title') })).toHaveCount(1);
    await expect(page.locator('a[data-section-tab="customFields"]')).toBeFocused();
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);

    // 2. Editor con vista previa: enviar baleiro marca os erros e leva o foco ao primeiro
    await page.getByRole('link', { name: text(lang, 'customFields.admin.new') }).click();
    await page.getByRole('button', { name: text(lang, 'common.save') }).click();
    await expect(page.locator('#def-labelGl')).toBeFocused();
    await expect(page.getByRole('alert').filter({ hasText: /erro/ })).toBeVisible();
    await page
      .getByRole('textbox', { name: text(lang, 'customFields.editor.labelGl'), exact: false })
      .first()
      .fill(`Código RIDEGA ${code}`);
    await page.locator('#def-labelEs').fill(`Código RIDEGA ${code} (es)`);
    await page.locator('#def-code').fill(code);
    // Onde se usa (maqueta 57): un campo, un sitio. Este vai na ficha do concello (paso 3), o sitio
    // por defecto, e é obrigatorio.
    const places = page.getByRole('radiogroup', { name: text(lang, 'customFields.editor.usages') });
    await expect(places.getByRole('radio', { name: text(lang, 'customFields.target.MUNICIPALITY') })).toBeChecked();
    await page.getByRole('checkbox', { name: text(lang, 'customFields.editor.usageRequired') }).check();
    await page.getByRole('spinbutton', { name: text(lang, 'customFields.rules.minLength'), exact: true }).fill('3');
    await page.getByRole('spinbutton', { name: text(lang, 'customFields.rules.maxLength'), exact: true }).fill('8');
    // Dato persoal (RGPD, AP2-07): privacidade por defecto, aviso do art. 9 ligado á caixa,
    // PUBLIC desactivado en cada sitio e paso automático a «Só persoal».
    const personal = page.locator('#def-personalData');
    const townVisibility = page.locator('#def-visibility');
    const publicLabel = text(lang, 'customFields.visibility.PUBLIC');
    const staffLabel = text(lang, 'customFields.visibility.STAFF');
    const publicOption = page.getByRole('option', { name: publicLabel, exact: true });
    await expect(townVisibility).toContainText(staffLabel);
    await townVisibility.click();
    await publicOption.click();
    await personal.check();
    await expect(page.locator('#def-personal-warning')).toContainText(
      text(lang, 'customFields.editor.personalDataWarning'),
    );
    await expect(personal).toHaveAttribute('aria-describedby', /\bdef-personal-warning\b/);
    await townVisibility.click();
    await expect(publicOption).toBeDisabled();
    await page.keyboard.press('Escape');
    await expect(townVisibility).toBeFocused();
    await expect(townVisibility).toHaveAttribute('aria-describedby', 'def-visibility-note');
    await expect(townVisibility).toContainText(staffLabel);
    await expectNoA11yViolations(page);
    await personal.uncheck();
    // Este campo (código RIDEGA, sen datos persoais) vese na ficha pública do concello (paso 5).
    await townVisibility.click();
    await publicOption.click();
    const preview = page.locator('furar-custom-field-preview');
    await expect(preview.locator('#preview-gl-' + code)).toBeVisible();
    await expect(preview.locator('label[for="preview-es-' + code + '"]')).toContainText(`Código RIDEGA ${code} (es)`);
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await page.getByRole('button', { name: text(lang, 'common.save') }).click();
    await expect(page.getByRole('status').filter({ hasText: label })).toBeVisible();
    await expect(page.getByText(code, { exact: true })).toBeVisible();

    // 3. Ficha do concello: o formulario dinámico valida (cliente) e garda (API)
    await openSectionTab(page, lang, 'general');
    const field = page.locator(`#settings-cf-${code}`);
    await expect(page.locator(`label[for="settings-cf-${code}"]`)).toContainText(label);
    await expect(field).toBeVisible();
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    // Teclado: chégase ao campo con Tab e enviar con Enter leva o foco ao erro.
    await page.locator('#settings-name').focus();
    let reached = false;
    for (let i = 0; i < 60 && !reached; i++) {
      await page.keyboard.press('Tab');
      reached = await field.evaluate((el) => el === document.activeElement);
    }
    expect(reached, 'o campo alcánzase con Tab').toBe(true);
    await page.keyboard.press('Enter');
    await expect(field).toBeFocused();
    await expect(field).toHaveAttribute('aria-invalid', 'true');
    const error = page.locator(`#settings-cf-${code}-error`);
    await expect(error).toHaveText(text(lang, 'errors.customfield.required'));
    // maxlength impide pasar de 8; a lonxitude mínima valídase ao enviar.
    await page.keyboard.type('12');
    await page.keyboard.press('Enter');
    await expect(error).toHaveText(text(lang, 'errors.customfield.too-short', { min: 3 }));
    await expect(field).toBeFocused();
    await field.fill(value);
    await page.keyboard.press('Enter');
    await expect(page.getByRole('status').filter({ hasText: text(lang, 'settings.saved') })).toBeVisible();

    // 4. Recarga: a sesión recupérase sen pedir contrasinal e o valor segue gardado
    await page.reload();
    await expect(userMenu(page)).toBeVisible();
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/xeral$/);
    await expect(page.locator(`#settings-cf-${code}`)).toHaveValue(value);

    // 5. Ficha pública (campo PUBLIC)
    await page.goto('/naron');
    await expect(page.getByRole('definition').filter({ hasText: value })).toBeVisible();

    // 6. Eliminar: ten un valor gardado, así que o diálogo o di e ofrece desactivalo (os valores
    // consérvanse; o campo deixa de contar no límite). O enderezo de antes leva á pestana.
    await page.goto('/naron/xestion/campos');
    await expect(page).toHaveURL(/\/naron\/xestion\/concello\/campos$/);
    await page.getByRole('button', { name: text(lang, 'customFields.admin.deleteNamed', { label }) }).click();
    const dialog = page.getByRole('alertdialog', {
      name: text(lang, 'customFields.admin.cannotDeleteTitle', { label }),
    });
    await expect(dialog).toBeVisible();
    await expectNoA11yViolations(page);
    await dialog.getByRole('button', { name: text(lang, 'customFields.admin.deactivate'), exact: true }).click();
    await expect(
      page.getByRole('status').filter({ hasText: text(lang, 'customFields.admin.deactivatedNotice', { label }) }),
    ).toBeVisible();
    await expect(
      page.getByRole('button', { name: text(lang, 'customFields.admin.activateNamed', { label }) }),
    ).toBeVisible();
  });

  test('campo da reserva: a veciña enche ao reservar e o persoal ve a resposta', async ({
    page,
    lang,
    request,
  }, testInfo) => {
    test.setTimeout(120_000);
    const code = `e2e_res_${testInfo.project.name.replace(/\W/g, '')}_${Date.now().toString(36)}`;
    const label = { gl: `Nivel ${code}`, es: `Nivel ${code} (es)` }[lang];

    // 1. O xestor créao na reserva: «Quen o ve» sen «Público»; escolle que o vexa quen reserva.
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await openSectionTab(page, lang, 'customFields');
    await page.getByRole('link', { name: text(lang, 'customFields.admin.new') }).click();
    await page.getByRole('radio', { name: text(lang, 'customFields.target.BOOKING') }).click();
    const visibility = page.locator('#def-visibility');
    await expect(visibility).toContainText(text(lang, 'customFields.visibilityBooking.STAFF'));
    await visibility.click();
    await expect(
      page.getByRole('option', { name: text(lang, 'customFields.visibility.PUBLIC'), exact: true }),
    ).toHaveCount(0);
    await page.getByRole('option', { name: text(lang, 'customFields.visibilityBooking.OWNER'), exact: true }).click();
    await expect(visibility).toContainText(text(lang, 'customFields.visibilityBooking.OWNER'));
    await page.locator('#def-labelGl').fill(`Nivel ${code}`);
    await page.locator('#def-labelEs').fill(`Nivel ${code} (es)`);
    await page.locator('#def-code').fill(code);
    await expectNoA11yViolations(page);
    await page.getByRole('button', { name: text(lang, 'common.save') }).click();
    await expect(page.getByRole('status').filter({ hasText: label })).toBeVisible();
    await logout(page, lang);
    await page.waitForURL((url) => !url.pathname.startsWith('/auth/'));

    // 2. A veciña: o campo sae no mesmo paso, antes de «Confirmar reserva» (a axuda, se a hai,
    // entre o rótulo e o campo).
    const day = bookingDay(FAR + 11, testInfo.project.name);
    const start = await firstSlotStart(request, PADEL.id, PADEL.use, day, 570);
    const date = start.slice(0, 10);
    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto(`/naron/instalacions/${PADEL.id}/reservar?uso=${PADEL.use}&data=${date}&hora=${hhmm(570)}`);
    const field = page.locator(`#reserve-cf-${code}`);
    await expect(page.locator(`label[for="reserve-cf-${code}"]`)).toContainText(label);
    await field.fill('Medio');
    await expectNoA11yViolations(page);
    await expectNoHorizontalScroll(page);
    await page.locator('[data-reserve-go]').click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.done.title') })).toBeVisible();
    await expect(page.locator(`[data-value="cf-${code}"]`)).toContainText('Medio');
    const booking = new URL(page.url()).searchParams.get('reserva');

    // 3. O persoal ve a resposta no detalle (API da xestión).
    const staff = await tokenFor(request, 'xestor-naron');
    const detail = await request.get(`/api/v1/municipalities/naron/bookings/${booking}`, { headers: staff.headers });
    expect(detail.ok()).toBe(true);
    expect(((await detail.json()) as { customFields: Record<string, unknown> }).customFields[code]).toBe('Medio');
  });

  test('sen permiso: o xestor de Narón non xestiona Ferrol e o veciño non xestiona Narón', async ({ page, lang }) => {
    await page.goto('/ferrol');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/ferrol/xestion');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'errorPage.403.title') })).toBeVisible();
    // «Sen permiso» mantén o URL pedido (as gardas da interface).
    await expect(page).toHaveURL(/\/ferrol\/xestion$/);
    await expectNoA11yViolations(page);
    await logout(page, lang);
    await page.waitForURL((url) => !url.pathname.startsWith('/auth/'));
    await expect(page.getByRole('button', { name: text(lang, 'session.login') })).toBeVisible();

    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto('/naron/xestion/campos');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'errorPage.403.title') })).toBeVisible();
  });

  test('pantalla de login de Keycloak en galego/castelán e accesible', async ({ page, lang }) => {
    await page.goto('/naron');
    await page.getByRole('button', { name: text(lang, 'session.login') }).click();
    await page.waitForURL(/\/auth\/realms\/furar\//);
    await expect(page.locator('html')).toHaveAttribute('lang', lang);
    await expectNoA11yViolations(page);
  });
});
