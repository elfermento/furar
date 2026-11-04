// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AffectedBookingResponse } from '../../../api/models/affected-booking-response';
import type { BookingDecisionRequest } from '../../../api/models/booking-decision-request';
import { ApiProblem } from '../../../core/errors/api-problem';

/** Unha reserva que queda afectada polo cambio (extensión `affected[]` do 409). */
export type AffectedBooking = AffectedBookingResponse;
/** Decisión que se reenvía en `decisions[]` (plan do dominio 1, §1.2.6). */
export type BookingDecision = BookingDecisionRequest;
export type BookingAction = BookingDecision['action'];
export type NotifyChannel = NonNullable<BookingDecision['channels']>[number];

/** Código do 409 que abre o modal de reservas afectadas. */
export const AFFECTED_BOOKINGS_CODE = 'facility.affected-bookings';

function isAffectedBooking(value: unknown): value is AffectedBooking {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return (
    typeof v['bookingId'] === 'string' &&
    typeof v['holderName'] === 'string' &&
    typeof v['date'] === 'string' &&
    typeof v['startMinute'] === 'number' &&
    typeof v['endMinute'] === 'number' &&
    typeof v['useName'] === 'string'
  );
}

/**
 * As reservas afectadas dun erro, se é o 409 `facility.affected-bookings` con `affected[]`; se non,
 * `null` (o erro segue o seu camiño). Os elementos mal formados descártanse.
 */
export function affectedFrom(error: unknown): AffectedBooking[] | null {
  if (!(error instanceof ApiProblem) || error.status !== 409 || error.code !== AFFECTED_BOOKINGS_CODE) {
    return null;
  }
  const affected = error.extensions['affected'];
  if (!Array.isArray(affected)) return null;
  return affected.filter(isAffectedBooking).map((booking) => ({ ...booking, hasEmail: booking.hasEmail === true }));
}
