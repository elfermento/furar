// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext } from '@playwright/test';
import {
  demoTokenForm,
  expect,
  expectNoA11yViolations,
  expectNoHorizontalScroll,
  loginAs,
  managerHeaders,
  test,
  text,
} from './fixtures';

/**
 * Actividades con horario, do lado da xestión e de recepción (ADR-0041): a xestión crea unha
 * actividade co formulario (Días · Horas · Datas, outro sitio) e ve a vista previa das sesións; unha
 * persoa apúntase; desde «Validar QR», o bloque «Actividades de hoxe» abre «Pasar lista», con todas
 * presentes, e a lista gárdase. Con axe en cada pantalla. Na demo, Narón; a actividade cancélase ao
 * rematar.
 */

const BASE = '/api/v1/municipalities/naron/events';

/** Agora en Madrid: o día, o día da semana (luns = 0) e os minutos do día. */
function madridNow(): { day: string; weekday: number; minutes: number } {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-GB', {
      timeZone: 'Europe/Madrid',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      weekday: 'short',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
    })
      .formatToParts(new Date())
      .map((part) => [part.type, part.value]),
  );
  return {
    day: `${parts['year']}-${parts['month']}-${parts['day']}`,
    weekday: ['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].indexOf(parts['weekday']),
    minutes: Number(parts['hour']) * 60 + Number(parts['minute']),
  };
}

async function removeActivities(request: APIRequestContext, prefix: string): Promise<void> {
  const headers = await managerHeaders(request, 'naron');
  const list = await request.get(`${BASE}/manage`, { headers });
  const events = (await list.json()) as { id: string; name: { gl: string }; cancelledAt?: string; version: number }[];
  for (const event of events.filter((e) => e.name.gl.startsWith(prefix) && !e.cancelledAt)) {
    await request.post(`${BASE}/manage/${event.id}/cancel`, {
      headers: { ...headers, 'If-Match': `"${event.version}"` },
    });
  }
}

test.describe('actividades con horario (xestión)', () => {
  test('crear unha actividade, ver as sesións e pasar lista desde «Validar QR»', async ({
    page,
    request,
    lang,
  }, info) => {
    // A API só crea as sesións que aínda non empezaron, e a lista pásase desde 45 minutos antes do
    // inicio: unha sesión hoxe que empece no seguinte cuarto de hora. No último cuarto de hora do día
    // non cabe ningunha: espérase á medianoite (como moito 15 minutos).
    let today = madridNow();
    if (today.minutes >= 23 * 60 + 45) {
      test.setTimeout(20 * 60_000);
      const day = today.day;
      await expect.poll(() => madridNow().day, { timeout: 17 * 60_000, intervals: [15_000] }).not.toBe(day);
      today = madridNow();
    }
    const start = Math.floor(today.minutes / 15) * 15 + 15;
    const end = Math.min(start + 60, 24 * 60);
    const name = `E2E ${info.project.name} actividade xestión ${Date.now()}`;
    try {
      await page.setViewportSize({ width: 1280, height: 900 });
      await page.goto('/naron');
      await loginAs(page, 'xestor-naron', lang);
      await page.goto('/naron/xestion/eventos');
      await page.locator('[data-event-new]').click();
      await page
        .getByRole('dialog', { name: text(lang, 'event.form.newEvent') })
        .locator('[data-event-kind="SCHEDULED"]')
        .click();
      // Con horario, o diálogo pasa a chamarse «Nova actividade».
      const dialog = page.getByRole('dialog', { name: text(lang, 'event.form.newActivity') });
      await expect(dialog).toBeVisible();

      const nameField = dialog.locator('.f-bilingual').first();
      for (const l of ['gl', 'es'] as const) {
        await nameField.locator(`[data-lang="${l}"]`).click();
        await expect(nameField.locator(`[data-lang="${l}"]`)).toHaveAttribute('aria-pressed', 'true');
        await nameField.locator('input').fill(name);
      }
      // Días: o de hoxe.
      await dialog.locator('[data-season-days] .f-field-popover__trigger').click();
      await page.locator('.f-weekdays__day').nth(today.weekday).click();
      await page.keyboard.press('Escape');
      // Horas: de `start` a unha hora despois (como moito, ás 24:00).
      await dialog.locator('[data-season-hours] .f-field-popover__trigger').click();
      await page.locator('[data-season-start] button').click();
      await page.locator(`[data-minutes="${start}"]`).click();
      await page.locator('[data-season-end] button').click();
      await page.locator(`[data-minutes="${end}"]`).click();
      await page.keyboard.press('Escape');
      // Datas: só hoxe.
      await dialog.locator('[data-season-dates] .f-field-popover__trigger').click();
      await page.locator(`[data-date="${today.day}"]`).click();
      await page.locator('[data-season-apply]').click();

      await dialog.locator('[data-place-kind="other"]').click();
      await dialog.locator('[data-event-place]').fill('Praza do concello');
      await dialog.locator('[data-event-capacity]').fill('10');
      await dialog.locator('[data-event-published] button[role="switch"]').click();
      await expect(dialog.locator('[data-event-sessions]')).toHaveText(text(lang, 'event.form.sessionsOne'));
      await expectNoA11yViolations(page);
      await dialog.locator('[data-event-save]').click();
      await expect(dialog).toBeHidden();
      const row = page.locator('[data-events-table] tr', { hasText: name });
      await expect(row.locator('[data-cell="kind"]')).toHaveText(text(lang, 'event.kind.SCHEDULED'));

      // Unha persoa apúntase.
      const manager = await managerHeaders(request, 'naron');
      const list = (await (await request.get(`${BASE}/manage`, { headers: manager })).json()) as {
        id: string;
        name: { gl: string };
      }[];
      const id = list.find((e) => e.name.gl === name)!.id;
      const token = await request.post('/auth/realms/furar/protocol/openid-connect/token', {
        form: demoTokenForm('vecino-naron'),
      });
      const { access_token: accessToken } = (await token.json()) as { access_token: string };
      const joined = await request.post(`${BASE}/${id}/registration`, {
        headers: { Authorization: `Bearer ${accessToken}` },
      });
      expect(joined.ok(), 'apuntarse á actividade').toBe(true);

      // «Validar QR» no móbil: «Actividades de hoxe» e «Pasar lista».
      await page.setViewportSize({ width: 390, height: 844 });
      await page.goto('/naron/xestion/validar');
      const block = page.locator('[data-today-activities]');
      await expect(block.getByRole('heading', { name: text(lang, 'validate.activities.title') })).toBeVisible();
      const card = block.locator(`[data-today-session]`, { hasText: name });
      await expect(card.locator('[data-chip="pending"]')).toHaveText(text(lang, 'validate.activities.pending'));
      await expectNoA11yViolations(page);
      await card.getByRole('button', { name }).click();

      await expect(page.getByRole('heading', { level: 1 })).toHaveText(text(lang, 'event.attendance.title'));
      const person = page.locator('[data-attendee] button[role="switch"]').first();
      await expect(person).toHaveAttribute('aria-checked', 'true');
      await expect(page.locator('[data-attendance-count]')).toContainText('1');
      await expectNoHorizontalScroll(page);
      await expectNoA11yViolations(page);
      await person.click();
      await expect(person).toHaveAttribute('aria-checked', 'false');
      await page.locator('[data-attendance-save]').click();
      await expect(page.locator('[data-attendance-save]')).toHaveText(text(lang, 'event.attendance.saveChanges'));
      await expect(page.locator('furar-toast')).toBeVisible();
    } finally {
      await removeActivities(request, name);
    }
  });
});
