// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { APIRequestContext, Locator } from '@playwright/test';
import { DEMO_PASSWORD, type DemoUser, expect, text } from './fixtures';
import { isoDay } from './instalacions-axudas';

/**
 * Axudas dos e2e das reservas (D2-17): instalacións e usos da demo de Narón, tokens da persoa e
 * do xestor polo cliente `furar-e2e` (só no realm de demo), reservas e bloqueos feitos pola API
 * para preparar e limpar, e o Mailpit do compose (`localhost:8025`) para os correos.
 */

export const SLUG = 'naron';
const API = `/api/v1/municipalities/${SLUG}`;

/** Instalacións e usos de `R__demo_municipality_facilities.sql`. */
export const PADEL = {
  id: '019a0b6e-0000-7000-8000-000000000202',
  name: 'Pista de pádel 1 de O Alto',
  use: '019a0b6e-0000-7000-8000-000000000234',
  useName: 'Partido',
};
export const PAVILION = {
  id: '019a0b6e-0000-7000-8000-000000000204',
  name: 'Pavillón de Piñeiros',
  use: '019a0b6e-0000-7000-8000-000000000236',
  useName: 'Pista central',
};

/** As reservas da demo (`R__demo_naron_bookings.sql`) non se tocan ao limpar. */
const DEMO_BOOKING = /^019a0b6e-0000-7000-8000-0000000004/;
/** Motivo dos bloqueos que crean as probas (para atopalos e borralos). */
export const BLOCK_REASON = 'Mantemento e2e';

/** Mailpit do compose (perfís dev e full). */
const MAILPIT = process.env['FURAR_E2E_MAILPIT_URL'] ?? 'http://localhost:8025';

/**
 * Días desde hoxe ata os que reservan as probas: lonxe (≥ 40) para que as súas reservas e
 * cancelacións non caian no mes en curso da demo, que é o que ensina o panel de indicadores. Os
 * usos onde reservan (pádel 1 de O Alto e pista central do pavillón) non teñen antelación
 * máxima, e a demo non ten reservas tan adiante (a historia chega a seis días). O pavillón pecha
 * en xullo e agosto: unha execución de finais de maio en diante cae fóra da súa tempada.
 */
export const FAR = 42;

/** Desprazamento en días de cada proxecto: cada un traballa en días seus. */
export function projectOffset(project: string): number {
  return { gl: 0, es: 1, 'gl-mobile': 2 }[project] ?? 0;
}

interface Token {
  readonly headers: Record<string, string>;
}

/** Token dunha persoa da demo polo cliente furar-e2e. */
export async function tokenFor(request: APIRequestContext, user: DemoUser): Promise<Token> {
  const token = await request.post('/auth/realms/furar/protocol/openid-connect/token', {
    form: { grant_type: 'password', client_id: 'furar-e2e', username: user, password: DEMO_PASSWORD },
  });
  expect(token.ok(), `token de ${user}`).toBe(true);
  const { access_token: accessToken } = (await token.json()) as { access_token: string };
  return { headers: { Authorization: `Bearer ${accessToken}` } };
}

interface Slot {
  start: string;
  startMinute: number;
}

/** Os ocos libres dun uso nun día (a mesma consulta pública que a páxina de reservar). */
export async function freeSlots(request: APIRequestContext, facilityId: string, useId: string, date: string) {
  const response = await request.get(`${API}/public/facilities/${facilityId}/availability`, {
    params: { useId, date },
  });
  expect(response.ok(), 'dispoñibilidade').toBe(true);
  return ((await response.json()) as { slots: Slot[] }).slots;
}

/** O `start` (Instant) do oco dese uso, día e minuto; falla se non está libre. */
export async function slotStart(
  request: APIRequestContext,
  facilityId: string,
  useId: string,
  date: string,
  minute: number,
): Promise<string> {
  const slot = (await freeSlots(request, facilityId, useId, date)).find((s) => s.startMinute === minute);
  expect(slot, `oco libre ás ${minute} o ${date}`).toBeDefined();
  return slot!.start;
}

/**
 * Coma `slotStart`, pero se ese día está pechado (unha excepción ou un festivo da demo pode caer
 * nel segundo a data de hoxe) proba os seguintes, ata unha semana. Para probas ás que lles dá igual
 * o día.
 */
export async function firstSlotStart(
  request: APIRequestContext,
  facilityId: string,
  useId: string,
  date: string,
  minute: number,
): Promise<string> {
  for (let i = 0; i < 7; i++) {
    const day = new Date(`${date}T00:00:00Z`);
    day.setUTCDate(day.getUTCDate() + i);
    const iso = day.toISOString().slice(0, 10);
    const slot = (await freeSlots(request, facilityId, useId, iso)).find((s) => s.startMinute === minute);
    if (slot) return slot.start;
  }
  expect(undefined, `oco libre ás ${minute} na semana desde o ${date}`).toBeDefined();
  return '';
}

export interface BookingRow {
  id: string;
  code: string;
  version: number;
  facilityId: string;
  start: string;
  paymentStatus: string;
  status: string;
}

/** Reserva pola API como a persoa do token. */
export async function book(
  request: APIRequestContext,
  token: Token,
  facilityId: string,
  useId: string,
  start: string,
): Promise<BookingRow> {
  const response = await request.post(`${API}/me/bookings`, {
    headers: { ...token.headers, 'Idempotency-Key': crypto.randomUUID() },
    data: { facilityId, useId, start, slots: 1 },
  });
  expect(response.status(), `reservar: ${await response.text()}`).toBe(201);
  return (await response.json()) as BookingRow;
}

/**
 * Cancela as reservas próximas da persoa nestas instalacións que non son da demo (as que deixase
 * unha execución anterior ou a propia proba): a demo queda como estaba.
 */
export async function cancelLeftovers(request: APIRequestContext, user: DemoUser, facilityIds: string[]) {
  const token = await tokenFor(request, user);
  const list = await request.get(`${API}/me/bookings`, {
    headers: token.headers,
    params: { scope: 'UPCOMING', size: 100 },
  });
  expect(list.ok(), 'as miñas reservas').toBe(true);
  const { items } = (await list.json()) as { items: BookingRow[] };
  for (const booking of items) {
    if (DEMO_BOOKING.test(booking.id) || !facilityIds.includes(booking.facilityId)) continue;
    if (booking.status !== 'CONFIRMED') continue;
    const cancelled = await request.post(`${API}/me/bookings/${booking.id}/cancel`, {
      headers: { ...token.headers, 'If-Match': `"${booking.version}"` },
    });
    expect([200, 204], `cancelar ${booking.code}`).toContain(cancelled.status());
  }
}

/** Borra os bloqueos das probas (motivo `BLOCK_REASON`) da instalación. */
export async function deleteE2eBlocks(request: APIRequestContext, facilityId: string): Promise<void> {
  const token = await tokenFor(request, 'xestor-naron');
  const list = await request.get(`${API}/facilities/${facilityId}/blocks`, { headers: token.headers });
  expect(list.ok(), 'bloqueos').toBe(true);
  const blocks = (await list.json()) as { id: string; reason?: string; version: number }[];
  for (const block of blocks.filter((b) => b.reason?.startsWith(BLOCK_REASON))) {
    const deleted = await request.delete(`${API}/facilities/${facilityId}/blocks/${block.id}`, {
      headers: { ...token.headers, 'If-Match': `"${block.version}"` },
    });
    expect([204, 404], 'borrar o bloqueo').toContain(deleted.status());
  }
}

interface MailSummary {
  ID: string;
  Subject: string;
  To: { Address: string }[];
}

interface MailDetail {
  Subject: string;
  HTML: string;
  Text: string;
  Attachments: { FileName: string; ContentType: string }[];
  Inline: { FileName: string; ContentType: string; ContentID: string }[];
}

/**
 * Espera o correo da reserva `code` cuxo asunto casa con `subject` e devólveo enteiro (HTML,
 * texto, adxuntos e imaxes incrustadas).
 */
export async function waitForMail(request: APIRequestContext, code: string, subject: RegExp): Promise<MailDetail> {
  let found: MailSummary | undefined;
  await expect
    .poll(
      async () => {
        const response = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `"${code}"` } });
        if (!response.ok()) return false;
        const { messages } = (await response.json()) as { messages: MailSummary[] };
        found = messages.find((m) => subject.test(m.Subject));
        return Boolean(found);
      },
      { message: `correo «${subject}» de ${code} en Mailpit`, timeout: 30_000 },
    )
    .toBe(true);
  const detail = await request.get(`${MAILPIT}/api/v1/message/${found!.ID}`);
  expect(detail.ok(), 'correo en Mailpit').toBe(true);
  return (await detail.json()) as MailDetail;
}

/** O correo leva o código, o QR incrustado (PNG, `cid:`) e o `.ics` adxunto. */
export function expectBookingMail(mail: MailDetail, code: string): void {
  expect(mail.HTML).toContain(code);
  expect(mail.Text).toContain(code);
  expect(mail.Attachments.map((a) => a.FileName)).toContain(`reserva-${code}.ics`);
  expect(mail.Attachments.find((a) => a.FileName.endsWith('.ics'))?.ContentType).toMatch(/^text\/calendar/);
  const qr = mail.Inline.find((i) => i.ContentType === 'image/png' && mail.HTML.includes(`cid:${i.ContentID}`));
  expect(qr, 'QR incrustado').toBeDefined();
}

/** Escolle un día no `furar-calendar` aberto dentro de `panel` (avanza meses se fai falta). */
export async function pickCalendarDay(panel: Locator, iso: string, lang: 'gl' | 'es'): Promise<void> {
  const shown = panel.locator('td[data-date]').first();
  await expect(shown).toBeVisible();
  for (let i = 0; i < 12 && !(await panel.locator(`td[data-date="${iso}"]`).count()); i++) {
    const month = ((await shown.getAttribute('data-date')) ?? '').slice(0, 7);
    await panel.getByRole('button', { name: text(lang, 'ui.calendar.nextMonth') }).click();
    await expect(shown).not.toHaveAttribute('data-date', new RegExp(`^${month}`));
  }
  await panel.locator(`td[data-date="${iso}"]`).click();
}

/** «11:00» dun minuto do día. */
export function hhmm(minute: number): string {
  return `${String(Math.floor(minute / 60)).padStart(2, '0')}:${String(minute % 60).padStart(2, '0')}`;
}

/** Día de reserva do proxecto: `base` días despois de hoxe máis o desprazamento do proxecto. */
export function bookingDay(base: number, project: string): string {
  return isoDay(base + projectOffset(project));
}
