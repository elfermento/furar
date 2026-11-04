// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { formatDayMonth } from '../../../shared/ui/calendar/local-date';
import { formatMinutes } from '../../../shared/ui/time-picker/time-format';
import type { PublicFacility } from './public-facility.store';

type PublicClosure = PublicFacility['closures'][number];

/** Unha liña de «Próximos peches»: as datas, que pecha (todo o día ou unhas horas) e por que. */
export interface ClosureLine {
  readonly key: string;
  readonly dates: string;
  readonly what: string;
  readonly why: string | null;
}

/** «6 dec.», «12–14 dec.» ou «30 dec. – 2 xan.» (maqueta 54). */
export function closureDates(from: string, to: string, lang: string): string {
  if (from === to) return formatDayMonth(from, lang);
  if (from.slice(0, 7) === to.slice(0, 7)) return `${Number(from.slice(8, 10))}–${formatDayMonth(to, lang)}`;
  return `${formatDayMonth(from, lang)} – ${formatDayMonth(to, lang)}`;
}

/**
 * Os peches da API en liñas: todo o día é `closedDay` («Pechado»); un bloqueo de parte do día, as
 * súas horas. O motivo é o que escribiu o concello ou, se non hai, o nome do festivo.
 */
export function closureLines(closures: readonly PublicClosure[], lang: string, closedDay: string): ClosureLine[] {
  return closures.map((c, i) => {
    const hours =
      typeof c.startMinute === 'number' && typeof c.endMinute === 'number'
        ? `${formatMinutes(c.startMinute, lang)} – ${formatMinutes(c.endMinute, lang)}`
        : null;
    const holiday = c.holiday ? (lang === 'es' ? c.holiday.es : c.holiday.gl) : null;
    return {
      key: `${c.from}-${i}`,
      dates: closureDates(c.from, c.to, lang),
      what: hours ?? closedDay,
      why: c.reason || holiday || null,
    };
  });
}
