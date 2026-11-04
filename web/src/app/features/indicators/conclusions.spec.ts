// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { emptyPanel, naronPanel, translator } from '../../../testing/indicators';
import { IndicatorFormats } from './indicator-format';
import { chartViews } from './indicator-views';

const NBSP = ' ';
const nb = (text: string) => text.replaceAll(NBSP, ' ');

function conclusions(lang: 'gl' | 'es', panel = naronPanel()) {
  return chartViews(panel, translator(lang), new IndicatorFormats(lang)).map((v) => [v.id, nb(v.conclusion)]);
}

describe('Frases de conclusión (D3-08)', () => {
  it('cos datos da maqueta 31, en galego', () => {
    expect(conclusions('gl')).toEqual([
      ['occupancy', 'Pista de pádel 2 de O Alto é a máis chea: 71 % das súas horas abertas.'],
      ['daily', 'A semana máis chea, a do 12 de out.: 58 %.'],
      ['peak', 'O martes de 19 a 21 h é a franxa máis reservada; as mañás entre semana, case baleiras.'],
      ['cancellations', '37 cancelacións; o concello cancelou 4 a semana do 12 de out.'],
      ['revenue', 'Cobrado 2.946 €; quedan 312 € pendentes de pago no mostrador e 48 € por devolver.'],
      ['people', '187 en outubro, o máximo dos últimos 6 meses.'],
    ]);
  });

  it('cos datos da maqueta 31, en castelán', () => {
    expect(conclusions('es')).toEqual([
      ['occupancy', 'Pista de pádel 2 de O Alto es la más llena: 71 % de sus horas abiertas.'],
      ['daily', 'La semana más llena, la del 12 oct: 58 %.'],
      ['peak', 'El martes de 19 a 21 h es la franja más reservada; las mañanas entre semana, casi vacías.'],
      ['cancellations', '37 cancelaciones; el ayuntamiento canceló 4 la semana del 12 oct.'],
      ['revenue', 'Cobrado 2.946 €; quedan 312 € pendientes de pago en el mostrador y 48 € por devolver.'],
      ['people', '187 en octubre, el máximo de los últimos 6 meses.'],
    ]);
  });

  it('non nomean unha parte a cero: sen concello nin cartos por devolver, non saen', () => {
    const panel = naronPanel();
    const quiet = {
      ...panel,
      cancellations: panel.cancellations.map((c) => ({ ...c, byMunicipality: 0 })),
      revenue: panel.revenue.map((r) => ({ ...r, toRefundCents: 0, pendingCents: 0 })),
    };
    const byId = Object.fromEntries(conclusions('gl', quiet));
    expect(byId['cancellations']).toBe('33 cancelacións en prazo.');
    expect(byId['revenue']).toBe('Cobrado 2.946 €.');
    expect(byId['cancellations']).not.toContain('fóra de prazo');
  });

  it('fóra de prazo e o concello, cando os hai; unha soa cancelación en singular', () => {
    const panel = naronPanel();
    const late = { ...panel, cancellations: panel.cancellations.map((c, i) => ({ ...c, late: i === 0 ? 2 : 0 })) };
    expect(Object.fromEntries(conclusions('gl', late))['cancellations']).toBe(
      '39 cancelacións; 2 fóra de prazo e 4 polo concello.',
    );
    const one = {
      ...panel,
      cancellations: panel.cancellations.map((c, i) => ({ ...c, inTime: i === 0 ? 1 : 0, byMunicipality: 0 })),
    };
    expect(Object.fromEntries(conclusions('es', one))['cancellations']).toBe('1 cancelación en plazo.');
  });

  it('as fins de semana, por días; e as persoas fronte ao mes anterior', () => {
    const panel = naronPanel();
    // Semana do 26 de outubro ao 1 de novembro: de luns a venres ao 40 %, sábado e domingo ao 66 %.
    const days = ['26', '27', '28', '29', '30', '31'].map((d) => `2026-10-${d}`).concat('2026-11-01');
    const week = {
      ...panel,
      period: { ...panel.period, kind: 'WEEK' as const, breakdownUnit: 'DAY' as const },
      dailyOccupancy: days.map((d, i) => ({
        from: d,
        to: d,
        reservedMinutes: i >= 5 ? 66 : 40,
        openMinutes: 100,
        partial: false,
      })),
      people: panel.people.map((p, i) => ({ ...p, people: i === 5 ? 150 : i === 4 ? 140 : 160 })),
    };
    const byId = Object.fromEntries(conclusions('gl', week));
    expect(byId['daily']).toBe('As fins de semana soben a máis do 60 %.');
    expect(byId['people']).toBe('150 en outubro, 10 máis ca en setembro.');
  });

  it('as persoas iguais ca no mes anterior: «tantas coma» (gl) e «tantas como» (es)', () => {
    const panel = naronPanel();
    const same = {
      ...panel,
      people: panel.people.map((p, i) => ({ ...p, people: i >= 4 ? 205 : i === 0 ? 300 : 100 })),
    };
    expect(Object.fromEntries(conclusions('gl', same))['people']).toBe('205 en outubro, tantas coma en setembro.');
    expect(Object.fromEntries(conclusions('es', same))['people']).toBe('205 en octubre, tantas como en septiembre.');
  });

  it('sen datos (Ferrol): «Sen reservas neste período» en todas', () => {
    expect(conclusions('gl', emptyPanel()).map(([, text]) => text)).toEqual(
      Array.from({ length: 6 }, () => 'Sen reservas neste período'),
    );
    expect(conclusions('es', emptyPanel())[0]?.[1]).toBe('Sin reservas en este periodo');
  });
});
