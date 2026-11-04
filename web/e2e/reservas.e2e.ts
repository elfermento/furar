// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Page } from '@playwright/test';
import { DEMO_PASSWORD, demoEmail, expect, loginAs, test, text } from './fixtures';
import { checkStep, hermetic, isoDay, tabTo } from './instalacions-axudas';
import {
  FAR,
  PADEL,
  bookingDay,
  projectOffset,
  cancelLeftovers,
  expectBookingMail,
  hhmm,
  pickCalendarDay,
  waitForMail,
} from './reservas-axudas';

/**
 * Reservas da veciñanza de punta a punta (plan do dominio 2, D2-17): listado → ficha → reservar
 * sen sesión → entrar → volve ao mesmo paso → confirmar → correo en Mailpit co `.ics` e o QR → as
 * miñas reservas → cambiar → cancelar; e o oco que colle outra lapela antes de confirmar, coas
 * tres horas libres máis próximas; reservar só co teclado; e o tema de Ferrol (o segundo tema) cun
 * uso por prazas. Axe en cada paso (e 320 px sen scroll horizontal en gl-mobile), sen peticións
 * fóra de localhost nin violacións de CSP.
 *
 * Cada proxecto reserva en días seus (`bookingDay`), lonxe de hoxe (`FAR`), na pista de pádel 1
 * da demo, en horas que a demo non usa; ao empezar e ao rematar cancélanse as reservas que non son da demo.
 */
test.describe.configure({ mode: 'serial' });

/** Asuntos dos correos (mail/notification_{gl,es}.properties): a reserva leva o idioma da persoa. */
const SUBJECT = {
  CONFIRMED: /^Reserva confirmada: /,
  CHANGED: /^Reserva cambiada: /,
  CANCELLED_BY_PERSON: /^Reserva cancelada: /,
};

/** Abre un campo da páxina de reservar (Que · Data · Hora · Parte) e devolve o seu panel. */
async function openField(page: Page, field: 'date' | 'time', lang: 'gl' | 'es') {
  const label = text(lang, field === 'date' ? 'booking.reserve.date' : 'booking.reserve.time');
  const panel = page.getByRole('dialog', { name: label });
  if (!(await panel.isVisible())) {
    await page.locator(`[data-reserve-field="${field}"] button[aria-haspopup="dialog"]`).click();
  }
  await expect(panel).toBeVisible();
  return panel;
}

/** Escolle a hora no panel Hora (ábreo se fai falta). */
async function pickTime(page: Page, time: string, lang: 'gl' | 'es') {
  const panel = await openField(page, 'time', lang);
  await panel.locator('[data-reserve-slot]', { hasText: time }).click();
  await expect(panel).toBeHidden();
}

test.describe('reservas da veciñanza', () => {
  test.beforeAll(async ({ request }) => {
    await cancelLeftovers(request, 'vecino-naron', [PADEL.id]);
  });

  test.afterAll(async ({ request }) => {
    await cancelLeftovers(request, 'vecino-naron', [PADEL.id]);
  });

  test('listado → ficha → reservar sen sesión → entrar → confirmar → correo → cambiar → cancelar', async ({
    page,
    request,
    lang,
  }, testInfo) => {
    test.setTimeout(180_000);
    const outside = await hermetic(page);
    const day = bookingDay(FAR, testInfo.project.name);
    const first = hhmm(660);
    const second = hhmm(750);

    // 1. Listado público
    await page.goto('/naron/instalacions');
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'facility.publicList.title') })).toBeVisible();
    const card = page.getByRole('link', { name: PADEL.name }).first();
    await expect(card).toBeVisible();
    await checkStep(page);

    // 2. Ficha pública con «Reservar»
    await card.click();
    await expect(page).toHaveURL(new RegExp(`/naron/instalacions/${PADEL.id}$`));
    await expect(page.getByRole('heading', { level: 1, name: PADEL.name })).toBeVisible();
    await expect(page.locator('[data-public-uses]')).toContainText(PADEL.useName);
    await checkStep(page);
    await page.locator('[data-public-reserve]').click();

    // 3. Reservar sen sesión: Data e Hora; o botón di «Continuar»
    await expect(page).toHaveURL(new RegExp(`/naron/instalacions/${PADEL.id}/reservar`));
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.title') })).toBeVisible();
    await checkStep(page);
    const datePanel = await openField(page, 'date', lang);
    await checkStep(page);
    await pickCalendarDay(datePanel, day, lang);
    await expect(datePanel).toBeHidden();
    // Escoller a data abre a Hora (o campo seguinte)
    const timePanel = await openField(page, 'time', lang);
    await expect(timePanel.locator('[data-reserve-slot]').first()).toBeVisible();
    await checkStep(page);
    await pickTime(page, first, lang);
    const go = page.locator('[data-reserve-go]');
    await expect(go).toBeEnabled();
    await expect(go).toHaveText(text(lang, 'booking.reserve.continue'));
    await expect(page.locator('[data-reserve-fine]')).toContainText(text(lang, 'booking.reserve.loginNote'));
    await checkStep(page);

    // 4. Continuar → Keycloak → volve ao mesmo paso (mesma data e hora), xa con «Confirmar reserva»
    await go.click();
    await page.waitForURL(/\/auth\/realms\/furar\//);
    await page.locator('#username').fill(demoEmail('vecino-naron'));
    await page.locator('#password').fill(DEMO_PASSWORD);
    await page.locator('#kc-login').click();
    await page.waitForURL((url) => url.pathname.endsWith('/reservar') && !url.search.includes('code='));
    const back = new URL(page.url());
    expect(back.searchParams.get('data')).toBe(day);
    expect(back.searchParams.get('hora')).toBe(first);
    await expect(go).toHaveText(text(lang, 'booking.reserve.confirm'));
    await expect(page.locator('[data-reserve-field="time"]')).toContainText(first);
    await checkStep(page);

    // 5. Confirmar → «Reserva confirmada» co QR e o código
    await go.click();
    await expect(page).toHaveURL(/[?&]reserva=[0-9a-f-]{36}/);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.done.title') })).toBeVisible();
    const code = (await page.locator('[data-done-code]').innerText()).trim();
    expect(code).toMatch(/^FUR-[A-HJ-NP-Z2-9]{4}$/);
    await expect(page.locator('furar-qr svg')).toBeVisible();
    await checkStep(page);
    const bookingId = new URL(page.url()).searchParams.get('reserva')!;

    // 6. Correo de confirmación en Mailpit, co QR incrustado e o .ics adxunto
    expectBookingMail(await waitForMail(request, code, SUBJECT.CONFIRMED), code);

    // 7. As miñas reservas
    await page.locator('[data-done-mine]').click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.mine.title') })).toBeVisible();
    const mine = page.locator(`[data-booking="${bookingId}"]`);
    await expect(mine).toContainText(PADEL.name);
    await expect(mine.locator('[data-booking-when]')).toContainText(first);
    await checkStep(page);

    // 8. Cambiar: mesmo fluxo, outra hora, «Confirmar o cambio»
    await mine.locator('[data-booking-change]').click();
    await expect(page).toHaveURL(new RegExp(`[?&]cambiar=${bookingId}`));
    await expect(
      page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.change.title') }),
    ).toBeVisible();
    await expect(page.locator('[data-reserve-field="date"] button[aria-haspopup="dialog"]')).toBeVisible();
    await pickTime(page, second, lang);
    await expect(go).toHaveText(text(lang, 'booking.reserve.change.confirm'));
    await expect(go).toBeEnabled();
    await checkStep(page);
    await go.click();
    await expect(page).toHaveURL(new RegExp(`[?&]reserva=${bookingId}`));
    await expect(page.locator('[data-done-code]')).toHaveText(code);
    await checkStep(page);
    expectBookingMail(await waitForMail(request, code, SUBJECT.CHANGED), code);

    // 9. Cancelar desde as miñas reservas
    await page.locator('[data-done-mine]').click();
    await expect(mine.locator('[data-booking-when]')).toContainText(second);
    await mine.locator('[data-booking-cancel]').click();
    const confirm = page.getByRole('alertdialog', { name: text(lang, 'booking.mine.cancel.title') });
    await expect(confirm).toBeVisible();
    await checkStep(page);
    await confirm.getByRole('button', { name: text(lang, 'booking.mine.cancel.confirm') }).click();
    await expect(confirm).toBeHidden();
    await expect(page.locator('[data-toast]')).toContainText(
      text(lang, 'booking.mine.cancel.done', { name: PADEL.name }),
    );
    // Queda na lista como cancelada, sen «Cambiar» nin «Cancelar»
    await expect(mine.locator('[data-chip="cancelled"]')).toHaveText(text(lang, 'booking.mine.status.cancelled'));
    await expect(mine.locator('[data-booking-change], [data-booking-cancel]')).toHaveCount(0);
    await checkStep(page);
    expectBookingMail(await waitForMail(request, code, SUBJECT.CANCELLED_BY_PERSON), code);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('oco collido noutra lapela: aviso coas tres horas libres máis próximas', async ({
    page,
    context,
    lang,
  }, testInfo) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const day = bookingDay(FAR, testInfo.project.name);
    const time = hhmm(840);
    const url = `/naron/instalacions/${PADEL.id}/reservar?uso=${PADEL.use}&data=${day}&hora=${time}`;

    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto(url);
    const go = page.locator('[data-reserve-go]');
    await expect(go).toHaveText(text(lang, 'booking.reserve.confirm'));
    await expect(page.locator('[data-reserve-field="time"]')).toContainText(time);

    // Outra lapela coa mesma sesión colle a hora primeiro
    const other = await context.newPage();
    const otherOutside = await hermetic(other);
    await other.goto(url);
    await other.locator('[data-reserve-go]').click();
    await expect(other).toHaveURL(/[?&]reserva=/);
    await other.close();

    // Esta lapela confirma tarde: aviso suave, Hora baleira e tres alternativas
    await go.click();
    const status = page.locator('[data-reserve-status]');
    await expect(status).toContainText(text(lang, 'booking.reserve.taken.title', { time }));
    const alternatives = status.locator('[data-reserve-alt]');
    await expect(alternatives).toHaveCount(3);
    await expect(page).not.toHaveURL(/[?&]hora=/);
    await checkStep(page);

    // Unha das alternativas: queda escollida e resérvase
    const alternative = (await alternatives.first().innerText()).trim();
    await alternatives.first().click();
    await expect(page.locator('[data-reserve-field="time"]')).toContainText(alternative);
    await go.click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.done.title') })).toBeVisible();
    await checkStep(page);

    expect([...outside, ...otherOutside], 'peticións fóra de localhost').toEqual([]);
  });
  test('só teclado: Data, Hora e confirmar sen rato', async ({ page, lang }, testInfo) => {
    test.setTimeout(120_000);
    const outside = await hermetic(page);
    const offset = FAR + 5 + projectOffset(testInfo.project.name);
    const time = hhmm(930);
    await page.goto('/naron');
    await loginAs(page, 'vecino-naron', lang);
    await page.goto(`/naron/instalacions/${PADEL.id}/reservar`);
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.title') })).toBeVisible();

    // Data: Tab ata o campo, Enter abre o calendario co foco en hoxe; frecha abaixo semana a
    // semana e á dereita día a día ata o día (o calendario pasa de mes só)
    const dateTrigger = page.locator('[data-reserve-field="date"] button[aria-haspopup="dialog"]');
    await tabTo(page, dateTrigger);
    await page.keyboard.press('Enter');
    const datePanel = page.getByRole('dialog', { name: text(lang, 'booking.reserve.date') });
    await expect(datePanel.locator(`td[data-date="${isoDay(0)}"]`)).toBeFocused();
    for (let i = 7; i <= offset; i += 7) {
      await page.keyboard.press('ArrowDown');
      await expect(datePanel.locator(`td[data-date="${isoDay(i)}"]`)).toBeFocused();
    }
    for (let i = offset - (offset % 7) + 1; i <= offset; i++) {
      await page.keyboard.press('ArrowRight');
      await expect(datePanel.locator(`td[data-date="${isoDay(i)}"]`)).toBeFocused();
    }
    await page.keyboard.press('Enter');
    await expect(datePanel).toBeHidden();

    // Hora: ábrese soa co foco dentro; Tab ata a hora e Enter
    const timePanel = page.getByRole('dialog', { name: text(lang, 'booking.reserve.time') });
    await expect(timePanel).toBeVisible();
    await expect.poll(() => timePanel.evaluate((panel) => panel.contains(document.activeElement))).toBe(true);
    await expect(timePanel.locator('[data-reserve-slot]').first()).toBeVisible();
    await checkStep(page);
    await tabTo(page, timePanel.locator('[data-reserve-slot]', { hasText: time }));
    await page.keyboard.press('Enter');
    await expect(timePanel).toBeHidden();
    await expect(page.locator('[data-reserve-field="time"]')).toContainText(time);

    // Confirmar co teclado: o foco vai ao título de «Reserva confirmada»
    const go = page.locator('[data-reserve-go]');
    await tabTo(page, go);
    await page.keyboard.press('Enter');
    const done = page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.done.title') });
    await expect(done).toBeVisible();
    await expect(done).toBeFocused();
    await checkStep(page);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });

  test('tema de Ferrol: listado, ficha e reservar unha praza, accesibles', async ({ page, lang }, testInfo) => {
    test.setTimeout(90_000);
    const outside = await hermetic(page);
    const name = 'Pista de atletismo da Malata';
    await page.goto('/ferrol/instalacions');
    const card = page.getByRole('link', { name }).first();
    await expect(card).toBeVisible();
    await checkStep(page);
    await card.click();
    await expect(page.getByRole('heading', { level: 1, name })).toBeVisible();
    await checkStep(page);
    await page.locator('[data-public-reserve]').click();
    await expect(page.getByRole('heading', { level: 1, name: text(lang, 'booking.reserve.title') })).toBeVisible();
    const datePanel = await openField(page, 'date', lang);
    await pickCalendarDay(datePanel, bookingDay(2, testInfo.project.name), lang);
    // Uso por prazas: cada hora di as prazas libres
    const timePanel = await openField(page, 'time', lang);
    await expect(timePanel.locator('[data-reserve-slot] small').first()).toHaveText(
      text(lang, 'booking.reserve.seatsFree.other', { count: 40 }),
    );
    await checkStep(page);
    await timePanel.locator('[data-reserve-slot]').first().click();
    await expect(page.locator('[data-reserve-go]')).toBeEnabled();
    await checkStep(page);

    expect(outside, 'peticións fóra de localhost').toEqual([]);
  });
});
