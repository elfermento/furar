// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { openMatchCounts } from '../../../shared/open-matches/open-match-format';
import type { MyOpenMatchResponse, OpenMatchResponse } from '../../../shared/open-matches/open-matches.store';

/** A partida vista por quen non ten sesión (`OpenMatchResponse`) ou por quen a ten (`MyOpenMatchResponse`). */
export type ShownMatch = OpenMatchResponse | MyOpenMatchResponse;

/** A resposta da persoa (con `me`), se a hai. */
export function asMine(match: ShownMatch | null): MyOpenMatchResponse | null {
  return match && 'me' in match ? match : null;
}

/** O que pode facer a persoa coa partida agora (a acción ao pé da tarxeta). */
export type MatchAction =
  'organizer' | 'joined' | 'pending' | 'rejected' | 'cancelled' | 'closed' | 'full' | 'join' | 'ask';

/**
 * Quen organiza, dentro, pendente ou rexeitada mandan; despois, se xa non busca xente (cancelada ou
 * pechada), se está completa e, se non, «Pedir unirme» (con aprobación) ou «Unirme».
 */
export function matchAction(match: ShownMatch): MatchAction {
  const role = asMine(match)?.me.role ?? 'NONE';
  if (role === 'ORGANIZER') return 'organizer';
  if (role === 'CONFIRMED') return 'joined';
  if (role === 'PENDING') return 'pending';
  if (role === 'REJECTED') return 'rejected';
  if (match.status === 'CANCELLED') return 'cancelled';
  if (match.status === 'CLOSED') return 'closed';
  if (openMatchCounts(match).free === 0) return 'full';
  return match.approval ? 'ask' : 'join';
}
