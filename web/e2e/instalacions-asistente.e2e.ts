// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Page } from '@playwright/test';
import { deleteE2eFacilities, expect, loginAs, test, text } from './fixtures';
import { checkStep, escape, hermetic } from './instalacions-axudas';

/**
 * Asistente «Nova instalación» (maqueta 53) de punta a punta: os oito pasos ata «Publicar» (a ficha
 * queda publicada) e o borrador co nome só (gardar ao saír, volver a el e descartalo). Axe en cada
 * paso e, en gl-mobile, 320 px sen scroll horizontal. Nada sae de localhost (`hermetic`: teselas
 * de proba e o buscador `fixed` da API); as instalacións da proba bórranse ao empezar e ao rematar.
 */
test.describe.configure({ mode: 'serial' });

const prefixOf = (project: string) => `Asistente e2e ${project} `;
const starts = (label: string) => new RegExp(`^${escape(label)}`);

async function openWizard(page: Page, lang: 'gl' | 'es'): Promise<void> {
  await page.goto('/naron');
  await loginAs(page, 'xestor-naron', lang);
  await page.goto('/naron/xestion/instalacions');
  await page.getByRole('button', { name: text(lang, 'facility.list.new') }).click();
  await expect(page).toHaveURL(/\/naron\/xestion\/instalacions\/nova$/);
  await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.wizard.title') })).toBeVisible();
}

/** O paso actual do indicador (o seu nome). */
async function expectStep(page: Page, lang: 'gl' | 'es', key: string): Promise<void> {
  const label = text(lang, `facility.wizard.steps.${key}`);
  // No móbil a lista vai oculta (vese «Paso N de 8»), pero segue no DOM co paso actual.
  await expect(page.locator('furar-stepper [aria-current="step"]')).toContainText(label);
}

test.describe('asistente «Nova instalación»', () => {
  test.beforeAll(async ({ request }, testInfo) => {
    await deleteE2eFacilities(request, prefixOf(testInfo.project.name));
  });

  test.afterAll(async ({ request }, testInfo) => {
    await deleteE2eFacilities(request, prefixOf(testInfo.project.name));
  });

  test('os oito pasos ata «Publicar»', async ({ page, lang }, testInfo) => {
    test.setTimeout(150_000);
    const outside = await hermetic(page);
    const name = `${prefixOf(testInfo.project.name)}${Date.now().toString(36)}`;
    await openWizard(page, lang);
    await checkStep(page);

    // 1. Nome e deportes
    await page.getByRole('textbox', { name: starts(text(lang, 'facility.wizard.data.name')) }).fill(name);
    await page.getByRole('combobox', { name: starts(text(lang, 'facility.data.sports.label')) }).fill('Bal');
    await page.getByRole('listbox').getByRole('option').first().click();
    await expect(page.getByRole('list', { name: text(lang, 'facility.data.sports.chosen') })).toBeVisible();
    await checkStep(page);
    await page.locator('[data-next]').click();

    // 2. Ubicación: o buscador enche os campos e a chincheta
    await expectStep(page, lang, 'location');
    await page.getByRole('combobox', { name: text(lang, 'facility.location.search') }).fill('Castela');
    await page.getByRole('option', { name: /Estrada de Castela/ }).click();
    await expect(page.getByRole('textbox', { name: starts(text(lang, 'facility.location.field.street')) })).toHaveValue(
      'Estrada de Castela',
    );
    await checkStep(page);
    await page.locator('[data-next]').click();

    // 3. Contacto e servizos (opcional): un teléfono e un servizo
    await expectStep(page, lang, 'contact');
    await page.getByRole('textbox', { name: starts(text(lang, 'facility.data.phone.label')) }).fill('981 000 000');
    await page.getByRole('checkbox', { name: text(lang, 'facility.amenities.item.CHANGING_ROOMS') }).check();
    await checkStep(page);
    await page.locator('[data-next]').click();

    // 4. Fotos (opcional): sen fotos, o debuxo suxerido xa vai marcado
    await expectStep(page, lang, 'photos');
    await expect(page.locator('[data-drawings] input[type="radio"]:checked')).toHaveCount(1);
    await checkStep(page);
    await page.locator('[data-skip]').click();

    // 5. Usos: o primeiro atallo dos deportes (ou «Outro») e «Engadir uso»
    await expectStep(page, lang, 'uses');
    const preset = page.locator('[data-preset]').first();
    if (await preset.count()) await preset.click();
    else await page.locator('[data-use-other]').click();
    const useName = page.locator('#wizard-use-name');
    if (!(await useName.inputValue())) await useName.fill('Pista enteira');
    await checkStep(page);
    await page.locator('[data-use-apply]').click();
    await expect(page.locator('.f-wizard-uses__row')).toHaveCount(1);
    await page.locator('[data-next]').click();

    // 6. Horario: «Todo o ano», o mesmo horario todos os días
    await expectStep(page, lang, 'schedule');
    await checkStep(page);
    await page.locator('[data-next]').click();

    // 7. Normas (opcional)
    await page.locator('[data-skip]').click();

    // 8. Revisar: sen nada pendente; «Publicar» leva á ficha publicada
    await expectStep(page, lang, 'review');
    await expect(page.locator('#wizard-missing')).toHaveCount(0);
    await checkStep(page);
    await page.locator('[data-publish]').click();
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();
    await expect(page.locator('furar-facility-draft-notice')).toHaveCount(0);
    await checkStep(page);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('borrador só co nome: gardalo ao saír, volver a el e descartalo', async ({ page, lang }, testInfo) => {
    test.setTimeout(90_000);
    const outside = await hermetic(page);
    const name = `${prefixOf(testInfo.project.name)}borrador ${Date.now().toString(36)}`;
    await openWizard(page, lang);
    await page.getByRole('textbox', { name: starts(text(lang, 'facility.wizard.data.name')) }).fill(name);

    // Saír a medias pregunta: seguir, descartar ou gardar o borrador
    await page.locator('[data-wizard-back]').click();
    const leave = page.getByRole('dialog', { name: text(lang, 'facility.wizard.leave.title') });
    await expect(leave).toBeVisible();
    await checkStep(page);
    await leave.locator('[data-save-draft]').click();
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions$/);
    const card = page.locator('[data-facility]').filter({ hasText: name });
    await expect(card.locator('[data-draft]')).toBeVisible();
    await expect(card.locator('[data-draft-step]')).toHaveText(
      text(lang, 'facility.list.draftStep', { step: 1, total: 8 }),
    );
    await checkStep(page);

    // A tarxeta volve ao asistente, no paso onde quedou
    await card.getByRole('link').click();
    await expect(page).toHaveURL(/\/instalacions\/nova\/[0-9a-f-]{36}$/);
    await expect(page.getByRole('textbox', { name: starts(text(lang, 'facility.wizard.data.name')) })).toHaveValue(
      name,
    );
    await expectStep(page, lang, 'data');

    // «Descartar» elimínao
    await page.locator('[data-wizard-back]').click();
    await page
      .getByRole('dialog', { name: text(lang, 'facility.wizard.leave.title') })
      .locator('[data-discard]')
      .click();
    await expect(page).toHaveURL(/\/naron\/xestion\/instalacions$/);
    await expect(page.locator('[data-facility]').filter({ hasText: name })).toHaveCount(0);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
});
