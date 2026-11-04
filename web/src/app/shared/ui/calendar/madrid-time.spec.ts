// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { formatInstant, inMadrid, madridToday, madridWall } from './madrid-time';

describe('instantes no reloxo de Europe/Madrid', () => {
  it('«29/10/2026, 00:28» igual en galego e en castelán, sexa cal sexa a zona do navegador', () => {
    expect(formatInstant('2026-10-28T23:28:00Z')).toBe('29/10/2026, 00:28');
    expect(formatInstant(new Date('2026-10-28T23:28:00Z'))).toBe('29/10/2026, 00:28');
    expect(formatInstant('2026-10-27T23:30:00Z', false)).toBe('28/10/2026');
  });

  it('sen datos gl en Intl (Chrome e Chromium) non cae nun formato raíz', () => {
    const spy = vi.spyOn(Intl.DateTimeFormat, 'supportedLocalesOf').mockReturnValue([]);
    try {
      expect(formatInstant('2026-10-28T23:28:00Z')).toBe('29/10/2026, 00:28');
    } finally {
      spy.mockRestore();
    }
  });

  it('cos cambios de hora: UTC+2 no verán, UTC+1 no inverno', () => {
    // 25 de outubro: ás 3:00 de verán volven ser as 2:00.
    expect(formatInstant('2026-10-25T00:30:00Z')).toBe('25/10/2026, 02:30');
    expect(formatInstant('2026-10-25T01:30:00Z')).toBe('25/10/2026, 02:30');
    expect(formatInstant('2026-10-25T02:30:00Z')).toBe('25/10/2026, 03:30');
    // 29 de marzo: das 2:00 pásase ás 3:00.
    expect(formatInstant('2026-03-29T00:59:00Z')).toBe('29/03/2026, 01:59');
    expect(formatInstant('2026-03-29T01:00:00Z')).toBe('29/03/2026, 03:00');
    expect(inMadrid('2026-07-01T22:30:00Z')).toEqual({ date: '2026-07-02', time: '00:30' });
    expect(inMadrid('2026-10-28T17:00:00Z')).toEqual({ date: '2026-10-28', time: '18:00' });
  });

  it('hoxe é o día de Madrid, tamén pasada a medianoite', () => {
    expect(madridToday(new Date('2026-10-28T22:59:00Z'))).toBe('2026-10-28');
    expect(madridToday(new Date('2026-10-28T23:00:00Z'))).toBe('2026-10-29');
    expect(madridToday(new Date('2026-10-31T23:30:00Z'))).toBe('2026-11-01');
    expect(madridToday(new Date('2026-06-30T22:30:00Z'))).toBe('2026-07-01');
  });

  it('a medianoite é 00, non 24; sen data ou con lixo, null', () => {
    expect(madridWall('2026-01-01T23:00:00Z')).toEqual({
      year: '2026',
      month: '01',
      day: '02',
      hour: '00',
      minute: '00',
      second: '00',
    });
    expect(formatInstant('lixo')).toBeNull();
    expect(formatInstant(undefined)).toBeNull();
    expect(formatInstant(null)).toBeNull();
    expect(formatInstant('')).toBeNull();
  });
});
