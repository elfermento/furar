// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { expectRequest, fakeSession, flushProblem } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import {
  BASE,
  OVERVIEW,
  SUMMARY_URL,
  detail,
  openDetail,
  renderManage,
  text,
  useMonday,
} from '../../../../testing/manage-bookings';
import { attendanceChip, bookingChip } from '../mine/my-bookings';

/** Unha reserva de onte, pagada e sen marca: a ficha ofrece «Non presentado». */
const ENDED = detail({
  start: '2026-10-25T17:00:00Z',
  end: '2026-10-25T18:00:00Z',
  done: true,
  paymentStatus: 'PAID',
  paymentTargets: ['PENDING'],
  canMarkNoShow: true,
});

describe('Ficha da reserva: «Non presentado»', () => {
  useMonday();

  it('nunha reserva xa pasada, un botón; ao marcar: chip vermello, aviso das faltas e «Quitar a falta»; axe', async () => {
    const { el, settle } = await renderManage(fakeSession({ staff: 'naron' }));
    const card = await openDetail(el, settle, ENDED);
    const mark = card.querySelector<HTMLButtonElement>('[data-detail-no-show-mark]')!;
    expect(text(mark)).toBe('Non presentado');
    // Na lista de accións, en vermello (a destrutiva).
    expect(mark.closest('furar-action-item')?.getAttribute('data-tone')).toBe('danger');
    expect(card.querySelector('[data-detail-attendance-undo]')).toBeNull();
    expect(card.querySelector('[data-attendance]')).toBeNull();

    mark.click();
    const put = await expectRequest(`${BASE}/bookings/b1/attendance`);
    expect(put.request.method).toBe('PUT');
    expect(put.request.headers.get('If-Match')).toBe('"3"');
    expect(put.request.body).toEqual({ attendance: 'NO_SHOW' });
    put.flush({
      ...ENDED,
      attendance: 'NO_SHOW',
      canMarkNoShow: false,
      noShow: { faults: 2, windowDays: 30, threshold: 3, blockDays: 15 },
      version: 4,
    });
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
    await vi.waitFor(async () => {
      await settle();
      expect(card.querySelector('[data-attendance="NO_SHOW"]')).not.toBeNull();
    });
    // O chip vermello, xunto ao do pago.
    const chips = [...card.querySelectorAll('[data-detail-total] furar-tag')];
    expect(chips.map((chip) => [text(chip), chip.getAttribute('data-tone')])).toEqual([
      ['Pagada', 'success'],
      ['Non presentado', 'danger'],
    ]);
    expect(text(card.querySelector('[data-detail-no-show]'))).toContain(
      'Ana Rey leva 2 faltas en 30 días. Con 3 non poderá reservar por internet durante 15 días.',
    );
    expect(card.querySelector('[data-detail-no-show-mark]')).toBeNull();
    const undo = card.querySelector<HTMLButtonElement>('[data-detail-attendance-undo]')!;
    expect(text(undo)).toBe('Quitar a falta');
    await expectNoAxeViolations(card);

    undo.click();
    const del = await expectRequest(`${BASE}/bookings/b1/attendance`);
    expect(del.request.method).toBe('DELETE');
    expect(del.request.headers.get('If-Match')).toBe('"4"');
    del.flush({ ...ENDED, version: 5 });
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
    await vi.waitFor(async () => {
      await settle();
      expect(card.querySelector('[data-detail-no-show-mark]')).not.toBeNull();
    });
    expect(card.querySelector('[data-attendance]')).toBeNull();
    expect(card.querySelector('[data-detail-no-show]')).toBeNull();
  });

  it('se queda bloqueada, o aviso di ata cando; cunha soa falta, en singular', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, ENDED);
    card.querySelector<HTMLButtonElement>('[data-detail-no-show-mark]')!.click();
    (await expectRequest(`${BASE}/bookings/b1/attendance`)).flush({
      ...ENDED,
      attendance: 'NO_SHOW',
      canMarkNoShow: false,
      noShow: { faults: 1, windowDays: 30, threshold: 1, blockDays: 15, blockedUntil: '2026-11-10' },
      version: 4,
    });
    (await expectRequest((r) => r.url === SUMMARY_URL)).flush(OVERVIEW);
    await vi.waitFor(async () => {
      await settle();
      expect(text(card.querySelector('[data-detail-no-show]'))).toContain(
        'Ana Rey leva 1 falta en 30 días: non pode reservar por internet ata o 10 de novembro.',
      );
    });
  });

  it('unha próxima non ofrece nin «Non presentado» nin quitar nada', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle);
    expect(card.querySelector('[data-detail-no-show-mark]')).toBeNull();
    expect(card.querySelector('[data-detail-attendance-undo]')).toBeNull();
  });

  it('validada: o chip gris e só «Quitar a validación»; un erro queda no aviso da ficha', async () => {
    const { el, settle } = await renderManage();
    const card = await openDetail(el, settle, { ...ENDED, attendance: 'USED', canMarkNoShow: false });
    await vi.waitFor(async () => {
      await settle();
      expect(card.querySelector('[data-attendance="USED"]')?.getAttribute('data-tone')).toBe('muted');
    });
    expect(text(card.querySelector('[data-attendance="USED"]'))).toBe('Validada');
    expect(text(card.querySelector('[data-detail-attendance-undo]'))).toBe('Quitar a validación');
    card.querySelector<HTMLButtonElement>('[data-detail-attendance-undo]')!.click();
    flushProblem(await expectRequest(`${BASE}/bookings/b1/attendance`), 409, 'booking.attendance-not-marked');
    await vi.waitFor(async () => {
      await settle();
      expect(text(card.querySelector('[data-detail-error]'))).toContain('Esta reserva non ten nada que desfacer.');
    });
  });
});

describe('etiquetas da asistencia', () => {
  const done = { status: 'CONFIRMED', paymentStatus: 'PAID', done: true, price: { kind: 'PAID' } } as const;

  it('«Non presentado» en vermello e «Validada» en gris; sen marca, ningunha', () => {
    expect(attendanceChip({ attendance: 'NO_SHOW' })).toEqual({ key: 'NO_SHOW', tone: 'danger' });
    expect(attendanceChip({ attendance: 'USED' })).toEqual({ key: 'USED', tone: 'muted' });
    expect(attendanceChip({ attendance: 'NONE' })).toBeNull();
    // A persoa só sabe se quedou como falta.
    expect(attendanceChip({ noShow: true })).toEqual({ key: 'NO_SHOW', tone: 'danger' });
    expect(attendanceChip({ noShow: false })).toBeNull();
  });

  it('pasada, con marca de asistencia ou sen ela, leva a etiqueta do pago', () => {
    // A asistencia vai na súa etiqueta: non cambia a do pago.
    const marked = (mark: object) => bookingChip({ ...done, ...mark })?.key;
    expect(marked({})).toBe('paid');
    expect(marked({ attendance: 'NO_SHOW' })).toBe('paid');
    expect(marked({ noShow: true })).toBe('paid');
    expect(marked({ attendance: 'USED' })).toBe('paid');
    expect(marked({ paymentStatus: 'NONE', price: { kind: 'FREE' }, noShow: true })).toBe('free');
  });
});
