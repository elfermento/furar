// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IncidentResponse } from './incidents.store';
import type { ColumnFilterOption } from '../../shared/ui/column-filter/column-filter.component';
import type { TagTone } from '../../shared/ui/tag/tag.component';
import { inMadrid } from '../../shared/ui/calendar/madrid-time';

export type Incident = IncidentResponse;
export type IncidentStatus = IncidentResponse['status'];
export type IncidentColumn = 'date' | 'facility' | 'type' | 'status' | 'reporter';

export const STATUSES: readonly IncidentStatus[] = ['NEW', 'IN_PROGRESS', 'RESOLVED'];
export const TYPES: readonly IncidentResponse['type'][] = ['BREAKDOWN', 'CLEANING', 'SAFETY', 'OTHER'];
export const COLUMNS: readonly IncidentColumn[] = ['date', 'facility', 'type', 'status', 'reporter'];

/** O ton de cada estado: Nova, perigo; En curso, información; Resolta, éxito. */
export const STATUS_TONE: Readonly<Record<IncidentStatus, TagTone>> = {
  NEW: 'danger',
  IN_PROGRESS: 'info',
  RESOLVED: 'success',
};

/** Filtros postos por columna: os valores escollidos (baleiro, sen filtro). */
export type IncidentFilters = Readonly<Record<IncidentColumn, readonly string[]>>;
export const NO_FILTERS: IncidentFilters = { date: [], facility: [], type: [], status: [], reporter: [] };

/** «11/11/2026» e «10:42», en Europe/Madrid. */
export function dayOf(iso: string): string {
  const [y, m, d] = inMadrid(iso).date.split('-');
  return `${d}/${m}/${y}`;
}

export function timeOf(iso: string): string {
  return inMadrid(iso).time;
}

/** O mes do aviso (`2026-11`): o valor do filtro Data. */
export function monthOf(iso: string): string {
  return inMadrid(iso).date.slice(0, 7);
}

/** Quen avisou, para a lista: o nome, se non o correo; `null` se está anonimizada ou é do persoal sen nome. */
export function reporterOf(incident: Incident): string | null {
  return incident.reporter.name ?? incident.reporter.email ?? null;
}

/** O valor dunha incidencia nunha columna (o que compara o filtro). */
export function valueOf(incident: Incident, column: IncidentColumn): string {
  switch (column) {
    case 'date':
      return monthOf(incident.createdAt);
    case 'facility':
      return incident.facilityId;
    case 'type':
      return incident.type;
    case 'status':
      return incident.status;
    case 'reporter':
      return reporterOf(incident) ?? '';
  }
}

/** As que pasan os filtros e a busca (no texto que se ve: instalación, tipo, persoa, aviso). */
export function visible(
  items: readonly Incident[],
  filters: IncidentFilters,
  query: string,
  label: (incident: Incident) => string,
): Incident[] {
  const needle = normalize(query);
  return items.filter(
    (incident) =>
      COLUMNS.every((column) => !filters[column].length || filters[column].includes(valueOf(incident, column))) &&
      (!needle || normalize(label(incident)).includes(needle)),
  );
}

/** As opcións dunha columna coas filas que levan cada unha, na orde en que aparecen. */
export function optionsOf(
  items: readonly Incident[],
  column: IncidentColumn,
  label: (value: string, incident: Incident) => string,
  tone?: (value: string) => TagTone,
): ColumnFilterOption[] {
  const counts = new Map<string, { label: string; count: number }>();
  for (const incident of items) {
    const value = valueOf(incident, column);
    if (!value) continue;
    const seen = counts.get(value);
    if (seen) seen.count++;
    else counts.set(value, { label: label(value, incident), count: 1 });
  }
  const order = column === 'status' ? STATUSES : column === 'type' ? TYPES : null;
  const entries = [...counts.entries()];
  if (order) entries.sort(([a], [b]) => order.indexOf(a as never) - order.indexOf(b as never));
  else if (column !== 'date') entries.sort(([, a], [, b]) => a.label.localeCompare(b.label, 'gl'));
  return entries.map(([value, { label: text, count }]) => ({
    value,
    label: text,
    count,
    tone: tone ? tone(value) : null,
  }));
}

/** Pon ou quita un valor dun filtro. */
export function toggled(filters: IncidentFilters, column: IncidentColumn, value: string): IncidentFilters {
  const current = filters[column];
  const next = current.includes(value) ? current.filter((v) => v !== value) : [...current, value];
  return { ...filters, [column]: next };
}

function normalize(text: string): string {
  return text
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase()
    .trim();
}
