// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { longDay, shortDay } from '../events/event-format';
import { inMadrid } from '../ui/calendar/madrid-time';
import type { TagTone } from '../ui/tag/tag.component';
import type { BookingOpenMatchResponse, OpenMatchCardResponse } from './open-matches.store';

/** Traducir unha clave cos seus parámetros (Transloco ou o das probas). */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

export type OpenMatchLevel = NonNullable<OpenMatchCardResponse['level']>;
export const OPEN_MATCH_LEVELS: readonly OpenMatchLevel[] = ['BEGINNER', 'INTERMEDIATE', 'ADVANCED'];

/** As contas dunha partida (ADR-0040, punto 3): quen falta para o mínimo e o sitio que queda. */
export interface OpenMatchCounts {
  readonly total: number;
  readonly maximum: number;
  /** Para o mínimo (0 se xa se chegou). */
  readonly missing: number;
  /** Ata o máximo (0: completa). */
  readonly free: number;
}

export function openMatchCounts(match: Pick<OpenMatchCardResponse, 'total' | 'minimum' | 'maximum'>): OpenMatchCounts {
  return {
    total: match.total,
    maximum: match.maximum,
    missing: Math.max(0, match.minimum - match.total),
    free: Math.max(0, match.maximum - match.total),
  };
}

/** «ven. 20 nov. · 20:00–21:30» (ou, sen `short`, «venres, 20 de novembro · 20:00–21:30»), en Madrid. */
export function openMatchWhenParts(
  match: Pick<OpenMatchCardResponse, 'start' | 'end'>,
  lang: string,
  short = false,
): [string, string] {
  const start = inMadrid(match.start);
  const end = inMadrid(match.end);
  return [short ? shortDay(start.date, lang) : longDay(start.date, lang), `${start.time}–${end.time}`];
}

/**
 * O nome da partida: o título ou, sen el, o uso («Fútbol sala»). O día e a hora van sempre na liña
 * de debaixo: no nome repetiríanse.
 */
export function openMatchTitle(match: Pick<OpenMatchCardResponse, 'title' | 'useName'>): string {
  return match.title || match.useName;
}

/** O uso, só se non fai xa de nome (partida sen título): para non repetilo debaixo. */
export function openMatchUseDetail(match: Pick<OpenMatchCardResponse, 'title' | 'useName'>): string | null {
  return match.title ? match.useName : null;
}

/** «4 de 10 · faltan 6», «3 de 4 · falta 1», «6 de 8 · quedan 2 prazas» ou «12 de 12 · completa». */
export function openMatchCountsText(
  match: Pick<OpenMatchCardResponse, 'total' | 'minimum' | 'maximum'>,
  t: Translate,
): string {
  return [
    t('openMatch.ofTotal', { total: match.total, maximum: match.maximum }),
    lowerFirst(statusText(match, t)),
  ].join(' · ');
}

/**
 * O estado das contas para «4 de 10 · faltan 6» (As miñas reservas, detalle da reserva): o que falta
 * (azul), o sitio que queda (verde) ou completa (vermello). As portadas e a ficha usan o chip común das prazas
 * (`furar-seats-chip`).
 */
export interface OpenMatchChip {
  readonly key: 'missingOne' | 'missingOther' | 'freeOne' | 'freeOther' | 'full';
  readonly tone: TagTone;
  readonly count: number;
}

export function openMatchChip(match: Pick<OpenMatchCardResponse, 'total' | 'minimum' | 'maximum'>): OpenMatchChip {
  const { missing, free } = openMatchCounts(match);
  if (missing > 0) return { key: missing === 1 ? 'missingOne' : 'missingOther', tone: 'info', count: missing };
  if (free > 0) return { key: free === 1 ? 'freeOne' : 'freeOther', tone: 'success', count: free };
  return { key: 'full', tone: 'danger', count: 0 };
}

function statusText(match: Pick<OpenMatchCardResponse, 'total' | 'minimum' | 'maximum'>, t: Translate): string {
  const chip = openMatchChip(match);
  // Prefixo literal: i18n:check ve as claves dinámicas de `openMatch.` só así.
  return t('openMatch.' + chip.key, { count: chip.count });
}

function lowerFirst(text: string): string {
  return text.charAt(0).toLocaleLowerCase() + text.slice(1);
}

/**
 * O chip «Buscando xente» na tarxeta da reserva de quen organiza (azul, info): só mentres busca e
 * se se ve na portada. A nota, «4 de 10».
 */
export function searchingChip(
  openMatch: BookingOpenMatchResponse | null | undefined,
): { total: number; maximum: number } | null {
  if (!openMatch || openMatch.status !== 'OPEN' || openMatch.hidden) return null;
  return { total: openMatch.total, maximum: openMatch.maximum };
}

/** «4 de 10 · faltan 2» para a fila «Partida» do detalle da reserva, só mentres busca e se ve. */
export function searchingCountsText(
  openMatch: BookingOpenMatchResponse | null | undefined,
  t: Translate,
): string | null {
  return openMatch && searchingChip(openMatch) ? openMatchCountsText(openMatch, t) : null;
}

/** «Xógase se hai 10 persoas antes do venres, 20 de novembro ás 08:00. Máximo 12.» */
export function openMatchRule(
  match: Pick<OpenMatchCardResponse, 'minimum' | 'maximum'> & { readonly deadline: string },
  lang: string,
  t: Translate,
): string {
  const { date, time } = inMadrid(match.deadline);
  return t('openMatch.page.rule', {
    minimum: match.minimum,
    maximum: match.maximum,
    day: longDay(date, lang),
    time,
  });
}
