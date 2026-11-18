// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { contrastRatio } from '../../../core/theme/contrast';
import { DEFAULT_THEME, deriveTokens } from '../../../core/theme/theme-tokens';
import { alertDecal, heatmap, patternDecal, stackedBars } from './chart-composite';
import type { ChartOption } from './chart-options';
import { NARROW_WIDTH, columns, hBars, labelsFromLast, lineArea, sparkline, whenNarrow } from './chart-options';
import { PARTIAL_DASH, splitAtPartial } from './chart-partial';
import { chartTokensFrom, readChartTokens } from './chart-tokens';

const t = chartTokensFrom(deriveTokens(DEFAULT_THEME));
const percent = (value: number) => `${value} %`;
const months = ['xan', 'feb', 'mar', 'abr'];

const built: [string, ChartOption][] = [
  [
    'lineArea',
    lineArea(t, { categories: months, series: { name: 'Ocupación', values: [40, 52, null, 61] }, format: percent }),
  ],
  ['sparkline', sparkline(t, [3, 4, 2, 6])],
  [
    'hBars',
    hBars(t, {
      categories: ['Pavillón', 'Piscina'],
      series: { name: 'Ocupación', values: [72, 40] },
      format: percent,
      max: 100,
    }),
  ],
  ['columns', columns(t, { categories: months, series: { name: 'Persoas', values: [120, 98, 143, 150] } })],
  [
    'stackedBars',
    stackedBars(t, {
      categories: months,
      series: [
        { name: 'En prazo', tone: 'success', values: [4, 2, 1, 3] },
        { name: 'Fóra de prazo', tone: 'danger', pattern: 'stripes', values: [0, 1, 0, 0] },
        { name: 'Concello', tone: 'info', pattern: 'dots', values: [1, 0, 0, 2] },
      ],
    }),
  ],
  [
    'heatmap',
    heatmap(t, {
      columns: ['9', '10'],
      rows: ['luns', 'martes'],
      cells: [
        { x: 0, y: 0, value: 20 },
        { x: 1, y: 0, value: 80 },
        { x: 0, y: 1, value: null },
        { x: 1, y: 1, value: 0 },
      ],
      closedLabel: 'Pechado',
      format: percent,
    }),
  ],
  [
    'lineArea en curso',
    lineArea(t, {
      categories: months,
      series: { name: 'Reservas', values: [40, 52, 58, 30] },
      format: percent,
      partial: 3,
      partialLabel: 'en curso',
    }),
  ],
  ['sparkline en curso', sparkline(t, [3, 4, 2, 6], 3)],
];

/** Todos os textos que pode pintar un gráfico: cadeas das opcións e o que devolven os formatadores. */
function texts(node: unknown, out: string[] = []): string[] {
  if (typeof node === 'string') out.push(node);
  else if (typeof node === 'function') {
    for (const sample of [42, { value: 42, data: 42 }, [1, 2, 42]]) {
      const result: unknown = (node as (arg: unknown, index: number) => unknown)(sample, 0);
      if (typeof result === 'string') out.push(result);
    }
  } else if (Array.isArray(node)) node.forEach((item) => texts(item, out));
  else if (node && typeof node === 'object') Object.values(node).forEach((value) => texts(value, out));
  return out;
}

type Loose = Record<string, unknown> & { series: Record<string, unknown>[] };
const loose = (option: ChartOption) => option as unknown as Loose;

/** Un axuste de `whenNarrow`, tal como o le ECharts. */
interface Media {
  query?: { maxWidth: number };
  option: Record<string, { axisLabel: Record<string, unknown> }>;
}

describe('construtores de gráficos', () => {
  it.each(built)('%s: tooltip en richText e ningún texto con HTML nin style', (_name, option) => {
    expect((option.tooltip as { renderMode: string }).renderMode).toBe('richText');
    for (const text of texts(option)) {
      expect(text).not.toMatch(/[<>]/);
      expect(text).not.toMatch(/style/i);
    }
  });

  it.each(built)('%s: as cores saen dos tokens', (_name, option) => {
    const colors = new Set([
      t.primary,
      t.surface,
      t.ink,
      t.inkSoft,
      t.line,
      t.divider,
      t.success,
      t.danger,
      t.info,
      ...t.scale,
    ]);
    for (const text of texts(option).filter((value) => value.startsWith('#'))) expect(colors).toContain(text);
  });

  it('lineArea: etiqueta só no último valor con dato, co formato de quen chama', () => {
    const data = loose(built[0]![1]).series[0]!['data'] as { label?: { show: boolean; formatter: () => string } }[];
    expect(data.map((item) => item.label?.show ?? false)).toEqual([false, false, false, true]);
    expect(data[3]?.label?.formatter()).toBe('61 %');
    const valueFormatter = (built[0]![1].tooltip as { valueFormatter: (v: unknown) => string }).valueFormatter;
    expect(valueFormatter(52)).toBe('52 %');
    expect(valueFormatter('-')).toBe('-');
  });

  it('lineArea: o último período leva sempre rótulo no eixo X, en ancho e en estreito', () => {
    const categories = Array.from({ length: 31 }, (_, i) => `${i + 1} out.`);
    const option = lineArea(t, { categories, series: { name: 'x', values: categories.map(() => 1) } });
    const media = (option as { media: Media[] }).media;
    for (const unit of media) {
      const interval = unit.option['xAxis']!.axisLabel['interval'] as (i: number) => boolean;
      expect(interval(30)).toBe(true);
      expect(unit.option['xAxis']!.axisLabel['hideOverlap']).toBe(false);
      expect(categories.filter((_, i) => interval(i)).length).toBeLessThanOrEqual(unit.query ? 4 : 8);
    }
    expect(labelsFromLast(12, 8)).toBeTypeOf('function');
    expect([0, 1, 2, 3, 4].filter(labelsFromLast(5, 2))).toEqual([1, 4]);
    expect([0, 1, 2, 3, 4].filter(labelsFromLast(5, 8))).toEqual([0, 1, 2, 3, 4]);
  });

  it('lineArea: a etiqueta do último valor vai enriba se a liña sobe e debaixo á esquerda se baixa', () => {
    const label = (values: number[]) => {
      const data = loose(lineArea(t, { categories: values.map(String), series: { name: 'x', values } })).series[0]![
        'data'
      ] as { label?: { position: string } }[];
      return data.at(-1)?.label?.position;
    };
    expect(label([10, 20, 50])).toBe('top');
    expect(label([50, 51, 12])).toBe('left');
    expect(label([5])).toBe('top');
  });

  it('splitAtPartial: continua ata o punto anterior ao en curso e discontinua desde el, co punto de unión nas dúas', () => {
    expect(splitAtPartial([1, 2, 3, 4], 3)).toEqual({ solid: [1, 2, 3, null], dashed: [null, null, 3, 4] });
    // O en curso non é o último (tramos futuros con reservas xa feitas): discontinua ata o final.
    expect(splitAtPartial([1, 2, 3, 4], 1)).toEqual({ solid: [1, null, null, null], dashed: [1, 2, 3, 4] });
    // O primeiro punto xa está en curso: todo discontinuo.
    expect(splitAtPartial([1, 2], 0)).toEqual({ solid: [null, null], dashed: [1, 2] });
    // Sen punto en curso, ou sen datos desde el: todo continuo.
    expect(splitAtPartial([1, 2], undefined)).toEqual({ solid: [1, 2], dashed: null });
    expect(splitAtPartial([1, 2, null], 2)).toEqual({ solid: [1, 2, null], dashed: null });
  });

  it('lineArea en curso: o último tramo discontinuo, coa mesma cor e o mesmo nome; o resto igual', () => {
    const option = loose(built[6]![1]);
    const series = option.series as {
      name: string;
      color: string;
      lineStyle: { color: string; width: number; type?: number[] };
      areaStyle: { color: string; opacity: number };
      data: { value: number | string; label?: { show: boolean; formatter: () => string } }[];
    }[];
    expect(series).toHaveLength(2);
    const [solid, dashed] = series as [(typeof series)[0], (typeof series)[0]];
    expect(solid.lineStyle.type).toBeUndefined();
    expect(dashed.lineStyle.type).toEqual([...PARTIAL_DASH]);
    // Patrón de trazo, non cor nova: mesma cor, ancho, área e nome.
    expect([dashed.color, dashed.lineStyle.color, dashed.lineStyle.width]).toEqual([t.primary, t.primary, 2]);
    expect(dashed.areaStyle).toEqual(solid.areaStyle);
    expect(dashed.name).toBe(solid.name);
    expect(solid.data.map((d) => d.value)).toEqual([40, 52, 58, '-']);
    expect(dashed.data.map((d) => d.value)).toEqual(['-', '-', 58, 30]);
    // A etiqueta, só no último valor, di que está en curso (texto, non só estilo).
    expect(dashed.data[3]?.label?.formatter()).toBe('30 %\nen curso');
    expect(solid.data.some((d) => d.label?.show)).toBe(false);
  });

  it('lineArea en curso: o tooltip dá un só valor por categoría e «(en curso)» no punto en curso', () => {
    const formatter = (built[6]![1].tooltip as { formatter: (params: unknown) => string }).formatter;
    const param = (seriesIndex: number, dataIndex: number, value: number | string) => ({
      seriesIndex,
      dataIndex,
      value,
      name: months[dataIndex],
      seriesName: 'Reservas',
    });
    expect(formatter([param(0, 2, 58), param(1, 2, 58)])).toBe('mar\nReservas: 58 %');
    expect(formatter([param(0, 3, '-'), param(1, 3, 30)])).toBe('abr\nReservas: 30 % (en curso)');
    expect(formatter([param(0, 0, 40), param(1, 0, '-')])).toBe('xan\nReservas: 40 %');
  });

  it('lineArea: sen punto en curso, unha soa serie continua (os períodos pechados non cambian)', () => {
    const option = loose(built[0]![1]);
    expect(option.series).toHaveLength(1);
    expect((option.series[0]!['lineStyle'] as { type?: unknown }).type).toBeUndefined();
    expect((option['tooltip'] as { formatter?: unknown }).formatter).toBeUndefined();
  });

  it('sparkline en curso: o último tramo discontinuo, sen tooltip', () => {
    const option = loose(built[7]![1]);
    const series = option.series as { lineStyle: { color: string; type?: number[] } }[];
    expect(series.map((s) => s.lineStyle.type)).toEqual([undefined, [...PARTIAL_DASH]]);
    expect(series.map((s) => s.lineStyle.color)).toEqual([t.primary, t.primary]);
    expect((option['tooltip'] as { show: boolean }).show).toBe(false);
    expect(loose(built[1]![1]).series).toHaveLength(1);
  });

  it('sparkline: sen eixes nin tooltip', () => {
    const option = loose(built[1]![1]);
    expect((option['xAxis'] as { show: boolean }).show).toBe(false);
    expect((option['yAxis'] as { show: boolean }).show).toBe(false);
    expect((option['tooltip'] as { show: boolean }).show).toBe(false);
  });

  it('columns: a actual en primary e as pasadas no paso 2 da escala, con bordo primary', () => {
    const data = loose(built[3]![1]).series[0]!['data'] as { itemStyle: { color: string; borderColor?: string } }[];
    expect(data.at(-1)?.itemStyle.color).toBe(t.primary);
    expect(data[0]?.itemStyle).toEqual({ color: t.scale[1], borderColor: t.primary, borderWidth: 1 });
    expect(contrastRatio(t.primary, t.surface)).toBeGreaterThanOrEqual(3);
  });

  it('stackedBars: separador de 2 px na superficie e a trama de cada serie que a leva', () => {
    const series = loose(built[4]![1]).series as {
      stack: string;
      itemStyle: { borderColor: string; borderWidth: number; decal?: unknown; color: string };
    }[];
    expect(series.map((s) => s.stack)).toEqual(['total', 'total', 'total']);
    for (const s of series) expect([s.itemStyle.borderColor, s.itemStyle.borderWidth]).toEqual([t.surface, 2]);
    expect(series.map((s) => s.itemStyle.decal)).toEqual([undefined, alertDecal(t), patternDecal(t, 'dots')]);
    expect(series.map((s) => s.itemStyle.color)).toEqual([t.success, t.danger, t.info]);
  });

  it('patternDecal: tres tramas distintas (raias diagonais, puntos e raias horizontais) na cor da superficie', () => {
    const decals = (['stripes', 'dots', 'lines'] as const).map((p) => patternDecal(t, p));
    expect(new Set(decals.map((d) => JSON.stringify(d))).size).toBe(3);
    expect(decals.map((d) => d.symbol)).toEqual(['rect', 'circle', 'rect']);
    expect(decals[0]!.rotation).not.toBe(0);
    expect(decals[2]!.rotation ?? 0).toBe(0);
    for (const d of decals) expect(d.color).toBe('rgba(255, 255, 255, 0.6)');
  });

  it('stackedBars: un tramo estreito (1 de 53) ou 0 vai sen bordo propio, para que o separador non o coma', () => {
    const option = loose(
      stackedBars(t, {
        categories: ['a', 'b'],
        series: [
          { name: 'En prazo', tone: 'success', values: [50, 20] },
          { name: 'Concello', tone: 'info', values: [1, 0] },
          { name: 'Fóra', tone: 'danger', values: [2, 10] },
        ],
      }),
    );
    const data = option.series.map((s) => s['data'] as unknown[]);
    expect(data[0]).toEqual([50, 20]);
    expect(data[1]).toEqual([
      { value: 1, itemStyle: { borderWidth: 0 } },
      { value: 0, itemStyle: { borderWidth: 0 } },
    ]);
    // 2 de 53 é estreito (≤ 4 %); 10 non.
    expect(data[2]).toEqual([{ value: 2, itemStyle: { borderWidth: 0 } }, 10]);
  });

  it('stackedBars deitadas: as categorías van no eixe vertical', () => {
    const option = loose(stackedBars(t, { categories: ['A'], series: [], horizontal: true }));
    expect((option['yAxis'] as { type: string }).type).toBe('category');
    expect((option['xAxis'] as { type: string }).type).toBe('value');
  });

  it('heatmap: as celas pechadas quedan fóra da escala, sen recheo e con bordo discontinuo, e o tooltip dío', () => {
    const option = loose(built[5]![1]);
    const visualMap = option['visualMap'] as {
      min: number;
      max: number;
      inRange: { color: string[] };
      outOfRange: { color: string[] };
    };
    type Cell = number[] | { value: number[]; itemStyle: Record<string, unknown> };
    const data = option.series[0]!['data'] as Cell[];
    const at = (x: number, y: number) =>
      data.find((c) => {
        const v = Array.isArray(c) ? c : c.value;
        return v[0] === x && v[1] === y;
      });
    const closed = at(0, 1) as { value: number[]; itemStyle: Record<string, unknown> };
    expect(closed.value[2]).toBeLessThan(visualMap.min);
    expect(closed.itemStyle).toEqual({ borderColor: t.line, borderWidth: 1, borderType: 'dashed' });
    expect(contrastRatio(t.line, t.surface)).toBeGreaterThanOrEqual(3);
    expect(visualMap.outOfRange.color).toEqual([t.surface]);
    expect((visualMap as unknown as { unboundedRange: boolean }).unboundedRange).toBe(false);
    expect(visualMap.inRange.color).toEqual([...t.scale]);
    expect(visualMap.max).toBe(80);
    const formatter = (option['tooltip'] as { formatter: (p: unknown) => string }).formatter;
    expect(formatter({ value: closed.value })).toBe('martes · 9 · Pechado');
    expect(formatter([{ value: [1, 0, 80] }])).toBe('luns · 10 · 80 %');
    expect(formatter({ value: 3 })).toBe(' ·  · -');
  });

  it('heatmap: unha cela pechada non se pinta coma un 0 % (outra cor e bordo propio)', () => {
    const option = loose(built[5]![1]);
    const data = option.series[0]!['data'] as unknown[];
    // A cela 0 % (1, 1) é un número máis da escala, co primeiro paso; a pechada (0, 1), un obxecto co seu bordo.
    expect(data).toContainEqual([1, 1, 0]);
    expect(data).not.toContainEqual([0, 1, -1]);
    const visualMap = option['visualMap'] as { inRange: { color: string[] }; outOfRange: { color: string[] } };
    expect(visualMap.outOfRange.color[0]).not.toBe(visualMap.inRange.color[0]);
  });

  it('heatmap: o tope nunca queda por debaixo dun valor (non sairía coma pechada)', () => {
    const option = loose(
      heatmap(t, {
        columns: ['9'],
        rows: ['luns'],
        cells: [{ x: 0, y: 0, value: 130 }],
        closedLabel: 'Pechado',
        max: 100,
      }),
    );
    expect((option['visualMap'] as { max: number }).max).toBe(130);
  });

  it('estreito (≤ NARROW_WIDTH px de gráfico): hBars só cos extremos do eixo e columnas con todos os meses', () => {
    const media = (option: ChartOption) => (option as { media: Media[] }).media;
    const bars = media(
      hBars(t, { categories: ['a'], series: { name: 'x', values: [50] }, format: (v) => `${v} %`, max: 100 }),
    );
    expect(bars[0]!.query).toEqual({ maxWidth: NARROW_WIDTH });
    const ends = bars[0]!.option['xAxis']!.axisLabel['formatter'] as (v: number) => string;
    expect([0, 25, 50, 75, 100].map(ends)).toEqual(['0 %', '', '', '', '100 %']);
    expect((bars[1]!.option['xAxis']!.axisLabel['formatter'] as (v: number) => string)(25)).toBe('25 %');
    // Sen tope, as marcas son as de sempre (agóchanse as que se pisan).
    expect(media(hBars(t, { categories: ['a'], series: { name: 'x', values: [5] } }))).toBeUndefined();

    const months = (n: number) => Array.from({ length: n }, (_, i) => `m${i}`);
    const six = media(columns(t, { categories: months(6), series: { name: 'x', values: months(6).map(() => 1) } }));
    expect(six[0]!.option['xAxis']!.axisLabel).toEqual({
      interval: 0,
      hideOverlap: false,
      rotate: 0,
      fontSize: t.fontSize - 2,
    });
    expect(six[1]!.option['xAxis']!.axisLabel).toMatchObject({ interval: 'auto', hideOverlap: true, rotate: 0 });
    const twelve = media(
      columns(t, { categories: months(12), series: { name: 'x', values: months(12).map(() => 1) } }),
    );
    expect(twelve[0]!.option['xAxis']!.axisLabel['rotate']).toBe(90);
  });

  it('whenNarrow suma os axustes de varias chamadas', () => {
    const option = whenNarrow(
      whenNarrow({}, { xAxis: { a: 1 } }, { xAxis: { a: 2 } }),
      { yAxis: { b: 1 } },
      { yAxis: { b: 2 } },
    );
    expect((option as { media: unknown }).media).toEqual([
      { query: { maxWidth: NARROW_WIDTH }, option: { xAxis: { a: 1 }, yAxis: { b: 1 } } },
      { option: { xAxis: { a: 2 }, yAxis: { b: 2 } } },
    ]);
  });

  it('o eixo de valores agocha as marcas que se pisan', () => {
    const axis = lineArea(t, { categories: ['a'], series: { name: 'x', values: [1] } }).yAxis as {
      axisLabel: { hideOverlap: boolean };
    };
    expect(axis.axisLabel.hideOverlap).toBe(true);
  });

  it('readChartTokens: le as variables de <html> e cae no tema por defecto se faltan', () => {
    const element = document.createElement('div');
    document.body.append(element);
    expect(readChartTokens(element).primary).toBe(t.primary);
    document.documentElement.style.setProperty('--f-primary', '#7a1f2b');
    const tokens = readChartTokens(element);
    expect(tokens.primary).toBe('#7a1f2b');
    expect(tokens.scale.at(-1)).toBe('#7a1f2b');
    expect(tokens.fontSize).toBeGreaterThanOrEqual(14);
    document.documentElement.style.removeProperty('--f-primary');
    element.remove();
  });
});
