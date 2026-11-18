// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Indicadores da plataforma (ADR-0026, revisión do 2026-11-11): a táboa dos
// concellos, a orde dende a cabeceira (no móbil, «Ordenar»), as tarxetas do móbil e o CSV. Puro.

import type { CsvRow } from '../../../shared/ui/export-menu/csv';
import type { Translate } from '../conclusions';
import type { IndicatorFormats } from '../indicator-format';
import type { KpiPanel } from '../indicator-kpis';
import { ratio } from '../indicator-views';
import type { PlatformIndicatorsResponse, PlatformMunicipalityIndicatorsResponse } from './platform-indicators.store';

export type { PlatformIndicatorsResponse, PlatformMunicipalityIndicatorsResponse };

/** As columnas da táboa, na súa orde. */
export const COLUMNS = ['name', 'bookings', 'occupancy', 'reservedHours', 'people', 'paidCents'] as const;
export type ColumnId = (typeof COLUMNS)[number];
export type SortDirection = 'asc' | 'desc';

export interface Sort {
  readonly column: ColumnId;
  readonly direction: SortDirection;
}

/** De entrada, os que máis reservan primeiro. */
export const DEFAULT_SORT: Sort = { column: 'bookings', direction: 'desc' };

const T = 'platformIndicators.';

/** Os totais coa forma das cifras do panel: sen persoas nin valoración (non se suman). */
export function totalsPanel(response: PlatformIndicatorsResponse): KpiPanel {
  return {
    period: response.period,
    previous: response.previous,
    comparison: response.comparison,
    kpis: response.kpis,
  };
}

/** O valor polo que se ordena unha columna; a ocupación sen horas abertas vai ao final. */
function sortValue(row: PlatformMunicipalityIndicatorsResponse, column: Exclude<ColumnId, 'name'>): number {
  switch (column) {
    case 'occupancy':
      return ratio(row.occupancy) ?? -1;
    case 'reservedHours':
      return row.reservedMinutes;
    default:
      return row[column];
  }
}

/** As filas na orde escollida; a igualdade, por nome (sempre de A a Z). */
export function sortRows(
  rows: readonly PlatformMunicipalityIndicatorsResponse[],
  sort: Sort,
  lang: string,
): PlatformMunicipalityIndicatorsResponse[] {
  const collator = new Intl.Collator(lang, { sensitivity: 'base', numeric: true });
  const byName = (a: PlatformMunicipalityIndicatorsResponse, b: PlatformMunicipalityIndicatorsResponse) =>
    collator.compare(a.name, b.name);
  const sign = sort.direction === 'asc' ? 1 : -1;
  const column = sort.column;
  return [...rows].sort((a, b) => {
    if (column === 'name') return sign * byName(a, b);
    const diff = sortValue(a, column) - sortValue(b, column);
    return diff === 0 ? byName(a, b) : sign * diff;
  });
}

/** Unha fila xa formatada: as cifras da táboa e a liña da tarxeta do móbil. */
export interface RowView {
  readonly slug: string;
  readonly name: string;
  readonly bookings: string;
  readonly occupancy: string;
  readonly reservedHours: string;
  readonly people: string;
  readonly paidCents: string;
  /** «2.105 reservas · 63 % de ocupación · 2.388 h · 1.031 persoas». */
  readonly summary: string;
}

export function rowViews(
  rows: readonly PlatformMunicipalityIndicatorsResponse[],
  t: Translate,
  f: IndicatorFormats,
): RowView[] {
  return rows.map((row) => {
    const share = ratio(row.occupancy);
    const occupancy = share === null ? '—' : f.percent(share);
    const hours = f.hours(row.reservedMinutes);
    const count = (key: string, n: number) => t(`${T}card.${key}${n === 1 ? 'One' : ''}`, { count: f.integer(n) });
    return {
      slug: row.slug,
      name: row.name,
      bookings: f.integer(row.bookings),
      occupancy,
      reservedHours: hours,
      people: f.integer(row.people),
      paidCents: f.eurosExact(row.paidCents),
      summary: [
        count('bookings', row.bookings),
        share === null ? t(`${T}card.noOccupancy`) : t(`${T}card.occupancy`, { value: occupancy }),
        hours,
        count('people', row.people),
      ].join(' · '),
    };
  });
}

/** O rótulo dunha columna (a cabeceira e o seu panel). */
export function columnLabel(column: ColumnId, t: Translate): string {
  return t(`${T}column.${column}`);
}

/** As dúas ordes dunha columna, no seu panel da cabeceira: «De maior a menor» e «De menor a maior». */
export function columnSortOptions(column: ColumnId, t: Translate): { value: SortDirection; label: string }[] {
  const kind = column === 'name' ? 'text' : 'number';
  return [
    { value: column === 'name' ? 'asc' : 'desc', label: t(`${T}sort.${kind}.first`) },
    { value: column === 'name' ? 'desc' : 'asc', label: t(`${T}sort.${kind}.second`) },
  ];
}

/** «Ordenar» no móbil: o nome de A a Z e cada cifra de maior a menor. */
export function mobileSortOptions(t: Translate): { value: string; label: string; sort: Sort }[] {
  return COLUMNS.map((column) => {
    const sort: Sort = { column, direction: column === 'name' ? 'asc' : 'desc' };
    return { value: column, label: t(`${T}sort.mobile.${column}`), sort };
  });
}

/**
 * O CSV: a cabeceira impresa, as catro cifras (cos seus valores en bruto) e a táboa dos concellos,
 * na orde que se ve. Ocupación en porcentaxe cun decimal, horas cun decimal e euros con céntimos.
 */
export function platformCsv(
  heading: string,
  kpis: readonly { readonly csv: CsvRow }[],
  kpiHeader: CsvRow,
  rows: readonly PlatformMunicipalityIndicatorsResponse[],
  t: Translate,
): CsvRow[] {
  const percent = (r: number | null) => (r === null ? null : Math.round(r * 1000) / 10);
  return [
    [heading],
    [],
    [t('indicators.kpi.title')],
    kpiHeader,
    ...kpis.map((k) => k.csv),
    [],
    [t(`${T}caption`)],
    [
      t(`${T}column.name`),
      t(`${T}column.bookings`),
      t('indicators.kpi.occupancyPercent'),
      t(`${T}column.reservedHours`),
      t(`${T}column.people`),
      t(`${T}column.paidCents`),
    ],
    ...rows.map((row) => [
      row.name,
      row.bookings,
      percent(ratio(row.occupancy)),
      Math.round(row.reservedMinutes / 6) / 10,
      row.people,
      Math.round(row.paidCents) / 100,
    ]),
  ];
}
