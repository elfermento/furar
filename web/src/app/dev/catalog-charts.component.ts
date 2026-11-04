// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../core/i18n/language.service';
import { CardComponent } from '../shared/ui/card/card.component';
import { heatmap, stackedBars } from '../shared/ui/chart/chart-composite';
import type { ChartSpec } from '../shared/ui/chart/chart-options';
import { columns, hBars, lineArea, sparkline } from '../shared/ui/chart/chart-options';
import { ChartComponent } from '../shared/ui/chart/chart.component';

/** Textos dos datos de exemplo (catalog.charts.data): listas separadas por comas. */
interface SampleTexts {
  readonly months: string;
  readonly facilities: string;
  readonly days: string;
  readonly occupancy: string;
  readonly people: string;
  readonly inTime: string;
  readonly late: string;
  readonly municipality: string;
  readonly closed: string;
}

/** Prefixo dos textos de exemplo (i18n:check dá por usadas as claves que empezan por el). */
const SAMPLE = 'catalog.charts.data.';

const HOURS = ['9', '11', '13', '17', '19', '21'];

/** Ocupación de exemplo por día e hora; `null` = pechado (domingo pola tarde). */
function sampleCells() {
  const base = [20, 35, 15, 60, 85, 70];
  return Array.from({ length: 7 }, (_, y) =>
    base.map((value, x) => ({ x, y, value: y === 6 && x >= 3 ? null : Math.min(100, value + y * 4) })),
  ).flat();
}

/**
 * Sección «Gráficos» do catálogo (só desenvolvemento): un exemplo de cada construtor de
 * `furar-chart` con datos inventados. Cambiando o tema de proba vese o redebuxado.
 */
@Component({
  selector: 'furar-catalog-charts',
  imports: [TranslocoPipe, CardComponent, ChartComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card [heading]="'catalog.charts.title' | transloco" id="graficos">
      <p class="text-muted mb-item">{{ 'catalog.charts.intro' | transloco }}</p>
      @if (specs(); as s) {
        <div class="gap-form grid lg:grid-cols-2">
          <div class="gap-unit grid content-start">
            <h3 class="f-label">{{ 'catalog.charts.lineArea' | transloco }}</h3>
            <furar-chart [chart]="s.line" size="sm" />
          </div>
          <div class="gap-unit grid content-start">
            <h3 class="f-label">{{ 'catalog.charts.sparkline' | transloco }}</h3>
            <furar-chart [chart]="s.spark" size="spark" />
          </div>
          <div class="gap-unit grid content-start">
            <h3 class="f-label">{{ 'catalog.charts.hBars' | transloco }}</h3>
            <furar-chart [chart]="s.bars" size="sm" />
          </div>
          <div class="gap-unit grid content-start">
            <h3 class="f-label">{{ 'catalog.charts.columns' | transloco }}</h3>
            <furar-chart [chart]="s.columns" size="sm" />
          </div>
          <div class="gap-unit grid content-start">
            <h3 class="f-label">{{ 'catalog.charts.stackedBars' | transloco }}</h3>
            <furar-chart [chart]="s.stacked" />
          </div>
          <div class="gap-unit grid content-start">
            <h3 class="f-label">{{ 'catalog.charts.heatmap' | transloco }}</h3>
            <furar-chart [chart]="s.heat" />
          </div>
        </div>
      }
    </furar-card>
  `,
})
export class CatalogChartsComponent {
  private readonly language = inject(LanguageService);
  private readonly texts = toSignal(inject(TranslocoService).selectTranslateObject<SampleTexts>(SAMPLE.slice(0, -1)));

  protected readonly specs = computed(() => {
    const texts = this.texts();
    if (!texts) return null;
    const list = (value: string) => value.split(',');
    const number = new Intl.NumberFormat(this.language.lang() === 'gl' ? 'es' : this.language.lang());
    const percent = (value: number) => `${number.format(value)} %`;
    const months = list(texts.months);
    return {
      line: ((t) =>
        lineArea(t, {
          categories: months,
          series: { name: texts.occupancy, values: [42, 48, 45, 57, 61, 64] },
          format: percent,
        })) as ChartSpec,
      spark: ((t) => sparkline(t, [120, 132, 101, 134, 150, 162])) as ChartSpec,
      bars: ((t) =>
        hBars(t, {
          categories: list(texts.facilities),
          series: { name: texts.occupancy, values: [78, 54, 31] },
          format: percent,
          max: 100,
        })) as ChartSpec,
      columns: ((t) =>
        columns(t, {
          categories: months,
          series: { name: texts.people, values: [212, 198, 240, 251, 233, 268] },
        })) as ChartSpec,
      stacked: ((t) =>
        stackedBars(t, {
          categories: months,
          series: [
            { name: texts.inTime, tone: 'success', values: [12, 9, 14, 11, 8, 13] },
            { name: texts.late, tone: 'danger', pattern: 'stripes', values: [2, 1, 3, 0, 2, 1] },
            { name: texts.municipality, tone: 'info', pattern: 'dots', values: [1, 0, 2, 1, 0, 3] },
          ],
        })) as ChartSpec,
      heat: ((t) =>
        heatmap(t, {
          columns: HOURS,
          rows: list(texts.days),
          cells: sampleCells(),
          closedLabel: texts.closed,
          format: percent,
          max: 100,
        })) as ChartSpec,
    };
  });
}
