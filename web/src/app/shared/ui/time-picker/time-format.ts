// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

/** Minutos dun día enteiro: 1440 é a medianoite do final do día («24:00»). */
export const END_OF_DAY = 1440;

/** Paso do selector de hora: cada 15 minutos (a API só admite múltiplos de 15, §1.2.8 do plan). */
export const TIME_STEP = 15;

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(lang: string): Intl.DateTimeFormat {
  let f = formatters.get(lang);
  if (!f) {
    f = new Intl.DateTimeFormat(lang, { hour: 'numeric', minute: '2-digit', hourCycle: 'h23', timeZone: 'UTC' });
    formatters.set(lang, f);
  }
  return f;
}

/**
 * Hora de reloxo de parede desde minutos (0–1440) co formato do idioma («8:00», «23:45»). Intl non
 * coñece as 24:00: fórmase coas 0:00 e cámbiase a hora por 24, así segue o separador do idioma.
 */
export function formatMinutes(minutes: number, lang: string): string {
  const endOfDay = minutes === END_OF_DAY;
  const m = endOfDay ? 0 : minutes;
  const parts = formatter(lang).formatToParts(Date.UTC(1970, 0, 1, Math.floor(m / 60), m % 60));
  return parts.map((p) => (endOfDay && p.type === 'hour' ? '24' : p.value)).join('');
}

/** Opcións cada 15 min entre `min` e `max` (incluídos); 1440 só se `allowEndOfDay`. */
export function timeOptions(min: number, max: number, allowEndOfDay: boolean): number[] {
  const upper = Math.min(max, allowEndOfDay ? END_OF_DAY : END_OF_DAY - TIME_STEP);
  const lower = Math.max(0, Math.ceil(min / TIME_STEP) * TIME_STEP);
  const out: number[] = [];
  for (let m = lower; m <= upper; m += TIME_STEP) {
    out.push(m);
  }
  return out;
}
