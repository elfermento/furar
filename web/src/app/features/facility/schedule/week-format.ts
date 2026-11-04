// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { formatDayShortMonth, weekdayNames } from '../../../shared/ui/calendar/local-date';
import { formatMinutes } from '../../../shared/ui/time-picker/time-format';
import type { Interval, Season } from './schedule.store';

/**
 * «Luns», «Lunes»… co idioma activo, por `Intl` como o calendario (§1.2.16 do plan); a fila de
 * festivos leva o seu texto de i18n.
 */
export function weekdayName(index: number, lang: string): string {
  const name = weekdayNames(lang)[index]?.long ?? '';
  return name.charAt(0).toLocaleUpperCase(lang) + name.slice(1);
}

/** «8:00 – 14:00 · 16:00 – 20:00» (baleiro se o día está pechado). */
export function intervalsText(intervals: readonly Interval[], lang: string): string {
  return intervals
    .map(({ startMinute, endMinute }) => `${formatMinutes(startMinute, lang)} – ${formatMinutes(endMinute, lang)}`)
    .join(' · ');
}

/**
 * «1 set. 2026 – 30 xuño 2027» («1 sept 2026 – 30 jun 2027») ou, sen fin, «Desde 1 xan. 2026 · sen
 * fin»: as dúas co mes curto do idioma (decisión do 2026-11-04; `t` traduce
 * `facility.schedule.seasons.openEnded`). `endDate` baleiro: aínda sen escoller.
 */
export function seasonRange(
  season: Pick<Season, 'startDate' | 'endDate'>,
  lang: string,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  if (!season.startDate) return '';
  if (season.endDate === null) {
    return t('facility.schedule.seasons.openEnded', { from: formatDayShortMonth(season.startDate, lang) });
  }
  const from = formatDayShortMonth(season.startDate, lang);
  if (!season.endDate) return `${from} –`;
  return `${from} – ${formatDayShortMonth(season.endDate, lang)}`;
}
