// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, formatMonth, weekdayNames, weekdayIndex } from '../../../shared/ui/calendar/local-date';

/** A forma dun código de reserva: o QR leva só o código (ADR-0025, punto 13). */
const CODE = /^FUR-[A-Z0-9]{4}$/;

/** O que se teclea ou se le, sen espazos e en maiúsculas. */
export function typedCode(text: string): string {
  return text.trim().toUpperCase();
}

/** Se o texto é un código de reserva enteiro: o que non, descártase sen chamar á API. */
export function isBookingCode(text: string): boolean {
  return CODE.test(text);
}

/** «9 de novembro». */
export function dayOfMonth(iso: IsoDate, lang: string): string {
  const month = formatMonth(iso, lang).split(' ')[0].toLocaleLowerCase(lang);
  return `${Number(iso.slice(8))} de ${month}`;
}

/** «luns 9 de novembro». */
export function weekdayAndDay(iso: IsoDate, lang: string): string {
  return `${weekdayNames(lang)[weekdayIndex(iso)].long} ${dayOfMonth(iso, lang)}`;
}

/** «hoxe» ou «mañá» se o día o é; se non, nada. */
export function relativeDay(iso: IsoDate, today: IsoDate, names: { today: string; tomorrow: string }): string | null {
  if (iso === today) return names.today;
  return iso === addDays(today, 1) ? names.tomorrow : null;
}
