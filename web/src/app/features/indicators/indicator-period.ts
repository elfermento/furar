// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// O período en texto (cabeceira impresa e anuncio), o nome dos CSV e o CSV do panel. Puro.
import type { CsvRow } from '../../shared/ui/export-menu/csv';
import type { Translate } from './conclusions';
import type { IndicatorFormats } from './indicator-format';
import type { KpiView } from './indicator-kpis';
import type { ChartView } from './indicator-views';
import type { IndicatorPeriodResponse } from './indicators.store';

/** «Outubro de 2026», «Semana do 26/10/2026 ao 01/11/2026», «4.º trimestre de 2026», «Ano 2026»… */
export function periodTitle(period: IndicatorPeriodResponse, t: Translate, f: IndicatorFormats): string {
  if (period.kind === 'MONTH') return f.monthYear(period.from);
  const params = {
    from: f.shortDate(period.from),
    to: f.shortDate(period.to),
    year: period.from.slice(0, 4),
    quarter: String(Math.floor((Number(period.from.slice(5, 7)) - 1) / 3) + 1),
  };
  return t(`indicators.period.${period.kind}`, params);
}

/** A parte do período no nome do ficheiro: `2026-10`, `2026-t4`, `2026`, `semana-2026-10-26`… */
export function periodFile(period: IndicatorPeriodResponse): string {
  const year = period.from.slice(0, 4);
  switch (period.kind) {
    case 'MONTH':
      return period.from.slice(0, 7);
    case 'QUARTER':
      return `${year}-t${Math.floor((Number(period.from.slice(5, 7)) - 1) / 3) + 1}`;
    case 'YEAR':
      return year;
    case 'WEEK':
      return `semana-${period.from}`;
    default:
      return `${period.from}_${period.to}`;
  }
}

/** `indicadores-<concello>-<período>[-<gráfico>].csv` (ADR-0026; a evolución dunha cifra, `evolucion-<cifra>`). */
export function csvName(slug: string, period: IndicatorPeriodResponse, chart?: Pick<ChartView, 'file'>): string {
  return ['indicadores', slug, periodFile(period), ...(chart ? [chart.file] : [])].join('-') + '.csv';
}

/**
 * O CSV do panel: un bloque por táboa (as cifras e os seis gráficos), cada un co seu título. A
 * cabeceira das cifras é `kpiCsvHeader` (no período en curso leva os tramos comparados).
 */
export function panelCsv(
  heading: readonly string[],
  kpis: readonly KpiView[],
  charts: readonly ChartView[],
  t: Translate,
  kpiHeader?: CsvRow,
): CsvRow[] {
  const rows: CsvRow[] = [heading, []];
  rows.push([t('indicators.kpi.title')]);
  rows.push(
    kpiHeader ?? [t('indicators.column.figure'), t('indicators.column.value'), t('indicators.column.previous')],
  );
  rows.push(...kpis.map((k) => k.csv));
  for (const chart of charts) {
    rows.push([], [chart.title], ...chart.csv);
  }
  return rows;
}
