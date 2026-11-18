// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import { joinNames, namedParticipants } from '../../../shared/dependents/participants';
import { formatCents } from '../../../shared/ui/money/money';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';

/** Pestana de «As miñas reservas»: o `scope` de `GET /me/bookings`. */
export type BookingScope = 'UPCOMING' | 'HISTORY';

/** Etiqueta de estado dunha reserva: a clave do texto (`booking.mine.status.*`) e a cor. */
export interface BookingChip {
  readonly key: 'pending' | 'paid' | 'toRefund' | 'refunded' | 'free' | 'cancelled';
  readonly tone: TagTone;
}

const chip = (key: BookingChip['key'], tone: TagTone): BookingChip => ({ key, tone });

/** Asistencia dunha reserva, á parte do estado e do cobro (ADR-0032). */
export type Attendance = 'NONE' | 'USED' | 'NO_SHOW';

/** Etiqueta da asistencia: «Non presentado» en vermello e «Validada» en gris (`booking.attendance.*`). */
export interface AttendanceChip {
  readonly key: Exclude<Attendance, 'NONE'>;
  readonly tone: TagTone;
}

/** O que se sabe da asistencia: a xestión ve a marca; a persoa, só se quedou como falta. */
interface Attended {
  readonly attendance?: Attendance;
  /** Na reserva da persoa, se quedou como falta; na da xestión é outra cousa (as faltas que leva). */
  readonly noShow?: unknown;
}

const attendanceOf = (booking: Attended): Attendance =>
  booking.attendance ?? (booking.noShow === true ? 'NO_SHOW' : 'NONE');

/**
 * A etiqueta da asistencia, ao lado da do pago: a mesma na lista, na ficha, no calendario e no
 * historial da persoa (que só ve «Non presentado»: a validación non se lle mostra).
 */
export function attendanceChip(booking: Attended): AttendanceChip | null {
  const attendance = attendanceOf(booking);
  if (attendance === 'NONE') return null;
  return { key: attendance, tone: attendance === 'NO_SHOW' ? 'danger' : 'muted' };
}

export const isCancelled = (booking: Pick<MyBookingResponse, 'status'>): boolean => booking.status !== 'CONFIRMED';

/**
 * Unha soa etiqueta por reserva, coa paleta de estados (docs/deseno.md P1-9): Pendente `warn`, Pagada `success`,
 * A devolver e Cancelada `danger`, Devolta `muted` e Gratis `info`. Cancelada, manda o pago se
 * hai algo que devolver ou xa devolto. Sen prezo («Sen prezo») e sen cobro: ningunha. Unha
 * reserva xa pasada leva a do pago coma calquera outra (que pasou xa o din a data e o
 * historial), e a da asistencia ao lado se a ten. A mesma etiqueta nas reservas do concello.
 */
export function bookingChip(
  booking: Pick<MyBookingResponse, 'status' | 'paymentStatus' | 'price'>,
): BookingChip | null {
  const payment = booking.paymentStatus;
  if (isCancelled(booking)) {
    if (payment === 'TO_REFUND') return chip('toRefund', 'danger');
    if (payment === 'REFUNDED') return chip('refunded', 'muted');
    return chip('cancelled', 'danger');
  }
  if (payment === 'PENDING') return chip('pending', 'warn');
  if (payment === 'PAID') return chip('paid', 'success');
  if (booking.price.kind === 'FREE') return chip('free', 'info');
  return null;
}

/** Realizada ou cancelada: vai no historial (sen QR; «Reservar de novo»). */
export const isPast = (booking: Pick<MyBookingResponse, 'status' | 'done'>): boolean =>
  booking.done || isCancelled(booking);

/** Se aínda se pode cancelar ou cambiar por internet (dentro do prazo e confirmada). */
export const canChange = (booking: MyBookingResponse): boolean =>
  !isCancelled(booking) && !booking.done && booking.canCancel;

/** Próxima, confirmada e fóra do prazo para cancelar: sae o teléfono do servizo. */
export const pastDeadline = (booking: MyBookingResponse): boolean =>
  !isCancelled(booking) && !booking.done && !booking.canCancel;

/**
 * A liña de debaixo da data na tarxeta: «Fútbol 7 · Metade sur · cancelada polo concello»; nas
 * prazas, «… · Marta e Uxía · 2 prazas».
 */
export function bookingMeta(
  booking: Pick<MyBookingResponse, 'status' | 'useName' | 'partName' | 'participants' | 'seats'>,
  t: (key: string, params?: Record<string, unknown>) => string,
): string {
  return bookingMetaParts(booking, t).join(' · ');
}

/** Os anacos de `bookingMeta`, un por dato (a tarxeta non os parte por dentro); nunha sesión, «Tempada». */
export function bookingMetaParts(
  booking: Pick<MyBookingResponse, 'status' | 'useName' | 'partName' | 'participants' | 'seats' | 'organization'>,
  t: (key: string, params?: Record<string, unknown>) => string,
): string[] {
  const who =
    booking.status === 'CANCELLED_BY_MUNICIPALITY'
      ? 'booking.mine.cancelledByMunicipality'
      : booking.status === 'CANCELLED_BY_PERSON'
        ? 'booking.mine.cancelledByPerson'
        : null;
  return [
    booking.useName,
    booking.partName,
    joinNames(namedParticipants(booking.participants), t('booking.who.and')),
    booking.seats > 1 ? t('booking.who.seats', { count: booking.seats }) : null,
    booking.organization?.series ? t('booking.seasons.session') : null,
    who ? t(who) : null,
  ].filter((part): part is string => !!part);
}

/** O importe da tarxeta: «30,00 €» se é de pago; se non, nada. */
export function bookingAmount(booking: Pick<MyBookingResponse, 'price'> | null): string | null {
  const price = booking?.price;
  return price?.kind === 'PAID' && typeof price.amountCents === 'number' ? formatCents(price.amountCents) : null;
}

/** A páxina de reservar da instalación da reserva (cambiar e «Reservar de novo»). */
export function reserveLink(slug: string, booking: Pick<MyBookingResponse, 'facilityId'> | null): unknown[] {
  return ['/', slug, 'instalacions', booking?.facilityId, 'reservar'];
}

/** «Reservar de novo»: o mesmo uso e a mesma parte. */
export function againParams(booking: Pick<MyBookingResponse, 'useId' | 'partId'> | null) {
  return { uso: booking?.useId, parte: booking?.partId ?? null };
}
