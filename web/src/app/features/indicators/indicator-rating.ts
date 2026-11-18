// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// A cifra «Valoración media» (ADR-0037): a media das valoracións feitas no período, «4,2 de 5»,
// e o seu cambio co período anterior na mesma unidade (unha décima: «+0,1»). Sen valoracións, «—»
// e sen cambio. Puro.
import type { StatTrend } from '../../shared/ui/stat-tile/stat-tile.component';
import { formatAverage } from '../../shared/ui/stars/rating-format';

/** Valoracións en bruto, coma na API. */
export interface RatingTotals {
  readonly reviews: number;
  readonly starsSum: number;
}

/** A media cun decimal (como se mostra); `null` sen valoracións. */
export function ratingAverage(rating: RatingTotals): number | null {
  return rating.reviews > 0 ? Math.round((rating.starsSum / rating.reviews) * 10) / 10 : null;
}

/** «+0,1», «−0,3», «0,0»; sen media nun dos dous, nada. */
export function ratingChange(
  now: RatingTotals,
  before: RatingTotals,
  lang: string,
): { change: string; trend: StatTrend | null } {
  const a = ratingAverage(now);
  const b = ratingAverage(before);
  if (a === null || b === null) return { change: '', trend: null };
  const diff = Math.round((a - b) * 10) / 10;
  const sign = diff > 0 ? '+' : diff < 0 ? '−' : '';
  return {
    change: `${sign}${formatAverage(Math.abs(diff), lang)}`,
    trend: diff > 0 ? 'up' : diff < 0 ? 'down' : 'same',
  };
}
