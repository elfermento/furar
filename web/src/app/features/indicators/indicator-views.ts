// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Do panel da API ás seis tarxetas (D3-08): opcións de cada gráfico, filas da táboa, filas do CSV,
// lenda e conclusión. Puro: os textos chegan por `t` e as cifras por IndicatorFormats.
import type { ChartLegendItem } from '../../shared/ui/chart-card/chart-card.component';
import type { ChartSpec } from '../../shared/ui/chart/chart-options';
import { columns as columnChart, hBars, lineArea } from '../../shared/ui/chart/chart-options';
import { heatmap, stackedBars } from '../../shared/ui/chart/chart-composite';
import type { DataTableColumn, DataTableRow } from '../../shared/ui/data-table/data-table.component';
import type { CsvRow } from '../../shared/ui/export-menu/csv';
import type { PeakCell, Phrase, Translate } from './conclusions';
import {
  cancellationsConclusion,
  dailyConclusion,
  occupancyConclusion,
  peakConclusion,
  peopleConclusion,
  revenueConclusion,
  translatePhrase,
} from './conclusions';
import type { IndicatorFormats } from './indicator-format';
import { capitalize } from './indicator-format';
import { barsHeight, wrapNames } from './indicator-axes';
import { markInProgress, partialIndex, seriesLabels } from './indicator-buckets';
import type { Part } from './stacked-parts';
import { legendItem, patternOf } from './stacked-parts';
import type {
  IndicatorBucketOccupancyResponse,
  IndicatorOccupancyResponse,
  IndicatorsResponse,
} from './indicators.store';

/** As claves dos textos: `indicators.*`. */
const PREFIX = 'indicators.';

export type ChartId = 'occupancy' | 'daily' | 'peak' | 'cancellations' | 'revenue' | 'people';

/** Todo o que leva unha tarxeta de gráfico. */
export interface ChartView {
  readonly id: ChartId;
  /** Final do nome do CSV (`indicadores-naron-2026-10-ocupacion.csv`). */
  readonly file: string;
  readonly title: string;
  readonly conclusion: string;
  readonly legend: readonly ChartLegendItem[];
  readonly spec: ChartSpec;
  /** Alto do gráfico en rem (segundo as filas); sen el, o do tamaño. */
  readonly height?: number;
  /** A escala do mapa de calor vai nunha nota á parte. */
  readonly scale?: boolean;
  readonly columns: readonly DataTableColumn[];
  readonly rows: readonly DataTableRow[];
  /** Cabeceira e filas co número en bruto (porcentaxes cun decimal, euros con céntimos). */
  readonly csv: readonly CsvRow[];
}

/** Ocupación (0–1) dunha parte; `null` sen horas abertas. */
export function ratio(o: IndicatorOccupancyResponse): number | null {
  return o.openMinutes > 0 ? Math.min(1, o.reservedMinutes / o.openMinutes) : null;
}

/** Porcentaxe cun decimal (eixes e CSV). */
const pct = (r: number | null) => (r === null ? null : Math.round(r * 1000) / 10);
const euros = (cents: number) => Math.round(cents) / 100;

/** Un panel sen nada: nin reservas, nin cancelacións, nin cartos, nin persoas nos meses que se ven. */
export function emptyPanel(panel: IndicatorsResponse): boolean {
  const cancelled = panel.cancellations.some((c) => c.inTime + c.late + c.byMunicipality > 0);
  const money = panel.revenue.some((r) => r.paidCents + r.pendingCents + r.toRefundCents + r.refundedCents > 0);
  return panel.kpis.bookings.value === 0 && !cancelled && !money && panel.people.every((p) => p.people === 0);
}

/** As seis tarxetas, na orde da maqueta 31 (opción A). */
export function chartViews(panel: IndicatorsResponse, t: Translate, f: IndicatorFormats): ChartView[] {
  const unit = panel.period.breakdownUnit;
  const bookings = panel.kpis.bookings.value;
  const say = (phrase: Phrase) => translatePhrase(t, phrase);
  const tx = (key: string, params?: Record<string, string>) => t(PREFIX + key, params);
  const views: ChartView[] = [];

  // Ocupación por instalación, da máis chea á menos.
  const occupancy = panel.occupancyByFacility
    .map((row) => ({ name: row.name, ratio: ratio(row) ?? 0 }))
    .sort((a, b) => b.ratio - a.ratio);
  const occupancyTitle = tx('occupancy.title');
  views.push({
    id: 'occupancy',
    file: 'ocupacion',
    title: occupancyTitle,
    conclusion: say(occupancyConclusion(occupancy, f)),
    legend: [],
    height: barsHeight(occupancy.length),
    spec: (k) =>
      wrapNames(
        hBars(k, {
          categories: occupancy.map((r) => r.name),
          series: { name: occupancyTitle, values: occupancy.map((r) => pct(r.ratio)) },
          format: (v) => f.percentValue(v),
          max: 100,
        }),
        k.fontSize,
      ),
    columns: [{ label: tx('column.facility') }, { label: tx('column.occupancy'), numeric: true }],
    rows: occupancy.map((r) => [r.name, f.percent(r.ratio)]),
    csv: [[tx('column.facility'), tx('column.occupancyPercent')], ...occupancy.map((r) => [r.name, pct(r.ratio)])],
  });

  // Ocupación ao longo do período.
  const daily = panel.dailyOccupancy.map((b: IndicatorBucketOccupancyResponse) => ({
    ...seriesLabels(unit, b, f, t),
    from: b.from,
    ratio: ratio(b),
    partial: b.partial,
  }));
  const dailyTitle = tx(`daily.title.${unit}`);
  views.push({
    id: 'daily',
    file: 'dia-a-dia',
    title: dailyTitle,
    conclusion: say(dailyConclusion(daily, unit, f)),
    legend: [],
    spec: (k) =>
      lineArea(k, {
        categories: daily.map((d) => d.short),
        series: { name: dailyTitle, values: daily.map((d) => pct(d.ratio)) },
        format: (v) => f.percentValue(v),
        partial: partialIndex(daily),
        partialLabel: t(PREFIX + 'inProgress'),
      }),
    columns: [{ label: tx('column.period') }, { label: tx('column.occupancy'), numeric: true }],
    rows: daily.map((d) => [d.long, d.ratio === null ? tx('peak.closed') : f.percent(d.ratio)]),
    csv: [[tx('column.period'), tx('column.occupancyPercent')], ...daily.map((d) => [d.long, pct(d.ratio)])],
  });

  views.push(peakView(panel, tx, say, f));

  // Cancelacións por motivo: en prazo (verde), fóra de prazo (vermello con raias) e concello (azul
  // con puntos). Cada serie cunha trama distinta: ningunha parella vai só polo ton (1.4.1).
  const cancellations = panel.cancellations.map((c) => ({ ...c, ...seriesLabels(unit, c, f, t) }));
  type Cancel = (typeof cancellations)[0];
  const cancelSeries = [
    { name: tx('cancellations.inTime'), tone: 'success', pick: (c: Cancel) => c.inTime },
    { name: tx('cancellations.late'), tone: 'danger', pattern: 'stripes', pick: (c: Cancel) => c.late },
    { name: tx('cancellations.municipality'), tone: 'info', pattern: 'dots', pick: (c: Cancel) => c.byMunicipality },
  ] as const satisfies readonly Part<Cancel>[];
  views.push({
    id: 'cancellations',
    file: 'cancelacions',
    title: tx('cancellations.title'),
    conclusion: say(cancellationsConclusion(panel.cancellations, unit, bookings, f)),
    legend: cancelSeries.map(legendItem),
    spec: (k) =>
      stackedBars(k, {
        categories: cancellations.map((c) => c.short),
        series: cancelSeries.map((s) => ({
          name: s.name,
          tone: s.tone,
          pattern: patternOf(s),
          values: cancellations.map(s.pick),
        })),
        format: (v) => f.integer(v),
      }),
    columns: [{ label: tx('column.period') }, ...cancelSeries.map((s) => ({ label: s.name, numeric: true }))],
    rows: cancellations.map((c) => [c.long, ...cancelSeries.map((s) => f.integer(s.pick(c)))]),
    csv: [
      [tx('column.period'), ...cancelSeries.map((s) => s.name)],
      ...cancellations.map((c) => [c.long, ...cancelSeries.map((s) => s.pick(c))]),
    ],
  });

  // Ingresos por instalación: cobrado (verde), pendente (ámbar con puntos: co verde só cambia o
  // ton), a devolver (vermello con raias, a alerta) e devolto (gris con raias horizontais: en
  // escuro ten a luminancia do vermello).
  const revenue = panel.revenue;
  type Money = (typeof revenue)[0];
  const moneySeries = [
    { name: tx('revenue.paid'), tone: 'success', pick: (r: Money) => r.paidCents },
    { name: tx('revenue.pending'), tone: 'warn', pattern: 'dots', pick: (r: Money) => r.pendingCents },
    { name: tx('revenue.toRefund'), tone: 'danger', pattern: 'stripes', pick: (r: Money) => r.toRefundCents },
    { name: tx('revenue.refunded'), tone: 'muted', pattern: 'lines', pick: (r: Money) => r.refundedCents },
  ] as const satisfies readonly Part<Money>[];
  views.push({
    id: 'revenue',
    file: 'ingresos',
    title: tx('revenue.title'),
    conclusion: say(revenueConclusion(revenue, bookings, f)),
    legend: moneySeries.map(legendItem),
    height: barsHeight(revenue.length),
    spec: (k) =>
      wrapNames(
        stackedBars(k, {
          horizontal: true,
          categories: revenue.map((r) => r.name),
          series: moneySeries.map((s) => ({
            name: s.name,
            tone: s.tone,
            pattern: patternOf(s),
            values: revenue.map((r) => euros(s.pick(r))),
          })),
          format: (v) => f.euros(v * 100),
        }),
        k.fontSize,
      ),
    columns: [{ label: tx('column.facility') }, ...moneySeries.map((s) => ({ label: s.name, numeric: true }))],
    rows: revenue.map((r) => [r.name, ...moneySeries.map((s) => f.eurosExact(s.pick(r)))]),
    csv: [
      [tx('column.facility'), ...moneySeries.map((s) => tx('column.euros', { name: s.name }))],
      ...revenue.map((r) => [r.name, ...moneySeries.map((s) => euros(s.pick(r)))]),
    ],
  });

  // Persoas distintas por mes: o último en `primary`, os anteriores co paso 2 da escala.
  const people = panel.people;
  const peopleTitle = tx('people.title');
  views.push({
    id: 'people',
    file: 'persoas',
    title: peopleTitle,
    conclusion: say(peopleConclusion(people, f)),
    legend: [],
    spec: (k) =>
      columnChart(k, {
        categories: people.map((p) => f.monthShort(p.from)),
        series: { name: peopleTitle, values: people.map((p) => p.people) },
        format: (v) => f.integer(v),
      }),
    columns: [{ label: tx('column.month') }, { label: tx('column.people'), numeric: true }],
    rows: people.map((p) => [markInProgress(f.monthYear(p.from), p.partial, t), f.integer(p.people)]),
    csv: [
      [tx('column.month'), tx('column.people')],
      ...people.map((p) => [markInProgress(f.monthYear(p.from), p.partial, t), p.people]),
    ],
  });
  return views;
}

/** Horas punta: só as horas nas que abre algún día; as celas sen horas abertas, «pechado» (bordo discontinuo). */
function peakView(
  panel: IndicatorsResponse,
  tx: (key: string, params?: Record<string, string>) => string,
  say: (phrase: Phrase) => string,
  f: IndicatorFormats,
): ChartView {
  const cells: PeakCell[] = panel.peakHours.map((c) => ({ weekday: c.isoWeekday, hour: c.hour, ratio: ratio(c) }));
  const openHours = cells.filter((c) => c.ratio !== null).map((c) => c.hour);
  const first = openHours.length ? Math.min(...openHours) : 0;
  const last = openHours.length ? Math.max(...openHours) : -1;
  const hours = Array.from({ length: last - first + 1 }, (_, i) => first + i);
  const days = [1, 2, 3, 4, 5, 6, 7];
  const at = (day: number, hour: number) => cells.find((c) => c.weekday === day && c.hour === hour)?.ratio ?? null;
  const hourLabel = (hour: number) => `${hour}\u00a0h`;
  // Na táboa e no CSV, a hora enteira: «8–9 h».
  const spanLabel = (hour: number) => `${hour}–${hour + 1}\u00a0h`;
  const closed = tx('peak.closed');
  const title = tx('peak.title');
  return {
    id: 'peak',
    file: 'horas-punta',
    title,
    conclusion: say(peakConclusion(cells, f)),
    legend: [],
    scale: true,
    height: 18,
    spec: (k) =>
      heatmap(k, {
        columns: hours.map(hourLabel),
        rows: days.map((d) => capitalize(f.weekdayShort(d), f.lang)),
        cells: days.flatMap((d, y) => hours.map((h, x) => ({ x, y, value: pct(at(d, h)) }))),
        closedLabel: capitalize(closed, f.lang),
        format: (v) => f.percentValue(v),
        max: 100,
      }),
    columns: [{ label: tx('column.day') }, ...hours.map((h) => ({ label: spanLabel(h), numeric: true }))],
    rows: days.map((d) => [
      capitalize(f.weekdayLong(d), f.lang),
      ...hours.map((h) => {
        const value = at(d, h);
        return value === null ? closed : f.percent(value);
      }),
    ]),
    csv: [
      [tx('column.day'), ...hours.map(spanLabel)],
      ...days.map((d) => [capitalize(f.weekdayLong(d), f.lang), ...hours.map((h) => pct(at(d, h)) ?? closed)]),
    ],
  };
}
