// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  effect,
  inject,
  input,
  numberAttribute,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { ThemeChanges } from '../../../core/theme/theme-changes';
import type { ChartInstance } from './chart-engine';
import { CHART_ENGINE } from './chart-engine';
import type { ChartSpec } from './chart-options';
import type { ChartTokens } from './chart-tokens';
import { lightChartTokens, readChartTokens } from './chart-tokens';

/** Os gráficos vivos polo seu elemento, para cambialos por imaxes ao imprimir (chartImage). */
const CHARTS = new WeakMap<Element, ChartComponent>();

/** Como sae a imaxe: co tema que se ve ou en claro (papel). */
export type ChartImageTheme = 'current' | 'light';

/** A imaxe do `furar-chart` que é `element` (ver `ChartComponent.image`); `null` se non o é ou non se debuxou. */
export function chartImage(element: Element, theme: ChartImageTheme = 'current'): string | null {
  return CHARTS.get(element)?.image(theme) ?? null;
}

/** Alto por defecto de cada tamaño (chart.css); `height` en rem cámbiao por CSSOM. */
export type ChartSize = 'spark' | 'sm' | 'md' | 'lg';

/**
 * O único sitio con gráficos (docs/deseno.md §8, ADR-0026): envoltorio de Apache ECharts.
 *
 * - ECharts cárgase co primeiro gráfico (`import()` diferido, CHART_ENGINE), despois das fontes
 *   (`document.fonts.ready`: o canvas mide o texto coa letra que haxa ao debuxar).
 * - Debuxa en canvas coas cores dos tokens `--f-*` lidas das variables CSS; redebuxa se cambia
 *   o tema (estilo ou clase de `<html>`, co observador compartido `ThemeChanges`) e axusta o
 *   tamaño co `ResizeObserver`.
 * - Sen animación con `prefers-reduced-motion: reduce`.
 * - O canvas é `aria-hidden`: o nome e a alternativa (táboa) danos quen o usa (furar-chart-card).
 * - Sen estilos en liña: o tamaño vai en chart.css e `height` ponse por CSSOM (CSP).
 * - `image()` dá un PNG (data URL, a dobre resolución) para imprimir, co tema que se ve ou en
 *   claro; `chartImage(elemento)` dao desde o DOM (o PDF cambia os gráficos por imaxes).
 */
@Component({
  selector: 'furar-chart',
  host: { class: 'f-chart', '[attr.data-size]': 'size()' },
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div #canvas class="f-chart__canvas" aria-hidden="true"></div>`,
})
export class ChartComponent {
  private readonly document = inject(DOCUMENT);
  private readonly loadEngine = inject(CHART_ENGINE);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly canvas = viewChild.required<ElementRef<HTMLElement>>('canvas');
  private readonly theme = inject(ThemeChanges).version;

  /** Opcións en función dos tokens: un construtor de chart-options.ts ou chart-composite.ts. */
  readonly chart = input.required<ChartSpec>();
  readonly size = input<ChartSize>('md');
  /** Alto en rem (p. ex. un mapa de calor segundo as filas); sen el, o do tamaño. */
  readonly height = input(undefined, {
    transform: (value: unknown) => (value === null || value === undefined ? undefined : numberAttribute(value)),
  });

  private readonly ready = signal(false);
  private instance?: ChartInstance;
  private tokensKey = '';
  private destroyed = false;

  constructor() {
    const destroyRef = inject(DestroyRef);
    CHARTS.set(this.host.nativeElement, this);
    afterNextRender(() => void this.start(destroyRef));
    effect(() => {
      const spec = this.chart();
      if (this.ready()) untracked(() => this.draw(spec, true));
    });
    // ThemeService pon o tema en `style` de <html>; o resto de cambios de estilo non mudan os tokens.
    effect(() => {
      this.theme();
      if (this.ready()) untracked(() => this.draw(this.chart(), false));
    });
    effect(() => {
      const height = this.height();
      const style = this.host.nativeElement.style;
      if (height === undefined) style.removeProperty('--f-chart-height');
      else style.setProperty('--f-chart-height', `${height}rem`);
    });
    destroyRef.onDestroy(() => {
      this.destroyed = true;
      CHARTS.delete(this.host.nativeElement);
      this.instance?.dispose();
      this.instance = undefined;
    });
  }

  /**
   * PNG do gráfico sobre a súa superficie e a dobre resolución; `null` se aínda non se debuxou.
   * En `light`, cun tema escuro debúxase un momento en claro (sen animación) e volve ao de antes.
   */
  image(theme: ChartImageTheme = 'current'): string | null {
    const instance = this.instance;
    if (!instance) return null;
    const current = this.tokens();
    const tokens = theme === 'light' ? lightChartTokens(current) : current;
    if (tokens === current) {
      return instance.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: current.surface });
    }
    const spec = this.chart();
    instance.setOption({ ...spec(tokens), animation: false }, { notMerge: true });
    try {
      return instance.getDataURL({ type: 'png', pixelRatio: 2, backgroundColor: tokens.surface });
    } finally {
      instance.setOption({ ...spec(current), animation: false }, { notMerge: true });
    }
  }

  private async start(destroyRef: DestroyRef): Promise<void> {
    try {
      await this.document.fonts?.ready;
      const engine = await this.loadEngine();
      if (this.destroyed) return;
      this.instance = engine.init(this.canvas().nativeElement);
    } catch (error) {
      console.error('furar-chart: non se puido cargar o motor dos gráficos', error);
      return;
    }
    this.observe(destroyRef);
    this.ready.set(true);
  }

  private observe(destroyRef: DestroyRef): void {
    const view = this.document.defaultView;
    if (view?.ResizeObserver) {
      const resize = new view.ResizeObserver(() => this.instance?.resize());
      resize.observe(this.canvas().nativeElement);
      destroyRef.onDestroy(() => resize.disconnect());
    }
  }

  /** Debuxa coas cores de agora; se `force` é falso, só cando os tokens cambiaron. */
  private draw(spec: ChartSpec, force: boolean): void {
    if (!this.instance) return;
    const tokens = this.tokens();
    const key = JSON.stringify(tokens);
    if (!force && key === this.tokensKey) return;
    this.tokensKey = key;
    this.instance.setOption({ ...spec(tokens), animation: !this.reducedMotion() }, { notMerge: true });
  }

  private tokens(): ChartTokens {
    return readChartTokens(this.canvas().nativeElement);
  }

  private reducedMotion(): boolean {
    return this.document.defaultView?.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }
}
