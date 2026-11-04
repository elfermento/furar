// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Construtores con varias series ou celas (barras apiladas e mapa de calor). Como en
// chart-options.ts, só tipos de ECharts: funcións puras sen cargar a libraría.
import type { BarSeriesOption } from 'echarts/charts';
import type { ChartOption, ChartPattern, ChartSeries, ChartTone, ValueFormat } from './chart-options';
import { chartTooltip } from './chart-options';
import type { ChartTokens } from './chart-tokens';
import { rgb } from '../../../core/theme/color';

type Decal = Exclude<NonNullable<NonNullable<BarSeriesOption['itemStyle']>['decal']>, 'none'>;

const plain: ValueFormat = (value) => String(value);

/**
 * Trama dunha serie, en raias ou puntos da cor da superficie sobre o ton, para que dúas series
 * non se distingan só pola cor (1.4.1): verde e vermello, ou verde e ámbar, teñen case a mesma
 * luminancia. `stripes` (raias diagonais) é a da serie de alerta (fóra de prazo, a devolver);
 * `dots` (puntos) e `lines` (raias horizontais), as doutras series que se confunden polo ton.
 * As mostras da lenda (chart-card.css) debuxan a mesma trama.
 */
export function patternDecal(t: ChartTokens, pattern: ChartPattern): Decal {
  const [r, g, b] = rgb(t.surface);
  const color = `rgba(${r}, ${g}, ${b}, 0.6)`;
  if (pattern === 'dots') return { symbol: 'circle', symbolSize: 1, dashArrayX: [3, 3], dashArrayY: [3, 3], color };
  if (pattern === 'lines') return { symbol: 'rect', symbolSize: 1, dashArrayX: [1, 0], dashArrayY: [2, 3], color };
  return { symbol: 'rect', symbolSize: 1, dashArrayX: [1, 0], dashArrayY: [3, 4], rotation: -Math.PI / 4, color };
}

/** A trama da serie de alerta (`stripes`). */
export const alertDecal = (t: ChartTokens): Decal => patternDecal(t, 'stripes');

function axisLabel(t: ChartTokens) {
  return { color: t.inkSoft, fontSize: t.fontSize, hideOverlap: true };
}

export interface StackedSeries extends ChartSeries {
  readonly tone: ChartTone;
  /** Trama (`patternDecal`): `stripes` na serie de alerta. */
  readonly pattern?: ChartPattern;
}

export interface StackedBarsInput {
  readonly categories: readonly string[];
  readonly series: readonly StackedSeries[];
  readonly format?: ValueFormat;
  /** Barras deitadas (unha por instalación); por defecto, columnas (unha por período). */
  readonly horizontal?: boolean;
}

/** Por debaixo desta fracción da barra máis longa, un tramo é estreito e vai sen bordo propio. */
const NARROW_PART = 0.04;

/**
 * Barras apiladas por estado (cancelacións, ingresos): cada serie co seu ton e un separador de
 * 2 px na cor da superficie entre tramos; as que se confunden polo ton, ademais, con trama
 * (`pattern`). Un tramo estreito (unha cancelación entre cincuenta) non leva bordo propio: o
 * separador de 2 px comeríao enteiro; sepárao a metade de dentro do bordo do tramo de antes. Un
 * tramo 0 tampouco (debuxaría unha raia da superficie sobre o veciño).
 */
export function stackedBars(
  t: ChartTokens,
  { categories, series, format = plain, horizontal = false }: StackedBarsInput,
): ChartOption {
  const category = {
    type: 'category' as const,
    data: [...categories],
    inverse: horizontal,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: t.line } },
    axisLabel: axisLabel(t),
  };
  const value = {
    type: 'value' as const,
    splitNumber: 4,
    axisLabel: { ...axisLabel(t), formatter: (v: number) => format(v) },
    splitLine: { lineStyle: { color: t.divider } },
  };
  const longest = Math.max(
    0,
    ...categories.map((_, i) => series.reduce((sum, item) => sum + Math.abs(item.values[i] ?? 0), 0)),
  );
  const narrow = (v: number | null) => v !== null && Math.abs(v) <= longest * NARROW_PART;
  return {
    backgroundColor: 'transparent',
    textStyle: { fontFamily: t.fontFamily, fontSize: t.fontSize, color: t.ink },
    tooltip: chartTooltip(t, 'axis', format),
    grid: { left: 8, right: 16, top: 24, bottom: 8 },
    xAxis: horizontal ? value : category,
    yAxis: horizontal ? category : value,
    series: series.map((item) => ({
      type: 'bar' as const,
      name: item.name,
      stack: 'total',
      barMaxWidth: horizontal ? 24 : 40,
      data: item.values.map((v) => (narrow(v) ? { value: v as number, itemStyle: { borderWidth: 0 } } : (v ?? '-'))),
      color: t[item.tone],
      itemStyle: {
        color: t[item.tone],
        borderColor: t.surface,
        borderWidth: 2,
        ...(item.pattern ? { decal: patternDecal(t, item.pattern) } : {}),
      },
    })),
  };
}

/** Unha cela do mapa de calor: columna, fila e valor; `null` = pechado (sen horas abertas). */
export interface HeatmapCell {
  readonly x: number;
  readonly y: number;
  readonly value: number | null;
}

export interface HeatmapInput {
  /** Columnas (horas). */
  readonly columns: readonly string[];
  /** Filas (días). */
  readonly rows: readonly string[];
  readonly cells: readonly HeatmapCell[];
  /** Texto do tooltip dunha cela pechada («Pechado»), xa traducido. */
  readonly closedLabel: string;
  readonly format?: ValueFormat;
  /** Tope da escala (100 nunha porcentaxe); por defecto, o maior valor. */
  readonly max?: number;
}

/** Valor das celas pechadas: fóra do rango do `visualMap`, que as pinta coa superficie. */
const CLOSED = -1;

/** Bordo dunha cela pechada: discontinuo e na cor `line`, dentro do oco de 2 px. */
const CLOSED_STYLE = (t: ChartTokens) => ({ borderColor: t.line, borderWidth: 1, borderType: 'dashed' as const });

/**
 * Mapa de calor (horas punta, día × hora): as celas abertas coa escala secuencial do `primary`,
 * separadas por 2 px da superficie; as pechadas, sen recheo (a superficie) e cun bordo
 * discontinuo `line` (3:1), para que non se confundan cun 0 % nin cos pasos baixos (1.4.1, 1.4.11).
 * O `visualMap` non se ve: a lenda e a táboa van en texto fóra do gráfico.
 */
export function heatmap(
  t: ChartTokens,
  { columns, rows, cells, closedLabel, format = plain, max }: HeatmapInput,
): ChartOption {
  // O tope nunca queda por debaixo dun valor: unha cela aberta fóra do rango sairía coma pechada.
  const top = Math.max(max ?? 1, ...cells.map((cell) => cell.value ?? 0));
  return {
    backgroundColor: 'transparent',
    textStyle: { fontFamily: t.fontFamily, fontSize: t.fontSize, color: t.ink },
    tooltip: {
      ...chartTooltip(t, 'item', format),
      // «día · hora · valor» en texto plano (richText): o tooltip por defecto non di o día.
      formatter: (params) => {
        const item = Array.isArray(params) ? params[0] : params;
        const [x, y, value] = (Array.isArray(item?.value) ? item.value : []) as number[];
        const shown = value === CLOSED ? closedLabel : typeof value === 'number' ? format(value) : '-';
        return [rows[y ?? -1] ?? '', columns[x ?? -1] ?? '', shown].join(' · ');
      },
    },
    grid: { left: 8, right: 8, top: 8, bottom: 8 },
    xAxis: {
      type: 'category',
      data: [...columns],
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: axisLabel(t),
    },
    yAxis: {
      type: 'category',
      data: [...rows],
      inverse: true,
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: axisLabel(t),
    },
    visualMap: {
      type: 'continuous',
      show: false,
      min: 0,
      max: top,
      dimension: 2,
      // Sen isto, o que queda por debaixo de `min` contaría como dentro (e as pechadas sairían co primeiro paso).
      unboundedRange: false,
      inRange: { color: [...t.scale] },
      outOfRange: { color: [t.surface] },
    },
    series: [
      {
        type: 'heatmap',
        data: cells.map((cell) =>
          cell.value === null
            ? { value: [cell.x, cell.y, CLOSED], itemStyle: CLOSED_STYLE(t) }
            : [cell.x, cell.y, cell.value],
        ),
        itemStyle: { borderColor: t.surface, borderWidth: 2, borderRadius: 4 },
        emphasis: { itemStyle: { borderColor: t.ink } },
      },
    ],
  };
}
