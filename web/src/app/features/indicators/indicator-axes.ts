// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Os eixes das barras deitadas dos indicadores (ocupación e ingresos por instalación): o nome de
// cada instalación vai na súa liña, enriba da súa barra e con todo o ancho do gráfico; se parte en
// varias liñas, as filas levan o alto xusto do nome máis longo, para que ningún pise o seguinte.
import type { ChartHeight, ChartOption } from '../../shared/ui/chart/chart-options';
import type { ChartTokens } from '../../shared/ui/chart/chart-tokens';

/** Grosor de cada barra (px). */
const BAR = 20;
/** Entre o nome e a súa barra (px). */
const NAME_GAP = 4;
/** Entre unha barra e o nome da fila seguinte (px). */
const ROW_GAP = 12;
/** Debaixo da última fila: o eixo cos valores (px). */
const AXIS = 32;
/** Os nomes non pasan do sitio do valor ao final da barra máis longa (px, o `grid.right` de `hBars`). */
const RIGHT = 56;
/** Ancho que se supón sen medida (o dun móbil estreito). */
const FALLBACK_WIDTH = 320;
/** Ancho medio dunha letra (en `em`) se o navegador non deixa medir o texto (nos tests). */
const LETTER = 0.55;

/** Id da serie baleira que leva os nomes (as demais son as do gráfico). */
export const NAMES_SERIES = 'names';

/** Mide o ancho dun texto en px. */
export type TextMeasure = (text: string) => number;

/** Aire enriba do primeiro nome (px): que non toque o bordo do gráfico nin da imaxe impresa. */
const TOP = 4;

const lineHeight = (fontSize: number) => Math.round(fontSize * 1.3);

/** Ancho dos nomes: o do gráfico menos o sitio do valor da barra. */
const namesWidth = (width: number | undefined) => Math.max(120, (width || FALLBACK_WIDTH) - RIGHT);

let context: OffscreenCanvasRenderingContext2D | null | undefined;

/** Mide coa letra do gráfico (un canvas fóra da páxina); sen canvas, estímao polas letras. */
function measureWith(t: ChartTokens): TextMeasure {
  if (context === undefined) {
    context = typeof OffscreenCanvas === 'undefined' ? null : new OffscreenCanvas(1, 1).getContext('2d');
  }
  const ctx = context;
  if (!ctx) return (text) => text.length * t.fontSize * LETTER;
  const font = `${t.fontSize}px ${t.fontFamily}`;
  return (text) => {
    ctx.font = font;
    return ctx.measureText(text).width;
  };
}

/**
 * Liñas que ocupa `name` partindo por palabras en `limit` px (como `overflow: 'break'` de ECharts;
 * unha palabra máis longa ca a liña pártese polo medio).
 */
export function nameLines(name: string, limit: number, measure: TextMeasure): number {
  const space = measure(' ');
  let lines = 1;
  let used = 0;
  for (const word of name.trim().split(/\s+/)) {
    const size = measure(word);
    if (used > 0 && used + space + size <= limit) {
      used += space + size;
      continue;
    }
    if (used > 0) lines += 1;
    const pieces = Math.max(1, Math.ceil(size / limit));
    lines += pieces - 1;
    used = size - (pieces - 1) * limit;
  }
  return lines;
}

/** Liñas do nome máis longo co ancho do gráfico. */
function maxLines(names: readonly string[], width: number | undefined, t: ChartTokens): number {
  const measure = measureWith(t);
  // Marxe do 10 %: o gráfico mide algunhas letras (acentuadas) máis anchas ca este canvas e
  // partiría o nome nunha liña máis da reservada; mellor unha liña de sobra ca un nome cortado.
  const limit = Math.floor((namesWidth(width) - 2) * 0.9);
  return Math.max(1, ...names.map((name) => nameLines(name, limit, measure)));
}

/** Oco do nome enriba da barra (px): as súas liñas e o aire ata a barra. */
const nameSlot = (lines: number, fontSize: number) => lines * lineHeight(fontSize) + NAME_GAP;

/** Alto dunha fila (px): o nome, a barra e o aire ata a seguinte. */
const rowHeight = (lines: number, fontSize: number) => nameSlot(lines, fontSize) + BAR + ROW_GAP;

/** Alto do gráfico (px) segundo o seu ancho: as filas, xustas para o nome máis longo. */
export function barsHeight(names: readonly string[]): ChartHeight {
  return (width: number, t: ChartTokens) =>
    TOP + Math.max(1, names.length) * rowHeight(maxLines(names, width, t), t.fontSize) + AXIS;
}

/**
 * Pon os nomes das instalacións enriba de cada barra, co ancho do gráfico (`width`, en px). O nome
 * vai nunha serie baleira (sen tooltip nin rato) que ocupa o oco de enriba en cada categoría, á
 * beira da barra (`barGap: 0`): así a barra queda abaixo e a fila non leva oco debaixo. O eixo das
 * categorías non se pinta (nin a liña, que cruzaba os nomes). O alto vén de `barsHeight`.
 */
export function namesAbove(option: ChartOption, width: number | undefined, t: ChartTokens): ChartOption {
  const axis = option.yAxis as { data?: readonly string[] } | undefined;
  const series = option.series;
  if (!axis || Array.isArray(axis) || !Array.isArray(series)) return option;
  const names = axis.data ?? [];
  const slot = nameSlot(maxLines(names, width, t), t.fontSize);
  const label = {
    show: true,
    position: [0, slot] as [number, number],
    align: 'left' as const,
    verticalAlign: 'bottom' as const,
    padding: [0, 0, NAME_GAP, 0],
    width: namesWidth(width),
    overflow: 'break' as const,
    color: t.inkSoft,
    fontSize: t.fontSize,
    lineHeight: lineHeight(t.fontSize),
    textBorderWidth: 0,
    formatter: ({ name }: { name: string }) => name,
  };
  return {
    ...option,
    // Os nomes empezan onde o texto da tarxeta; arriba, só uns px para que o primeiro nome non toque o bordo.
    grid: { ...(option.grid as object | undefined), left: 0, top: TOP, bottom: AXIS },
    yAxis: { ...axis, axisLine: { show: false }, axisTick: { show: false }, axisLabel: { show: false } },
    series: [
      {
        type: 'bar',
        id: NAMES_SERIES,
        name: '',
        stack: NAMES_SERIES,
        data: names.map(() => 0),
        barWidth: slot,
        barGap: '0%',
        silent: true,
        tooltip: { show: false },
        itemStyle: { color: 'transparent', borderWidth: 0 },
        emphasis: { disabled: true },
        label,
      },
      ...series.map((item) => ({ ...item, barWidth: BAR, barMaxWidth: BAR, barGap: '0%' })),
    ],
  } as ChartOption;
}
