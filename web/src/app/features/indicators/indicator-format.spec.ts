// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { IndicatorFormats } from './indicator-format';

const nb = (text: string) => text.replaceAll(' ', ' ');

describe('Cifras e datas dos indicadores con Intl (D3-08)', () => {
  it('en galego: miles con punto, porcentaxes, horas, euros e cambios co signo', () => {
    const f = new IndicatorFormats('gl');
    expect(f.integer(1234)).toBe('1.234');
    expect(nb(f.percent(0.537))).toBe('54 %');
    expect(nb(f.percentValue(71))).toBe('71 %');
    expect(nb(f.hours(618 * 60 + 20))).toBe('618 h');
    expect(nb(f.euros(294_649))).toBe('2.946 €');
    expect(nb(f.eurosExact(294_650))).toBe('2.946,50 €');
    expect(nb(f.change(8, 'percent'))).toBe('+8 %');
    expect(nb(f.change(-3, 'points'))).toBe('−3 p.p.');
    expect(nb(f.change(0, 'percent'))).toBe('0 %');
  });

  it('nomes de meses e días no idioma (gl e es)', () => {
    const gl = new IndicatorFormats('gl');
    const es = new IndicatorFormats('es');
    expect(gl.monthTitle('2026-10-15')).toBe('Outubro');
    expect(es.monthTitle('2026-10-15')).toBe('Octubre');
    expect(gl.monthYear('2026-10-01')).toBe('Outubro de 2026');
    expect(es.monthYear('2026-10-01')).toBe('Octubre de 2026');
    expect(gl.monthName('2026-09-01')).toBe('setembro');
    expect(gl.weekdayLong(2)).toBe('martes');
    expect(es.weekdayLong(3)).toBe('miércoles');
    expect(es.dayMonth('2026-10-12')).toBe('12 oct');
    expect(gl.shortDate('2026-03-29')).toBe('29/03/2026');
  });

  it('datas e horas en Europe/Madrid, tamén nos días do cambio de hora', () => {
    const f = new IndicatorFormats('gl');
    expect(f.dateTime(new Date('2026-10-28T12:05:00Z'))).toBe('28/10/2026, 13:05');
    expect(f.dateTime(new Date('2026-07-01T22:30:00Z'))).toBe('02/07/2026, 00:30');
    // Os días sen hora non se moven: o 25 de outubro (25 horas) segue sendo o 25.
    expect(f.shortDate('2026-10-25')).toBe('25/10/2026');
  });
});
