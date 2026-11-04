// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Frases de conclusión dos gráficos (plan do dominio 3, §1.2.6 e §4): puras, cunha clave de
// `indicators.conclusion.*` e os seus parámetros xa formatados. Non nomean unha parte que vale 0
// (P5: «fóra de prazo» non sae mentres sexa 0) e, sen datos, din «Sen reservas neste período».
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { toUtc } from '../../shared/ui/calendar/local-date';
import type { IndicatorFormats } from './indicator-format';
import type {
  IndicatorCancellationsResponse,
  IndicatorPeopleResponse,
  IndicatorRevenueResponse,
} from './indicators.store';

/** Unha frase por traducir; un parámetro pode ser outra frase (un reconto, un «cando»). */
export interface Phrase {
  readonly key: string;
  readonly params?: Readonly<Record<string, string | Phrase>>;
}

export type Translate = (key: string, params?: Record<string, string>) => string;

export type BucketUnit = 'DAY' | 'WEEK' | 'MONTH' | 'QUARTER' | 'YEAR';

const C = 'indicators.conclusion.';

export const EMPTY: Phrase = { key: `${C}empty` };

/** Traduce a frase e, antes, as frases dos seus parámetros. */
export function translatePhrase(t: Translate, phrase: Phrase): string {
  const params: Record<string, string> = {};
  for (const [name, value] of Object.entries(phrase.params ?? {})) {
    params[name] = typeof value === 'string' ? value : translatePhrase(t, value);
  }
  // Unha abreviatura ao final («12 out.») non leva outro punto.
  return t(phrase.key, params).replace(/\.\.$/, '.');
}

/** «o 26 out.», «a semana do 12 out.», «en outubro»: cando pasou algo, segundo a desagregación. */
export function when(unit: BucketUnit, from: IsoDate, f: IndicatorFormats): Phrase {
  if (unit === 'DAY') return { key: 'indicators.when.DAY', params: { label: f.dayMonth(from) } };
  if (unit === 'WEEK') return { key: 'indicators.when.WEEK', params: { label: f.dayMonth(from) } };
  return { key: 'indicators.when.MONTH', params: { label: f.monthName(from) } };
}

/** A instalación máis chea. */
export function occupancyConclusion(rows: readonly { name: string; ratio: number }[], f: IndicatorFormats): Phrase {
  const top = rows.reduce<{ name: string; ratio: number } | null>((a, b) => (!a || b.ratio > a.ratio ? b : a), null);
  if (!top || top.ratio <= 0) return EMPTY;
  const params = { name: top.name, value: f.percent(top.ratio) };
  return { key: `${C}occupancy.${rows.length === 1 ? 'one' : 'top'}`, params };
}

const weekend = (iso: IsoDate) => toUtc(iso).getUTCDay() % 6 === 0;
const mean = (values: readonly number[]) => values.reduce((sum, v) => sum + v, 0) / values.length;

/**
 * Ocupación ao longo do período: por días, se as fins de semana destacan (máis de 5 puntos sobre
 * os días de diario), dío co seu décimo redondo («máis do 60 %»); se non, o tramo máis cheo.
 */
export function dailyConclusion(
  buckets: readonly { from: IsoDate; ratio: number | null }[],
  unit: BucketUnit,
  f: IndicatorFormats,
): Phrase {
  const open = buckets.filter((b): b is { from: IsoDate; ratio: number } => b.ratio !== null);
  if (!open.some((b) => b.ratio > 0)) return EMPTY;
  if (unit === 'DAY') {
    const ends = open.filter((b) => weekend(b.from)).map((b) => b.ratio);
    const weekdays = open.filter((b) => !weekend(b.from)).map((b) => b.ratio);
    if (ends.length && weekdays.length) {
      const floor = Math.floor(mean(ends) * 10) / 10;
      if (mean(ends) - mean(weekdays) > 0.05 && floor > 0) {
        return { key: `${C}daily.weekends`, params: { value: f.percent(floor) } };
      }
    }
  }
  const top = open.reduce((a, b) => (b.ratio > a.ratio ? b : a));
  const label = unit === 'MONTH' ? f.monthName(top.from) : f.dayMonth(top.from);
  return {
    key: `${C}daily.top.${unit === 'MONTH' ? 'MONTH' : unit === 'WEEK' ? 'WEEK' : 'DAY'}`,
    params: { label, value: f.percent(top.ratio) },
  };
}

/** Unha cela do mapa de calor: día ISO (1 = luns), hora e ocupación (`null`, pechado). */
export interface PeakCell {
  readonly weekday: number;
  readonly hour: number;
  readonly ratio: number | null;
}

/** Horas da mañá (antes das 14) e ocupación por debaixo da que se din «case baleiras». */
const MORNING_END = 14;
const ALMOST_EMPTY = 0.15;

/**
 * A franxa máis reservada: a cela máis chea e as horas seguidas do mesmo día que quedan a menos
 * dun 10 % dela. Se as mañás de diario (abertas) quedan por debaixo do 15 %, engádeo.
 */
export function peakConclusion(cells: readonly PeakCell[], f: IndicatorFormats): Phrase {
  const top = cells.reduce<PeakCell | null>((a, b) => ((b.ratio ?? 0) > (a?.ratio ?? 0) ? b : a), null);
  const peak = top?.ratio;
  if (!top || !peak) return EMPTY;
  const at = (hour: number) => cells.find((c) => c.weekday === top.weekday && c.hour === hour)?.ratio ?? 0;
  const near = (hour: number) => at(hour) >= peak * 0.9;
  let first = top.hour;
  let last = top.hour;
  while (first > 0 && near(first - 1)) first--;
  while (last < 23 && near(last + 1)) last++;
  const mornings = cells
    .filter((c) => c.weekday <= 5 && c.hour < MORNING_END && c.ratio !== null)
    .map((c) => c.ratio as number);
  const quiet = mornings.length > 0 && mean(mornings) < ALMOST_EMPTY;
  const params = {
    day: f.weekdayLong(top.weekday),
    from: String(first),
    to: String(last + 1),
  };
  return { key: `${C}peak.${quiet ? 'topQuietMornings' : 'top'}`, params };
}

function cancellationCount(count: number, f: IndicatorFormats): Phrase {
  return {
    key: `indicators.count.${count === 1 ? 'cancellation' : 'cancellations'}`,
    params: { count: f.integer(count) },
  };
}

/** As cancelacións: o total e só as partes que non son 0; o concello, co seu tramo se foi nun só. */
export function cancellationsConclusion(
  rows: readonly IndicatorCancellationsResponse[],
  unit: BucketUnit,
  bookings: number,
  f: IndicatorFormats,
): Phrase {
  const sum = (pick: (r: IndicatorCancellationsResponse) => number) => rows.reduce((s, r) => s + pick(r), 0);
  const inTime = sum((r) => r.inTime);
  const late = sum((r) => r.late);
  const town = sum((r) => r.byMunicipality);
  const total = inTime + late + town;
  if (total === 0) return bookings > 0 ? { key: `${C}cancellations.none` } : EMPTY;
  const params = {
    total: cancellationCount(total, f),
    late: f.integer(late),
    municipality: f.integer(town),
  };
  const townRows = rows.filter((r) => r.byMunicipality > 0);
  const only = townRows.length === 1 ? townRows[0] : undefined;
  const single = only ? { when: when(unit, only.from, f) } : null;
  if (late > 0)
    return { key: `${C}cancellations.${town > 0 ? 'lateMunicipality' : inTime > 0 ? 'inTimeLate' : 'late'}`, params };
  if (town === 0) return { key: `${C}cancellations.inTime`, params };
  const base = inTime > 0 ? 'inTimeMunicipality' : 'municipality';
  return single
    ? { key: `${C}cancellations.${base}When`, params: { ...params, ...single } }
    : { key: `${C}cancellations.${base}`, params };
}

/** Os cartos: o cobrado e o que queda pendente ou por devolver, sen nomear o que vale 0. */
export function revenueConclusion(
  rows: readonly IndicatorRevenueResponse[],
  bookings: number,
  f: IndicatorFormats,
): Phrase {
  const sum = (pick: (r: IndicatorRevenueResponse) => number) => rows.reduce((s, r) => s + pick(r), 0);
  const paid = sum((r) => r.paidCents);
  const pending = sum((r) => r.pendingCents);
  const toRefund = sum((r) => r.toRefundCents);
  const refunded = sum((r) => r.refundedCents);
  const params = {
    paid: f.euros(paid),
    pending: f.euros(pending),
    toRefund: f.euros(toRefund),
    refunded: f.euros(refunded),
  };
  const flags = `${paid > 0 ? 'p' : ''}${pending > 0 ? 'n' : ''}${toRefund > 0 ? 'r' : ''}`;
  if (flags) return { key: `${C}revenue.${flags}`, params };
  if (refunded > 0) return { key: `${C}revenue.refunded`, params };
  return bookings > 0 ? { key: `${C}revenue.none` } : EMPTY;
}

/** As persoas do último mes: o máximo ou o mínimo dos meses que se ven, ou fronte ao mes anterior. */
export function peopleConclusion(rows: readonly IndicatorPeopleResponse[], f: IndicatorFormats): Phrase {
  const last = rows.at(-1);
  if (!last || rows.every((r) => r.people === 0)) return EMPTY;
  const params = { value: f.integer(last.people), month: f.monthName(last.from), count: f.integer(rows.length) };
  const previous = rows.at(-2);
  if (!previous) return { key: `${C}people.only`, params };
  const others = rows.slice(0, -1).map((r) => r.people);
  if (last.people > Math.max(...others)) return { key: `${C}people.max`, params };
  if (last.people < Math.min(...others)) return { key: `${C}people.min`, params };
  const diff = last.people - previous.people;
  const compared = { ...params, diff: f.integer(Math.abs(diff)), previous: f.monthName(previous.from) };
  const key = diff > 0 ? 'more' : diff < 0 ? 'fewer' : 'same';
  return { key: `${C}people.${key}`, params: compared };
}
