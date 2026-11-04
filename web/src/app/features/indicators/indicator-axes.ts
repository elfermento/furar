// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Os eixes das barras deitadas dos indicadores (ocupación e ingresos por instalación): nomes que
// parten en liñas, máis estreitos nun móbil, e o alto segundo as filas. Puro.
import type { ChartOption } from '../../shared/ui/chart/chart-options';
import { whenNarrow } from '../../shared/ui/chart/chart-options';

/** Ancho como moito dos nomes das instalacións no eixo (px): os longos parten en dúas liñas. */
const NAME_WIDTH = 128;
/**
 * O mesmo nun gráfico estreito (móbil): un punto menos de letra e máis liñas no nome para que a
 * barra teña sitio; ningún nome se agocha (cada fila ten 2,75 rem, onde caben tres liñas).
 */
const NARROW_NAME_WIDTH = 96;
const NARROW_NAME_SIZE = 12;

/**
 * Nas barras deitadas, os nomes das instalacións non pasan de NAME_WIDTH (NARROW_NAME_WIDTH nun
 * gráfico estreito) e parten en liñas: nun móbil, un nome longo non deixa a barra sen sitio.
 */
export function wrapNames(option: ChartOption, nameSize: number): ChartOption {
  const axis = option.yAxis as { axisLabel?: Record<string, unknown> } | undefined;
  if (!axis || Array.isArray(axis)) return option;
  const wrapped = {
    ...option,
    yAxis: { ...axis, axisLabel: { ...axis.axisLabel, width: NAME_WIDTH, overflow: 'break' } },
  } as ChartOption;
  return whenNarrow(
    wrapped,
    {
      yAxis: {
        axisLabel: { width: NARROW_NAME_WIDTH, fontSize: NARROW_NAME_SIZE, lineHeight: 14, hideOverlap: false },
      },
    },
    {
      yAxis: {
        axisLabel: { width: NAME_WIDTH, fontSize: nameSize, lineHeight: Math.round(nameSize * 1.3), hideOverlap: true },
      },
    },
  );
}

/** Alto dun gráfico de barras deitadas: unha fila de ~2,75 rem por categoría. */
export const barsHeight = (count: number) => Math.max(8, count * 2.75 + 2);
