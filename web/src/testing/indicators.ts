// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: CC0-1.0
//
// Panel de indicadores de proba cos datos da maqueta 31 (outubro de 2026 en Narón) e un baleiro.
import type { IndicatorFiltersResponse, IndicatorsResponse } from '../app/features/indicators/indicators.store';
import type { Translate } from '../app/features/indicators/conclusions';
import es from '../i18n/es.json';
import gl from '../i18n/gl.json';

const months = (end: number[]) =>
  end.map((value, i) => {
    const month = i + 11; // de novembro de 2025 a outubro de 2026
    const year = month > 12 ? 2026 : 2025;
    const m = String(((month - 1) % 12) + 1).padStart(2, '0');
    return { from: `${year}-${m}-01`, to: `${year}-${m}-28`, value, partial: false };
  });

const evolution = (last: number, previous: number) =>
  months(
    [0.6, 0.62, 0.66, 0.7, 0.72, 0.75, 0.8, 0.84, 0.88, 0.9].map((k) => Math.round(last * k)).concat([previous, last]),
  );

/** Unha cifra dun período pechado: compárase enteira co anterior enteiro. */
const kpi = (value: number, previous: number) => ({
  value,
  previous,
  valueToDate: value,
  previousToDate: previous,
  evolution: evolution(value, previous),
});

/** Ocupación (reservado / aberto) cun total de 6.000 minutos abertos. */
const occ = (percent: number, open = 6000) => ({ reservedMinutes: (open * percent) / 100, openMinutes: open });

/** As horas punta: abre de 8 a 23; martes de 19 a 21 o máis cheo; as mañás de diario, case baleiras. */
function peakHours(): IndicatorsResponse['peakHours'] {
  const cells: IndicatorsResponse['peakHours'] = [];
  for (let day = 1; day <= 7; day++) {
    for (let hour = 0; hour < 24; hour++) {
      const open = hour >= 8 && hour < 23 ? 240 : 0;
      let percent = hour >= 18 && hour <= 21 ? 55 : hour >= 16 ? 30 : hour < 14 ? 6 : 18;
      if (day >= 6) percent = hour >= 10 && hour <= 13 ? 45 : 20;
      if (day === 2 && (hour === 19 || hour === 20)) percent = 85;
      cells.push({ isoWeekday: day, hour, openMinutes: open, reservedMinutes: open ? (open * percent) / 100 : 0 });
    }
  }
  return cells;
}

const WEEKS = [
  ['2026-10-01', '2026-10-04'],
  ['2026-10-05', '2026-10-11'],
  ['2026-10-12', '2026-10-18'],
  ['2026-10-19', '2026-10-25'],
  ['2026-10-26', '2026-10-31'],
];

export const FACILITY_IDS = { padel2: 'f-p2', padel1: 'f-p1', pavilion: 'f-pav', football: 'f-fut' };

/** Outubro de 2026 en Narón, coas cifras da maqueta 31. */
export function naronPanel(): IndicatorsResponse {
  return {
    period: { kind: 'MONTH', from: '2026-10-01', to: '2026-10-31', evolutionUnit: 'MONTH', breakdownUnit: 'WEEK' },
    previous: { from: '2026-09-01', to: '2026-09-30' },
    comparison: {
      toDate: false,
      from: '2026-10-01',
      to: '2026-10-31',
      comparedFrom: '2026-09-01',
      comparedTo: '2026-09-30',
    },
    kpis: {
      bookings: kpi(412, 381),
      occupancy: {
        value: occ(54),
        previous: occ(49),
        valueToDate: occ(54),
        previousToDate: occ(49),
        evolution: evolution(54, 49).map((p) => ({ ...p, value: occ(p.value) })),
      },
      reservedHours: kpi(618 * 60, 567 * 60),
      people: kpi(187, 154),
      paidCents: kpi(294_600, 278_000),
    },
    occupancyByFacility: [
      { facilityId: FACILITY_IDS.football, name: 'Campo de fútbol 1 de O Alto', ...occ(33) },
      { facilityId: FACILITY_IDS.pavilion, name: 'Pavillón de Piñeiros', ...occ(48) },
      { facilityId: FACILITY_IDS.padel1, name: 'Pista de pádel 1 de O Alto', ...occ(64) },
      { facilityId: FACILITY_IDS.padel2, name: 'Pista de pádel 2 de O Alto', ...occ(71) },
    ],
    dailyOccupancy: WEEKS.map(([from, to], i) => ({
      from: from!,
      to: to!,
      ...occ([46, 52, 58, 55, 50][i]!),
      partial: false,
    })),
    peakHours: peakHours(),
    cancellations: WEEKS.map(([from, to], i) => ({
      from: from!,
      to: to!,
      inTime: [6, 9, 7, 8, 3][i]!,
      late: 0,
      byMunicipality: i === 2 ? 4 : 0,
      partial: false,
    })),
    revenue: [
      {
        facilityId: FACILITY_IDS.football,
        name: 'Campo de fútbol 1 de O Alto',
        paidCents: 29_900,
        pendingCents: 6_400,
        toRefundCents: 4_800,
        refundedCents: 0,
      },
      {
        facilityId: FACILITY_IDS.pavilion,
        name: 'Pavillón de Piñeiros',
        paidCents: 51_200,
        pendingCents: 6_400,
        toRefundCents: 0,
        refundedCents: 800,
      },
      {
        facilityId: FACILITY_IDS.padel1,
        name: 'Pista de pádel 1 de O Alto',
        paidCents: 103_100,
        pendingCents: 8_800,
        toRefundCents: 0,
        refundedCents: 0,
      },
      {
        facilityId: FACILITY_IDS.padel2,
        name: 'Pista de pádel 2 de O Alto',
        paidCents: 110_400,
        pendingCents: 9_600,
        toRefundCents: 0,
        refundedCents: 1_600,
      },
    ],
    people: ['05', '06', '07', '08', '09', '10'].map((m, i) => ({
      from: `2026-${m}-01`,
      to: `2026-${m}-28`,
      people: [98, 121, 84, 62, 154, 187][i]!,
      partial: false,
    })),
  };
}

/** Un concello sen reservas (Ferrol): todo a 0, sen horas abertas nin instalacións con cartos. */
export function emptyPanel(): IndicatorsResponse {
  const panel = naronPanel();
  const zero = kpi(0, 0);
  const closed = { reservedMinutes: 0, openMinutes: 0 };
  return {
    ...panel,
    kpis: {
      bookings: zero,
      occupancy: {
        value: closed,
        previous: closed,
        valueToDate: closed,
        previousToDate: closed,
        evolution: zero.evolution.map((p) => ({ ...p, value: closed })),
      },
      reservedHours: zero,
      people: zero,
      paidCents: zero,
    },
    occupancyByFacility: [],
    dailyOccupancy: panel.dailyOccupancy.map((b) => ({ ...b, ...closed })),
    peakHours: panel.peakHours.map((c) => ({ ...c, ...closed })),
    cancellations: panel.cancellations.map((c) => ({ ...c, inTime: 0, byMunicipality: 0 })),
    revenue: [],
    people: panel.people.map((p) => ({ ...p, people: 0 })),
  };
}

/**
 * Outubro de 2026 en curso (hoxe, o 29): o valor é o mes enteiro coas reservas xa feitas, a
 * variación compara do 1 ao 29 co 1 ao 29 de setembro e o último tramo de cada serie está en curso.
 * Reservas: 398 ata hoxe fronte a 362 (+10 %); ocupación 52 % fronte a 50 % (+2 p.p.).
 */
export function inProgressPanel(): IndicatorsResponse {
  const panel = naronPanel();
  const last = <T extends { partial: boolean }>(list: readonly T[]) =>
    list.map((item, i) => ({ ...item, partial: i === list.length - 1 }));
  const { kpis } = panel;
  return {
    ...panel,
    comparison: {
      toDate: true,
      from: '2026-10-01',
      to: '2026-10-29',
      comparedFrom: '2026-09-01',
      comparedTo: '2026-09-29',
    },
    kpis: {
      bookings: { ...kpis.bookings, valueToDate: 398, previousToDate: 362, evolution: last(kpis.bookings.evolution) },
      occupancy: {
        ...kpis.occupancy,
        valueToDate: occ(52),
        previousToDate: occ(50),
        evolution: last(kpis.occupancy.evolution),
      },
      reservedHours: {
        ...kpis.reservedHours,
        valueToDate: 600 * 60,
        previousToDate: 540 * 60,
        evolution: last(kpis.reservedHours.evolution),
      },
      people: { ...kpis.people, valueToDate: 180, previousToDate: 150, evolution: last(kpis.people.evolution) },
      paidCents: {
        ...kpis.paidCents,
        valueToDate: 280_000,
        previousToDate: 266_000,
        evolution: last(kpis.paidCents.evolution),
      },
    },
    dailyOccupancy: last(panel.dailyOccupancy),
    cancellations: last(panel.cancellations),
    people: last(panel.people),
  };
}

/** As opcións dos filtros: dúas pistas co uso «Partido» (agrúpanse sen instalación) e o campo. */
export function filterOptions(): IndicatorFiltersResponse {
  return {
    facilities: [
      { id: FACILITY_IDS.padel1, name: 'Pista de pádel 1 de O Alto', status: 'ACTIVE' },
      { id: FACILITY_IDS.padel2, name: 'Pista de pádel 2 de O Alto', status: 'ACTIVE' },
      { id: FACILITY_IDS.football, name: 'Campo de fútbol 1 de O Alto', status: 'WITHDRAWN' },
    ],
    uses: [
      { id: 'u-p1', facilityId: FACILITY_IDS.padel1, name: 'Partido', archived: false },
      { id: 'u-p2', facilityId: FACILITY_IDS.padel2, name: 'Partido', archived: false },
      { id: 'u-f7', facilityId: FACILITY_IDS.football, name: 'Fútbol 7', archived: true },
      { id: 'u-fw', facilityId: FACILITY_IDS.football, name: 'Campo enteiro', archived: false },
    ],
  };
}

/** Traduce cos textos reais, sen Transloco (para as funcións puras). */
export function translator(lang: 'gl' | 'es'): Translate {
  const texts = (lang === 'gl' ? gl : es) as unknown as Record<string, unknown>;
  return (key, params = {}) => {
    const value = key.split('.').reduce<unknown>((node, part) => (node as Record<string, unknown>)?.[part], texts);
    if (typeof value !== 'string') throw new Error(`Non hai texto para ${key}`);
    return value.replace(/\{\{\s*(\w+)\s*\}\}/g, (_, name: string) => params[name] ?? `{{${name}}}`);
  };
}
