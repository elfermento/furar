// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Só tipos de ECharts (`import type`): os construtores son funcións puras que non cargan ECharts,
// así que as features poden usalos sen meter a libraría no seu chunk.
import type { BarSeriesOption, HeatmapSeriesOption, LineSeriesOption } from 'echarts/charts';
import type { GridComponentOption, TooltipComponentOption, VisualMapComponentOption } from 'echarts/components';
import type { ComposeOption } from 'echarts/core';
import { PARTIAL_DASH, partialTooltip, splitAtPartial } from './chart-partial';
import type { ChartTokens } from './chart-tokens';

/** Opcións dun gráfico, limitadas ás pezas rexistradas en echarts-setup.ts. */
export type ChartOption = ComposeOption<
  | LineSeriesOption
  | BarSeriesOption
  | HeatmapSeriesOption
  | GridComponentOption
  | TooltipComponentOption
  | VisualMapComponentOption
>;

/** O que recibe furar-chart: as opcións en función dos tokens, para redebuxar ao cambiar o tema. */
export type ChartSpec = (tokens: ChartTokens) => ChartOption;

/** Como se escribe un valor (no idioma de quen le: `Intl`, unidades…); por defecto, o número. */
export type ValueFormat = (value: number) => string;

/** Cores de estado (docs/deseno.md §3.1) e o `primary` para cantidades. */
export type ChartTone = 'primary' | 'success' | 'danger' | 'warn' | 'info' | 'muted';

/** Trama dunha serie (ademais do ton): raias diagonais (alerta), puntos ou raias horizontais. */
export type ChartPattern = 'stripes' | 'dots' | 'lines';

/** Unha serie: nome (tooltip) e un valor por categoría; `null` é «sen dato». */
export interface ChartSeries {
  readonly name: string;
  readonly values: readonly (number | null)[];
}

const plain: ValueFormat = (value) => String(value);
/** Marca de ECharts para «sen dato» nunha serie. */
const EMPTY = '-';

const data = (values: readonly (number | null)[]) => values.map((value) => value ?? EMPTY);

/** O último índice con dato (para a etiqueta do último valor). */
function lastIndex(values: readonly (number | null)[]): number {
  for (let i = values.length - 1; i >= 0; i--) if (values[i] !== null) return i;
  return -1;
}

/** Valor do tooltip: sempre texto plano co formato de quen chama. */
function formatted(format: ValueFormat, value: unknown): string {
  const number = Array.isArray(value) ? value.at(-1) : value;
  return typeof number === 'number' && Number.isFinite(number) ? format(number) : EMPTY;
}

/**
 * Tooltip en `richText` (debuxado no canvas): o tooltip HTML de ECharts mete `<span style>` por
 * `innerHTML` e a CSP (`style-src 'self'`) bloquearíao.
 */
export function chartTooltip(
  t: ChartTokens,
  trigger: 'axis' | 'item',
  format: ValueFormat = plain,
  show = true,
): TooltipComponentOption {
  return {
    show,
    trigger,
    renderMode: 'richText',
    confine: true,
    backgroundColor: t.surface,
    borderColor: t.line,
    borderWidth: 1,
    textStyle: { color: t.ink, fontFamily: t.fontFamily, fontSize: t.fontSize },
    // Liña e non sombra: o nome do tipo sombra nun literal faría que Tailwind xerase unha clase máis.
    axisPointer: { type: 'line', lineStyle: { color: t.line } },
    valueFormatter: (value) => formatted(format, value),
  };
}

function base(t: ChartTokens) {
  return {
    backgroundColor: 'transparent',
    textStyle: { fontFamily: t.fontFamily, fontSize: t.fontSize, color: t.ink },
  } satisfies ChartOption;
}

function categoryAxis(t: ChartTokens, categories: readonly string[], boundaryGap = true) {
  return {
    type: 'category' as const,
    data: [...categories],
    boundaryGap,
    axisTick: { show: false },
    axisLine: { lineStyle: { color: t.line } },
    axisLabel: { color: t.inkSoft, fontSize: t.fontSize, hideOverlap: true },
  };
}

function valueAxis(t: ChartTokens, format: ValueFormat, max?: number) {
  return {
    type: 'value' as const,
    max,
    // Cun tope (100 %), catro tramos redondos: 0, 25, 50, 75 e 100.
    interval: max === undefined ? undefined : max / 4,
    splitNumber: 4,
    // Nun gráfico estreito (móbil) as marcas que se pisan agóchanse; as liñas quedan.
    axisLabel: {
      color: t.inkSoft,
      fontSize: t.fontSize,
      hideOverlap: true,
      formatter: (value: number) => format(value),
    },
    splitLine: { lineStyle: { color: t.divider } },
  };
}

const GRID = { left: 8, right: 16, top: 24, bottom: 8 };

/** Ancho do gráfico (px, non da páxina) ata o que vale o axuste para estreito: un móbil a 320 px. */
export const NARROW_WIDTH = 360;

/** Un anaco de opcións que se mestura coas do gráfico (os tipos de ECharts non levan `media`). */
type UnitOption = Record<string, unknown>;
interface MediaUnit {
  readonly query?: { readonly maxWidth: number };
  readonly option: UnitOption;
}

/**
 * Engade un axuste para gráficos estreitos coas media queries de ECharts (miden o gráfico e
 * vólvense aplicar ao cambiar de tamaño): `narrow` ata NARROW_WIDTH e `wide`, os valores de
 * sempre, ao medrar. Varias chamadas suman as súas claves (cada unha, un compoñente distinto).
 */
export function whenNarrow(option: ChartOption, narrow: UnitOption, wide: UnitOption): ChartOption {
  const [small, normal] = (option as { media?: readonly MediaUnit[] })['media'] ?? [];
  const media: MediaUnit[] = [
    { query: { maxWidth: NARROW_WIDTH }, option: { ...small?.option, ...narrow } },
    { option: { ...normal?.option, ...wide } },
  ];
  return { ...option, media } as ChartOption;
}

export interface LineAreaInput {
  readonly categories: readonly string[];
  readonly series: ChartSeries;
  readonly format?: ValueFormat;
  /**
   * Índice do punto en curso (o período que aínda non rematou), se o hai. Desde o punto anterior a
   * el ata o final, a liña vai discontinua (trazo, non cor: tamén en `forced-colors` e no papel), e
   * o tooltip e, se é o último, a etiqueta engaden `partialLabel`.
   */
  readonly partial?: number;
  /** «en curso». */
  readonly partialLabel?: string;
}

/** Rótulos do eixo X dunha liña: como moito estes, en ancho e en estreito. */
const LINE_LABELS = { wide: 8, narrow: 4 } as const;

/**
 * Os rótulos que se ven nun eixo de `count` categorías: un de cada `step`, contados desde o
 * último, que sempre leva rótulo (o período de agora non queda sen nome).
 */
export function labelsFromLast(count: number, max: number): (index: number) => boolean {
  const step = Math.max(1, Math.ceil(count / max));
  return (index) => (count - 1 - index) % step === 0;
}

/**
 * Liña con área en `primary` (evolución, ocupación día a día), coa etiqueta só no último valor:
 * enriba se a liña chega subindo ou plana, e debaixo á esquerda se chega baixando (o lado que a
 * liña deixa libre, para non tapala). O último período leva sempre rótulo no eixo. Cun punto en
 * curso (`partial`), o tramo desde o anterior vai discontinuo nunha segunda serie co mesmo nome e
 * a mesma cor, e o tooltip e a etiqueta din «en curso».
 */
export function lineArea(
  t: ChartTokens,
  { categories, series, format = plain, partial, partialLabel = '' }: LineAreaInput,
): ChartOption {
  const last = lastIndex(series.values);
  const before = series.values[lastIndex(series.values.slice(0, Math.max(0, last)))];
  const falling = before !== undefined && before !== null && before > (series.values[last] ?? 0);
  const label = falling ? { position: 'left' as const, offset: [-2, 12] } : { position: 'top' as const };
  const axis = (max: number) => ({
    xAxis: { axisLabel: { interval: labelsFromLast(categories.length, max), hideOverlap: false } },
  });
  const lastText = (value: number) =>
    last === partial && partialLabel ? `${format(value)}\n${partialLabel}` : format(value);
  const points = (values: readonly (number | null)[]) =>
    values.map((value, i) =>
      i === last && value !== null
        ? { value, symbolSize: 8, label: { show: true, ...label, formatter: () => lastText(value) } }
        : { value: value ?? EMPTY, symbolSize: 0 },
    );
  const line = (values: readonly (number | null)[], dashed: boolean): LineSeriesOption => ({
    type: 'line',
    name: series.name,
    data: points(values),
    symbol: 'circle',
    color: t.primary,
    lineStyle: { color: t.primary, width: 2, ...(dashed ? { type: [...PARTIAL_DASH] } : {}) },
    areaStyle: { color: t.primary, opacity: 0.12 },
    label: { show: false, color: t.ink, fontWeight: 'bold', fontSize: t.fontSize },
  });
  const { solid, dashed } = splitAtPartial(series.values, partial);
  const tooltip = chartTooltip(t, 'axis', format);
  return whenNarrow(
    {
      ...base(t),
      tooltip:
        dashed && partial !== undefined
          ? { ...tooltip, formatter: partialTooltip(format, partial, partialLabel) }
          : tooltip,
      grid: { ...GRID, right: 24, top: 32 },
      xAxis: categoryAxis(t, categories, false),
      yAxis: valueAxis(t, format),
      series: dashed ? [line(solid, false), line(dashed, true)] : [line(solid, false)],
    },
    axis(LINE_LABELS.narrow),
    axis(LINE_LABELS.wide),
  );
}

/**
 * Minigráfica dunha cifra: liña con área, sen eixes nin tooltip (o valor vai en texto na tarxeta).
 * Cun punto en curso (`partial`), o tramo desde o anterior vai discontinuo, como en `lineArea`.
 */
export function sparkline(t: ChartTokens, values: readonly (number | null)[], partial?: number): ChartOption {
  const last = lastIndex(values);
  const line = (part: readonly (number | null)[], dashed: boolean): LineSeriesOption => ({
    type: 'line',
    silent: true,
    data: part.map((value, i) => ({ value: value ?? EMPTY, symbolSize: i === last ? 6 : 0 })),
    symbol: 'circle',
    color: t.primary,
    lineStyle: { color: t.primary, width: 2, ...(dashed ? { type: [...PARTIAL_DASH] } : {}) },
    areaStyle: { color: t.primary, opacity: 0.12 },
  });
  const { solid, dashed } = splitAtPartial(values, partial);
  return {
    ...base(t),
    tooltip: chartTooltip(t, 'axis', plain, false),
    grid: { left: 4, right: 4, top: 4, bottom: 4 },
    xAxis: { type: 'category', show: false, boundaryGap: false, data: values.map((_, i) => String(i)) },
    yAxis: { type: 'value', show: false, scale: true },
    series: dashed ? [line(solid, false), line(dashed, true)] : [line(solid, false)],
  };
}

export interface BarsInput {
  readonly categories: readonly string[];
  readonly series: ChartSeries;
  readonly format?: ValueFormat;
  /** Tope do eixo (100 nunha porcentaxe). */
  readonly max?: number;
}

/** Barras horizontais en `primary` (ocupación por instalación), co valor ao final de cada barra. */
export function hBars(t: ChartTokens, { categories, series, format = plain, max }: BarsInput): ChartOption {
  const option: ChartOption = {
    ...base(t),
    tooltip: chartTooltip(t, 'axis', format),
    grid: { ...GRID, right: 56 },
    xAxis: valueAxis(t, format, max),
    yAxis: { ...categoryAxis(t, categories), inverse: true },
    series: [
      {
        type: 'bar',
        name: series.name,
        data: data(series.values),
        color: t.primary,
        barMaxWidth: 20,
        label: {
          show: true,
          position: 'right',
          color: t.ink,
          fontSize: t.fontSize,
          formatter: ({ value }) => formatted(format, value),
        },
      },
    ],
  };
  if (max === undefined) return option;
  // Estreito: só os extremos (0 e 100 %); o valor de cada barra xa vai ao seu carón.
  const ends = (value: number) => (value === 0 || value === max ? format(value) : '');
  return whenNarrow(
    option,
    { xAxis: { axisLabel: { formatter: ends } } },
    { xAxis: { axisLabel: { formatter: (value: number) => format(value) } } },
  );
}

export interface ColumnsInput extends BarsInput {
  /** Columna destacada en `primary` (o período en curso); as demais, no paso 2 da escala. Por defecto, a última. */
  readonly current?: number;
}

/**
 * Columnas (persoas distintas por mes): a actual en `primary` e as pasadas no paso 2 da escala
 * secuencial cun bordo `primary`, que dá os 3:1 dun obxecto gráfico (WCAG 1.4.11). En estreito,
 * todos os rótulos cun punto menos e, con máis de seis, en vertical (non se agocha ningún mes).
 */
export function columns(
  t: ChartTokens,
  { categories, series, format = plain, max, current }: ColumnsInput,
): ChartOption {
  const highlighted = current ?? series.values.length - 1;
  const past = t.scale[1] ?? t.primary;
  const option: ChartOption = {
    ...base(t),
    tooltip: chartTooltip(t, 'axis', format),
    grid: GRID,
    xAxis: categoryAxis(t, categories),
    yAxis: valueAxis(t, format, max),
    series: [
      {
        type: 'bar',
        name: series.name,
        barMaxWidth: 32,
        data: series.values.map((value, i) => ({
          value: value ?? EMPTY,
          itemStyle: i === highlighted ? { color: t.primary } : { color: past, borderColor: t.primary, borderWidth: 1 },
        })),
      },
    ],
  };
  const rotate = categories.length > 6 ? 90 : 0;
  return whenNarrow(
    option,
    { xAxis: { axisLabel: { interval: 0, hideOverlap: false, rotate, fontSize: t.fontSize - 2 } } },
    { xAxis: { axisLabel: { interval: 'auto', hideOverlap: true, rotate: 0, fontSize: t.fontSize } } },
  );
}
