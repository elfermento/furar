// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { booking } from '../reserve/reserve.fixture';
import { bookingChip, canChange, pastDeadline } from './my-bookings';

describe('etiqueta e accións dunha reserva', () => {
  const key = (overrides: Parameters<typeof booking>[0]) => {
    const chip = bookingChip(booking(overrides));
    return chip ? `${chip.key}:${chip.tone}` : null;
  };

  it('a paleta de estados e os valores por defecto', () => {
    expect(key({ paymentStatus: 'PENDING' })).toBe('pending:warn');
    expect(key({ paymentStatus: 'PAID' })).toBe('paid:success');
    expect(key({ paymentStatus: 'NONE', price: { kind: 'FREE' } })).toBe('free:info');
    expect(key({ paymentStatus: 'NONE', price: { kind: 'NONE' } })).toBeNull();
    // Xa pasada: a do pago, coma calquera outra.
    expect(key({ done: true, paymentStatus: 'PAID' })).toBe('paid:success');
    expect(key({ done: true, paymentStatus: 'NONE', price: { kind: 'FREE' } })).toBe('free:info');
    expect(key({ done: true, paymentStatus: 'PENDING' })).toBe('pending:warn');
    expect(key({ done: true, paymentStatus: 'NONE', price: { kind: 'NONE' } })).toBeNull();
    expect(key({ status: 'CANCELLED_BY_PERSON', paymentStatus: 'TO_REFUND' })).toBe('toRefund:danger');
    expect(key({ status: 'CANCELLED_BY_MUNICIPALITY', paymentStatus: 'REFUNDED' })).toBe('refunded:muted');
    expect(key({ status: 'CANCELLED_BY_PERSON', paymentStatus: 'PENDING' })).toBe('cancelled:danger');
  });

  it('cambiar e cancelar só dentro do prazo; fóra del, o aviso', () => {
    expect(canChange(booking())).toBe(true);
    expect(pastDeadline(booking())).toBe(false);
    expect(canChange(booking({ canCancel: false }))).toBe(false);
    expect(pastDeadline(booking({ canCancel: false }))).toBe(true);
    expect(pastDeadline(booking({ canCancel: false, done: true }))).toBe(false);
    expect(pastDeadline(booking({ canCancel: false, status: 'CANCELLED_BY_PERSON' }))).toBe(false);
  });
});
