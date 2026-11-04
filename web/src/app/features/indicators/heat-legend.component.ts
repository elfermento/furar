// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  afterNextRender,
  inject,
  viewChildren,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { SCALE_STEPS, readChartTokens } from '../../shared/ui/chart/chart-tokens';

/**
 * Lenda do mapa de calor en texto: «Ocupación: 0 %», os pasos da escala, «100 %» e a mostra de
 * «Pechado» (sen recheo e con bordo discontinuo, coma no gráfico). As cores da escala son as que
 * usa o gráfico (derivada do `primary` do concello), así que se len dos tokens e van por CSSOM
 * (CSP); cambian co tema, coma o gráfico.
 */
@Component({
  selector: 'furar-heat-legend',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-heat-legend' },
  template: `
    <span>{{ 'indicators.peak.less' | transloco }}</span>
    <span class="f-heat-legend__scale" aria-hidden="true">
      @for (step of steps; track step) {
        <span #swatch class="f-heat-legend__swatch"></span>
      }
    </span>
    <span>{{ 'indicators.peak.more' | transloco }}</span>
    <span class="f-heat-legend__closed">
      <span class="f-heat-legend__swatch f-heat-legend__swatch--closed" aria-hidden="true"></span>
      {{ 'indicators.peak.closedLegend' | transloco }}
    </span>
  `,
})
export class HeatLegendComponent {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;
  private readonly swatches = viewChildren<ElementRef<HTMLElement>>('swatch');

  protected readonly steps = Array.from({ length: SCALE_STEPS }, (_, i) => i);

  constructor() {
    const document = inject(DOCUMENT);
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      this.paint();
      const view = document.defaultView;
      if (!view?.MutationObserver) return;
      // O tema vai en `style` e `class` de <html> (ThemeService), coma en furar-chart.
      const theme = new view.MutationObserver(() => this.paint());
      theme.observe(document.documentElement, { attributes: true, attributeFilter: ['style', 'class'] });
      destroyRef.onDestroy(() => theme.disconnect());
    });
  }

  private paint(): void {
    const tokens = readChartTokens(this.host);
    this.swatches().forEach((swatch, i) =>
      swatch.nativeElement.style.setProperty('background-color', tokens.scale[i] ?? tokens.primary),
    );
  }
}
