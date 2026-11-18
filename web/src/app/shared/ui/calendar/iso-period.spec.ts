// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { minusPeriod, parseIsoPeriod, periodText } from './iso-period';

describe('iso-period', () => {
  it('reads calendar periods and rejects the rest', () => {
    expect(parseIsoPeriod('P3Y')).toEqual({ years: 3, months: 0, days: 0 });
    expect(parseIsoPeriod('P1Y6M')).toEqual({ years: 1, months: 6, days: 0 });
    expect(parseIsoPeriod('P2W')).toEqual({ years: 0, months: 0, days: 14 });
    expect(parseIsoPeriod('P')).toBeNull();
    expect(parseIsoPeriod('PT1H')).toBeNull();
    expect(parseIsoPeriod('3 anos')).toBeNull();
    expect(parseIsoPeriod(undefined)).toBeNull();
  });

  it('names whole years, months or days and nothing mixed', () => {
    expect(periodText(parseIsoPeriod('P3Y'))).toEqual({ key: 'common.period.yearsOther', count: 3 });
    expect(periodText(parseIsoPeriod('P1Y'))).toEqual({ key: 'common.period.yearsOne', count: 1 });
    expect(periodText(parseIsoPeriod('P1Y6M'))).toEqual({ key: 'common.period.monthsOther', count: 18 });
    expect(periodText(parseIsoPeriod('P90D'))).toEqual({ key: 'common.period.daysOther', count: 90 });
    expect(periodText(parseIsoPeriod('P1Y1D'))).toBeNull();
    expect(periodText(null)).toBeNull();
  });

  it('goes back like a Java Period', () => {
    expect(minusPeriod('2029-03-01', { years: 3, months: 0, days: 0 })).toBe('2026-03-01');
    expect(minusPeriod('2028-02-29', { years: 1, months: 0, days: 0 })).toBe('2027-02-28');
    expect(minusPeriod('2028-03-10', { years: 0, months: 1, days: 10 })).toBe('2028-01-31');
  });
});
