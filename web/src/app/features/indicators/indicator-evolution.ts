// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// A evolución dunha cifra no modal (D3-09; plan do dominio 3, §1.2.5): na unidade do filtro (12
// semanas, 12 meses, 8 trimestres ou 5 anos), coa mesma liña con área da minigráfica e a etiqueta
// só no último valor, a táboa e as filas do CSV. O punto en curso vai co tramo discontinuo e
// «(en curso)» no tooltip, na etiqueta, na táboa e no CSV. Puro.
import type { ChartSpec } from '../../shared/ui/chart/chart-options';
import { lineArea } from '../../shared/ui/chart/chart-options';
import type { DataTableColumn, DataTableRow } from '../../shared/ui/data-table/data-table.component';
import type { CsvRow } from '../../shared/ui/export-menu/csv';
import type { StatTrend } from '../../shared/ui/stat-tile/stat-tile.component';
import type { BucketUnit, Translate } from './conclusions';
import type { IndicatorFormats } from './indicator-format';
import type { KpiId, KpiView } from './indicator-kpis';
import { bucketLabels, partialIndex } from './indicator-buckets';
import { ratio } from './indicator-views';
import type { IndicatorsResponse } from './indicators.store';

const E = 'indicators.evolution.';

/** Todo o que leva o modal dunha cifra. */
export interface EvolutionView {
  readonly id: KpiId;
  /** «Reservas por meses». */
  readonly title: string;
  readonly value: string;
  readonly change: string;
  readonly trend: StatTrend | null;
  readonly comparison: string;
  /** Nome do gráfico e `alt` da súa imaxe impresa: o título e o que di. */
  readonly name: string;
  /** Final do nome do CSV (`indicadores-naron-2026-10-evolucion-reservas.csv`). */
  readonly file: string;
  readonly spec: ChartSpec;
  readonly columns: readonly DataTableColumn[];
  readonly rows: readonly DataTableRow[];
  readonly csv: readonly CsvRow[];
}

/** Un punto da evolución: o tramo e o valor xa na unidade do gráfico (porcentaxe, horas, euros). */
interface Point {
  readonly from: string;
  readonly to: string;
  readonly value: number | null;
  /** O período en curso (aínda non rematou). */
  readonly partial: boolean;
}

const FILES: Record<KpiId, string> = {
  bookings: 'reservas',
  occupancy: 'ocupacion',
  reservedHours: 'horas',
  people: 'persoas',
  paidCents: 'cobrado',
};

/** Os puntos dunha cifra, cos valores en bruto como no CSV do panel. */
function points(panel: IndicatorsResponse, id: KpiId): Point[] {
  const { kpis } = panel;
  switch (id) {
    case 'occupancy':
      return kpis.occupancy.evolution.map((p) => {
        const r = ratio(p.value);
        return { ...p, value: r === null ? null : Math.round(r * 1000) / 10 };
      });
    case 'reservedHours':
      return kpis.reservedHours.evolution.map((p) => ({ ...p, value: Math.round(p.value / 6) / 10 }));
    case 'paidCents':
      return kpis.paidCents.evolution.map((p) => ({ ...p, value: Math.round(p.value) / 100 }));
    default:
      return kpis[id].evolution.map((p) => ({ ...p }));
  }
}

/** Como se escribe un valor da evolución (xa en porcentaxe, horas ou euros). */
function formatter(id: KpiId, f: IndicatorFormats): (value: number) => string {
  switch (id) {
    case 'occupancy':
      return (v) => f.percentValue(v);
    case 'reservedHours':
      return (v) => f.hours(v * 60);
    case 'paidCents':
      return (v) => f.euros(v * 100);
    default:
      return (v) => f.integer(v);
  }
}

/**
 * Rótulos dun tramo: curto no eixo e longo na táboa. Os meses levan o ano curto no primeiro e en
 * xaneiro («nov. 25», «xan. 26»), para que doce meses de dous anos non se confundan.
 */
export function evolutionLabels(
  unit: BucketUnit,
  from: string,
  to: string,
  first: boolean,
  t: Translate,
  f: IndicatorFormats,
) {
  const year = from.slice(0, 4);
  if (unit === 'YEAR') return { short: year, long: year };
  if (unit === 'QUARTER') {
    const quarter = String(Math.floor((Number(from.slice(5, 7)) - 1) / 3) + 1);
    return {
      short: t(`${E}quarterShort`, { quarter, year: year.slice(2) }),
      long: t('indicators.period.QUARTER', { quarter, year }),
    };
  }
  const labels = bucketLabels(unit, from, to, f);
  if (unit === 'MONTH' && (first || from.slice(5, 7) === '01')) {
    return { ...labels, short: `${labels.short} ${year.slice(2)}` };
  }
  return labels;
}

/** O modal da cifra `kpi` (o seu valor, cambio e comparación son os da tarxeta). */
export function evolutionView(
  panel: IndicatorsResponse,
  kpi: KpiView,
  t: Translate,
  f: IndicatorFormats,
): EvolutionView {
  const unit = panel.period.evolutionUnit;
  const format = formatter(kpi.id, f);
  const inProgress = t('indicators.inProgress');
  const series = points(panel, kpi.id).map((p, i) => {
    const labels = evolutionLabels(unit, p.from, p.to, i === 0, t, f);
    return { ...p, ...labels, long: p.partial ? `${labels.long} (${inProgress})` : labels.long };
  });
  const partial = partialIndex(series);
  const title = t(`${E}title`, { figure: kpi.label, unit: t(`${E}unit.${unit}`) });
  const none = t('ui.statTile.noPrevious');
  const said = kpi.trend ? `${kpi.change} ${kpi.comparison}` : none.charAt(0).toLocaleLowerCase(f.lang) + none.slice(1);
  const raw = kpi.id === 'occupancy' ? t('indicators.kpi.occupancyPercent') : t(`${E}column.${kpi.id}`);
  return {
    id: kpi.id,
    title,
    value: kpi.value,
    change: kpi.change,
    trend: kpi.trend,
    comparison: kpi.comparison,
    name: `${title}. ${t(`${E}summary`, { value: kpi.value, change: said })}`,
    file: `evolucion-${FILES[kpi.id]}`,
    spec: (k) =>
      lineArea(k, {
        categories: series.map((p) => p.short),
        series: { name: kpi.label, values: series.map((p) => p.value) },
        format,
        partial,
        partialLabel: inProgress,
      }),
    columns: [{ label: t('indicators.column.period') }, { label: kpi.label, numeric: true }],
    rows: series.map((p) => [p.long, p.value === null ? '—' : format(p.value)]),
    csv: [[t('indicators.column.period'), raw], ...series.map((p) => [p.long, p.value])],
  };
}
