// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, loginAs, test, text } from './fixtures';

/**
 * Incidencias (ADR-0036): a veciñanza avisa dun problema desde a ficha cunha foto; a
 * xestión vea na súa pestana, cambia o estado co chip e resólvea cunha mensaxe; Recepción tamén ve
 * e cambia; a xestión elimina (con confirmación e rastro no rexistro); no móbil (320 px) nada desborda. Con axe en cada pantalla. Na demo, Narón.
 */

/** Pista de pádel 2 de O Alto (R__demo_municipality_facilities.sql). */
const FACILITY = '/naron/instalacions/019a0b6e-0000-7000-8000-000000000203';

/** Un PNG de 1×1 px. */
const PNG = Buffer.from(
  'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==',
  'base64',
);

test.describe('incidencias', () => {
  test('a veciñanza avisa cunha foto desde a ficha', async ({ page, lang }) => {
    await page.goto(FACILITY);
    await loginAs(page, 'vecino-naron', lang);
    await page.goto(FACILITY);
    const block = page.locator('[data-report-problem]');
    await expect(block).toContainText(text(lang, 'incident.report.question'));
    await block.getByRole('button', { name: text(lang, 'incident.report.open') }).click();

    const dialog = page.getByRole('dialog', { name: text(lang, 'incident.report.title') });
    await expect(dialog).toBeVisible();
    await expectNoA11yViolations(page);
    await dialog.locator('[data-incident-type] button').click();
    await page.getByRole('option', { name: text(lang, 'incident.types.BREAKDOWN') }).click();
    // O «?» do campo («Axuda sobre «Descrición»») tamén leva o nome: o campo polo seu rol.
    await dialog
      .getByRole('textbox', { name: text(lang, 'incident.report.description') })
      .fill('A rede está rota (proba e2e).');
    await dialog.locator('input[type="file"]').setInputFiles({ name: 'rede.png', mimeType: 'image/png', buffer: PNG });
    await expect(dialog.locator('.f-incident-photos__item img')).toHaveCount(1);
    await dialog.getByRole('button', { name: text(lang, 'incident.report.send') }).click();
    await expect(dialog).toBeHidden();
    await expect(page.locator('furar-toast')).toContainText(text(lang, 'incident.report.sent'));
  });

  test('a xestión cambia o estado co chip e resolve cunha mensaxe', async ({ page, lang }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/incidencias');
    const table = page.locator('[data-incidents-table]');
    await expect(table.locator('tbody tr').first()).toBeVisible();
    await expect(
      page.locator('[data-manage-tabs]').getByRole('link', { name: new RegExp(text(lang, 'nav.incidents')) }),
    ).toHaveAttribute('aria-current', 'page');
    await expectNoA11yViolations(page);

    const row = table.locator('tbody tr', { has: page.locator('[data-status="NEW"]') }).first();
    await row.locator('[data-cell="facility"] button').click();
    const dialog = page.getByRole('dialog');
    await expect(dialog.locator('[data-incident-status]')).toBeVisible();
    await expectNoA11yViolations(page);

    await dialog.locator('[data-incident-status] button').click();
    await page.getByRole('menuitemradio', { name: text(lang, 'incident.status.RESOLVED') }).click();
    const message = dialog.locator('[data-incident-message]');
    await expect(message).toBeVisible();
    await message.fill('Cambiamos a rede esta mañá.');
    await dialog.locator('[data-incident-note]').fill('Avisado o servizo de mantemento.');
    await dialog.getByRole('button', { name: text(lang, 'incident.detail.save') }).click();
    await expect(dialog).toBeHidden();
    await expect(page.locator('furar-toast')).toContainText(text(lang, 'incident.detail.saved'));
  });

  test('a xestión elimina unha incidencia, con confirmación; queda no rexistro', async ({ page, lang }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/incidencias');
    const rows = page.locator('[data-incidents-table] tbody tr');
    await expect(rows.first()).toBeVisible();
    const before = await rows.count();
    await rows.first().locator('[data-cell="facility"] button').click();
    const dialog = page.getByRole('dialog');
    await dialog.locator('[data-incident-delete] button').click();
    const confirm = page.getByRole('alertdialog', { name: text(lang, 'incident.detail.delete.heading') });
    await expectNoA11yViolations(page);
    await confirm.getByRole('button', { name: text(lang, 'incident.detail.delete.confirm') }).click();
    await expect(page.locator('furar-toast')).toContainText(text(lang, 'incident.detail.delete.done'));
    await expect(rows).toHaveCount(before - 1);
    await page.goto('/naron/xestion/concello/rexistro');
    await expect(page.getByText(text(lang, 'activity.done.incident.delete')).first()).toBeVisible();
  });

  test('Recepción ve a lista e cambia un estado', async ({ page, lang }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await page.goto('/naron');
    await loginAs(page, 'recepcion-naron', lang);
    await page.goto('/naron/xestion/incidencias');
    // Unha aberta (Nova ou En curso): pásase á outra e despois vólvese deixar como estaba, para que
    // a proba se poida repetir sobre a mesma base (gl, es e gl-mobile).
    const open = page.locator('[data-incidents-table] tbody tr', {
      has: page.locator('[data-status="IN_PROGRESS"], [data-status="NEW"]'),
    });
    await expect(open.first()).toBeVisible();
    const id = await open.first().getAttribute('data-incident');
    const row = page.locator(`[data-incidents-table] tbody tr[data-incident="${id}"]`);
    const before = (await row.locator('[data-status]').getAttribute('data-status')) as 'NEW' | 'IN_PROGRESS';
    const after = before === 'NEW' ? 'IN_PROGRESS' : 'NEW';
    const dialog = page.getByRole('dialog');
    for (const status of [after, before]) {
      await row.locator('[data-cell="facility"] button').click();
      await dialog.locator('[data-incident-status] button').click();
      await page.getByRole('menuitemradio', { name: text(lang, `incident.status.${status}`) }).click();
      await dialog.getByRole('button', { name: text(lang, 'incident.detail.save') }).click();
      await expect(dialog).toBeHidden();
      await expect(row.locator('[data-status]')).toHaveAttribute('data-status', status);
      if (status === after) await expectNoA11yViolations(page);
    }
  });

  test('móbil a 320 px: tarxetas e «Filtros» sen desbordar; «Nova incidencia» do persoal', async ({ page, lang }) => {
    await page.setViewportSize({ width: 320, height: 720 });
    await page.goto('/naron');
    await loginAs(page, 'xestor-naron', lang);
    await page.goto('/naron/xestion/incidencias');
    await expect(page.locator('[data-incident-card]').first()).toBeVisible();
    await expect(page.locator('[data-incidents-table]')).toBeHidden();
    const overflow = await page.evaluate(
      () => document.documentElement.scrollWidth - document.documentElement.clientWidth,
    );
    expect(overflow).toBeLessThanOrEqual(0);
    await expectNoA11yViolations(page);

    await page.locator('[data-incident-new]').click();
    const dialog = page.getByRole('dialog', { name: text(lang, 'incident.report.newTitle') });
    await expect(dialog.locator('[data-incident-facility]')).toBeVisible();
    const inDialog = await dialog.evaluate((el) => el.scrollWidth - el.clientWidth);
    expect(inDialog).toBeLessThanOrEqual(0);
    // No móbil é unha folla: chega aos dous bordos aínda que o formulario sexa estreito.
    const sheet = await page.locator('.f-dialog-pane--sheet .f-dialog').boundingBox();
    expect(Math.round(sheet?.width ?? 0), 'ancho da folla').toBe(320);
    await expectNoA11yViolations(page);
  });
});
