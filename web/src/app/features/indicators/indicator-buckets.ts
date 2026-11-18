// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Os tramos de tempo das series (días, semanas, meses): os seus rótulos e o tramo en curso, que
// leva «(en curso)» en texto e o trazo discontinuo nas liñas (ADR-0026, revisión do 2026-10-29).
// Puro.
import type { BucketUnit, Translate } from './conclusions';
import type { IndicatorFormats } from './indicator-format';

/** Rótulo curto dun tramo (eixes) e longo (táboas e CSV). */
export function bucketLabels(unit: BucketUnit, from: string, to: string, f: IndicatorFormats) {
  if (unit === 'MONTH') return { short: f.monthShort(from), long: f.monthYear(from) };
  if (unit === 'DAY') return { short: f.dayMonth(from), long: f.shortDate(from) };
  return { short: f.dayMonth(from), long: `${f.shortDate(from)} – ${f.shortDate(to)}` };
}

/** O índice do punto en curso dunha serie; `undefined` se non o hai. */
export function partialIndex(points: readonly { partial: boolean }[]): number | undefined {
  const index = points.findIndex((p) => p.partial);
  return index < 0 ? undefined : index;
}

/** O rótulo longo (táboa e CSV) do tramo en curso leva «(en curso)» en texto, non só no estilo. */
export function markInProgress(long: string, partial: boolean, t: Translate): string {
  return partial ? `${long} (${t('indicators.inProgress')})` : long;
}

/** Os rótulos dun tramo dunha serie, co longo marcado se está en curso. */
export function seriesLabels(
  unit: BucketUnit,
  bucket: { from: string; to: string; partial: boolean },
  f: IndicatorFormats,
  t: Translate,
) {
  const labels = bucketLabels(unit, bucket.from, bucket.to, f);
  return { ...labels, long: markInProgress(labels.long, bucket.partial, t) };
}
