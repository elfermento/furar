// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../calendar/local-date';
import { addDays, startOfWeek, toUtc } from '../calendar/local-date';

/** Os atallos de sempre do filtro (xestión de reservas). */
export type DatePreset = 'today' | 'tomorrow' | 'week';

/**
 * Un atallo do panel: a clave do texto (enteira, para `transloco`) e o rango que pon. A xestión de
 * reservas usa os de sempre; outra pantalla pasa os seus (`presets`).
 */
export interface DateFilterPreset {
  readonly id: string;
  readonly label: string;
  readonly from: IsoDate;
  readonly to: IsoDate;
}

/** O rango de cada atallo: hoxe, mañá ou de hoxe ao domingo. */
export function presetRange(preset: DatePreset, today: IsoDate): { from: IsoDate; to: IsoDate } {
  switch (preset) {
    case 'today':
      return { from: today, to: today };
    case 'tomorrow':
      return { from: addDays(today, 1), to: addDays(today, 1) };
    case 'week':
      return { from: today, to: addDays(startOfWeek(today), 6) };
  }
}

/** Os atallos de sempre, xa como `DateFilterPreset`. */
export function defaultPresets(today: IsoDate): DateFilterPreset[] {
  return (['today', 'tomorrow', 'week'] as const).map((id) => ({
    id,
    label: `booking.manage.filters.dates.preset.${id}`,
    ...presetRange(id, today),
  }));
}

/** «26/10». */
export function shortDate(iso: IsoDate): string {
  return `${iso.slice(8)}/${iso.slice(5, 7)}`;
}

/** Como se le o filtro de datas: a clave do texto e os seus valores («Do 26/10 ao 31/10»). */
export function dateFilterText(
  from: IsoDate | null,
  to: IsoDate | null,
  today: IsoDate,
): { key: string; params: Record<string, string> } | null {
  if (!from) return null;
  if (to === from && from === today) return { key: 'today', params: {} };
  if (to === from && from === addDays(today, 1)) return { key: 'tomorrow', params: {} };
  if (to === from) return { key: 'on', params: { day: shortDate(from) } };
  if (to) return { key: 'between', params: { from: shortDate(from), to: shortDate(to) } };
  return { key: 'since', params: { day: shortDate(from) } };
}

/** Un clic no calendario do filtro: primeiro o inicio, despois o fin (antes do inicio, volve empezar). */
export function pickDay(
  range: { from: IsoDate | null; to: IsoDate | null },
  day: IsoDate,
): { from: IsoDate; to: IsoDate | null } {
  if (!range.from || range.to || day < range.from) return { from: day, to: null };
  return { from: range.from, to: day };
}

/** Días de `from` a `to` (0 se son o mesmo). */
export function daysBetween(from: IsoDate, to: IsoDate): number {
  return Math.round((toUtc(to).getTime() - toUtc(from).getTime()) / 86_400_000);
}
