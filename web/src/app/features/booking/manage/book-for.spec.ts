// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApiProblem } from '../../../core/errors/api-problem';
import { detail } from '../../../../testing/manage-bookings';
import { doneToast, keptSlot, slotParams, takenAlternatives } from './book-for';
import { bookableFacilities, holderBody, holderForm } from './book-for.store';

const ANA = detail({ holderName: 'Ana', partId: 'p2', start: '2026-10-28T17:00:00Z', end: '2026-10-28T18:00:00Z' });

describe('reservar no nome doutra persoa: funcións', () => {
  it('o oco da reserva vai ao URL do formulario', () => {
    expect(slotParams(ANA)).toEqual({ uso: 'u1', data: '2026-10-28', hora: '18:00', franxas: null, parte: 'p2' });
  });

  it('ao cambiar sen escoller outra hora, queda o oco de antes', () => {
    expect(keptSlot(ANA, null)).toEqual({ useId: 'u1', start: ANA.start, slots: 1, partId: 'p2' });
    expect(keptSlot(ANA, '18:00')).not.toBeNull();
    expect(keptSlot(ANA, '19:00')).toBeNull();
    expect(keptSlot(null, null)).toBeNull();
  });

  it('o aviso de feito di a quen se mandou o correo ou o código', () => {
    expect(doneToast(ANA, null, true).key).toBe('booking.bookFor.done.sent');
    expect(doneToast({ ...ANA, holderEmail: undefined }, null, true)).toEqual({
      key: 'booking.bookFor.done.code',
      params: { code: 'FUR-7K2Q' },
    });
    const later = { ...ANA, start: '2026-10-28T18:00:00Z', end: '2026-10-28T19:00:00Z' };
    expect(doneToast(later, ANA, true)).toEqual({
      key: 'booking.bookFor.done.changedSent',
      params: { time: '19:00', name: 'Ana' },
    });
    expect(doneToast(later, ANA, false).key).toBe('booking.bookFor.done.changed');
    expect(doneToast(ANA, ANA, true).key).toBe('booking.bookFor.done.changed');
  });

  it('as alternativas dun 409 e o corpo de «Para quen?»', () => {
    const problem = new ApiProblem(409, 'booking.slot-taken', {}, [], undefined, '', {
      alternatives: [{ startMinute: 1140 }, { x: 1 }],
    });
    expect(takenAlternatives(problem)).toEqual(['19:00']);
    const form = holderForm();
    form.setValue({ name: ' Ana ', email: '', phone: ' 600 ' });
    expect(holderBody(form)).toEqual({ name: 'Ana', phone: '600' });
    expect(
      bookableFacilities([
        { id: 'a', published: true, status: 'ACTIVE' },
        { id: 'b', published: false, status: 'ACTIVE' },
        { id: 'c', published: true, status: 'WITHDRAWN' },
      ] as never).map((f) => f.id),
    ).toEqual(['a']);
  });
});
