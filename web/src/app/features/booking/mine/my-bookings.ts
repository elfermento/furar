// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { TagLook, TagTone } from '../../../shared/ui/tag/tag.component';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';

/** Pestana de «As miñas reservas»: o `scope` de `GET /me/bookings`. */
export type BookingScope = 'UPCOMING' | 'HISTORY';

/** Etiqueta de estado dunha reserva: a clave do texto (`booking.mine.status.*`) e a cor. */
export interface BookingChip {
  readonly key: 'pending' | 'paid' | 'toRefund' | 'refunded' | 'free' | 'done' | 'cancelled';
  readonly tone: TagTone;
  readonly look: TagLook;
}

const chip = (key: BookingChip['key'], tone: TagTone, look: TagLook = 'solid'): BookingChip => ({ key, tone, look });

export const isCancelled = (booking: Pick<MyBookingResponse, 'status'>): boolean => booking.status !== 'CONFIRMED';

/**
 * Unha soa etiqueta por reserva, coa paleta do §1.2.20: Pendente `warn`, Pagada `success`,
 * A devolver e Cancelada `danger`, Devolta `muted` (P11), Gratis `info`, e Realizada neutra en
 * contorno (P10). Cancelada, manda o pago se hai algo que devolver ou xa devolto; realizada, só
 * se aínda está pendente de pago. Sen prezo («Sen prezo», P5), próxima e sen cobro: ningunha.
 * A mesma etiqueta nas reservas do concello (D2-15).
 */
export function bookingChip(
  booking: Pick<MyBookingResponse, 'status' | 'paymentStatus' | 'done' | 'price'>,
): BookingChip | null {
  const payment = booking.paymentStatus;
  if (isCancelled(booking)) {
    if (payment === 'TO_REFUND') return chip('toRefund', 'danger');
    if (payment === 'REFUNDED') return chip('refunded', 'muted');
    return chip('cancelled', 'danger');
  }
  if (payment === 'PENDING') return chip('pending', 'warn');
  if (booking.done) return chip('done', 'neutral', 'outline');
  if (payment === 'PAID') return chip('paid', 'success');
  if (booking.price.kind === 'FREE') return chip('free', 'info');
  return null;
}

/** Se aínda se pode cancelar ou cambiar por internet (P1: dentro do prazo e confirmada). */
export const canChange = (booking: MyBookingResponse): boolean =>
  !isCancelled(booking) && !booking.done && booking.canCancel;

/** Próxima, confirmada e fóra do prazo para cancelar: sae o teléfono do servizo (P1). */
export const pastDeadline = (booking: MyBookingResponse): boolean =>
  !isCancelled(booking) && !booking.done && !booking.canCancel;
