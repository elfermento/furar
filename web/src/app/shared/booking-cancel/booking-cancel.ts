// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { BookingOpenMatchResponse } from '../open-matches/open-matches.store';
import type { IsoDate } from '../ui/calendar/local-date';
import { formatMonth, weekdayIndex, weekdayNames } from '../ui/calendar/local-date';
import type { ConfirmDetail } from '../ui/dialog/confirm.component';

/** Traduce unha clave cos seus parámetros. */
type Translate = (key: string, params?: Record<string, unknown>) => string;

/**
 * Día sen ano: `long` «martes, 27 de outubro»; `short` «mar 27 out» (campo Data), coas tres
 * primeiras letras dos nomes enteiros. Sae dos nomes de `local-date` (con ou sen datos `gl` en
 * Intl); vive aquí e non alí para non engadir nada á carga inicial.
 */
export function formatDay(iso: IsoDate, lang: string, style: 'short' | 'long'): string {
  const weekday = weekdayNames(lang)[weekdayIndex(iso)].long;
  const month = formatMonth(iso, lang).split(' ')[0].toLocaleLowerCase(lang);
  const day = Number(iso.slice(8));
  return style === 'short' ? `${weekday.slice(0, 3)} ${day} ${month.slice(0, 3)}` : `${weekday}, ${day} de ${month}`;
}

/** Cancelar unha reserva con partida (ADR-0040, punto 6): a partida tamén, e avísase ás apuntadas. */
export function matchCancelNotice(
  match: BookingOpenMatchResponse | null | undefined,
  t: Translate,
): ConfirmDetail | null {
  if (!match || match.status === 'CANCELLED') return null;
  const count = match.confirmed;
  const key = count === 0 ? 'cancelNoticeNone' : count === 1 ? 'cancelNoticeOne' : 'cancelNotice';
  return { icon: 'group', tone: 'warn', text: t(`openMatch.${key}`, { count }) };
}
