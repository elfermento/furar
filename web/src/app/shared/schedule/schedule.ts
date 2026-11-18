// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../ui/calendar/local-date';
import { formatDayMonth, weekdayIndex, weekdayNames } from '../ui/calendar/local-date';
import { inMadrid } from '../ui/calendar/madrid-time';
import { formatMinutes } from '../ui/time-picker/time-format';

/**
 * Un horario que se repite (tempadas dunha entidade, ADR-0039, e actividades con horario,
 * ADR-0041): días da semana, horas e datas, e a vista previa dos días que non se crean.
 */

/** Traduce unha clave cos seus parámetros. */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** Un día que non se crea porque xa hai algo (a forma común das dúas vistas previas). */
export interface ScheduleConflict {
  readonly bookingId?: string;
  readonly date: string;
  readonly start: string;
  readonly end: string;
  readonly kind: string;
  readonly label?: string;
  readonly name?: string;
}

/** A vista previa dun horario: as sesións que se crean e os días que non. */
export interface SchedulePreview {
  readonly sessions: number;
  readonly conflicts: readonly ScheduleConflict[];
  readonly holidays: readonly string[];
  readonly useOff: readonly string[];
  readonly closed: readonly string[];
}

/** Días ISO (luns = 1) → máscara de bits do selector de días (luns = 1, domingo = 64). */
export function weekdaysToMask(weekdays: readonly number[]): number {
  return weekdays.reduce((mask, day) => (day >= 1 && day <= 7 ? mask | (1 << (day - 1)) : mask), 0);
}

/** Máscara do selector de días → días ISO en orde (luns = 1). */
export function maskToWeekdays(mask: number): number[] {
  return [1, 2, 3, 4, 5, 6, 7].filter((day) => (mask & (1 << (day - 1))) !== 0);
}

/** Une unha lista coma unha frase: «a», «a e b», «a, b e c». */
export function joinList(items: readonly string[], and: string): string {
  if (items.length < 2) return items.join('');
  return `${items.slice(0, -1).join(', ')}${and}${items.at(-1)}`;
}

/** «luns e mércores» (días ISO, en minúscula). */
export function weekdaysText(weekdays: readonly number[], lang: string, and: string): string {
  const names = weekdayNames(lang);
  const sorted = [...new Set(weekdays)].filter((day) => day >= 1 && day <= 7).sort((a, b) => a - b);
  return joinList(
    sorted.map((day) => names[day - 1].long.toLocaleLowerCase(lang)),
    and,
  );
}

/** Con maiúscula inicial («Luns e mércores»), para o valor dun campo. */
export function capitalized(text: string, lang: string): string {
  return text.charAt(0).toLocaleUpperCase(lang) + text.slice(1);
}

/** «19:00–20:30» (reloxo de parede en Europe/Madrid; 1440 = 24:00). */
export function minutesText(start: number, end: number, lang: string): string {
  return `${formatMinutes(start, lang)}–${formatMinutes(end, lang)}`;
}

/** O último día que admite un horario que empeza en `from` (365 días despois). */
export function yearAfter(from: IsoDate): IsoDate {
  const date = new Date(`${from}T00:00:00Z`);
  date.setUTCDate(date.getUTCDate() + 365);
  return date.toISOString().slice(0, 10);
}

/** As datas distintas dos choques, en orde: o `skip` que se manda ao crear. */
export function conflictDates(preview: Pick<SchedulePreview, 'conflicts'> | null): string[] {
  return [...new Set((preview?.conflicts ?? []).map((conflict) => conflict.date))].sort();
}

/** O que hai ese día, nunha frase («Reserva de Roi Varela», «Bloqueo: Torneo»). */
export function conflictText(conflict: ScheduleConflict, t: Translate): string {
  const what = conflict.name ?? conflict.label ?? '';
  switch (conflict.kind) {
    case 'BOOKING':
      return t('booking.seasons.conflict.BOOKING', { name: what });
    case 'SESSION':
      return t('booking.seasons.conflict.SESSION', { name: what });
    case 'BLOCK':
      return what ? t('booking.seasons.conflict.BLOCK', { name: what }) : t('booking.seasons.conflict.blocked');
    case 'OTHER_USE_EXCLUSIVE':
      return t('booking.seasons.conflict.OTHER_USE_EXCLUSIVE', { name: what });
    default:
      return conflict.label
        ? t('booking.seasons.conflict.OUT_OF_HOURS', { hours: conflict.label })
        : t('booking.seasons.conflict.closedHours');
  }
}

/** «luns 23 nov. · 19:00–20:00» ou, se ocupa o día enteiro, «luns 14 dec. · todo o día». */
export function conflictWhen(conflict: ScheduleConflict, lang: string, t: Translate): string {
  const day = `${weekdayNames(lang)[weekdayIndex(conflict.date)].long.toLocaleLowerCase(lang)} ${formatDayMonth(conflict.date, lang)}`;
  const long = new Date(conflict.end).getTime() - new Date(conflict.start).getTime() >= 86_400_000;
  const hours = long ? t('booking.seasons.allDay') : `${inMadrid(conflict.start).time}–${inMadrid(conflict.end).time}`;
  return `${day} · ${hours}`;
}

/** Ata `max` datas curtas en frase e, se hai máis, «e N máis». */
export function datesText(dates: readonly string[], lang: string, t: Translate, max = 6): string {
  const shown = dates.slice(0, max).map((date) => formatDayMonth(date, lang));
  const rest = dates.length - shown.length;
  if (rest > 0) shown.push(t('booking.seasons.more', { count: rest }));
  return joinList(shown, t('booking.who.and'));
}
