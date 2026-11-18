// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// As cinco cifras do panel: valor, cambio co período anterior (en porcentaxe nas contas,
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
import { formatAverage } from '../../shared/ui/stars/rating-format';
import { ratingAverage, ratingChange } from './indicator-rating';
import { ratio } from './indicator-views';
import type { IndicatorsResponse } from './indicators.store';

/**
 * O que precisan as cifras e o modal da evolución: o período, a comparación e as cifras. As
 * persoas e a valoración poden faltar (os totais da plataforma non as levan: unha persoa que
 * reserva en dous concellos contaría dúas veces); entón esas tarxetas non saen.
 */
export type KpiPanel = Pick<IndicatorsResponse, 'period' | 'previous' | 'comparison'> & {
  readonly kpis: Pick<IndicatorsResponse['kpis'], 'bookings' | 'occupancy' | 'reservedHours' | 'paidCents'> &
    Partial<Pick<IndicatorsResponse['kpis'], 'people' | 'rating'>>;
};

/** As claves dos textos das cifras. */
const KPI = 'indicators.kpi.';

export type KpiId = 'bookings' | 'occupancy' | 'reservedHours' | 'people' | 'paidCents' | 'rating';

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
export function comparisonText(panel: KpiPanel, t: Translate, f: IndicatorFormats): string {
  const { kind } = panel.period;
  const from = panel.previous.from;
  const label = kind === 'MONTH' ? f.monthName(from) : kind === 'YEAR' ? from.slice(0, 4) : '';
  const days = String(Math.round((Date.parse(panel.previous.to) - Date.parse(from)) / 86_400_000) + 1);
  const key = panel.comparison.toDate ? 'comparisonToDate' : 'comparison';
  return t(`${KPI}${key}.${kind}`, { label, days });
}

/** Cabeceira das cifras no CSV do panel: no período en curso, cos dous tramos comparados en texto. */
export function kpiCsvHeader(panel: KpiPanel, t: Translate, f: IndicatorFormats): CsvRow {
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

export function kpiViews(panel: KpiPanel, t: Translate, f: IndicatorFormats): KpiView[] {
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
  // A valoración media (ADR-0037): o cambio en décimas de nota, na mesma unidade ca a cifra.
  const rating = kpis.rating;
  const average = rating ? ratingAverage(rating.value) : null;
  const ratingView: KpiView | null = rating
    ? {
        id: 'rating',
        label: label('rating'),
        value: average === null ? '—' : t(`${KPI}ratingValue`, { value: formatAverage(average, f.lang) }),
        ...ratingChange(rating.valueToDate, rating.previousToDate, f.lang),
        comparison,
        spark: spark(rating.evolution.map((p) => ({ value: ratingAverage(p.value), partial: p.partial }))),
        csv: toDate
          ? [label('rating'), average, ratingAverage(rating.valueToDate), ratingAverage(rating.previousToDate)]
          : [label('rating'), average, ratingAverage(rating.previous)],
      }
    : null;
  const hours = (minutes: number) => Math.round(minutes / 6) / 10;
  const views: (KpiView | null)[] = [
    counted(
      'bookings',
      kpis.bookings,
      (v) => f.integer(v),
      (v) => v,
    ),
    occupancy,
    counted('reservedHours', kpis.reservedHours, (v) => f.hours(v), hours),
    kpis.people
      ? counted(
          'people',
          kpis.people,
          (v) => f.integer(v),
          (v) => v,
        )
      : null,
    counted(
      'paidCents',
      kpis.paidCents,
      (v) => f.euros(v),
      (v) => Math.round(v) / 100,
    ),
    ratingView,
  ];
  return views.filter((view): view is KpiView => view !== null);
}
