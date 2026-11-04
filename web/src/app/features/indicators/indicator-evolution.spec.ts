// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { provideLocationMocks } from '@angular/common/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { LanguageService } from '../../core/i18n/language.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { DEFAULT_THEME, deriveTokens } from '../../core/theme/theme-tokens';
import { chartTokensFrom } from '../../shared/ui/chart/chart-tokens';
import type { ChartEngine, ChartInstance } from '../../shared/ui/chart/chart-engine';
import { CHART_ENGINE } from '../../shared/ui/chart/chart-engine';
import { backend, expectRequest, fakeSession, provideTestApi } from '../../../testing/api';
import { expectNoAxeViolations } from '../../../testing/axe';
import { municipality } from '../../../testing/fixtures';
import { filterOptions, inProgressPanel, naronPanel, translator } from '../../../testing/indicators';
import { BASE, useMonday } from '../../../testing/manage-bookings';
import { evolutionView } from './indicator-evolution';
import { IndicatorFormats } from './indicator-format';
import { kpiViews } from './indicator-kpis';
import { IndicatorsPage } from './indicators.page';

/** Os espazos que non parten, coma espazos. */
const plain = (value: string | null | undefined) => (value ?? '').replaceAll('\u00a0', ' ');
const text = (e: Element | null | undefined) => (e?.textContent ?? '').replaceAll(' ', ' ').replace(/\s+/g, ' ').trim();

/** Motor falso: cada gráfico debúxase ao momento e a súa imaxe di con que superficie se fixo. */
const engine: ChartEngine = {
  init: (): ChartInstance => ({
    setOption: () => undefined,
    resize: () => undefined,
    dispose: () => undefined,
    getDataURL: ({ backgroundColor }) => `data:image/png;base64,${btoa(backgroundColor)}`,
  }),
};

async function render() {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(fakeSession()),
      provideRouter([{ path: 'naron/xestion/indicadores', component: IndicatorsPage }]),
      provideLocationMocks(),
      { provide: CHART_ENGINE, useValue: () => Promise.resolve(engine) },
    ],
  });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const harness = await RouterTestingHarness.create('/naron/xestion/indicadores');
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  (await expectRequest((r) => r.url === `${BASE}/indicators/filters`)).flush(filterOptions());
  (await expectRequest((r) => r.url === `${BASE}/indicators`)).flush(naronPanel());
  await vi.waitFor(async () => {
    await settle();
    expect(el.querySelector('furar-stat-tile')).not.toBeNull();
  });
  return { el, settle };
}

const tile = (el: Element, id = 'bookings') =>
  el.querySelector<HTMLButtonElement>(`[data-kpi="${id}"] .f-stat-tile__button`)!;
const modal = () => document.querySelector('furar-indicator-evolution');

async function open(el: Element, settle: () => Promise<void>, id = 'bookings') {
  tile(el, id).focus();
  tile(el, id).click();
  await vi.waitFor(async () => {
    await settle();
    expect(modal()).not.toBeNull();
  });
}

async function closed(settle: () => Promise<void>) {
  await vi.waitFor(async () => {
    await settle();
    expect(modal()).toBeNull();
  });
}

function escape() {
  document
    .querySelector('.f-dialog-container')!
    .dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', keyCode: 27, bubbles: true }));
}

/** `matchMedia` con `prefers-reduced-motion` a `reduce` ou non. */
function motion(reduce: boolean) {
  window.matchMedia = ((query: string) =>
    ({ matches: reduce && query.includes('reduce'), media: query }) as MediaQueryList) as typeof window.matchMedia;
}

/** Unha `startViewTransition` que fai o cambio ao momento e lembra o nome que levaba a orixe. */
function fakeTransition() {
  const names: string[] = [];
  const start = vi.fn((update: () => Promise<void>) => {
    names.push(
      ...[...document.querySelectorAll<HTMLElement>('*')]
        .map((e) => e.style.getPropertyValue('view-transition-name'))
        .filter(Boolean),
    );
    const done = update();
    return { updateCallbackDone: done, finished: done };
  });
  (document as unknown as { startViewTransition: unknown }).startViewTransition = start;
  return { start, names };
}

describe('A evolución dunha cifra (D3-09)', () => {
  useMonday();

  const matchMedia = window.matchMedia;

  afterEach(() => {
    window.matchMedia = matchMedia;
    delete (document as unknown as { startViewTransition?: unknown }).startViewTransition;
    vi.restoreAllMocks();
    document.documentElement.removeAttribute('data-print');
  });

  it('sen startViewTransition abre igual: título enfocado, cifra, cambio en texto, táboa; Esc pecha e o foco volve; axe', async () => {
    const { el, settle } = await render();
    await open(el, settle);
    const dialog = modal()!;
    const title = dialog.querySelector('h2')!;
    expect(text(title)).toBe('Reservas por meses');
    await vi.waitFor(() => expect(document.activeElement).toBe(title));
    expect(document.querySelector('[role="dialog"]')!.getAttribute('aria-labelledby')).toContain(title.id);
    expect(text(dialog.querySelector('.f-evolution__value'))).toBe('412');
    expect(text(dialog.querySelector('.f-evolution__change'))).toBe('↑ +8 % respecto a setembro');
    expect(dialog.querySelector('.f-stat-tile__delta')!.getAttribute('data-trend')).toBe('up');
    const figure = dialog.querySelector('figure[role="img"]')!;
    expect(plain(figure.getAttribute('aria-label'))).toBe(
      'Reservas por meses. 412 neste período, +8 % respecto a setembro.',
    );
    const rows = [...dialog.querySelectorAll('tbody tr')].map((r) => [...r.children].map((c) => text(c)).join(' | '));
    expect(rows).toHaveLength(12);
    expect(rows[0]).toBe('Novembro de 2025 | 247');
    expect(rows.at(-1)).toBe('Outubro de 2026 | 412');

    const toggle = dialog.querySelector<HTMLButtonElement>('.f-evolution__toggle')!;
    expect(toggle.getAttribute('aria-pressed')).toBe('false');
    toggle.click();
    await settle();
    expect(toggle.getAttribute('aria-pressed')).toBe('true');
    expect(dialog.querySelector<HTMLElement>('.f-evolution__chart')!.hidden).toBe(true);
    expect(dialog.querySelector<HTMLElement>(`#${toggle.getAttribute('aria-controls')}`)!.hidden).toBe(false);
    await expectNoAxeViolations(document.querySelector('.cdk-overlay-container')!);

    escape();
    await closed(settle);
    expect(document.activeElement).toBe(tile(el));
  });

  it('picar fóra pecha e o foco volve á tarxeta', async () => {
    const { el, settle } = await render();
    await open(el, settle, 'occupancy');
    expect(text(modal()!.querySelector('h2'))).toBe('Ocupación media por meses');
    document.querySelector<HTMLElement>('.f-dialog-backdrop')!.click();
    await closed(settle);
    expect(document.activeElement).toBe(tile(el, 'occupancy'));
  });

  it('coa API e sen movemento reducido: transición á ida e á volta desde a tarxeta', async () => {
    motion(false);
    const { start, names } = fakeTransition();
    const { el, settle } = await render();
    await open(el, settle);
    expect(start).toHaveBeenCalledTimes(1);
    expect(names[0]).toMatch(/^furar-dialog-\d+$/);
    modal()!.querySelector<HTMLButtonElement>('.f-dialog__close')!.click();
    await closed(settle);
    expect(start).toHaveBeenCalledTimes(2);
    expect(names[1]).toBe(names[0]);
    // Ao rematar non queda ningún nome posto.
    expect(tile(el).style.getPropertyValue('view-transition-name')).toBe('');
  });

  it('con prefers-reduced-motion: reduce abre e pecha sen transición', async () => {
    motion(true);
    const { start } = fakeTransition();
    const { el, settle } = await render();
    await open(el, settle);
    escape();
    await closed(settle);
    expect(start).not.toHaveBeenCalled();
  });

  it('PDF do modal: alcance dialog, imaxe en claro do gráfico co alt, cabeceira con hora; afterprint restaura', async () => {
    const { el, settle } = await render();
    await open(el, settle);
    vi.setSystemTime(new Date('2026-10-26T12:05:00Z'));
    const seen: { scope: string | null; alts: string[]; header: string; canvasHidden: boolean }[] = [];
    vi.spyOn(window, 'print').mockImplementation(() => {
      const dialog = modal()!;
      seen.push({
        scope: document.documentElement.getAttribute('data-print'),
        alts: [...dialog.querySelectorAll<HTMLImageElement>('img[data-print-image]')].map((i) => plain(i.alt)),
        header: text(dialog.querySelector('.f-print-header')),
        canvasHidden: dialog.querySelector<HTMLElement>('.f-chart__canvas')!.hidden === true,
      });
    });
    modal()!.querySelector<HTMLButtonElement>('.f-export-menu__trigger')!.click();
    await settle();
    document.querySelector<HTMLButtonElement>('[data-format="pdf"]')!.click();
    await vi.waitFor(() => expect(seen).toHaveLength(1));
    expect(seen[0]).toEqual({
      scope: 'dialog',
      alts: ['Reservas por meses. 412 neste período, +8 % respecto a setembro.'],
      header:
        'Concello de Narón · Outubro de 2026 · Todas as instalacións · Todos os usos · xerado o 26/10/2026, 13:05',
      // O canvas segue na pantalla; a folla de impresión cambia a imaxe por el só no papel.
      canvasHidden: false,
    });
    window.dispatchEvent(new Event('afterprint'));
    expect(modal()!.querySelector('img[data-print-image]')).toBeNull();
    expect(modal()!.querySelector<HTMLElement>('.f-chart__canvas')!.hidden).toBe(false);
    expect(document.documentElement.hasAttribute('data-print')).toBe(false);
  });

  it('PDF do panel e dunha tarxeta: o alcance e só os gráficos del como imaxes', async () => {
    const { el, settle } = await render();
    const seen: { scope: string | null; images: number; target: string | null; alt?: string }[] = [];
    vi.spyOn(window, 'print').mockImplementation(() => {
      const images = [...document.querySelectorAll<HTMLImageElement>('img[data-print-image]')];
      seen.push({
        scope: document.documentElement.getAttribute('data-print'),
        images: images.length,
        target: document.querySelector('[data-print-target]')?.getAttribute('data-chart') ?? null,
        alt: plain(images.find((i) => i.alt)?.alt),
      });
      window.dispatchEvent(new Event('afterprint'));
    });
    const pdf = async (scope: Element) => {
      scope.querySelector<HTMLButtonElement>('.f-export-menu__trigger')!.click();
      await settle();
      document.querySelector<HTMLButtonElement>('[data-format="pdf"]')!.click();
      await settle();
    };
    await vi.waitFor(() => expect(el.querySelectorAll('.f-chart__canvas')).toHaveLength(11));
    await pdf(el.querySelector('furar-indicator-filters')!);
    await pdf(el.querySelector('[data-chart="occupancy"]')!);
    expect(seen).toEqual([
      // As seis gráficas e as cinco minigráficas (estas, decorativas: alt baleiro).
      { scope: 'panel', images: 11, target: null, alt: expect.stringMatching(/^Ocupación por instalación\. /) },
      {
        scope: 'card',
        images: 1,
        target: 'occupancy',
        alt: 'Ocupación por instalación. Pista de pádel 2 de O Alto é a máis chea: 71 % das súas horas abertas.',
      },
    ]);
    expect(document.querySelector('img[data-print-image]')).toBeNull();
  });

  it('CSV do modal: nome e contido; cabeceira impresa en castelán', async () => {
    const { el, settle } = await render();
    await TestBed.inject(LanguageService).use('es');
    await settle();
    const files: { name: string; blob: Blob }[] = [];
    let last: Blob | undefined;
    URL.createObjectURL = vi.fn((blob: Blob) => {
      last = blob;
      return 'blob:x';
    });
    URL.revokeObjectURL = vi.fn();
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(function (this: HTMLAnchorElement) {
      files.push({ name: this.download, blob: last! });
    });
    await open(el, settle, 'paidCents');
    expect(text(modal()!.querySelector('h2'))).toBe('Cobrado por meses');
    modal()!.querySelector<HTMLButtonElement>('.f-export-menu__trigger')!.click();
    await settle();
    document.querySelector<HTMLButtonElement>('[data-format="csv"]')!.click();
    await settle();
    expect(files.map((f) => f.name)).toEqual(['indicadores-naron-2026-10-evolucion-cobrado.csv']);
    const lines = (await files[0]!.blob.text()).replace(/^\uFEFF/, '').split('\r\n');
    expect(lines.slice(0, 4)).toEqual([
      'Concello de Narón · Octubre de 2026 · Todas las instalaciones · Todos los usos · generado el 26/10/2026, 11:00',
      '',
      'Periodo;Cobrado (€)',
      'Noviembre de 2025;1767,6',
    ]);
    expect(lines.at(-2)).toBe('Octubre de 2026;2946');
    backend().verify();
  });
});

describe('evolutionView', () => {
  const f = new IndicatorFormats('gl');
  const t = translator('gl');

  it('trimestres e anos: rótulos curtos no eixo e longos na táboa', () => {
    const panel = naronPanel();
    const quarters = ['2025-01-01', '2025-04-01', '2025-07-01', '2025-10-01'];
    const quarterly = {
      ...panel,
      period: { ...panel.period, kind: 'QUARTER' as const, evolutionUnit: 'QUARTER' as const },
      kpis: {
        ...panel.kpis,
        bookings: {
          ...panel.kpis.bookings,
          evolution: quarters.map((from, i) => ({ from, to: from, value: i, partial: false })),
        },
      },
    };
    const kpi = kpiViews(quarterly, t, f)[0]!;
    const view = evolutionView(quarterly, kpi, t, f);
    expect(view.title).toBe('Reservas por trimestres');
    expect(view.rows.map((r) => r[0])).toEqual([
      '1.º trimestre de 2025',
      '2.º trimestre de 2025',
      '3.º trimestre de 2025',
      '4.º trimestre de 2025',
    ]);
    const axis = view.spec(chartTokensFrom(deriveTokens(DEFAULT_THEME))).xAxis as { data: string[] };
    expect(axis.data).toEqual(['T1 25', 'T2 25', 'T3 25', 'T4 25']);
    expect(view.csv[0]).toEqual(['Período', 'Reservas']);
  });

  it('meses: o ano curto no primeiro e en xaneiro', () => {
    const panel = naronPanel();
    const view = evolutionView(panel, kpiViews(panel, t, f)[0]!, t, f);
    const axis = view.spec(chartTokensFrom(deriveTokens(DEFAULT_THEME))).xAxis as { data: string[] };
    expect(axis.data.slice(0, 3)).toEqual(['nov. 25', 'dec.', 'xan. 26']);
    expect(view.file).toBe('evolucion-reservas');
  });

  it('período en curso: o último período di «(en curso)» na táboa e no CSV, e a liña vai discontinua ata el', () => {
    const panel = inProgressPanel();
    const kpi = kpiViews(panel, t, f)[0]!;
    const view = evolutionView(panel, kpi, t, f);
    expect(view.rows.at(-1)).toEqual(['Outubro de 2026 (en curso)', '412']);
    expect(view.rows.at(-2)?.[0]).toBe('Setembro de 2026');
    expect(view.csv.at(-1)).toEqual(['Outubro de 2026 (en curso)', 412]);
    expect(view.name.replaceAll('\u00a0', ' ')).toBe(
      'Reservas por meses. 412 neste período, +10 % ata hoxe, fronte ao mesmo tramo de setembro.',
    );
    const option = view.spec(chartTokensFrom(deriveTokens(DEFAULT_THEME)));
    const series = option.series as {
      lineStyle: { type?: unknown };
      data: { label?: { formatter: () => string } }[];
    }[];
    expect(series).toHaveLength(2);
    expect(series[1]!.lineStyle.type).toBeDefined();
    expect(series[1]!.data.at(-1)?.label?.formatter()).toBe('412\nen curso');
    const tooltip = option.tooltip as { formatter: (p: unknown) => string };
    expect(tooltip.formatter([{ dataIndex: 11, value: 412, name: 'out.', seriesName: 'Reservas' }])).toBe(
      'out.\nReservas: 412 (en curso)',
    );
  });
});
