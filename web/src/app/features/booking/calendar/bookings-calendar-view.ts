// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { formatLong, weekdayIndex, weekdayNames } from '../../../shared/ui/calendar/local-date';
import type { ManagedBookingResponse } from '../manage/manage-bookings';
import type { CalendarColumn, CalendarMode, PlacedBlock } from './bookings-calendar';
import { facilityColumns, minutes, monthWeeks, placeBlocks, shortDay, weekDays } from './bookings-calendar';
import type { BookingCalendarResponse } from './bookings-calendar.store';

/** Unha reserva da grella, co seu nome enteiro. */
export interface GridBlock extends PlacedBlock {
  /** Quen reservou («Persoa anonimizada» se a conservación xa a quitou). */
  readonly person: string;
  readonly name: string;
}

/** Unha conta da semana de todas: cantas reservas empezan ese día a esa hora. */
export interface GridCount {
  readonly column: number;
  readonly date: IsoDate;
  readonly hour: number;
  readonly text: string;
  readonly name: string;
}

/** Un día do mes: o número, a conta (texto curto e nome enteiro) e se é hoxe. */
export interface MonthDay {
  readonly date: IsoDate;
  readonly day: number;
  readonly count: number;
  readonly text: string;
  readonly longDate: string;
  readonly today: boolean;
}

/** Que abrir na vista rápida e desde onde. */
export interface PeekRequest {
  readonly date: IsoDate;
  readonly hour: number | null;
  readonly trigger: HTMLElement;
}

/** Idioma, tradución (clave enteira) e hoxe, para os textos da vista. */
export interface ViewText {
  readonly lang: string;
  readonly translate: (key: string, params?: Record<string, unknown>) => string;
  readonly today: IsoDate;
}

/** «luns 26»: o día da semana enteiro e o número. */
export function longDay(date: IsoDate, lang: string): string {
  return `${weekdayNames(lang)[weekdayIndex(date)].long} ${Number(date.slice(8))}`;
}

/** «19:00». */
export const hourText = (hour: number): string => `${String(hour).padStart(2, '0')}:00`;

/** «1 reserva», «3 reservas». */
export function countText(count: number, text: ViewText): string {
  return text.translate(`booking.manage.${count === 1 ? 'countOne' : 'countOther'}`, { count });
}

/** Día: as instalacións (ou a escollida); semana: os sete días. */
export function gridColumns(
  mode: CalendarMode,
  date: IsoDate,
  facilityId: string,
  facilities: Parameters<typeof facilityColumns>[0],
  blocks: readonly ManagedBookingResponse[],
  text: ViewText,
): CalendarColumn[] {
  if (mode === 'WEEK') {
    return weekDays(date).map((day) => ({
      key: day,
      date: day,
      facilityId: facilityId || null,
      title: shortDay(day, text.lang),
      subtitle: '',
      today: day === text.today,
    }));
  }
  return facilityColumns(facilities, blocks)
    .filter((f) => !facilityId || f.id === facilityId)
    .map((f) => ({ key: f.id, date, facilityId: f.id, title: f.name, subtitle: f.locality, today: false }));
}

/**
 * Os bloques do día (columna por instalación) ou da semana (por día), co nome enteiro: «Ana Rey,
 * Partido, Pista de pádel 1, 19:00–20:00, Pendente» (na semana, tamén o día).
 */
export function gridBlocks(
  blocks: readonly ManagedBookingResponse[],
  mode: CalendarMode,
  columns: readonly CalendarColumn[],
  first: number,
  text: ViewText,
): GridBlock[] {
  const week = mode === 'WEEK';
  const index = (b: ManagedBookingResponse) =>
    week
      ? columns.findIndex((c) => c.date === minutes(b).date)
      : columns.findIndex((c) => c.facilityId === b.facilityId);
  return placeBlocks(blocks, index, first).map((placed) => {
    const person = placed.booking.holderName ?? text.translate('booking.manage.anonymized');
    return {
      ...placed,
      person,
      // O texto visible (persoa e uso) vai ao principio do nome (WCAG 2.5.3, nome na etiqueta).
      name: [
        person,
        placed.booking.useName,
        placed.booking.facilityName,
        ...(week ? [longDay(columns[placed.column].date, text.lang)] : []),
        `${placed.startText}–${placed.endText}`,
        text.translate(`booking.manage.filters.paymentOption.${placed.booking.paymentStatus}`),
      ].join(', '),
    };
  });
}

/**
 * As celas pechadas, que se raian (maqueta 27): `columna|hora` das horas sen ningún tramo aberto
 * da instalación da columna ese día (semana de todas as instalacións: de ningunha).
 */
export function closedCells(
  openings: BookingCalendarResponse['openings'],
  columns: readonly CalendarColumn[],
  hours: readonly number[],
): Set<string> {
  const closed = new Set<string>();
  for (const column of columns) {
    const open = openings.filter(
      (o) => o.date === column.date && (!column.facilityId || o.facilityId === column.facilityId),
    );
    for (const hour of hours) {
      if (!open.some((o) => o.startMinute < (hour + 1) * 60 && o.endMinute > hour * 60)) {
        closed.add(`${column.key}|${hour}`);
      }
    }
  }
  return closed;
}

/** As contas por hora da semana de todas, na columna do seu día. */
export function gridCounts(
  hours: BookingCalendarResponse['hours'],
  columns: readonly CalendarColumn[],
  text: ViewText,
): GridCount[] {
  return hours
    .filter((h) => h.count > 0)
    .map((h) => {
      const label = countText(h.count, text);
      return {
        column: columns.findIndex((c) => c.date === h.date),
        date: h.date,
        hour: h.hour,
        text: label,
        name: `${label}, ${longDay(h.date, text.lang)}, ${hourText(h.hour)}`,
      };
    })
    .filter((c) => c.column >= 0);
}

/** As semanas do mes coa conta de cada día («Libre» se non hai; nada mentres non chegan). */
export function monthDays(
  date: IsoDate,
  days: BookingCalendarResponse['days'] | null,
  text: ViewText,
): (MonthDay | null)[][] {
  const counts = new Map((days ?? []).map((d) => [d.date, d.count]));
  return monthWeeks(date).map((week) =>
    week.map((day) => {
      if (!day) return null;
      const count = counts.get(day) ?? 0;
      const label = !days ? '' : count ? countText(count, text) : text.translate('booking.calendar.free');
      return {
        date: day,
        day: Number(day.slice(8)),
        count,
        text: label,
        longDate: formatLong(day, text.lang),
        today: day === text.today,
      };
    }),
  );
}
