// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../ui/calendar/local-date';
import { formatDayMonth, formatMonth, toUtc, weekdayIndex, weekdayNames } from '../ui/calendar/local-date';
import { inMadrid, madridWall } from '../ui/calendar/madrid-time';
import { formatCents } from '../ui/money/money';
import { formatMinutes } from '../ui/time-picker/time-format';
import type { PublicEventResponse } from './public-events.store';

/** Traducir unha clave cos seus parámetros (Transloco ou o das probas). */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** Un texto do evento en galego e castelán (nome, descrición, texto alternativo). */
export interface EventText {
  readonly gl: string;
  readonly es: string;
}

/** O texto no idioma de quen mira. */
export function inLang(text: EventText, lang: string): string {
  return lang === 'es' ? text.es : text.gl;
}

/** O día enteiro: «domingo, 20 de decembro» (na ficha). */
export function longDay(iso: IsoDate, lang: string): string {
  const weekday = weekdayNames(lang)[weekdayIndex(iso)].long;
  return `${weekday}, ${Number(iso.slice(8))} de ${monthName(iso, lang)}`;
}

/** O día curto: «dom. 20 dec.» (nas tarxetas). */
export function shortDay(iso: IsoDate, lang: string): string {
  const weekday = weekdayNames(lang)[weekdayIndex(iso)].long;
  return `${weekday.slice(0, 3)}. ${Number(iso.slice(8))} ${monthName(iso, lang).slice(0, 3)}.`;
}

/** «domingo, 20 de decembro · 11:00» ou, con `short`, «dom. 20 dec. · 11:00», en Madrid. */
export function eventWhen(startsAt: string, lang: string, short = false): string {
  const { date, time } = inMadrid(startsAt);
  return `${short ? shortDay(date, lang) : longDay(date, lang)} · ${time}`;
}

function monthName(iso: IsoDate, lang: string): string {
  return formatMonth(iso, lang).split(' ')[0].toLocaleLowerCase(lang);
}

/** Reloxo de parede de Madrid dun instante, en milisegundos «como se fose UTC». */
function wallMs(instant: number): number {
  const w = madridWall(instant);
  return Date.UTC(+w.year, +w.month - 1, +w.day, +w.hour, +w.minute, +w.second);
}

/**
 * O instante ISO dun día e un minuto do día na hora de Madrid (UTC+1 no inverno, UTC+2 no verán):
 * o que escribe o persoal no formulario («20 dec., 11:00»).
 */
export function madridInstant(day: IsoDate, minute: number): string {
  const guess = toUtc(day).getTime() + minute * 60_000;
  let instant = guess - (wallMs(guess) - guess);
  instant = guess - (wallMs(instant) - instant);
  return new Date(instant).toISOString();
}

/** O día e o minuto en Madrid dun instante (para encher o formulario). */
export function madridDayMinute(instant: string): { day: IsoDate; minute: number } {
  const w = madridWall(instant);
  return { day: `${w.year}-${w.month}-${w.day}`, minute: +w.hour * 60 + +w.minute };
}

/** «20 de decembro · 11:00» (a tarxeta dun evento dun día na portada), en Madrid. */
export function dayMonthTime(startsAt: string, lang: string): string {
  const { date, time } = inMadrid(startsAt);
  return `${dayMonth(date, lang)} · ${time}`;
}

/** «20 de decembro». */
export function dayMonth(iso: IsoDate, lang: string): string {
  return `${Number(iso.slice(8))} de ${monthName(iso, lang)}`;
}

/** O horario dunha actividade (ADR-0041). */
export type EventSchedule = NonNullable<PublicEventResponse['schedule']>;

/** É unha actividade con horario (e trae o horario). */
export function isScheduled(
  event: Pick<PublicEventResponse, 'kind' | 'schedule'>,
): event is Pick<PublicEventResponse, 'kind'> & { schedule: EventSchedule } {
  return event.kind === 'SCHEDULED' && !!event.schedule;
}

/** O lugar: a instalación e, se hai, a parte («Piscina municipal · Vaso pequeno»), ou o outro sitio. */
export function placeText(event: Pick<PublicEventResponse, 'facility' | 'partName' | 'place'>): string {
  return event.facility ? [event.facility.name, event.partName].filter(Boolean).join(' · ') : (event.place ?? '');
}

/** «Luns e mércores» (días ISO, luns = 1), coa primeira en maiúscula. */
export function weekdaysText(weekdays: readonly number[], lang: string, t: Translate): string {
  const names = weekdayNames(lang);
  const days = [...new Set(weekdays)]
    .filter((day) => day >= 1 && day <= 7)
    .sort((a, b) => a - b)
    .map((day) => names[day - 1].long.toLocaleLowerCase(lang));
  const text = days.length < 2 ? days.join('') : `${days.slice(0, -1).join(', ')}${t('booking.who.and')}${days.at(-1)}`;
  return text.charAt(0).toLocaleUpperCase(lang) + text.slice(1);
}

/** «Luns e mércores · 17:00–18:00» ou, con `short` (tarxeta da portada), «Luns e mércores · 17:00». */
export function scheduleWhen(schedule: EventSchedule, lang: string, t: Translate, short = false): string {
  const start = formatMinutes(schedule.startMinute, lang);
  const time = short ? start : `${start}–${formatMinutes(schedule.endMinute, lang)}`;
  return `${weekdaysText(schedule.weekdays, lang, t)} · ${time}`;
}

/**
 * As datas dunha actividade, da primeira á última sesión (se remata antes, ata a nova última):
 * «Do 1 de outubro ao 19 de xuño» ou, con `short`, «Do 1 out. ao 19 xuñ.».
 */
export function activityDates(
  event: Pick<PublicEventResponse, 'startsAt' | 'endsAt'>,
  lang: string,
  t: Translate,
  short = false,
): string {
  const from = inMadrid(event.startsAt).date;
  const to = inMadrid(event.endsAt).date;
  const format = short ? (iso: IsoDate) => formatDayMonth(iso, lang) : (iso: IsoDate) => dayMonth(iso, lang);
  return t('event.public.dates', { from: format(from), to: format(to) });
}

/** Hai ano (nin `null` nin `undefined`). */
function isYear(year: number | null | undefined): year is number {
  return year !== null && year !== undefined;
}

/** Ten límite de idade (por ano de nacemento). */
export function hasAgeLimit(event: Pick<PublicEventResponse, 'birthYearFrom' | 'birthYearTo'>): boolean {
  return isYear(event.birthYearFrom) || isYear(event.birthYearTo);
}

/** «Nacidos entre 2014 e 2016», «Nacidos desde 2014», «Nacidos ata 2016»; sen límite, baleiro. */
export function ageText(event: Pick<PublicEventResponse, 'birthYearFrom' | 'birthYearTo'>, t: Translate): string {
  const { birthYearFrom: from, birthYearTo: to } = event;
  if (isYear(from) && isYear(to))
    return from === to ? t('event.public.age.only', { year: from }) : t('event.public.age.between', { from, to });
  if (isYear(from)) return t('event.public.age.from', { year: from });
  if (isYear(to)) return t('event.public.age.to', { year: to });
  return '';
}

/** O ano de nacemento entra no límite de idade (sen límite, sempre). */
export function birthYearFits(
  event: Pick<PublicEventResponse, 'birthYearFrom' | 'birthYearTo'>,
  year: number,
): boolean {
  return (
    (!isYear(event.birthYearFrom) || year >= event.birthYearFrom) &&
    (!isYear(event.birthYearTo) || year <= event.birthYearTo)
  );
}

/** «25,00 € ao trimestre», «5,00 €»; gratis, baleiro. */
export function priceText(event: Pick<PublicEventResponse, 'priceCents' | 'pricePeriod'>, t: Translate): string {
  if (!event.priceCents) return '';
  const price = formatCents(event.priceCents);
  const period = event.pricePeriod ?? 'ONCE';
  return period === 'ONCE' ? price : t('event.public.pricePer.' + period, { price });
}

/** O posto na lista de espera: «3.ª». */
export function ordinal(position: number): string {
  return `${position}.ª`;
}
