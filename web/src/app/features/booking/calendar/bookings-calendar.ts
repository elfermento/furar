// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, addMonths, formatMonth, startOfWeek, weekdayIndex } from '../../../shared/ui/calendar/local-date';
import type { ManagedBookingResponse } from '../manage/manage-bookings';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay } from '../reserve/reserve';

/** Vista do calendario (maqueta 27), co mesmo nome ca na API (`view`). */
export type CalendarMode = 'DAY' | 'WEEK' | 'MONTH';
export const MODES: readonly CalendarMode[] = ['DAY', 'WEEK', 'MONTH'];

/** Primeira e última hora da grella se as reservas non piden máis (a da maqueta). */
export const FIRST_HOUR = 9;
export const LAST_HOUR = 23;
/** Alto dunha hora e da cabeceira das columnas, en rem (a mesma medida no CSS). */
export const HOUR_REM = 3.25;
export const HEAD_REM = 4;
/** Ancho da columna das horas, en rem. */
export const GUTTER_REM = 3.5;
/** Reservas que se ven na vista rápida dun día ou dunha hora; as demais, no día. */
export const PEEK_MAX = 4;

/** O mesmo día, semana ou mes, un paso atrás ou adiante (calquera data, sen límites). */
export function step(mode: CalendarMode, date: IsoDate, delta: number): IsoDate {
  if (mode === 'DAY') return addDays(date, delta);
  if (mode === 'WEEK') return addDays(date, 7 * delta);
  return addMonths(date, delta);
}

/** O nome do mes en minúscula («outubro»), para escribilo dentro dunha frase. */
export function monthName(date: IsoDate, lang: string): string {
  return formatMonth(date, lang).split(' ')[0].toLocaleLowerCase(lang);
}

/** «lun 26»: o día da semana curto e o número (cabeceira da semana e vista rápida). */
export function shortDay(date: IsoDate, lang: string): string {
  return formatDay(date, lang, 'short').split(' ').slice(0, 2).join(' ');
}

/**
 * O texto do período: o día («luns, 26 de outubro»), a semana (`translate('weekSame' |
 * 'weekCross', …)`, «Semana do 26 de outubro ao 1 de novembro») ou o mes («Outubro de 2026»).
 */
export function periodLabel(
  mode: CalendarMode,
  date: IsoDate,
  lang: string,
  translate: (key: string, params: Record<string, string>) => string,
): string {
  if (mode === 'DAY') return formatDay(date, lang, 'long');
  if (mode === 'MONTH') return formatMonth(date, lang);
  const from = startOfWeek(date);
  const to = addDays(from, 6);
  const day = (iso: IsoDate) => String(Number(iso.slice(8)));
  return from.slice(0, 7) === to.slice(0, 7)
    ? translate('weekSame', { from: day(from), to: day(to), month: monthName(from, lang) })
    : translate('weekCross', {
        from: day(from),
        fromMonth: monthName(from, lang),
        to: day(to),
        toMonth: monthName(to, lang),
      });
}

/** Unha columna da grella: unha instalación (día) ou un día (semana). */
export interface CalendarColumn {
  readonly key: string;
  readonly date: IsoDate;
  /** A instalación da columna (día) ou `null` (semana: a do selector, ou todas). */
  readonly facilityId: string | null;
  readonly title: string;
  readonly subtitle: string;
  readonly today: boolean;
}

/** Os sete días da semana da data, de luns a domingo. */
export function weekDays(date: IsoDate): IsoDate[] {
  const monday = startOfWeek(date);
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i));
}

/** Os días do mes en semanas de luns a domingo, con `null` nos ocos de antes e despois. */
export function monthWeeks(date: IsoDate): (IsoDate | null)[][] {
  const first = `${date.slice(0, 7)}-01`;
  const days: (IsoDate | null)[] = Array.from({ length: weekdayIndex(first) }, () => null);
  for (let day = first; day.slice(0, 7) === first.slice(0, 7); day = addDays(day, 1)) days.push(day);
  while (days.length % 7) days.push(null);
  return Array.from({ length: days.length / 7 }, (_, i) => days.slice(i * 7, i * 7 + 7));
}

/** Minuto do día en Europe/Madrid do inicio e do fin (o fin noutro día conta ata as 24:00). */
export function minutes(booking: Pick<ManagedBookingResponse, 'start' | 'end'>): {
  date: IsoDate;
  start: number;
  end: number;
  startText: string;
  endText: string;
} {
  const start = inMadrid(booking.start);
  const end = inMadrid(booking.end);
  const toMinute = (time: string) => Number(time.slice(0, 2)) * 60 + Number(time.slice(3, 5));
  const startMinute = toMinute(start.time);
  const endMinute = end.date === start.date ? toMinute(end.time) : 24 * 60;
  return {
    date: start.date,
    start: startMinute,
    end: Math.max(endMinute, startMinute + 15),
    startText: start.time,
    endText: end.time,
  };
}

/**
 * As horas da grella: as da maqueta, máis as que precisen as reservas e as contas; se algunha
 * instalación das que se ven abre antes das 9:00, a grella empeza na súa hora de apertura.
 */
export function hourRange(
  blocks: readonly Pick<ManagedBookingResponse, 'start' | 'end'>[],
  hours: readonly { hour: number }[] = [],
  openings: readonly { startMinute: number }[] = [],
): { first: number; last: number } {
  let first = FIRST_HOUR;
  let last = LAST_HOUR;
  for (const opening of openings) first = Math.min(first, Math.floor(opening.startMinute / 60));
  for (const block of blocks) {
    const m = minutes(block);
    first = Math.min(first, Math.floor(m.start / 60));
    last = Math.max(last, Math.ceil(m.end / 60));
  }
  for (const { hour } of hours) {
    first = Math.min(first, hour);
    last = Math.max(last, hour + 1);
  }
  return { first, last: Math.min(last, 24) };
}

/** As horas da grella, coa apertura das instalacións que se ven (as das súas columnas, ou todas). */
export function gridHours(
  data: {
    blocks: readonly Pick<ManagedBookingResponse, 'start' | 'end'>[];
    hours: readonly { hour: number }[];
    openings: readonly { facilityId: string; startMinute: number }[];
  } | null,
  columns: readonly Pick<CalendarColumn, 'facilityId'>[],
): number[] {
  const shown = new Set(columns.map((c) => c.facilityId));
  const open = (data?.openings ?? []).filter((o) => shown.has(null) || shown.has(o.facilityId));
  const { first, last } = hourRange(data?.blocks ?? [], data?.hours ?? [], open);
  return Array.from({ length: last - first }, (_, i) => first + i);
}

/** Unha reserva colocada na grella: columna, carril (se se pisan) e posición en rem. */
export interface PlacedBlock {
  readonly booking: ManagedBookingResponse;
  readonly column: number;
  readonly lane: number;
  readonly lanes: number;
  readonly top: number;
  readonly height: number;
  readonly startText: string;
  readonly endText: string;
}

/**
 * Coloca as reservas: cada unha na súa columna (`columnOf`, −1 = fóra) e, se se pisan na mesma
 * columna, en carrís lado a lado. Volven na orde de lectura (hora, columna, carril), que é a do
 * teclado.
 */
export function placeBlocks(
  blocks: readonly ManagedBookingResponse[],
  columnOf: (booking: ManagedBookingResponse) => number,
  first: number,
): PlacedBlock[] {
  const byColumn = new Map<number, { booking: ManagedBookingResponse; m: ReturnType<typeof minutes> }[]>();
  for (const booking of blocks) {
    const column = columnOf(booking);
    if (column < 0) continue;
    const list = byColumn.get(column) ?? [];
    list.push({ booking, m: minutes(booking) });
    byColumn.set(column, list);
  }
  const placed: PlacedBlock[] = [];
  for (const [column, list] of byColumn) {
    list.sort((a, b) => a.m.start - b.m.start || a.m.end - b.m.end);
    // Carrís por grupo de reservas que se pisan: unha soa ocupa o ancho enteiro.
    let ends: number[] = [];
    let groupEnd = -1;
    let group: number[] = [];
    const lanes: number[] = [];
    const widths: number[] = [];
    const closeGroup = () => group.forEach((i) => (widths[i] = ends.length));
    list.forEach(({ m }, i) => {
      if (m.start >= groupEnd) {
        closeGroup();
        ends = [];
        group = [];
      }
      let lane = ends.findIndex((end) => end <= m.start);
      if (lane < 0) lane = ends.push(0) - 1;
      ends[lane] = m.end;
      lanes[i] = lane;
      group.push(i);
      groupEnd = Math.max(groupEnd, m.end);
    });
    closeGroup();
    list.forEach(({ booking, m }, i) =>
      placed.push({
        booking,
        column,
        lane: lanes[i],
        lanes: widths[i],
        top: HEAD_REM + ((m.start - first * 60) / 60) * HOUR_REM,
        height: ((m.end - m.start) / 60) * HOUR_REM - 0.25,
        startText: m.startText,
        endText: m.endText,
      }),
    );
  }
  return placed.sort((a, b) => a.top - b.top || a.column - b.column || a.lane - b.lane);
}

/** As columnas da grella: as horas e `count` columnas de 8 rem como mínimo. */
export function gridTemplate(count: number): string {
  return `${GUTTER_REM}rem repeat(${count}, minmax(8rem, 1fr))`;
}

/** `left` e `width` en CSS dun elemento da columna `column` de `count`, no carril `lane` de `lanes`. */
export function columnBox(
  column: number,
  count: number,
  lane = 0,
  lanes = 1,
  inset = 3,
): { left: string; width: string } {
  const w = `((100% - ${GUTTER_REM}rem) / ${count})`;
  return {
    left: `calc(${GUTTER_REM}rem + ${column} * ${w} + ${lane} * (${w} / ${lanes}) + ${inset}px)`,
    width: `calc(${w} / ${lanes} - ${2 * inset}px)`,
  };
}

/** Reservas que empezan á mesma hora, baixo esa hora (vista rápida e lista do móbil). */
export interface StartGroup {
  readonly time: string;
  readonly items: readonly ManagedBookingResponse[];
}

/** Agrupa por hora de inicio («19:00–20:00» se todas acaban á vez; se non, «19:00»). */
export function groupByStart(bookings: readonly ManagedBookingResponse[]): StartGroup[] {
  const sorted = [...bookings].sort((a, b) => a.start.localeCompare(b.start) || a.end.localeCompare(b.end));
  const groups: { start: string; ends: Set<string>; items: ManagedBookingResponse[] }[] = [];
  for (const booking of sorted) {
    const m = minutes(booking);
    const last = groups.at(-1);
    if (last && last.start === m.startText) {
      last.items.push(booking);
      last.ends.add(m.endText);
    } else {
      groups.push({ start: m.startText, ends: new Set([m.endText]), items: [booking] });
    }
  }
  return groups.map((g) => ({
    time: g.ends.size === 1 ? `${g.start}–${[...g.ends][0]}` : g.start,
    items: g.items,
  }));
}

/** As primeiras `max` reservas, xa agrupadas, e cantas quedan fóra. */
export function peekGroups(
  bookings: readonly ManagedBookingResponse[],
  max = PEEK_MAX,
): { groups: StartGroup[]; more: number } {
  const sorted = [...bookings].sort((a, b) => a.start.localeCompare(b.start) || a.end.localeCompare(b.end));
  return { groups: groupByStart(sorted.slice(0, max)), more: Math.max(0, sorted.length - max) };
}

/** As columnas do día con «Todas»: as instalacións en uso e as que teñan reservas ese día. */
export function facilityColumns(
  facilities: readonly { id: string; name: string; status: string; address?: { locality?: string } }[],
  blocks: readonly ManagedBookingResponse[],
): { id: string; name: string; locality: string }[] {
  const columns = facilities
    .filter((f) => f.status !== 'WITHDRAWN' || blocks.some((b) => b.facilityId === f.id))
    .map((f) => ({ id: f.id, name: f.name, locality: f.address?.locality ?? '' }));
  for (const block of blocks) {
    if (!columns.some((c) => c.id === block.facilityId)) {
      columns.push({ id: block.facilityId, name: block.facilityName, locality: '' });
    }
  }
  return columns;
}
