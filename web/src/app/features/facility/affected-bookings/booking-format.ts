// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { GL_SHORT_MONTHS, GL_SHORT_WEEKDAYS, galicianFallback } from '../../../shared/ui/calendar/local-date';
import { formatMinutes } from '../../../shared/ui/time-picker/time-format';

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(lang: string): Intl.DateTimeFormat {
  let found = formatters.get(lang);
  if (!found) {
    found = new Intl.DateTimeFormat(lang, { weekday: 'short', day: 'numeric', month: 'short', timeZone: 'UTC' });
    formatters.set(lang, found);
  }
  return found;
}

const capitalize = (text: string, lang: string) => text.charAt(0).toLocaleUpperCase(lang) + text.slice(1);
const bare = (text: string) => text.replace(/\.$/, '');

/** «Mar 3 nov» («Luns 3 nov» en gl, como CLDR): día curto dunha reserva (data ISO `AAAA-MM-DD`), sen puntos de abreviatura. */
export function formatBookingDay(iso: string, lang: string): string {
  const [y, m, d] = iso.split('-').map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  // Sen datos `gl` en Intl (Chrome e Chromium), as abreviaturas de local-date.ts sen o punto.
  if (galicianFallback(lang)) {
    return `${capitalize(bare(GL_SHORT_WEEKDAYS[(date.getUTCDay() + 6) % 7]), lang)} ${d} ${bare(GL_SHORT_MONTHS[m - 1])}`;
  }
  const parts = formatter(lang).formatToParts(date);
  const part = (type: Intl.DateTimeFormatPartTypes) => bare(parts.find((p) => p.type === type)?.value ?? '');
  return `${capitalize(part('weekday'), lang)} ${part('day')} ${part('month')}`;
}

/** «8:00–9:30»: tramo de reloxo de parede en minutos (1440 = «24:00»). */
export function formatBookingTime(startMinute: number, endMinute: number, lang: string): string {
  return `${formatMinutes(startMinute, lang)}–${formatMinutes(endMinute, lang)}`;
}
