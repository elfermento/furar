// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { BookingFilters, ManagedBookingDetailResponse } from './manage-bookings';
import {
  NO_FILTERS,
  activeCount,
  agoText,
  galicianRelative,
  dateFilterText,
  filtersFor,
  listParams,
  manageWhen,
  paymentActions,
  pickDay,
  presetRange,
  toggle,
} from './manage-bookings';

const TODAY = '2026-10-26';

describe('Reservas do concello: filtros e textos', () => {
  it('«Ver máis» de cada sección usa os criterios da API para as contas de «Cobros»', () => {
    expect(filtersFor('latest', TODAY)).toEqual({ ...NO_FILTERS, sort: 'RECENT' });
    expect(filtersFor('pendingPayment', TODAY)).toEqual({
      ...NO_FILTERS,
      from: TODAY,
      to: '2026-11-24',
      payments: ['PENDING'],
      states: ['CONFIRMED'],
    });
    expect(filtersFor('toRefund', TODAY)).toEqual({ ...NO_FILTERS, payments: ['TO_REFUND'] });
    expect(filtersFor('cancelled', TODAY)).toMatchObject({ from: TODAY, to: '2026-11-24', states: ['CANCELLED'] });
    expect(filtersFor('results', TODAY)).toBe(NO_FILTERS);
  });

  it('os parámetros da lista: sen nada, as próximas desde hoxe; «Canceladas» son as dúas; a busca recortada', () => {
    expect(listParams('naron', NO_FILTERS, '  ', TODAY)).toEqual({ slug: 'naron', sort: 'START', from: TODAY });
    expect(listParams('naron', { ...NO_FILTERS, sort: 'RECENT' }, '', TODAY)).toEqual({
      slug: 'naron',
      sort: 'RECENT',
    });
    const filters: BookingFilters = {
      sort: 'START',
      from: '2026-10-27',
      to: null,
      facilityIds: ['f1'],
      payments: ['PENDING', 'NONE'],
      states: ['CANCELLED'],
    };
    expect(listParams('naron', filters, ' ana ', TODAY)).toEqual({
      slug: 'naron',
      sort: 'START',
      from: '2026-10-27',
      facilityId: ['f1'],
      payment: ['PENDING', 'NONE'],
      status: ['CANCELLED_BY_PERSON', 'CANCELLED_BY_MUNICIPALITY'],
      q: 'ana',
    });
    expect(activeCount(filters)).toBe(5);
    expect(listParams('naron', NO_FILTERS, 'FUR-7K2Q', TODAY)).toEqual({ slug: 'naron', sort: 'START', q: 'FUR-7K2Q' });
  });

  it('marcar e desmarcar garda a orde das opcións', () => {
    expect(toggle(['c'], 'a', ['a', 'b', 'c'])).toEqual(['a', 'c']);
    expect(toggle(['a', 'c'], 'a', ['a', 'b', 'c'])).toEqual(['c']);
  });

  it('datas: atallos, rango picando dous días e o texto do chip', () => {
    expect(presetRange('today', TODAY)).toEqual({ from: TODAY, to: TODAY });
    expect(presetRange('tomorrow', TODAY)).toEqual({ from: '2026-10-27', to: '2026-10-27' });
    expect(presetRange('week', '2026-10-28')).toEqual({ from: '2026-10-28', to: '2026-11-01' });
    let range = pickDay({ from: null, to: null }, '2026-10-28');
    expect(range).toEqual({ from: '2026-10-28', to: null });
    range = pickDay(range, '2026-10-30');
    expect(range).toEqual({ from: '2026-10-28', to: '2026-10-30' });
    expect(pickDay(range, '2026-10-29')).toEqual({ from: '2026-10-29', to: null });
    expect(pickDay({ from: '2026-10-28', to: null }, '2026-10-27')).toEqual({ from: '2026-10-27', to: null });
    expect(dateFilterText(null, null, TODAY)).toBeNull();
    expect(dateFilterText(TODAY, TODAY, TODAY)?.key).toBe('today');
    expect(dateFilterText('2026-10-27', '2026-10-27', TODAY)?.key).toBe('tomorrow');
    expect(dateFilterText('2026-10-29', '2026-10-29', TODAY)).toEqual({ key: 'on', params: { day: '29/10' } });
    expect(dateFilterText('2026-10-26', '2026-10-31', TODAY)).toEqual({
      key: 'between',
      params: { from: '26/10', to: '31/10' },
    });
    expect(dateFilterText('2026-10-28', null, TODAY)).toEqual({ key: 'since', params: { day: '28/10' } });
  });

  it('cando: «hoxe», «mañá» ou o día curto, coa hora de Madrid', () => {
    const relative = { today: 'hoxe', tomorrow: 'mañá' };
    const at = (start: string, end: string) => manageWhen({ start, end }, 'gl', TODAY, relative);
    expect(at('2026-10-26T18:00:00Z', '2026-10-26T19:00:00Z')).toBe('hoxe, lun 26 · 19:00–20:00');
    expect(at('2026-10-27T17:00:00Z', '2026-10-27T18:00:00Z')).toBe('mañá, mar 27 · 18:00–19:00');
    expect(at('2026-10-28T19:30:00Z', '2026-10-28T20:30:00Z')).toBe('mér 28 · 20:30–21:30');
  });

  it('canto hai que se reservou', () => {
    const now = new Date('2026-10-26T12:00:00Z');
    expect(agoText('2026-10-26T11:55:00Z', 'es', now)).toBe('hace 5 min');
    expect(agoText('2026-10-26T10:00:00Z', 'es', now)).toBe('hace 2 h');
    expect(agoText('2026-10-23T12:00:00Z', 'es', now)).toBe('hace 3 días');
  });

  it('en galego sen datos gl en Intl (Chrome) dá os mesmos textos ca Intl con datos', () => {
    // Regresión (D2-17): no Chrome do e2e saía «Reservada this minute».
    const intl = (value: number, unit: 'minute' | 'hour' | 'day') =>
      new Intl.RelativeTimeFormat('gl', { numeric: 'auto', ...(unit === 'day' ? {} : { style: 'short' }) }).format(
        value,
        unit,
      );
    for (const [value, unit] of [
      [0, 'minute'],
      [-5, 'minute'],
      [-2, 'hour'],
      [-1, 'day'],
      [-2, 'day'],
      [-3, 'day'],
      [1, 'day'],
      [3, 'day'],
      [5, 'minute'],
    ] as const) {
      expect(galicianRelative(value, unit)).toBe(intl(value, unit));
    }
  });

  it('accións de cobro: primeiro a que adianta e despois a de desfacer (P13)', () => {
    const detail = (paymentTargets: ManagedBookingDetailResponse['paymentTargets']) =>
      ({ paymentTargets }) as ManagedBookingDetailResponse;
    expect(paymentActions(detail(['PAID']))).toEqual([{ target: 'PAID', undo: false }]);
    expect(paymentActions(detail(['PENDING']))).toEqual([{ target: 'PENDING', undo: true }]);
    expect(paymentActions(detail(['TO_REFUND', 'REFUNDED']))).toEqual([
      { target: 'REFUNDED', undo: false },
      { target: 'TO_REFUND', undo: true },
    ]);
    expect(paymentActions(detail(['NONE']))).toEqual([]);
  });
});
