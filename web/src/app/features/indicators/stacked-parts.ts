// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// As partes das barras apiladas (cancelacións, ingresos): ton, trama e a súa entrada na lenda,
// a mesma mostra ca no gráfico (1.4.1). Puro.
import type { ChartLegendItem } from '../../shared/ui/chart-card/chart-card.component';
import type { ChartPattern, ChartTone } from '../../shared/ui/chart/chart-options';

/** Unha parte dunha barra apilada: nome, ton, trama (se a leva) e o seu valor nunha fila. */
export interface Part<T> {
  readonly name: string;
  readonly tone: ChartTone;
  readonly pattern?: ChartPattern;
  readonly pick: (row: T) => number;
}

export const patternOf = (part: object): ChartPattern | undefined =>
  'pattern' in part ? (part.pattern as ChartPattern) : undefined;

/** A entrada da lenda dunha parte: a mesma mostra (ton e trama) ca no gráfico. */
export const legendItem = (part: { name: string; tone: ChartTone }): ChartLegendItem => ({
  label: part.name,
  tone: part.tone,
  pattern: patternOf(part),
});
