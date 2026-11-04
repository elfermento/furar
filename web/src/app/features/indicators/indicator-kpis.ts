// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// As cinco cifras do panel (D3-08): valor, cambio co período anterior (en porcentaxe nas contas,
// horas e diñeiro; en puntos na ocupación), con que se compara e a minigráfica cos seis últimos
// valores da evolución. No período en curso, o cambio compara ata hoxe co mesmo tramo do anterior
// (`comparison` do panel; ADR-0026, revisión do 2026-10-29) e a minigráfica leva o último tramo
// discontinuo. Puro.
import type { ChartSpec } from '../../shared/ui/chart/chart-options';
import { sparkline } from '../../shared/ui/chart/chart-options';
import type { CsvRow } from '../../shared/ui/export-menu/csv';
import type { StatTrend } from '../../shared/ui/stat-tile/stat-tile.component';
import type { Translate } from './conclusions';
import type { IndicatorFormats } from './indicator-format';
import { partialIndex } from './indicator-buckets';
import { ratio } from './indicator-views';
import type { IndicatorsResponse } from './indicators.store';

/** As claves dos textos das cifras. */
const KPI = 'indicators.kpi.';

export type KpiId = 'bookings' | 'occupancy' | 'reservedHours' | 'people' | 'paidCents';

/** Valores da minigráfica: os seis últimos da evolución. */
export const SPARK_POINTS = 6;

export interface KpiView {
  readonly id: KpiId;
  readonly label: string;
  readonly value: string;
  /** «+8 %», «−3 p.p.»; baleiro sen período anterior. */
  readonly change: string;
  readonly trend: StatTrend | null;
  readonly comparison: string;
  readonly spark: ChartSpec;
  /**
   * Fila do CSV do panel (en bruto): cifra, valor e anterior; no período en curso, cifra, valor,
   * ata hoxe e o mesmo tramo do anterior (`kpiCsvHeader`).
   */
  readonly csv: CsvRow;
}

/** Cambio redondeado ao enteiro; `null` sen valor anterior (0 ou sen horas abertas). */
export function percentChange(value: number, previous: number): number | null {
  return previous === 0 ? null : Math.round(((value - previous) / previous) * 100);
}

export function pointsChange(value: number | null, previous: number | null): number | null {
  return value === null || previous === null ? null : Math.round((value - previous) * 100);
}

function trendOf(change: number | null): StatTrend | null {
  if (change === null) return null;
  return change > 0 ? 'up' : change < 0 ? 'down' : 'same';
}

/**
 * «respecto a setembro», «respecto á semana anterior»…; no período en curso, «ata hoxe, fronte ao
 * mesmo tramo de setembro».
 */
export function comparisonText(panel: IndicatorsResponse, t: Translate, f: IndicatorFormats): string {
  const { kind } = panel.period;
  const from = panel.previous.from;
  const label = kind === 'MONTH' ? f.monthName(from) : kind === 'YEAR' ? from.slice(0, 4) : '';
  const days = String(Math.round((Date.parse(panel.previous.to) - Date.parse(from)) / 86_400_000) + 1);
  const key = panel.comparison.toDate ? 'comparisonToDate' : 'comparison';
  return t(`${KPI}${key}.${kind}`, { label, days });
}

/** Cabeceira das cifras no CSV do panel: no período en curso, cos dous tramos comparados en texto. */
export function kpiCsvHeader(panel: IndicatorsResponse, t: Translate, f: IndicatorFormats): CsvRow {
  const { comparison } = panel;
  if (!comparison.toDate) {
    return [t('indicators.column.figure'), t('indicators.column.value'), t('indicators.column.previous')];
  }
  const span = (from: string, to: string) => `${f.shortDate(from)} – ${f.shortDate(to)}`;
  return [
    t('indicators.column.figure'),
    t('indicators.column.valueInProgress', { inProgress: t('indicators.inProgress') }),
    t('indicators.column.toDate', { span: span(comparison.from, comparison.to) }),
    t('indicators.column.comparedToDate', { span: span(comparison.comparedFrom, comparison.comparedTo) }),
  ];
}

/**
 * O cambio dunha conta co período anterior. Sen anterior (o período anterior non tivo horas
 * abertas: non houbo con que comparar), nada. Se o anterior foi 0, o porcentaxe non existe: sae
 * a diferenza na unidade da cifra («+205», «+1.234 €») ou, se tamén agora é 0, «0 %» igual.
 */
export function countChange(
  value: number,
  previous: number,
  hadPrevious: boolean,
  format: (v: number) => string,
  f: IndicatorFormats,
): { change: string; trend: StatTrend | null } {
  if (!hadPrevious) return { change: '', trend: null };
  if (previous === 0) {
    return value === 0
      ? { change: f.change(0, 'percent'), trend: 'same' }
      : { change: `+${format(value)}`, trend: 'up' };
  }
  const change = percentChange(value, previous) as number;
  return { change: f.change(change, 'percent'), trend: trendOf(change) };
}

export function kpiViews(panel: IndicatorsResponse, t: Translate, f: IndicatorFormats): KpiView[] {
  const { kpis } = panel;
  const toDate = panel.comparison.toDate;
  // Con que se compara: o mesmo tramo do anterior (no período en curso, ata hoxe).
  const hadPrevious = kpis.occupancy.previousToDate.openMinutes > 0;
  const comparison = comparisonText(panel, t, f);
  const label = (id: KpiId) => t(KPI + id);
  const spark = (points: readonly { value: number | null; partial: boolean }[]): ChartSpec => {
    const last = points.slice(-SPARK_POINTS);
    const values = last.map((p) => p.value);
    const partial = partialIndex(last);
    return (tokens) => sparkline(tokens, values, partial);
  };
  const counted = (
    id: KpiId,
    kpi: {
      value: number;
      previous: number;
      valueToDate: number;
      previousToDate: number;
      evolution: readonly { value: number; partial: boolean }[];
    },
    format: (v: number) => string,
    raw: (v: number) => number,
  ): KpiView => {
    return {
      id,
      label: label(id),
      value: format(kpi.value),
      ...countChange(kpi.valueToDate, kpi.previousToDate, hadPrevious, format, f),
      comparison,
      spark: spark(kpi.evolution),
      csv: toDate
        ? [label(id), raw(kpi.value), raw(kpi.valueToDate), raw(kpi.previousToDate)]
        : [label(id), raw(kpi.value), raw(kpi.previous)],
    };
  };
  const now = ratio(kpis.occupancy.value);
  const before = ratio(kpis.occupancy.previous);
  const nowToDate = ratio(kpis.occupancy.valueToDate);
  const beforeToDate = ratio(kpis.occupancy.previousToDate);
  const points = pointsChange(nowToDate, beforeToDate);
  const percent = (r: number | null) => (r === null ? null : Math.round(r * 1000) / 10);
  const occupancy: KpiView = {
    id: 'occupancy',
    label: label('occupancy'),
    value: now === null ? '—' : f.percent(now),
    change: points === null ? '' : f.change(points, 'points'),
    trend: trendOf(points),
    comparison,
    spark: spark(kpis.occupancy.evolution.map((p) => ({ value: percent(ratio(p.value)), partial: p.partial }))),
    csv: toDate
      ? [t(`${KPI}occupancyPercent`), percent(now), percent(nowToDate), percent(beforeToDate)]
      : [t(`${KPI}occupancyPercent`), percent(now), percent(before)],
  };
  const hours = (minutes: number) => Math.round(minutes / 6) / 10;
  return [
    counted(
      'bookings',
      kpis.bookings,
      (v) => f.integer(v),
      (v) => v,
    ),
    occupancy,
    counted('reservedHours', kpis.reservedHours, (v) => f.hours(v), hours),
    counted(
      'people',
      kpis.people,
      (v) => f.integer(v),
      (v) => v,
    ),
    counted(
      'paidCents',
      kpis.paidCents,
      (v) => f.euros(v),
      (v) => Math.round(v) / 100,
    ),
  ];
}
