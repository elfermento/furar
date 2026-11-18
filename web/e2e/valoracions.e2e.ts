// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expect, expectNoA11yViolations, expectNoHorizontalScroll, loginAs, test, text } from './fixtures';
import { PAVILION, SLUG } from './reservas-axudas';

/**
 * Valoracións de punta a punta (ADR-0037): «Valorar» no detalle dunha reserva do historial, a
 * fila «Valoración» da ficha pública coa capa e «Ordenar por», e a tarxeta «Valoracións» dos
 * indicadores con «Ocultar comentario» (e volver mostralo, para deixar a demo como estaba). As
 * valoracións de exemplo do pavillón veñen de `R__demo_naron_reviews.sql`. Axe en cada paso.
 */
test.describe.configure({ mode: 'serial' });

test('a veciñanza valora unha reserva pasada desde o historial', async ({ page, lang }) => {
  await page.goto(`/${SLUG}`);
  await loginAs(page, 'vecino-naron', lang);
  await page.goto(`/${SLUG}/reservas`);
  await page.getByRole('tab', { name: text(lang, 'booking.mine.history') }).click();
  await expect(page.locator('furar-booking-card').first()).toBeVisible();
  // «Valorar» está no detalle: ábrense as realizadas sen estrelas ata dar cunha valorable. As
  // canceladas doutras probas (futuras) van primeiro: «Ver máis» se fai falta.
  const candidates = page.locator(
    'furar-booking-card[data-past]:not(:has([data-booking-reviewed])):not(:has([data-chip="cancelled"], [data-chip="toRefund"], [data-chip="refunded"]))',
  );
  const reviewed = page.locator('furar-booking-card [data-booking-reviewed]');
  const more = page.locator('[data-mine-more]');
  const detail = page.getByRole('dialog').last();
  let id: string | null = null;
  let tried = 0;
  await expect
    .poll(
      async () => {
        const count = await candidates.count();
        for (; tried < count && !id; tried++) {
          const card = candidates.nth(tried);
          await card.locator('.f-status-card__open').click();
          await expect(detail).toBeVisible();
          if (await detail.locator('[data-booking-review]').count()) {
            id = await card.getAttribute('data-booking');
          } else {
            await page.keyboard.press('Escape');
            await expect(detail).toBeHidden();
          }
        }
        if (!id && (await more.isVisible())) await more.click();
        return !!id || ((await reviewed.count()) > 0 && !(await more.isVisible()));
      },
      { timeout: 30_000 },
    )
    .toBe(true);
  // Cada proxecto valora unha; se a demo xa non ten ningunha por valorar, quedan as estrelas.
  if (!id) {
    await expect(reviewed.first()).toBeVisible();
    return;
  }
  const card = page.locator(`furar-booking-card[data-booking="${id}"]`);
  await expectNoA11yViolations(page);
  await detail.locator('[data-booking-review]').click();
  const dialog = page.getByRole('dialog', { name: text(lang, 'reviews.dialog.title') });
  await expect(dialog).toBeVisible();
  await expectNoA11yViolations(page);
  await expectNoHorizontalScroll(page);

  await dialog.getByRole('button', { name: text(lang, 'reviews.dialog.send') }).click();
  await expect(dialog.locator('.f-field-error')).toHaveText(text(lang, 'reviews.dialog.starsRequired'));
  await dialog.getByRole('radio', { name: text(lang, 'ui.stars.option', { count: 4 }) }).check();
  await dialog.getByRole('textbox', { name: text(lang, 'reviews.dialog.comment') }).fill('Boa pista (e2e).');
  await dialog.getByRole('button', { name: text(lang, 'reviews.dialog.send') }).click();
  await expect(dialog).toBeHidden();
  await expect(page.getByText(text(lang, 'reviews.thanks'))).toBeVisible();
  await expect(card.locator('[data-booking-reviewed]')).toHaveAttribute(
    'aria-label',
    text(lang, 'ui.stars.label', { count: 4 }),
  );
  // No detalle xa non hai «Valorar», e si «A túa valoración».
  await card.locator('.f-status-card__open').click();
  await expect(detail.locator('[data-value="review"] [data-booking-reviewed]')).toBeVisible();
  await expect(detail.locator('[data-booking-review]')).toHaveCount(0);
  await page.keyboard.press('Escape');
  await expect(detail).toBeHidden();
});

test('a ficha pública: a fila «Valoración» abre as valoracións, sen nomes e con «Ordenar por»', async ({
  page,
  lang,
}) => {
  await page.goto(`/${SLUG}/instalacions/${PAVILION.id}`);
  const row = page.locator('[data-public-rating]');
  await expect(row).toBeVisible();
  await expect(row.getByRole('heading')).toHaveText(text(lang, 'reviews.row.title'));
  await row.locator('[data-public-rating-open]').click();
  const dialog = page.getByRole('dialog', { name: text(lang, 'reviews.layer.title') });
  await expect(dialog.locator('[data-review]').first()).toBeVisible();
  await expect(dialog.getByText(text(lang, 'reviews.layer.sort'), { exact: true })).toBeVisible();
  await expectNoA11yViolations(page);
  await expectNoHorizontalScroll(page);

  await dialog.getByRole('button', { name: new RegExp(text(lang, 'reviews.layer.sort')) }).click();
  await page.getByRole('option', { name: text(lang, 'reviews.layer.order.LOWEST') }).click();
  await expect(dialog.locator('[data-review] furar-stars').first()).toHaveAttribute('data-stars', '1');
  await page.keyboard.press('Escape');
  await expect(dialog).toBeHidden();
});

test('indicadores: a cifra «Valoración media» e a táboa, onde a xestión oculta un comentario', async ({
  page,
  lang,
}) => {
  await page.goto(`/${SLUG}`);
  await loginAs(page, 'xestor-naron', lang);
  await page.goto(`/${SLUG}/xestion/indicadores?periodo=ano`);
  await expect(page.locator('[data-kpi="rating"]')).toBeVisible();
  const card = page.locator('[data-indicator-reviews]');
  await expect(card.locator('[data-review]').first()).toBeVisible();
  await expectNoA11yViolations(page);
  await expectNoHorizontalScroll(page);

  const commented = card.locator('[data-review]', { has: page.locator('[data-review-text]:not([data-hidden])') });
  const id = await commented.first().getAttribute('data-review');
  await card.locator(`[data-open="${id}"]`).click();
  const dialog = page.getByRole('dialog', { name: text(lang, 'reviews.manage.detail') });
  await expect(dialog).toBeVisible();
  await expectNoA11yViolations(page);
  await dialog.getByRole('button', { name: text(lang, 'reviews.manage.hide') }).click();
  await expect(dialog.getByRole('button', { name: text(lang, 'reviews.manage.unhide') })).toBeVisible();
  await expect(card.locator(`[data-review="${id}"] [data-review-hidden]`)).toBeVisible();
  // Volve mostralo: a demo queda como estaba.
  await dialog.getByRole('button', { name: text(lang, 'reviews.manage.unhide') }).click();
  await expect(dialog.getByRole('button', { name: text(lang, 'reviews.manage.hide') })).toBeVisible();
  await page.keyboard.press('Escape');
});
