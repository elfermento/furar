// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { translator } from '../../../../testing/indicators';
import { platformResponse, row } from '../../../../testing/platform-indicators';
import { IndicatorFormats } from '../indicator-format';
import { kpiViews } from '../indicator-kpis';
import {
  DEFAULT_SORT,
  columnSortOptions,
  mobileSortOptions,
  platformCsv,
  rowViews,
  sortRows,
  totalsPanel,
} from './platform-indicators';

const t = translator('gl');
const f = new IndicatorFormats('gl');
const plain = (s: string) => s.replace(/[\u00a0\u202f]/g, ' ');

describe('Indicadores da plataforma: táboa e orde', () => {
  it('as catro cifras sumadas, sen persoas nin valoración', () => {
    const views = kpiViews(totalsPanel(platformResponse()), t, f);
    expect(views.map((v) => v.id)).toEqual(['bookings', 'occupancy', 'reservedHours', 'paidCents']);
  });

  it('de entrada, máis reservas primeiro; a igualdade, por nome', () => {
    const rows = platformResponse().municipalities;
    expect(sortRows(rows, DEFAULT_SORT, 'gl').map((r) => r.name)).toEqual(['Ferrol', 'Narón', 'A Capela']);
    const tied = [row('Narón', { bookings: 3 }), row('Ares', { bookings: 3 })];
    expect(sortRows(tied, DEFAULT_SORT, 'gl').map((r) => r.name)).toEqual(['Ares', 'Narón']);
  });

  it('por nome e por cada cifra, nos dous sentidos; sen horas abertas, a ocupación vai ao final', () => {
    const rows = platformResponse().municipalities;
    const names = (column: Parameters<typeof sortRows>[1]['column'], direction: 'asc' | 'desc') =>
      sortRows(rows, { column, direction }, 'gl').map((r) => r.name);
    expect(names('name', 'asc')).toEqual(['A Capela', 'Ferrol', 'Narón']);
    expect(names('name', 'desc')).toEqual(['Narón', 'Ferrol', 'A Capela']);
    expect(names('occupancy', 'desc')).toEqual(['Ferrol', 'Narón', 'A Capela']);
    expect(names('paidCents', 'asc')).toEqual(['A Capela', 'Narón', 'Ferrol']);
    expect(names('reservedHours', 'desc')).toEqual(['Ferrol', 'Narón', 'A Capela']);
    expect(names('people', 'asc')).toEqual(['A Capela', 'Narón', 'Ferrol']);
  });

  it('as ordes de cada cabeceira e as do móbil', () => {
    expect(columnSortOptions('name', t)).toEqual([
      { value: 'asc', label: 'De A a Z' },
      { value: 'desc', label: 'De Z a A' },
    ]);
    expect(columnSortOptions('bookings', t)).toEqual([
      { value: 'desc', label: 'De maior a menor' },
      { value: 'asc', label: 'De menor a maior' },
    ]);
    expect(mobileSortOptions(t).map((o) => o.label)).toEqual([
      'Concello, de A a Z',
      'Máis reservas',
      'Máis ocupación',
      'Máis horas reservadas',
      'Máis persoas distintas',
      'Máis cobrado',
    ]);
  });

  it('as filas formatadas e a liña da tarxeta do móbil', () => {
    const [ferrol, capela] = rowViews(
      [platformResponse().municipalities[1]!, platformResponse().municipalities[2]!],
      t,
      f,
    );
    expect(ferrol).toMatchObject({ name: 'Ferrol', bookings: '2.105', people: '1.031' });
    expect(plain(ferrol!.occupancy)).toBe('63 %');
    expect(plain(ferrol!.paidCents)).toBe('15.420,50 €');
    expect(plain(ferrol!.reservedHours)).toBe('2.388 h');
    expect(plain(ferrol!.summary)).toBe('2.105 reservas · 63 % de ocupación · 2.388 h · 1.031 persoas');
    expect(plain(capela!.summary)).toBe('1 reserva · sen horas abertas · 1 h · 1 persoa');
  });

  it('o CSV: a cabeceira, as cifras e a táboa na orde que se ve, en bruto', () => {
    const response = platformResponse();
    const kpis = kpiViews(totalsPanel(response), t, f);
    const rows = sortRows(response.municipalities, DEFAULT_SORT, 'gl');
    const csv = platformCsv('Indicadores da plataforma · outubro', kpis, ['Cifra', 'Valor', 'Anterior'], rows, t);
    expect(csv[0]).toEqual(['Indicadores da plataforma · outubro']);
    expect(csv).toContainEqual(['Cifra', 'Valor', 'Anterior']);
    const header = csv.findIndex((r) => r[0] === 'Concello');
    expect(csv[header]).toEqual([
      'Concello',
      'Reservas',
      t('indicators.kpi.occupancyPercent'),
      'Horas reservadas',
      'Persoas distintas',
      'Cobrado',
    ]);
    expect(csv[header + 1]).toEqual(['Ferrol', 2105, 63, 2388, 1031, 15420.5]);
    expect(csv[header + 3]).toEqual(['A Capela', 1, null, 1, 1, 0]);
  });
});
