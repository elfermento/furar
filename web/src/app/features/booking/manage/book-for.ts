// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import { formatDay, slotTime } from '../reserve/reserve';
import type { ManagedBookingDetailResponse } from './manage-bookings';

/** O oco que se garda (o corpo de `POST` e `PUT`). */
export interface SavedSlot {
  readonly useId: string;
  readonly start: string;
  readonly slots: number;
  readonly partId?: string;
}

/** O oco dunha reserva no URL do formulario (`?uso=&data=&hora=&franxas=&parte=`). */
export function slotParams(booking: ManagedBookingDetailResponse): Params {
  const start = inMadrid(booking.start);
  return {
    uso: booking.useId,
    data: start.date,
    hora: start.time,
    franxas: booking.slots > 1 ? booking.slots : null,
    parte: booking.partId ?? null,
  };
}

/**
 * Ao cambiar sen escoller outra hora, o oco de antes: a súa hora non se ofrece porque a ocupa a
 * propia reserva. `null` se se escolleu outra hora (que xa non está libre) ou non hai reserva.
 */
export function keptSlot(original: ManagedBookingDetailResponse | null, time: string | null): SavedSlot | null {
  if (!original || (time && time !== inMadrid(original.start).time)) return null;
  return {
    useId: original.useId,
    start: original.start,
    slots: original.slots,
    ...(original.partId ? { partId: original.partId } : {}),
  };
}

/** «mércores, 4 de novembro · 18:00–19:00». */
export function bookingWhen(booking: Pick<ManagedBookingDetailResponse, 'start' | 'end'>, lang: string): string {
  const start = inMadrid(booking.start);
  const end = inMadrid(booking.end);
  return `${formatDay(start.date, lang, 'long')} · ${start.time}–${end.time}`;
}

/** As (ata) tres horas libres máis próximas dun 409 `booking.slot-taken`. */
export function takenAlternatives(problem: ApiProblem): string[] {
  const raw = problem.extensions['alternatives'];
  return (Array.isArray(raw) ? (raw as { startMinute?: unknown }[]) : [])
    .filter((slot) => typeof slot?.startMinute === 'number')
    .slice(0, 3)
    .map((slot) => slotTime(slot.startMinute as number));
}

/** O aviso de feito: a quen se mandou o correo ou, sen el, o código que hai que darlle. */
export function doneToast(
  saved: ManagedBookingDetailResponse,
  before: Pick<ManagedBookingDetailResponse, 'start' | 'end'> | null,
  notify: boolean,
): { key: string; params: Record<string, unknown> } {
  const t = 'booking.bookFor.done.';
  if (!before) {
    return saved.holderEmail
      ? { key: `${t}sent`, params: { email: saved.holderEmail } }
      : { key: `${t}code`, params: { code: saved.code } };
  }
  const moved = before.start !== saved.start || before.end !== saved.end;
  return notify && moved && saved.holderEmail
    ? { key: `${t}changedSent`, params: { time: inMadrid(saved.start).time, name: saved.holderName ?? '' } }
    : { key: `${t}changed`, params: {} };
}
