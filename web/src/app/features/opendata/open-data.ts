// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import { addDays, addMonths, startOfMonth } from '../../shared/ui/calendar/local-date';
import type { DataTableRow } from '../../shared/ui/data-table/data-table.component';
import type { DateFilterPreset } from '../../shared/ui/date-filter/date-filter';
import type { ValueListItem } from '../../shared/ui/value-list/value-list.component';
import type { OpenDataCustomField } from './open-data.store';

type Translate = (key: string, params?: Record<string, unknown>) => string;

/** As tres pestanas, coa clave do URL (`?conxunto=`) e o conxunto da API. */
export type DatasetKey = 'FACILITIES' | 'CLOSURES' | 'OCCUPANCY';
export type DownloadFormat = 'csv' | 'json' | 'geojson';

export const DATASETS: readonly { key: DatasetKey; tab: string; url: string; file: string }[] = [
  { key: 'FACILITIES', tab: 'facilities', url: 'instalacions', file: 'instalacions' },
  { key: 'CLOSURES', tab: 'closures', url: 'peches', file: 'peches' },
  { key: 'OCCUPANCY', tab: 'occupancy', url: 'ocupacion', file: 'ocupacion' },
];

/** A pestana do URL (`?conxunto=peches|ocupacion`); sen el ou descoñecido, Instalacións. */
export function datasetFromUrl(value: string | null | undefined): DatasetKey {
  return DATASETS.find((d) => d.url === value)?.key ?? 'FACILITIES';
}

export function datasetUrl(key: DatasetKey): string | null {
  return key === 'FACILITIES' ? null : (DATASETS.find((d) => d.key === key)?.url ?? null);
}

/** Unha fila de «Columnas»: o nome (ou nomes) da columna e a clave do texto do seu contido. */
export interface ColumnRow {
  readonly name: string;
  readonly content: string;
}

export const FACILITY_COLUMNS: readonly ColumnRow[] = [
  { name: 'id', content: 'id' },
  { name: 'nome', content: 'nome' },
  { name: 'descricion', content: 'descricion' },
  { name: 'enderezo', content: 'enderezo' },
  { name: 'rua, numero, codigo_postal, localidade', content: 'enderezoPartes' },
  { name: 'latitude, lonxitude', content: 'coordenadas' },
  { name: 'telefono, correo', content: 'contacto' },
  { name: 'url', content: 'url' },
  { name: 'deportes', content: 'deportes' },
  { name: 'deportes_codigos', content: 'deportesCodigos' },
  { name: 'cuberta', content: 'cuberta' },
  { name: 'servizos', content: 'servizos' },
  { name: 'pechada_temporalmente', content: 'pechada' },
  { name: 'reserva_internet', content: 'reservaInternet' },
  { name: 'horario', content: 'horario' },
  { name: 'normas', content: 'normas' },
];

export const USE_COLUMNS: readonly ColumnRow[] = [
  { name: 'uso_id', content: 'usoId' },
  { name: 'uso', content: 'uso' },
  { name: 'modo_aluguer', content: 'modo' },
  { name: 'franxa_minutos', content: 'franxaMinutos' },
  { name: 'partes', content: 'partes' },
  { name: 'prazas', content: 'prazas' },
  { name: 'dias', content: 'dias' },
  { name: 'datas', content: 'datas' },
  { name: 'prezo_euros', content: 'prezoEuros' },
  { name: 'prezo_por', content: 'prezoPor' },
  { name: 'antelacion_minima_horas, antelacion_maxima_dias', content: 'antelacion' },
  { name: 'franxas_maximas', content: 'franxasMaximas' },
  { name: 'limite_por_persoa, limite_periodo', content: 'limite' },
  { name: 'cancelar_sen_custo_horas', content: 'cancelar' },
];

export const CLOSURE_COLUMNS: readonly ColumnRow[] = [
  { name: 'data', content: 'data' },
  { name: 'tipo', content: 'tipo' },
  { name: 'instalacion', content: 'instalacionPeche' },
  { name: 'motivo', content: 'motivo' },
];

export const OCCUPANCY_COLUMNS: readonly ColumnRow[] = [
  { name: 'instalacion', content: 'instalacion' },
  { name: 'uso_id, uso_nome, modo_aluguer', content: 'usoOcupacion' },
  { name: 'data', content: 'data' },
  { name: 'inicio', content: 'inicio' },
  { name: 'fin', content: 'fin' },
  { name: 'ocupacion', content: 'ocupacion' },
];

/** Na páxina de todos, a primeira columna é o concello. */
export const MUNICIPALITY_COLUMN: ColumnRow = { name: 'concello', content: 'concello' };

/**
 * Os atallos do filtro «Datas» da ocupación: onte, o mes pasado e, só nun concello, os últimos 12
 * meses (sempre dentro do que se pode pedir).
 */
export function occupancyPresets(today: IsoDate, availableFrom: IsoDate, all: boolean): DateFilterPreset[] {
  const yesterday = addDays(today, -1);
  const lastMonthStart = addMonths(startOfMonth(today), -1);
  const presets: DateFilterPreset[] = [
    { id: 'yesterday', label: 'openData.dates.preset.yesterday', from: yesterday, to: yesterday },
    {
      id: 'lastMonth',
      label: 'openData.dates.preset.lastMonth',
      from: lastMonthStart,
      to: addDays(startOfMonth(today), -1),
    },
  ];
  if (!all) {
    presets.push({ id: 'last12', label: 'openData.dates.preset.last12', from: availableFrom, to: yesterday });
  }
  return presets;
}

/** O erro do filtro: sen as dúas datas (só ao descargar) ou máis meses dos permitidos (ao momento). */
export function rangeError(from: IsoDate | null, to: IsoDate | null, maxMonths: number): 'required' | 'tooLong' | null {
  if (!from || !to) return 'required';
  // Como na API: ata < desde + maxMonths meses.
  return to >= addMonths(from, maxMonths) ? 'tooLong' : null;
}

/** Días entre dúas datas, as dúas incluídas. */
export function daysBetween(from: IsoDate, to: IsoDate): number {
  const ms = Date.parse(`${to}T00:00:00Z`) - Date.parse(`${from}T00:00:00Z`);
  return Math.round(ms / 86_400_000) + 1;
}

/** «4 MB», «850 kB», «12 kB»: o tamaño aproximado do diálogo. */
export function formatSize(bytes: number, lang: string): string {
  const locale = lang === 'es' ? 'es-ES' : 'gl-ES';
  if (bytes >= 1_000_000) {
    return `${new Intl.NumberFormat(locale, { maximumFractionDigits: bytes >= 10_000_000 ? 0 : 1 }).format(bytes / 1_000_000)} MB`;
  }
  return `${Math.max(1, Math.round(bytes / 1000))} kB`;
}

/** O nome do ficheiro, coma o da API (`Content-Disposition`). */
export function fileName(
  prefix: string,
  dataset: DatasetKey,
  format: DownloadFormat,
  range: { from: IsoDate; to: IsoDate } | null,
): string {
  const file = DATASETS.find((d) => d.key === dataset)?.file ?? 'datos';
  const period = dataset === 'OCCUPANCY' && range ? `-${range.from}-${range.to}` : '';
  return `${prefix}-${file}${period}.${format}`;
}

/** O nome de `Content-Disposition: attachment; filename="…"`, se o hai. */
export function dispositionName(header: string | null): string | null {
  const match = header ? /filename\*?=(?:UTF-8'')?"?([^";]+)"?/i.exec(header) : null;
  return match ? decodeURIComponent(match[1]) : null;
}

/** «Detalles» de cada conxunto: cada canto se actualiza, formatos e, na ocupación, o resto. */
export function detailRows(key: DatasetKey, t: Translate): ValueListItem[] {
  const d = 'openData.details.';
  const row = (id: string, value: string): ValueListItem => ({ id, label: t(d + id), value: t(value) });
  if (key === 'FACILITIES') {
    return [
      row('updates', 'openData.facilities.updates'),
      row('formats', 'openData.formats.three'),
      row('rows', 'openData.facilities.rows'),
    ];
  }
  if (key === 'CLOSURES') {
    return [row('updates', 'openData.closures.updates'), row('formats', 'openData.formats.two')];
  }
  return [
    row('updates', 'openData.occupancy.updates'),
    row('formats', 'openData.formats.two'),
    row('since', 'openData.occupancy.since'),
    row('rows', 'openData.occupancy.rows'),
    row('calc', 'openData.occupancy.calc'),
    row('group', 'openData.occupancy.group'),
  ];
}

/**
 * As táboas de «Columnas» (ADR-0034, punto 16): nas instalacións, «Da instalación» (cos campos
 * propios públicos do concello ao final) e «De cada uso»; nos demais, unha. Na páxina de todos, a
 * primeira columna é o concello.
 */
export function columnTables(
  key: DatasetKey,
  all: boolean,
  customFields: readonly OpenDataCustomField[],
  lang: string,
  t: Translate,
): { id: string; caption: string; rows: DataTableRow[] }[] {
  const rows = (list: readonly ColumnRow[], first = false): DataTableRow[] =>
    [...(first && all ? [MUNICIPALITY_COLUMN] : []), ...list].map((c) => [c.name, t('openData.column.' + c.content)]);
  if (key === 'FACILITIES') {
    const custom = customFields.map((f): DataTableRow => [
      `campo_${f.code}`,
      `${lang === 'es' ? f.label.es : f.label.gl} · ${t('openData.column.customField', {
        type: t('customFields.type.' + f.type),
      })}`,
    ]);
    return [
      { id: 'facility', caption: t('openData.columns.facility'), rows: [...rows(FACILITY_COLUMNS, true), ...custom] },
      { id: 'use', caption: t('openData.columns.use'), rows: rows(USE_COLUMNS) },
    ];
  }
  const list = key === 'CLOSURES' ? CLOSURE_COLUMNS : OCCUPANCY_COLUMNS;
  return [{ id: 'all', caption: t('openData.fold.columns'), rows: rows(list, true) }];
}
