// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Route } from '@playwright/test';
import { deleteE2eFacilities, expect, loginAs, test, text } from './fixtures';
import {
  AFFECTED,
  PHOTO,
  checkStep,
  escape,
  fieldButtonName,
  publishFromAddress,
  hermetic,
  isoDay,
  pickDate,
  tabTo,
} from './instalacions-axudas';

/**
 * Instalacións do xestor de punta a punta (plan do dominio 1, D1-25): alta no asistente só co nome →
 * borrador → no modo edición da ficha, usos, deportes e horario → «Gardar» → o enderezo e «Publicar»
 * no asistente; editar Datos; tempada que
 * pisa outra; excepción; bloqueo co modal de reservas afectadas só con teclado; foto; enderezo;
 * baixa e eliminar. Axe en cada paso, cos temas de Narón e Ferrol, e 320 px sen scroll horizontal
 * (gl-mobile). A instalación da proba elimínase ao final (e as que deixase unha execución
 * interrompida, ao empezar): a base da demo queda como estaba.
 *
 * Nada sae de localhost: as teselas (`/tiles`) respóndense aquí cun PNG de proba, o buscador de
 * enderezos é o `fixed` da API (`FURAR_GEOCODER=fixed` no entorno do e2e) e calquera outra
 * petición a fóra abórtase e fai fallar a proba. O dominio 1 aínda non ten reservas
 * (`NoBookingImpact`): o 409 `facility.affected-bookings` do bloqueo simúlao a proba, e o reenvío
 * coas decisións chega á API sen elas, como faría `booking` se as aceptase.
 */
test.describe.configure({ mode: 'serial' });

/** Ficha da instalación creada nesta execución, por proxecto (as probas van en serie). */
let facilityPath = '';
let facilityName = '';

/** Nome das instalacións que crea este proxecto (para atopalas e borralas). */
const prefixOf = (project: string) => `Pavillón e2e ${project} `;

test.describe('instalacións do xestor', () => {
  test.beforeAll(async ({ request }, testInfo) => {
    await deleteE2eFacilities(request, prefixOf(testInfo.project.name));
  });

  test.afterAll(async ({ request }, testInfo) => {
    await deleteE2eFacilities(request, prefixOf(testInfo.project.name));
  });

  test('alta co nome → borrador → usos, deportes e horario na ficha → publicar no asistente', async ({
    page,
    lang,
  }, testInfo) => {
    test.setTimeout(150_000);
    const outside = await hermetic(page);
    facilityName = `${prefixOf(testInfo.project.name)}${Date.now().toString(36)}`;

    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);

    // 1. Listado
    await page
      .getByRole('navigation', { name: text(lang, 'app.manageNav') })
      .getByRole('link', { name: text(lang, 'nav.facilities') })
      .click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.list.title') })).toBeVisible();
    await expect(page.locator('[data-facility]').first()).toBeVisible();
    await checkStep(page);

    // 2. «Nova instalación» abre o asistente nunha páxina; co nome abonda para gardar o borrador
    await page.getByRole('button', { name: text(lang, 'facility.list.new') }).click();
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions\/nova$/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.wizard.title') })).toBeVisible();
    await page
      .getByRole('textbox', { name: new RegExp(`^${escape(text(lang, 'facility.wizard.data.name'))}`) })
      .fill(facilityName);
    await checkStep(page);
    await page.locator('[data-draft]').click();
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions$/);
    const draftCard = page.locator('[data-facility]').filter({ hasText: facilityName });
    await expect(draftCard.locator('[data-draft-step]')).toHaveText(
      text(lang, 'facility.list.draftStep', { step: 1, total: 8 }),
    );
    const wizardHref = await draftCard.getByRole('link').getAttribute('href');
    expect(wizardHref).toMatch(/\/instalacions\/nova\/[0-9a-f-]{36}$/);

    // 3. A ficha do borrador: falta deportes, usos e horario, e aínda non está publicada
    await page.goto((wizardHref ?? '').replace('/nova/', '/'));
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions\/[0-9a-f-]{36}$/);
    facilityPath = new URL(page.url()).pathname;
    await expect(page.getByRole('heading', { level: 1, name: facilityName })).toBeVisible();
    const draft = page.locator('furar-facility-draft-notice');
    await expect(draft).toContainText(text(lang, 'facility.detail.draft.title'));
    for (const item of ['SPORTS', 'USES', 'SCHEDULE']) {
      await expect(
        draft.getByRole('link', { name: text(lang, `facility.detail.draft.missing.${item}`) }),
      ).toBeVisible();
    }
    await expect(draft.locator('[data-unpublished]')).toBeVisible();
    await checkStep(page);

    // 4. «Editar» pon a ficha en edición coa barra fixa. Uso novo (enteira, 60 min, sempre) no seu modal
    await page.locator('[data-edit]').click();
    const bar = page.locator('furar-action-bar');
    await expect(
      bar.getByRole('region', { name: text(lang, 'facility.detail.edit.bar', { name: facilityName }) }),
    ).toBeVisible();
    await draft.getByRole('link', { name: text(lang, 'facility.detail.draft.missing.USES') }).click();
    await expect(page.locator('#facility-uses-title')).toBeFocused();
    await page.locator('[data-use-add]').click();
    const useDialog = page.getByRole('dialog', { name: text(lang, 'facility.uses.add') });
    await useDialog.getByRole('textbox', { name: text(lang, 'facility.uses.name') }).fill('Pista completa');
    await checkStep(page);
    await useDialog.locator('[data-use-apply]').click();
    await expect(useDialog).toBeHidden();
    await expect(page.locator('[data-use-edit]')).toContainText('Pista completa');

    // 5. Datos: un deporte do catálogo (o campo xa está aberto no modo edición)
    const data = page.locator('furar-facility-data-card');
    const sports = data.getByRole('combobox', { name: text(lang, 'facility.data.sports.add') });
    await sports.fill('Fút');
    await data.getByRole('listbox').getByRole('option').first().click();
    await expect(data.getByRole('list', { name: text(lang, 'facility.data.sports.chosen') })).toBeVisible();
    await checkStep(page);

    // 6. Horario: «+» no selector de tempadas (desde hoxe) e o luns aberto (9:00–14:00, o proposto)
    const schedule = page.locator('furar-facility-schedule');
    await schedule.locator('[data-season-add]').click();
    const seasonDialog = page.getByRole('dialog', { name: text(lang, 'facility.schedule.seasonDialog.addTitle') });
    await seasonDialog
      .getByRole('textbox', { name: text(lang, 'facility.schedule.seasonDialog.name') })
      .fill('Inverno');
    await pickDate(page, seasonDialog, text(lang, 'facility.schedule.seasonDialog.from'), isoDay(0), lang);
    await pickDate(page, seasonDialog, text(lang, 'facility.schedule.seasonDialog.to'), isoDay(60), lang);
    await checkStep(page);
    await seasonDialog.locator('[data-save]').click();
    await expect(seasonDialog).toBeHidden();
    await expect(schedule.getByRole('button', { name: 'Inverno', exact: true })).toHaveAttribute(
      'aria-pressed',
      'true',
    );
    await schedule.locator('[data-day-edit="0"]').click();
    await schedule.locator('[data-day-row="0"]').getByRole('switch').click();
    await expect(schedule.locator('[data-day-row="0"] [data-interval]')).toHaveCount(1);
    await checkStep(page);

    // «Gardar» na barra: garda cada parte co seu If-Match e sae da edición
    await bar.locator('[data-edit-save]').click();
    await expect(bar).toHaveCount(0);
    await expect(page.locator('[data-edit]')).toBeFocused();
    // A tempada gardada empeza hoxe: é a vixente e leva «Agora»
    await expect(schedule.locator('[data-season-now]')).toHaveText(text(lang, 'facility.schedule.seasons.now'));

    // 7. Xa non lle falta nada, pero segue sen publicar: «Revisar e publicar» volve ao asistente,
    // que segue no enderezo (o borrador gardouse só co nome)
    await expect(draft.getByRole('link', { name: text(lang, 'facility.detail.draft.missing.USES') })).toHaveCount(0);
    await draft.locator('[data-wizard]').click();
    await expect(page).toHaveURL(/\/instalacions\/nova\/[0-9a-f-]{36}$/);
    await publishFromAddress(page, lang);

    // 8. Publicada: a ficha sen aviso de borrador e o listado sen etiqueta
    await expect(page).toHaveURL(new RegExp(`${escape(facilityPath)}$`));
    await expect(draft).toHaveCount(0);
    await checkStep(page);
    await page.getByRole('link', { name: text(lang, 'facility.detail.back') }).click();
    const card = page.locator('[data-facility]').filter({ hasText: facilityName });
    await expect(card).toBeVisible();
    await expect(card.locator('[data-draft]')).toHaveCount(0);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('editar Datos, tempada solapada e excepción', async ({ page, lang }) => {
    test.setTimeout(120_000);
    expect(facilityPath, 'ficha da proba anterior').not.toBe('');
    const outside = await hermetic(page);
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto(facilityPath);
    await expect(page.getByRole('heading', { level: 1, name: facilityName })).toBeVisible();

    // Datos: teléfono inválido → erro no campo e non se garda nada; válido → gardado e en texto
    await page.locator('[data-edit]').click();
    const bar = page.locator('furar-action-bar');
    const data = page.locator('furar-facility-data-card');
    const phoneLabel = text(lang, 'facility.data.phone.label');
    const phone = data.getByRole('textbox', { name: phoneLabel });
    await phone.fill('981 abc');
    await bar.locator('[data-edit-save]').click();
    await expect(phone).toHaveAttribute('aria-invalid', 'true');
    await expect(phone).toBeFocused();
    await checkStep(page);
    await phone.fill('981 000 000');
    await bar.locator('[data-edit-save]').click();
    await expect(bar).toHaveCount(0);
    await expect(data.locator('.f-edit-field__static').filter({ hasText: phoneLabel })).toContainText('981 000 000');

    // Tempada que pisa «Inverno»: o aviso no diálogo, que segue aberto
    await page.locator('[data-edit]').click();
    const schedule = page.locator('furar-facility-schedule');
    await schedule.locator('[data-season-add]').click();
    const seasonDialog = page.getByRole('dialog', { name: text(lang, 'facility.schedule.seasonDialog.addTitle') });
    await seasonDialog
      .getByRole('textbox', { name: text(lang, 'facility.schedule.seasonDialog.name') })
      .fill('Solapada');
    await pickDate(page, seasonDialog, text(lang, 'facility.schedule.seasonDialog.from'), isoDay(10), lang);
    await pickDate(page, seasonDialog, text(lang, 'facility.schedule.seasonDialog.to'), isoDay(20), lang);
    await seasonDialog.locator('[data-save]').click();
    await expect(seasonDialog.getByRole('alert')).toContainText(
      text(lang, 'errors.facility.season-overlap', { name: 'Inverno' }),
    );
    await checkStep(page);
    await seasonDialog.locator('[data-cancel]').click();
    await expect(seasonDialog).toBeHidden();

    // Excepción: un día pechado (o caso por defecto) cun motivo, no seu modal; «Gardar» na barra
    await schedule.locator('[data-exception-add]').click();
    const exception = page.getByRole('dialog', { name: text(lang, 'facility.schedule.exceptions.addTitle') });
    await pickDate(page, exception, text(lang, 'facility.schedule.exceptions.date'), isoDay(7), lang);
    await exception
      .getByRole('textbox', { name: text(lang, 'facility.schedule.exceptions.reason') })
      .fill('Festa local');
    await checkStep(page);
    await exception.locator('[data-exception-apply]').click();
    await expect(exception).toBeHidden();
    await expect(schedule.locator('[data-exception-id]')).toHaveCount(1);
    await expect(schedule.locator('[data-exception-id]').first()).toBeFocused();
    await checkStep(page);
    await bar.locator('[data-edit-save]').click();
    await expect(bar).toHaveCount(0);
    await expect(schedule.locator('furar-facility-exception-list furar-row')).toContainText('Festa local');
    await checkStep(page);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('bloqueo con reservas afectadas, só con teclado', async ({ page, lang }) => {
    test.setTimeout(120_000);
    expect(facilityPath, 'ficha da primeira proba').not.toBe('');
    const outside = await hermetic(page);
    const blockDay = isoDay(3);
    const affected = AFFECTED.map((booking) => ({ ...booking, date: blockDay }));
    const sent: unknown[] = [];
    // Primeiro POST: o 409 que devolverá `booking` no dominio 2. Reenvío: chega á API sen decisións.
    await page.route(/\/api\/v1\/municipalities\/naron\/facilities\/[^/]+\/blocks$/, async (route: Route) => {
      if (route.request().method() !== 'POST') return route.continue();
      const body = route.request().postDataJSON() as { decisions?: unknown[] };
      if (!body.decisions?.length) {
        return route.fulfill({
          status: 409,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'about:blank',
            title: 'Conflict',
            status: 409,
            code: 'facility.affected-bookings',
            affected,
          }),
        });
      }
      sent.push(body.decisions);
      return route.continue({ postData: JSON.stringify({ ...body, decisions: [] }) });
    });

    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto(facilityPath);
    await expect(page.getByRole('heading', { level: 1, name: facilityName })).toBeVisible();

    // Modo edición e bloqueo novo (9:00–14:00 por defecto) cun motivo, todo con teclado
    const edit = page.locator('[data-edit]');
    await edit.focus();
    await page.keyboard.press('Enter');
    const bar = page.locator('furar-action-bar');
    await expect(bar).toBeVisible();
    const blocks = page.locator('furar-facility-blocks');
    const add = blocks.locator('[data-block-add]');
    await add.focus();
    await page.keyboard.press('Enter');
    const form = page.getByRole('dialog', { name: text(lang, 'facility.blocks.addTitle') });
    await expect(form).toBeVisible();
    const dateButton = form.getByRole('button', { name: fieldButtonName(text(lang, 'facility.blocks.date'), lang) });
    await tabTo(page, dateButton);
    await page.keyboard.press('Enter');
    const panel = page.getByRole('dialog', {
      name: text(lang, 'ui.dateField.dialog', { label: text(lang, 'facility.blocks.date') }),
    });
    await expect(panel).toBeVisible();
    // O calendario abre en hoxe: tres días despois coa frecha dereita (cambia de mes se toca).
    await expect(panel.locator('td[tabindex="0"]')).toBeFocused();
    for (let i = 1; i <= 3; i++) {
      await page.keyboard.press('ArrowRight');
      await expect(panel.locator(`td[data-date="${isoDay(i)}"]`)).toBeFocused();
    }
    await page.keyboard.press('Enter');
    await expect(panel).toBeHidden();
    await expect(dateButton).toBeFocused();
    const reason = form.getByRole('textbox', { name: text(lang, 'facility.blocks.reason') });
    await tabTo(page, reason);
    await page.keyboard.type('Mantemento');
    await page.keyboard.press('Enter');
    await expect(form).toBeHidden();
    await expect(blocks.locator('[data-block-id]').first()).toBeFocused();
    // «Gardar» na barra, co teclado
    await tabTo(page, bar.locator('[data-edit-save]'), 200);
    await page.keyboard.press('Enter');

    // Modal: dúas reservas; a primeira cancélase (avísase por correo), a segunda mantense
    const modal = page.getByRole('dialog', {
      name: text(lang, 'facility.affectedBookings.titleOther', { count: 2 }),
    });
    await expect(modal).toBeVisible();
    await expect(modal.getByText('Uxía Bouza')).toBeVisible();
    await checkStep(page);
    // Confirmar sen decidir: aviso e o foco segue no modal
    const confirm = modal.locator('[data-confirm]');
    await tabTo(page, confirm);
    await page.keyboard.press('Enter');
    await expect(modal.getByRole('alert')).toContainText(
      text(lang, 'facility.affectedBookings.incompleteOther', { count: 2 }),
    );
    await checkStep(page);
    const first = modal.locator(`[data-booking="${affected[0]!.bookingId}"]`);
    const second = modal.locator(`[data-booking="${affected[1]!.bookingId}"]`);
    const cancelFirst = first.getByRole('radio', { name: text(lang, 'facility.affectedBookings.cancel') });
    await cancelFirst.focus();
    await page.keyboard.press('Space');
    await expect(cancelFirst).toHaveAttribute('aria-checked', 'true');
    await expect(
      first.getByRole('checkbox', { name: text(lang, 'facility.affectedBookings.channel.EMAIL') }),
    ).toBeChecked();
    // Só correo e aviso na web: a reserva non garda teléfono (decisión do 2026-11-04).
    await expect(first.locator('input[data-channel]')).toHaveCount(2);
    const cancelSecond = second.getByRole('radio', { name: text(lang, 'facility.affectedBookings.cancel') });
    await tabTo(page, cancelSecond);
    await page.keyboard.press('ArrowRight');
    const keepSecond = second.getByRole('radio', { name: text(lang, 'facility.affectedBookings.keep') });
    await expect(keepSecond).toBeFocused();
    await expect(keepSecond).toHaveAttribute('aria-checked', 'true');
    await expect(second).toContainText(text(lang, 'facility.affectedBookings.keepNoNotice'));
    await checkStep(page);
    await tabTo(page, confirm);
    await page.keyboard.press('Enter');
    await expect(modal).toBeHidden();

    // O reenvío levou as decisións; o bloqueo queda na lista co foco nel
    expect(sent).toEqual([
      [
        { bookingId: affected[0]!.bookingId, action: 'CANCEL', channels: ['EMAIL'] },
        { bookingId: affected[1]!.bookingId, action: 'KEEP', channels: [] },
      ],
    ]);
    await expect(bar).toHaveCount(0);
    await expect(edit).toBeFocused();
    await expect(blocks.locator('furar-row')).toHaveCount(1);
    await expect(blocks).toContainText('Mantemento');
    await checkStep(page);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('foto, enderezo, baixa e eliminar', async ({ page, lang }) => {
    test.setTimeout(120_000);
    expect(facilityPath, 'ficha da primeira proba').not.toBe('');
    const outside = await hermetic(page);
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto(facilityPath);
    await expect(page.getByRole('heading', { level: 1, name: facilityName })).toBeVisible();

    // Fotos (modo edición): biblioteca (subir unha e marcar un debuxo), orde con teclado e texto en
    // gl e es; «Aceptar» déixao na ficha e «Gardar» na barra gárdao
    await page.locator('[data-edit]').click();
    const bar = page.locator('furar-action-bar');
    const photos = page.locator('furar-facility-photos');
    await photos.locator('[data-change]').first().click();
    const dialog = page.getByRole('dialog', { name: facilityName });
    await expect(dialog).toBeVisible();
    await expect(dialog.locator('.f-photos__thumb')).toHaveCount(1); // o debuxo suxerido
    await checkStep(page);
    await dialog.getByRole('button', { name: text(lang, 'facility.photos.addLabel') }).click();
    const library = page.getByRole('dialog', { name: text(lang, 'facility.photos.library.title') });
    await expect(library).toBeVisible();
    await library
      .locator('#photo-library-file')
      .setInputFiles({ name: 'pista.jpg', mimeType: 'image/jpeg', buffer: PHOTO });
    await expect(library.locator('.f-library__pick[aria-pressed="true"]')).toHaveCount(1);
    await library.getByRole('tab', { name: text(lang, 'facility.photos.library.drawings') }).click();
    await library.locator('[data-key="DRAWING:pool"]').click();
    await checkStep(page);
    await library.locator('[data-add-selected]').click();
    await expect(library).toBeHidden();
    const thumbs = dialog.locator('.f-photos__thumb');
    await expect(thumbs).toHaveCount(2);
    await expect(dialog.locator('[data-changes]')).toContainText(text(lang, 'facility.photos.changes', { count: 2 }));
    // Alt + → leva a foto ao segundo posto e o debuxo pasa a portada; volve con Alt + ←.
    await thumbs.first().focus();
    await page.keyboard.press('Alt+ArrowRight');
    await expect(thumbs.first()).toHaveAttribute('data-key', 'DRAWING:pool');
    await page.keyboard.press('Alt+ArrowLeft');
    await expect(thumbs.first()).not.toHaveAttribute('data-key', 'DRAWING:pool');
    await thumbs.first().click();
    const alt = dialog.locator('#facility-photo-alt');
    await dialog.locator('button[data-lang="gl"]').click();
    await expect(alt).toHaveAttribute('lang', 'gl');
    await alt.fill('Pista de pádel con cristaleiras');
    await dialog.locator('button[data-lang="es"]').click();
    await expect(alt).toHaveAttribute('lang', 'es');
    await alt.fill('Pista de pádel con cristaleras');
    await checkStep(page);
    await dialog.locator('[data-save]').click();
    await expect(dialog).toBeHidden();

    // Enderezo no mesmo modo edición: buscar outro (fixed)
    const location = page.locator('furar-facility-location');
    await location.getByRole('combobox', { name: text(lang, 'facility.location.search') }).fill('Buxo');
    await location.getByRole('option', { name: /Rúa Buxo/ }).click();
    await expect(location.getByRole('textbox', { name: text(lang, 'facility.location.field.postalCode') })).toHaveValue(
      '15572',
    );
    await checkStep(page);
    await bar.locator('[data-edit-save]').click();
    await expect(bar).toHaveCount(0);
    const img = photos.locator('[data-photo] img').first();
    await expect(img).toHaveAttribute(
      'alt',
      lang === 'es' ? 'Pista de pádel con cristaleras' : 'Pista de pádel con cristaleiras',
    );
    await expect.poll(() => img.evaluate((el: HTMLImageElement) => el.naturalWidth)).toBeGreaterThan(0);
    await expect(photos.locator('[data-photo] img')).toHaveCount(2);
    await expect(page.locator('.f-page-header__intro')).toContainText('Rúa Buxo, 16');
    await checkStep(page);

    // Baixa desde o listado, con confirmación (P17): queda «De baixa»
    await page.getByRole('link', { name: text(lang, 'facility.detail.back') }).click();
    const card = page.locator('[data-facility]').filter({ hasText: facilityName });
    await expect(card).toContainText('Rúa Buxo, 16');
    const statusLabel = text(lang, 'facility.list.statusOf', { name: facilityName });
    await card.getByRole('button', { name: new RegExp(`^${escape(statusLabel)}`) }).click();
    await page.getByRole('menuitemradio', { name: text(lang, 'facility.list.status.WITHDRAWN') }).click();
    const confirm = page.getByRole('alertdialog', { name: text(lang, 'facility.list.withdraw.title') });
    await expect(confirm).toBeVisible();
    await checkStep(page);
    await confirm.getByRole('button', { name: text(lang, 'facility.list.withdraw.action') }).click();
    await expect(
      card.getByRole('button', {
        name: text(lang, 'ui.chipMenu.name', {
          label: statusLabel,
          value: text(lang, 'facility.list.status.WITHDRAWN'),
        }),
      }),
    ).toBeVisible();
    await checkStep(page);

    // Eliminar desde a ficha: hai que escribir a frase; volve ao listado e xa non está
    await card.getByRole('link').first().click();
    await expect(page.getByRole('heading', { level: 1, name: facilityName })).toBeVisible();
    await page.locator('[data-delete] button').click();
    const remove = page.getByRole('alertdialog', { name: text(lang, 'facility.detail.delete.title') });
    await expect(remove).toBeVisible();
    const accept = remove.locator('[data-confirm-accept]');
    await expect(accept).toBeDisabled();
    await remove
      .getByRole('textbox', {
        name: text(lang, 'ui.confirm.typeToConfirm', { phrase: text(lang, 'facility.detail.delete.phrase') }),
      })
      .fill(text(lang, 'facility.detail.delete.phrase'));
    await checkStep(page);
    await accept.click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.list.title') })).toBeVisible();
    await expect(page.locator('[data-facility]').filter({ hasText: facilityName })).toHaveCount(0);
    facilityPath = '';

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
});
