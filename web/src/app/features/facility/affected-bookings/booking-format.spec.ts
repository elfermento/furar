// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { formatBookingDay, formatBookingTime } from './booking-format';

describe('formato das reservas afectadas', () => {
  it('día curto con maiúscula e sen puntos, en gl e es', () => {
    expect(formatBookingDay('2026-11-03', 'gl')).toBe('Mar 3 nov');
    expect(formatBookingDay('2026-11-03', 'es')).toBe('Mar 3 nov');
    expect(formatBookingDay('2026-11-02', 'gl')).toBe('Luns 2 nov');
  });

  it('sen datos gl en Intl (Chromium), abreviaturas galegas propias', () => {
    const spy = vi.spyOn(Intl.DateTimeFormat, 'supportedLocalesOf').mockReturnValue([]);
    expect(formatBookingDay('2026-11-05', 'gl')).toBe('Xov 5 nov');
    expect(formatBookingDay('2026-06-21', 'gl')).toBe('Dom 21 xuño');
    spy.mockRestore();
  });

  it('tramo de reloxo de parede, con 24:00 para a medianoite', () => {
    expect(formatBookingTime(480, 570, 'gl')).toBe('8:00–9:30');
    expect(formatBookingTime(1380, 1440, 'es')).toBe('23:00–24:00');
  });
});
