// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { row } from '../../../../testing/manage-bookings';
import {
  columnBox,
  facilityColumns,
  groupByStart,
  hourRange,
  minutes,
  monthWeeks,
  peekGroups,
  periodLabel,
  placeBlocks,
  step,
  weekDays,
} from './bookings-calendar';
import { closedCells } from './bookings-calendar-view';

const translate = (key: string, params: Record<string, string>) => `${key}:${Object.values(params).join('|')}`;

describe('Calendario das reservas: cálculos (D2-16)', () => {
  it('pasos sen límites: día, semana e mes, tamén entre anos', () => {
    expect(step('DAY', '2026-12-31', 1)).toBe('2027-01-01');
    expect(step('WEEK', '2026-10-26', -1)).toBe('2026-10-19');
    expect(step('MONTH', '2026-01-31', -1)).toBe('2025-12-31');
  });

  it('texto do período: día, semana no mesmo mes ou entre dous e mes', () => {
    expect(periodLabel('DAY', '2026-10-26', 'gl', translate)).toBe('luns, 26 de outubro');
    expect(periodLabel('WEEK', '2026-10-14', 'gl', translate)).toBe('weekSame:12|18|outubro');
    expect(periodLabel('WEEK', '2026-10-28', 'gl', translate)).toBe('weekCross:26|outubro|1|novembro');
    expect(periodLabel('MONTH', '2026-10-28', 'es', translate)).toBe('Octubre de 2026');
  });

  it('semanas e meses de luns a domingo', () => {
    expect(weekDays('2026-10-28')).toEqual([
      '2026-10-26',
      '2026-10-27',
      '2026-10-28',
      '2026-10-29',
      '2026-10-30',
      '2026-10-31',
      '2026-11-01',
    ]);
    const weeks = monthWeeks('2026-10-15');
    expect(weeks).toHaveLength(5);
    expect(weeks[0].slice(0, 4)).toEqual([null, null, null, '2026-10-01']);
    expect(weeks[4]).toEqual([
      '2026-10-26',
      '2026-10-27',
      '2026-10-28',
      '2026-10-29',
      '2026-10-30',
      '2026-10-31',
      null,
    ]);
  });

  it('minutos en Europe/Madrid (o fin noutro día conta ata as 24:00) e horas da grella', () => {
    expect(minutes(row())).toEqual({
      date: '2026-10-26',
      start: 1140,
      end: 1200,
      startText: '19:00',
      endText: '20:00',
    });
    const late = row({ start: '2026-10-26T22:30:00Z', end: '2026-10-27T00:00:00Z' });
    expect(minutes(late)).toMatchObject({ start: 23 * 60 + 30, end: 24 * 60 });
    expect(hourRange([])).toEqual({ first: 9, last: 23 });
    expect(hourRange([row({ start: '2026-10-26T06:00:00Z', end: '2026-10-26T07:00:00Z' }), late])).toEqual({
      first: 7,
      last: 24,
    });
    expect(hourRange([], [{ hour: 23 }])).toEqual({ first: 9, last: 24 });
    // Unha instalación que abre ás 7:30 adianta a grella ás 7:00; unha que abre ás 10:00 non a atrasa.
    expect(hourRange([], [], [{ startMinute: 450 }, { startMinute: 600 }])).toEqual({ first: 7, last: 23 });
    expect(hourRange([], [], [{ startMinute: 600 }])).toEqual({ first: 9, last: 23 });
  });

  it('coloca as reservas: carrís se se pisan, fóra as sen columna, en orde de lectura', () => {
    const a = row({ id: 'a', start: '2026-10-26T18:00:00Z', end: '2026-10-26T19:30:00Z' });
    const b = row({ id: 'b', start: '2026-10-26T18:30:00Z', end: '2026-10-26T19:30:00Z' });
    const c = row({ id: 'c', facilityId: 'f2', start: '2026-10-26T08:00:00Z', end: '2026-10-26T09:00:00Z' });
    const d = row({ id: 'd', facilityId: 'nada' });
    const e = row({ id: 'e', start: '2026-10-26T19:30:00Z', end: '2026-10-26T20:30:00Z' });
    const placed = placeBlocks([e, a, b, c, d], (x) => ['f2', 'f1'].indexOf(x.facilityId), 9);
    expect(placed.map((p) => [p.booking.id, p.column, p.lane, p.lanes])).toEqual([
      ['c', 0, 0, 1],
      ['a', 1, 0, 2],
      ['b', 1, 1, 2],
      ['e', 1, 0, 1],
    ]);
    expect(placed[0]).toMatchObject({ top: 4, height: 3.25 - 0.25 });
    expect(columnBox(1, 2, 1, 2)).toEqual({
      left: 'calc(3.5rem + 1 * ((100% - 3.5rem) / 2) + 1 * (((100% - 3.5rem) / 2) / 2) + 3px)',
      width: 'calc(((100% - 3.5rem) / 2) / 2 - 6px)',
    });
  });

  it('agrupa por hora de inicio; a vista rápida colle catro e conta o resto', () => {
    const at = (id: string, start: string, end: string) =>
      row({ id, start: `2026-10-26T${start}:00Z`, end: `2026-10-26T${end}:00Z` });
    const list = [
      at('e', '19:00', '20:00'),
      at('a', '08:00', '09:00'),
      at('b', '08:00', '09:30'),
      at('c', '09:00', '10:00'),
      at('d', '09:00', '10:00'),
    ];
    expect(groupByStart(list).map((g) => [g.time, g.items.map((i) => i.id)])).toEqual([
      ['09:00', ['a', 'b']],
      ['10:00–11:00', ['c', 'd']],
      ['20:00–21:00', ['e']],
    ]);
    const { groups, more } = peekGroups(list);
    expect(groups.flatMap((g) => g.items.map((i) => i.id))).toEqual(['a', 'b', 'c', 'd']);
    expect(more).toBe(1);
  });

  it('columnas do día: as instalacións en uso e as que teñan reservas', () => {
    const facilities = [
      { id: 'f1', name: 'Pista 1', status: 'ACTIVE', address: { locality: 'Narón' } },
      { id: 'f2', name: 'Vella', status: 'WITHDRAWN' },
      { id: 'f3', name: 'Vella con reserva', status: 'WITHDRAWN' },
    ];
    const blocks = [row({ facilityId: 'f3' }), row({ facilityId: 'f9', facilityName: 'Sen listar' })];
    expect(facilityColumns(facilities, blocks)).toEqual([
      { id: 'f1', name: 'Pista 1', locality: 'Narón' },
      { id: 'f3', name: 'Vella con reserva', locality: '' },
      { id: 'f9', name: 'Sen listar', locality: '' },
    ]);
  });

  it('celas pechadas: sen tramo aberto da instalación da columna (semana de todas: de ningunha)', () => {
    const column = (key: string, date: string, facilityId: string | null) => ({
      key,
      date,
      facilityId,
      title: key,
      subtitle: '',
      today: false,
    });
    const openings = [
      { facilityId: 'f1', date: '2026-10-26', startMinute: 570, endMinute: 660 },
      { facilityId: 'f2', date: '2026-10-26', startMinute: 720, endMinute: 780 },
      { facilityId: 'f1', date: '2026-10-27', startMinute: 540, endMinute: 600 },
    ];
    const day = [column('f1', '2026-10-26', 'f1'), column('f2', '2026-10-26', 'f2')];
    expect([...closedCells(openings, day, [9, 10, 11, 12, 13])]).toEqual([
      'f1|11',
      'f1|12',
      'f1|13',
      'f2|9',
      'f2|10',
      'f2|11',
      'f2|13',
    ]);
    const week = [column('2026-10-26', '2026-10-26', null), column('2026-10-27', '2026-10-27', null)];
    expect([...closedCells(openings, week, [9, 12])]).toEqual(['2026-10-27|12']);
  });
});
