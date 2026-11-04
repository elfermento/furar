// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  addDays,
  addMonths,
  clamp,
  formatDateLong,
  formatLong,
  formatMonth,
  formatShort,
  formatWeekdayDay,
  isIsoDate,
  localToday,
  monthGrid,
  startOfWeek,
  weekdayIndex,
  weekdayNames,
} from './local-date';

describe('local-date', () => {
  it('só acepta datas ISO que existen', () => {
    expect(isIsoDate('2026-12-24')).toBe(true);
    expect(isIsoDate('2028-02-29')).toBe(true);
    expect(isIsoDate('2026-02-29')).toBe(false);
    expect(isIsoDate('24/12/2026')).toBe(false);
    expect(isIsoDate('2026-12-24T00:00')).toBe(false);
    expect(isIsoDate(null)).toBe(false);
  });

  it('suma días sen que o cambio de hora mova a data (29 mar e 25 out)', () => {
    expect(addDays('2026-03-28', 1)).toBe('2026-03-29');
    expect(addDays('2026-03-29', 1)).toBe('2026-03-30');
    expect(addDays('2026-10-25', 1)).toBe('2026-10-26');
    expect(addDays('2026-12-31', 1)).toBe('2027-01-01');
    expect(addDays('2026-03-01', -1)).toBe('2026-02-28');
  });

  it('suma meses e anos quedando no último día se o mes é máis curto', () => {
    expect(addMonths('2026-01-31', 1)).toBe('2026-02-28');
    expect(addMonths('2026-03-31', -1)).toBe('2026-02-28');
    expect(addMonths('2028-02-29', 12)).toBe('2029-02-28');
    expect(addMonths('2026-12-15', 1)).toBe('2027-01-15');
  });

  it('a semana empeza en luns', () => {
    expect(weekdayIndex('2026-10-26')).toBe(0);
    expect(weekdayIndex('2026-10-25')).toBe(6);
    expect(startOfWeek('2026-10-25')).toBe('2026-10-19');
  });

  it('a grella do mes ten seis semanas de luns a domingo e baleiros fóra do mes', () => {
    const grid = monthGrid('2026-02-10');
    expect(grid).toHaveLength(6);
    expect(grid.every((week) => week.length === 7)).toBe(true);
    // 1 feb 2026 é domingo: a primeira semana só ten ese día.
    expect(grid[0]).toEqual([null, null, null, null, null, null, '2026-02-01']);
    expect(grid.flat().filter(Boolean)).toHaveLength(28);
  });

  it('acouta a min e max', () => {
    expect(clamp('2026-01-01', '2026-02-01', null)).toBe('2026-02-01');
    expect(clamp('2026-05-01', null, '2026-04-30')).toBe('2026-04-30');
    expect(clamp('2026-03-01', null, null)).toBe('2026-03-01');
  });

  it('hoxe é a data local do navegador', () => {
    expect(localToday(new Date(2026, 9, 25, 23, 30))).toBe('2026-10-25');
  });

  it('formata «24/12/2026» en gl e es e os nomes por Intl', () => {
    expect(formatShort('2026-12-24')).toBe('24/12/2026');
    expect(formatShort('2026-12-24')).toBe('24/12/2026');
    expect(formatShort('2026-02-01')).toBe('01/02/2026');
    expect(formatLong('2026-12-24', 'gl')).toBe('xoves, 24 de decembro de 2026');
    expect(formatLong('2026-12-24', 'es')).toBe('jueves, 24 de diciembre de 2026');
    expect(formatMonth('2026-12-24', 'gl')).toBe('Decembro de 2026');
    expect(formatMonth('2026-12-24', 'es')).toBe('Diciembre de 2026');
    expect(formatDateLong('2026-12-24', 'gl')).toBe('24 de decembro de 2026');
    expect(formatDateLong('2026-12-24', 'es')).toBe('24 de diciembre de 2026');
    expect(formatWeekdayDay('2026-10-29', 'gl')).toBe('xov. 29');
    expect(formatWeekdayDay('2026-10-29', 'es')).toBe('jue 29');
    expect(weekdayNames('gl').map((d) => d.short)).toEqual(['L', 'M', 'M', 'X', 'V', 'S', 'D']);
    expect(weekdayNames('es')[3]).toEqual({ short: 'J', long: 'jueves' });
  });

  it('sen datos gl en Intl (Chrome) os nomes galegos son os mesmos ca os de Intl', () => {
    const dates = ['2026-01-05', '2026-06-17', '2026-07-01', '2026-10-29', '2026-12-24'];
    const all = (d: string) => [
      formatLong(d, 'gl'),
      formatMonth(d, 'gl'),
      formatDateLong(d, 'gl'),
      formatWeekdayDay(d, 'gl'),
    ];
    const withIntl = dates.map(all);
    const names = weekdayNames('gl');
    const spy = vi.spyOn(Intl.DateTimeFormat, 'supportedLocalesOf').mockReturnValue([]);
    try {
      expect(dates.map(all)).toEqual(withIntl);
      expect(formatDateLong('2026-10-29', 'gl')).toBe('29 de outubro de 2026');
      expect(formatWeekdayDay('2026-10-29', 'gl')).toBe('xov. 29');
      expect(weekdayNames('gl')).toEqual(names);
      expect(formatShort('2026-12-24')).toBe('24/12/2026');
    } finally {
      spy.mockRestore();
    }
  });
});
