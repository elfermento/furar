// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, test, text } from './fixtures';

/**
 * Os campos propios públicos na tarxeta da ficha pública: enriba de «Reservar», catro
 * e «Ver os N datos». Os valores engádense á resposta da API (a demo non trae campos públicos).
 */
test('ficha pública: campos propios na tarxeta, «Ver os N datos» co teclado; accesible', async ({ page, lang }) => {
  const label = (value: string) => ({ gl: value, es: value });
  await page.route(/\/api\/v1\/municipalities\/naron\/public\/facilities\/[0-9a-f-]+$/, async (route) => {
    const response = await route.fetch();
    const body = await response.json();
    body.customFieldDefinitions = ['pavimento', 'iluminacion', 'bancada', 'revision', 'notas'].map((code) => ({
      code,
      label: label(code),
      type: code === 'revision' ? 'DATE' : 'TEXT',
      options: [],
    }));
    body.customFields = {
      pavimento: 'Herba artificial',
      iluminacion: 'LED',
      bancada: '300 prazas',
      revision: '2026-03-12',
      notas: 'Sen tacos de aluminio',
    };
    await route.fulfill({ response, json: body });
  });
  await page.goto('/naron/instalacions');
  await page.locator('furar-facility-card').first().getByRole('link').first().click();
  const card = page.locator('furar-public-facility-card');
  const fields = card.locator('[data-public-fields]');
  await expect(fields).toBeVisible();
  await expect(fields.locator('dt')).toHaveCount(4);
  const more = fields.getByRole('button', { name: text(lang, 'facility.public.fields.more', { count: 5 }) });
  await more.focus();
  await page.keyboard.press('Enter');
  await expect(fields.locator('dt')).toHaveCount(5);
  await expect(fields.getByRole('button', { name: text(lang, 'facility.public.fields.less') })).toHaveAttribute(
    'aria-expanded',
    'true',
  );
  await expectNoA11yViolations(page);
  await expectNoHorizontalScroll(page);
});
