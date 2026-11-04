// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { booking } from '../reserve/reserve.fixture';
import { bookingChip, canChange, pastDeadline } from './my-bookings';

describe('etiqueta e accións dunha reserva', () => {
  const key = (overrides: Parameters<typeof booking>[0]) => {
    const chip = bookingChip(booking(overrides));
    return chip ? `${chip.key}:${chip.tone}:${chip.look}` : null;
  };

  it('a paleta do §1.2.20 e os valores por defecto de P5, P10 e P11', () => {
    expect(key({ paymentStatus: 'PENDING' })).toBe('pending:warn:solid');
    expect(key({ paymentStatus: 'PAID' })).toBe('paid:success:solid');
    expect(key({ paymentStatus: 'NONE', price: { kind: 'FREE' } })).toBe('free:info:solid');
    expect(key({ paymentStatus: 'NONE', price: { kind: 'NONE' } })).toBeNull();
    expect(key({ done: true, paymentStatus: 'PAID' })).toBe('done:neutral:outline');
    expect(key({ done: true, paymentStatus: 'NONE', price: { kind: 'FREE' } })).toBe('done:neutral:outline');
    expect(key({ done: true, paymentStatus: 'PENDING' })).toBe('pending:warn:solid');
    expect(key({ status: 'CANCELLED_BY_PERSON', paymentStatus: 'TO_REFUND' })).toBe('toRefund:danger:solid');
    expect(key({ status: 'CANCELLED_BY_MUNICIPALITY', paymentStatus: 'REFUNDED' })).toBe('refunded:muted:solid');
    expect(key({ status: 'CANCELLED_BY_PERSON', paymentStatus: 'PENDING' })).toBe('cancelled:danger:solid');
  });

  it('cambiar e cancelar só dentro do prazo; fóra del, o aviso (P1)', () => {
    expect(canChange(booking())).toBe(true);
    expect(pastDeadline(booking())).toBe(false);
    expect(canChange(booking({ canCancel: false }))).toBe(false);
    expect(pastDeadline(booking({ canCancel: false }))).toBe(true);
    expect(pastDeadline(booking({ canCancel: false, done: true }))).toBe(false);
    expect(pastDeadline(booking({ canCancel: false, status: 'CANCELLED_BY_PERSON' }))).toBe(false);
  });
});
