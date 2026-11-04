// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { emptyPanel, inProgressPanel, naronPanel, translator } from '../../../testing/indicators';
import { patternDecal } from '../../shared/ui/chart/chart-composite';
import { chartTokensFrom } from '../../shared/ui/chart/chart-tokens';
import { contrastRatio } from '../../core/theme/contrast';
import { DEFAULT_THEME, deriveTokens } from '../../core/theme/theme-tokens';
import { IndicatorFormats } from './indicator-format';
import { countChange, kpiCsvHeader, kpiViews } from './indicator-kpis';
import { csvName, panelCsv, periodTitle } from './indicator-period';
import type { ChartView } from './indicator-views';
import { partialIndex } from './indicator-buckets';
import { chartViews } from './indicator-views';

const nb = (text: string) => text.replaceAll(' ', ' ');
const tokens = chartTokensFrom(deriveTokens(DEFAULT_THEME));
const gl = () => chartViews(naronPanel(), translator('gl'), new IndicatorFormats('gl'));
const view = (views: ChartView[], id: string) => views.find((v) => v.id === id)!;

describe('Do panel ás tarxetas (D3-08)', () => {
  it('seis tarxetas na orde da maqueta, cada unha coa súa táboa', () => {
    const views = gl();
    expect(views.map((v) => [v.id, v.title])).toEqual([
      ['occupancy', 'Ocupación por instalación'],
      ['daily', 'Ocupación semana a semana'],
      ['peak', 'Horas punta'],
      ['cancellations', 'Cancelacións por motivo'],
      ['revenue', 'Ingresos por instalación'],
      ['people', 'Persoas distintas que reservan'],
    ]);
    for (const v of views) {
      expect(v.rows.length).toBeGreaterThan(0);
      expect(v.rows.every((row) => row.length === v.columns.length)).toBe(true);
      expect(v.csv[0]?.length).toBe(v.columns.length);
    }
    const occupancy = view(views, 'occupancy');
    expect(occupancy.rows.map((r) => [r[0], nb(r[1]!)])[0]).toEqual(['Pista de pádel 2 de O Alto', '71 %']);
    expect(occupancy.csv[1]).toEqual(['Pista de pádel 2 de O Alto', 71]);
  });

  it('mapa de calor: só as horas nas que abre algún día; as celas pechadas, en gris e «pechado» na táboa', () => {
    const panel = naronPanel();
    // O domingo non abre ás 8.
    const closed = panel.peakHours.find((c) => c.isoWeekday === 7 && c.hour === 8)!;
    closed.openMinutes = 0;
    closed.reservedMinutes = 0;
    const peak = view(chartViews(panel, translator('gl'), new IndicatorFormats('gl')), 'peak');
    // A táboa nomea a hora enteira: «8–9 h».
    expect(peak.columns.map((c) => nb(c.label))).toEqual([
      'Día',
      ...Array.from({ length: 15 }, (_, i) => `${i + 8}–${i + 9} h`),
    ]);
    expect(peak.rows.map((r) => r[0])).toEqual(['Luns', 'Martes', 'Mércores', 'Xoves', 'Venres', 'Sábado', 'Domingo']);
    expect(peak.rows[6]?.[1]).toBe('pechado');
    expect(nb(peak.rows[1]![12]!)).toBe('85 %');
    const option = peak.spec(tokens);
    type Cell = number[] | { value: number[]; itemStyle: { borderType: string } };
    const series = (option.series as { data: Cell[] }[])[0]!;
    const value = (c: Cell) => (Array.isArray(c) ? c : c.value);
    // Pechado = -1, fóra do rango do visualMap (sen recheo), co seu bordo discontinuo.
    const sunday8 = series.data.find((c) => value(c)[0] === 0 && value(c)[1] === 6) as Exclude<Cell, number[]>;
    expect(sunday8.value[2]).toBe(-1);
    expect(sunday8.itemStyle.borderType).toBe('dashed');
    expect((option.visualMap as { outOfRange: { color: string[] } }).outOfRange.color).toEqual([tokens.surface]);
  });

  it('cores dos estados na lenda e no gráfico; cada serie coa súa trama, a mesma na lenda', () => {
    const views = gl();
    expect(view(views, 'cancellations').legend).toEqual([
      { label: 'Persoa, en prazo', tone: 'success', pattern: undefined },
      { label: 'Persoa, fóra de prazo', tone: 'danger', pattern: 'stripes' },
      { label: 'Concello', tone: 'info', pattern: 'dots' },
    ]);
    expect(view(views, 'revenue').legend.map((l) => [l.tone, l.pattern])).toEqual([
      ['success', undefined],
      ['warn', 'dots'],
      ['danger', 'stripes'],
      ['muted', 'lines'],
    ]);
    const series = view(views, 'revenue').spec(tokens).series as { itemStyle: { color: string; decal?: unknown } }[];
    expect(series.map((s) => s.itemStyle.color)).toEqual([tokens.success, tokens.warn, tokens.danger, tokens.muted]);
    // Pendente e devolto levan trama propia, distinta da de alerta.
    expect(series.map((s) => s.itemStyle.decal)).toEqual([
      undefined,
      patternDecal(tokens, 'dots'),
      patternDecal(tokens, 'stripes'),
      patternDecal(tokens, 'lines'),
    ]);
    const people = view(views, 'people').spec(tokens).series as { data: { itemStyle: { color: string } }[] }[];
    expect(people[0]!.data.map((d) => d.itemStyle.color)).toEqual([...Array(5).fill(tokens.scale[1]), tokens.primary]);
  });

  it('ningunha parella de series apiladas vai só polo ton: trama distinta ou 3:1, en claro e en escuro', () => {
    const dark = chartTokensFrom(
      deriveTokens({ ...DEFAULT_THEME, primary: '#4d93d6', background: '#12161b', text: '#eef1f4' }),
    );
    for (const k of [tokens, dark]) {
      for (const id of ['cancellations', 'revenue'] as const) {
        const series = view(gl(), id).spec(k).series as {
          name: string;
          itemStyle: { color: string; decal?: unknown };
        }[];
        for (const [i, a] of series.entries()) {
          for (const b of series.slice(i + 1)) {
            const pattern = JSON.stringify(a.itemStyle.decal) !== JSON.stringify(b.itemStyle.decal);
            const contrast = contrastRatio(a.itemStyle.color, b.itemStyle.color) >= 3;
            expect(pattern || contrast, `${id}: ${a.name} / ${b.name}`).toBe(true);
          }
        }
      }
    }
  });

  it('cambio dunha conta: sen período anterior, nada; anterior 0, a diferenza; os dous 0, igual', () => {
    const f = new IndicatorFormats('gl');
    const n = (v: number) => f.integer(v);
    expect(countChange(205, 0, false, n, f)).toEqual({ change: '', trend: null });
    expect(countChange(205, 0, true, n, f)).toEqual({ change: '+205', trend: 'up' });
    expect(countChange(0, 0, true, n, f)).toEqual({ change: '0\u00a0%', trend: 'same' });
    expect(countChange(110, 100, true, n, f)).toEqual({ change: '+10\u00a0%', trend: 'up' });
  });

  it('cinco cifras: cambio en % e en puntos, con que se compara e seis valores na minigráfica', () => {
    const kpis = kpiViews(naronPanel(), translator('gl'), new IndicatorFormats('gl'));
    expect(kpis.map((k) => [k.label, nb(k.value), nb(k.change), k.trend])).toEqual([
      ['Reservas', '412', '+8 %', 'up'],
      ['Ocupación media', '54 %', '+5 p.p.', 'up'],
      ['Horas reservadas', '618 h', '+9 %', 'up'],
      ['Persoas distintas', '187', '+21 %', 'up'],
      ['Cobrado', '2.946 €', '+6 %', 'up'],
    ]);
    expect(kpis[0]?.comparison).toBe('respecto a setembro');
    const spark = kpis[0]!.spark(tokens).series as { data: unknown[] }[];
    expect(spark[0]!.data).toHaveLength(6);
    const empty = kpiViews(emptyPanel(), translator('es'), new IndicatorFormats('es'));
    expect(empty.map((k) => k.trend)).toEqual([null, null, null, null, null]);
    expect(empty[1]?.value).toBe('—');
  });

  it('período, nome dos CSV e CSV do panel por bloques', () => {
    const panel = naronPanel();
    const t = translator('gl');
    const f = new IndicatorFormats('gl');
    expect(periodTitle(panel.period, t, f)).toBe('Outubro de 2026');
    expect(periodTitle({ ...panel.period, kind: 'QUARTER', from: '2026-10-01', to: '2026-12-31' }, t, f)).toBe(
      '4.º trimestre de 2026',
    );
    const views = gl();
    expect(csvName('naron', panel.period)).toBe('indicadores-naron-2026-10.csv');
    expect(csvName('naron', panel.period, view(views, 'peak'))).toBe('indicadores-naron-2026-10-horas-punta.csv');
    const rows = panelCsv(['Concello de Narón'], kpiViews(panel, t, f), views, t);
    expect(rows.slice(0, 5)).toEqual([
      ['Concello de Narón'],
      [],
      ['Cifras principais'],
      ['Cifra', 'Valor', 'Período anterior'],
      ['Reservas', 412, 381],
    ]);
    expect(rows.filter((r) => r.length === 0)).toHaveLength(7);
  });

  describe('período en curso (ADR-0026, revisión do 2026-10-29)', () => {
    const t = translator('gl');
    const f = new IndicatorFormats('gl');

    it('as cifras: o valor é o período enteiro e a variación compara ata hoxe co mesmo tramo do anterior', () => {
      const kpis = kpiViews(inProgressPanel(), t, f);
      expect(kpis.map((k) => [nb(k.value), nb(k.change), k.trend])).toEqual([
        ['412', '+10 %', 'up'],
        ['54 %', '+2 p.p.', 'up'],
        ['618 h', '+11 %', 'up'],
        ['187', '+20 %', 'up'],
        ['2.946 €', '+5 %', 'up'],
      ]);
      expect(kpis[0]?.comparison).toBe('ata hoxe, fronte ao mesmo tramo de setembro');
      const es = kpiViews(inProgressPanel(), translator('es'), new IndicatorFormats('es'));
      expect(es[0]?.comparison).toBe('hasta hoy, frente al mismo tramo de septiembre');
      // A minigráfica leva o último tramo discontinuo.
      const spark = kpis[0]!.spark(tokens).series as { lineStyle: { type?: unknown } }[];
      expect(spark).toHaveLength(2);
      expect(spark[1]!.lineStyle.type).toBeDefined();
    });

    it('sen nada no mesmo tramo do anterior (sen horas abertas), «sen datos do período anterior»', () => {
      const panel = inProgressPanel();
      const closed = { reservedMinutes: 0, openMinutes: 0 };
      const none = {
        ...panel,
        kpis: { ...panel.kpis, occupancy: { ...panel.kpis.occupancy, previousToDate: closed } },
      };
      expect(kpiViews(none, t, f).map((k) => k.trend)).toEqual([null, null, null, null, null]);
    });

    it('cada semana do mes ten a súa comparación: a semana en curso fronte á anterior', () => {
      const panel = inProgressPanel();
      const week = {
        ...panel,
        period: { ...panel.period, kind: 'WEEK' as const },
        previous: { from: '2026-10-19', to: '2026-10-25' },
      };
      expect(kpiViews(week, t, f)[0]?.comparison).toBe('ata hoxe, fronte ao mesmo tramo da semana anterior');
    });

    it('CSV do panel: valor, ata hoxe e o mesmo tramo do anterior, cos dous tramos en texto', () => {
      const panel = inProgressPanel();
      const rows = panelCsv(
        ['Concello de Narón'],
        kpiViews(panel, t, f),
        chartViews(panel, t, f),
        t,
        kpiCsvHeader(panel, t, f),
      );
      expect(rows.slice(3, 6)).toEqual([
        [
          'Cifra',
          'Valor (en curso)',
          'Ata hoxe (01/10/2026 – 29/10/2026)',
          'Mesmo tramo anterior (01/09/2026 – 29/09/2026)',
        ],
        ['Reservas', 412, 398, 362],
        ['Ocupación media (%)', 54, 52, 50],
      ]);
      // Un período pechado segue igual.
      expect(kpiCsvHeader(naronPanel(), t, f)).toEqual(['Cifra', 'Valor', 'Período anterior']);
    });

    it('táboas e CSV marcan o tramo en curso con texto; a liña día a día, discontinua desde o anterior', () => {
      const views = chartViews(inProgressPanel(), t, f);
      expect(view(views, 'daily').rows.at(-1)?.[0]).toBe('26/10/2026 – 31/10/2026 (en curso)');
      expect(view(views, 'daily').csv.at(-1)?.[0]).toBe('26/10/2026 – 31/10/2026 (en curso)');
      expect(view(views, 'cancellations').rows.at(-1)?.[0]).toBe('26/10/2026 – 31/10/2026 (en curso)');
      expect(view(views, 'people').rows.at(-1)?.[0]).toBe('Outubro de 2026 (en curso)');
      expect(view(views, 'people').csv.at(-1)?.[0]).toBe('Outubro de 2026 (en curso)');
      const daily = view(views, 'daily').spec(tokens).series as { lineStyle: { type?: unknown } }[];
      expect(daily).toHaveLength(2);
      // Os pechados non cambian: ningún «en curso» e unha soa liña continua.
      const closed = gl();
      for (const v of closed) expect(JSON.stringify([v.rows, v.csv])).not.toContain('en curso');
      expect(view(closed, 'daily').spec(tokens).series).toHaveLength(1);
    });

    it('partialIndex: o primeiro punto en curso, ou ningún', () => {
      expect(partialIndex([{ partial: false }, { partial: true }])).toBe(1);
      expect(partialIndex([{ partial: false }])).toBeUndefined();
    });
  });
});
