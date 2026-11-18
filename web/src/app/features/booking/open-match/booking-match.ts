// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type {
  BookingOpenMatchResponse,
  OpenMatchPolicyResponse,
} from '../../../shared/open-matches/open-matches.store';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import type { MyBookingDetailAction } from '../mine/my-booking-detail.component';
import { canChange } from '../mine/my-bookings';

/** As accións da partida no detalle da reserva (`extraActions`, a páxina fainas). */
export type MatchActionId = 'match-open' | 'match-see' | 'match-edit' | 'match-stop';

/**
 * Pódese buscar xente (ADR-0040, punto 4): o concello ten a función acesa, a reserva está
 * confirmada e por vir, aínda se pode cancelar de balde, non é dun uso por prazas e aínda non
 * pasou o prazo do concello (inicio menos as horas). A API compróbao igual; aquí só se oculta a
 * acción que daría erro.
 */
export function canOpenMatch(
  booking: MyBookingResponse,
  policy: OpenMatchPolicyResponse | null | undefined,
  now: number = Date.now(),
): boolean {
  if (!policy?.enabled || !canChange(booking) || booking.rentalMode === 'SEATS') return false;
  if (booking.openMatch?.status === 'CANCELLED' || booking.openMatch?.status === 'OPEN') return false;
  return Date.parse(booking.start) - policy.cancelHours * 3_600_000 > now;
}

/**
 * Buscando: «Ver a partida», «Editar a partida» e «Deixar de buscar». Sen partida (ou xa pechada)
 * e dentro de prazo: «Buscar xente para xogar» (pechada, reábrea); pechada, tamén «Ver a partida»
 * (as persoas seguen).
 */
export function matchDetailActions(
  booking: MyBookingResponse,
  policy: OpenMatchPolicyResponse | null | undefined,
  t: (key: string) => string,
  now: number = Date.now(),
): MyBookingDetailAction[] {
  const action = (id: MatchActionId, icon: MyBookingDetailAction['icon'], key: string): MyBookingDetailAction => ({
    id,
    icon,
    label: t(key),
  });
  const match = booking.openMatch;
  const actions: MyBookingDetailAction[] = [];
  if (match && match.status !== 'CANCELLED' && !booking.done) {
    actions.push(action('match-see', 'group', 'openMatch.detail.see'));
  }
  if (match?.status === 'OPEN') {
    actions.push(action('match-edit', 'pencil', 'openMatch.detail.edit'));
    actions.push(action('match-stop', 'lock', 'openMatch.detail.stop'));
  } else if (canOpenMatch(booking, policy, now)) {
    actions.push(action('match-open', 'group-add', 'openMatch.action'));
  }
  return actions;
}

type Translate = (key: string, params?: Record<string, unknown>) => string;

/** No detalle do persoal: «4 de 10 · mínimo 6 · máximo 10», as pendentes e, se non busca, o estado. */
export function managedMatchText(match: BookingOpenMatchResponse, t: Translate): string {
  return [
    t('openMatch.ofTotal', { total: match.total, maximum: match.maximum }),
    t('openMatch.manage.minimum', { count: match.minimum }),
    t('openMatch.manage.maximum', { count: match.maximum }),
    match.pending ? t('openMatch.manage.pending', { count: match.pending }) : null,
    match.status === 'OPEN'
      ? null
      : t(match.status === 'CLOSED' ? 'openMatch.manage.status.CLOSED' : 'openMatch.manage.status.CANCELLED'),
  ]
    .filter((part): part is string => !!part)
    .join(' · ');
}

export { matchCancelNotice } from '../../../shared/booking-cancel/booking-cancel';
