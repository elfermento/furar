// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Datas sen hora nin zona (`LocalDate` da API, «2026-12-24»). Todo o cálculo vai en UTC para que
 * os cambios de hora do navegador non movan un día; os nomes de días e meses saen de `Intl` co
 * idioma activo (plan do dominio 1, §1.2.16), nunca de Transloco nin do `LOCALE_ID`.
 */
export type IsoDate = string;

const ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
const DAY_MS = 86_400_000;

/** Data ISO válida (existe no calendario: nada de 2026-02-30). */
export function isIsoDate(value: unknown): value is IsoDate {
  if (typeof value !== 'string') {
    return false;
  }
  const match = ISO.exec(value);
  if (!match) {
    return false;
  }
  const [, y, m, d] = match.map(Number);
  const date = new Date(Date.UTC(y, m - 1, d));
  return date.getUTCFullYear() === y && date.getUTCMonth() === m - 1 && date.getUTCDate() === d;
}

/** Medianoite UTC da data (só para cálculo e para `Intl` con `timeZone: 'UTC'`). */
export function toUtc(iso: IsoDate): Date {
  const [y, m, d] = iso.split('-').map(Number);
  return new Date(Date.UTC(y, m - 1, d));
}

export function fromUtc(date: Date): IsoDate {
  const y = String(date.getUTCFullYear()).padStart(4, '0');
  const m = String(date.getUTCMonth() + 1).padStart(2, '0');
  const d = String(date.getUTCDate()).padStart(2, '0');
  return `${y}-${m}-${d}`;
}

/** Hoxe segundo o reloxo do navegador; as features poden pasar o `today` da API no seu lugar. */
export function localToday(now: Date = new Date()): IsoDate {
  return fromUtc(new Date(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate())));
}

export function addDays(iso: IsoDate, days: number): IsoDate {
  return fromUtc(new Date(toUtc(iso).getTime() + days * DAY_MS));
}

/** Suma meses e, se o día non existe no mes de destino, queda no último (31 xan + 1 → 28 feb). */
export function addMonths(iso: IsoDate, months: number): IsoDate {
  const date = toUtc(iso);
  const target = new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() + months, 1));
  const last = new Date(Date.UTC(target.getUTCFullYear(), target.getUTCMonth() + 1, 0)).getUTCDate();
  target.setUTCDate(Math.min(date.getUTCDate(), last));
  return fromUtc(target);
}

/** Día da semana con luns = 0 e domingo = 6. */
export function weekdayIndex(iso: IsoDate): number {
  return (toUtc(iso).getUTCDay() + 6) % 7;
}

export function startOfWeek(iso: IsoDate): IsoDate {
  return addDays(iso, -weekdayIndex(iso));
}

export function startOfMonth(iso: IsoDate): IsoDate {
  return `${iso.slice(0, 7)}-01`;
}

/** Mesmo mes e ano. */
export function sameMonth(a: IsoDate, b: IsoDate): boolean {
  return a.slice(0, 7) === b.slice(0, 7);
}

/** As ISO compáranse coma texto; acoutar a [min, max] (calquera dos dous pode faltar). */
export function clamp(iso: IsoDate, min: IsoDate | null, max: IsoDate | null): IsoDate {
  if (min && iso < min) {
    return min;
  }
  if (max && iso > max) {
    return max;
  }
  return iso;
}

/**
 * Seis semanas de luns a domingo que conteñen o mes de `month`; `null` nos días doutro mes (a
 * grella sempre ten 6 filas, docs/deseno.md §8, e as celas baleiras non se len).
 */
export function monthGrid(month: IsoDate): (IsoDate | null)[][] {
  const first = startOfMonth(month);
  let day = startOfWeek(first);
  const weeks: (IsoDate | null)[][] = [];
  for (let w = 0; w < 6; w++) {
    const week: (IsoDate | null)[] = [];
    for (let i = 0; i < 7; i++) {
      week.push(sameMonth(day, first) ? day : null);
      day = addDays(day, 1);
    }
    weeks.push(week);
  }
  return weeks;
}

const formatters = new Map<string, Intl.DateTimeFormat>();

function formatter(lang: string, options: Intl.DateTimeFormatOptions): Intl.DateTimeFormat {
  const key = `${lang}|${JSON.stringify(options)}`;
  let found = formatters.get(key);
  if (!found) {
    found = new Intl.DateTimeFormat(lang, { ...options, timeZone: 'UTC' });
    formatters.set(key, found);
  }
  return found;
}

/*
 * Chrome e Chromium (tamén o do e2e) non traen datos `gl` en Intl e caen noutro idioma
 * («diciembre», «December»). Só para ese caso, os nomes galegos van aquí; con datos, manda Intl.
 */
export const GL_MONTHS = [
  'xaneiro',
  'febreiro',
  'marzo',
  'abril',
  'maio',
  'xuño',
  'xullo',
  'agosto',
  'setembro',
  'outubro',
  'novembro',
  'decembro',
];
export const GL_WEEKDAYS = ['luns', 'martes', 'mércores', 'xoves', 'venres', 'sábado', 'domingo'];
/** Abreviaturas de CLDR (co punto), de luns a domingo e de xaneiro a decembro. */
export const GL_SHORT_WEEKDAYS = ['luns', 'mar.', 'mér.', 'xov.', 'ven.', 'sáb.', 'dom.'];
export const GL_SHORT_MONTHS = [
  'xan.',
  'feb.',
  'mar.',
  'abr.',
  'maio',
  'xuño',
  'xul.',
  'ago.',
  'set.',
  'out.',
  'nov.',
  'dec.',
];

export function galicianFallback(lang: string): boolean {
  if (lang !== 'gl') {
    return false;
  }
  try {
    return Intl.DateTimeFormat.supportedLocalesOf([lang]).length === 0;
  } catch {
    return true;
  }
}

function capitalize(text: string, lang: string): string {
  return text.charAt(0).toLocaleUpperCase(lang) + text.slice(1);
}

/** «24/12/2026»: día e mes con dúas cifras, o formato de gl e es (sen depender de Intl). */
export function formatShort(iso: IsoDate): string {
  const [y, m, d] = iso.split('-');
  return `${d}/${m}/${y}`;
}

/** «6 dec.», «6 dic»: día e mes curto (o mes, de Intl; en galego sen Intl, o de CLDR). */
export function formatDayMonth(iso: IsoDate, lang: string): string {
  const m = Number(iso.slice(5, 7));
  const month = galicianFallback(lang)
    ? GL_SHORT_MONTHS[m - 1]
    : formatter(lang, { month: 'short' }).format(toUtc(iso));
  return `${Number(iso.slice(8, 10))} ${month}`;
}

/** «1 xan. 2026», «1 ene 2026»: día, mes curto e ano. */
export function formatDayShortMonth(iso: IsoDate, lang: string): string {
  return `${formatDayMonth(iso, lang)} ${iso.slice(0, 4)}`;
}

/** «xoves, 24 de decembro de 2026»: nome de cada día para lectores de pantalla. */
export function formatLong(iso: IsoDate, lang: string): string {
  if (galicianFallback(lang)) {
    const [y, m, d] = iso.split('-').map(Number);
    return `${GL_WEEKDAYS[weekdayIndex(iso)]}, ${d} de ${GL_MONTHS[m - 1]} de ${y}`;
  }
  return formatter(lang, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' }).format(toUtc(iso));
}

/** «24 de decembro de 2026»: a data enteira, sen o día da semana. */
export function formatDateLong(iso: IsoDate, lang: string): string {
  if (galicianFallback(lang)) {
    const [y, m, d] = iso.split('-').map(Number);
    return `${d} de ${GL_MONTHS[m - 1]} de ${y}`;
  }
  return formatter(lang, { day: 'numeric', month: 'long', year: 'numeric' }).format(toUtc(iso));
}

/** «xov. 29»: o día da semana curto e o número (os botóns de días). */
export function formatWeekdayDay(iso: IsoDate, lang: string): string {
  if (galicianFallback(lang)) {
    return `${GL_SHORT_WEEKDAYS[weekdayIndex(iso)]} ${Number(iso.slice(8, 10))}`;
  }
  return formatter(lang, { weekday: 'short', day: 'numeric' }).format(toUtc(iso));
}

/** «Decembro de 2026»: título do mes, con maiúscula inicial porque encabeza. */
export function formatMonth(iso: IsoDate, lang: string): string {
  if (galicianFallback(lang)) {
    return capitalize(`${GL_MONTHS[Number(iso.slice(5, 7)) - 1]} de ${iso.slice(0, 4)}`, lang);
  }
  return capitalize(formatter(lang, { month: 'long', year: 'numeric' }).format(toUtc(startOfMonth(iso))), lang);
}

/** Cabeceiras da semana, de luns a domingo: inicial á vista e nome enteiro para lectores. */
export function weekdayNames(lang: string): { short: string; long: string }[] {
  const monday = '2026-01-05';
  const fallback = galicianFallback(lang);
  return Array.from({ length: 7 }, (_, i) => {
    const date = toUtc(addDays(monday, i));
    const long = fallback ? GL_WEEKDAYS[i] : formatter(lang, { weekday: 'long' }).format(date);
    const short = fallback ? long.charAt(0) : formatter(lang, { weekday: 'narrow' }).format(date);
    return { short: short.toLocaleUpperCase(lang), long };
  });
}
