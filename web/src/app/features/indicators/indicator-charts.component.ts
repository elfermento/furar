// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChartCardComponent } from '../../shared/ui/chart-card/chart-card.component';
import { ChartComponent } from '../../shared/ui/chart/chart.component';
import { DataTableComponent } from '../../shared/ui/data-table/data-table.component';
import type { ExportAction } from '../../shared/ui/export-menu/export-menu.component';
import { ExportMenuComponent } from '../../shared/ui/export-menu/export-menu.component';
import { HeatLegendComponent } from './heat-legend.component';
import type { ChartView } from './indicator-views';

/** Exportar un gráfico: cal e a súa tarxeta (o alcance da impresión). */
export interface ChartExport {
  readonly view: ChartView;
  readonly card: HTMLElement;
}

/** Ocupan as dúas columnas: o mapa de calor (moitas horas) e as persoas (fecha o panel). */
const WIDE = new Set(['peak', 'people']);

/**
 * Os seis gráficos do panel (maqueta 31, opción A) en dúas columnas: cada `furar-chart-card` co
 * título, a conclusión, «Táboa», «Exportar» (PDF e CSV dese gráfico), a lenda en texto e a táboa.
 */
@Component({
  selector: 'furar-indicator-charts',
  imports: [
    TranslocoPipe,
    ChartCardComponent,
    ChartComponent,
    DataTableComponent,
    ExportMenuComponent,
    HeatLegendComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-indicators__charts' },
  template: `
    @for (chart of charts(); track chart.id) {
      <furar-chart-card
        #card
        [attr.data-chart]="chart.id"
        [class.f-indicators__wide]="wide.has(chart.id)"
        [headingLevel]="2"
        [heading]="chart.title"
        [conclusion]="chart.conclusion"
        [legend]="chart.legend"
      >
        <furar-export-menu
          furarChartCardActions
          [subject]="chart.title"
          [pdfHint]="'indicators.export.chartPdf' | transloco"
          [csvHint]="'indicators.export.chartCsv' | transloco"
          [pdf]="exportsOf(chart.id, card.element).pdf"
          [csv]="exportsOf(chart.id, card.element).csv"
        />
        <furar-chart [chart]="chart.spec" [height]="chart.height" />
        @if (chart.scale) {
          <furar-heat-legend furarChartCardNote />
        }
        <furar-data-table captionHidden [caption]="chart.title" [columns]="chart.columns" [rows]="chart.rows" />
      </furar-chart-card>
    }
  `,
})
export class IndicatorChartsComponent {
  readonly charts = input.required<readonly ChartView[]>();
  /** PDF dun gráfico. */
  readonly pdf = input.required<(chart: ChartExport) => unknown>();
  /** CSV dun gráfico. */
  readonly csv = input.required<(chart: ChartExport) => unknown>();

  protected readonly wide = WIDE;
  /** As exportacións de cada gráfico, unha vez (o mesmo valor en cada render); colle o gráfico ao premer. */
  private readonly exports = new Map<string, { pdf: ExportAction; csv: ExportAction }>();

  protected exportsOf(id: string, card: HTMLElement): { pdf: ExportAction; csv: ExportAction } {
    let actions = this.exports.get(id);
    if (!actions) {
      const run = (action: (chart: ChartExport) => unknown) => () => {
        const view = this.charts().find((chart) => chart.id === id);
        return view ? action({ view, card }) : undefined;
      };
      actions = { pdf: run((chart) => this.pdf()(chart)), csv: run((chart) => this.csv()(chart)) };
      this.exports.set(id, actions);
    }
    return actions;
  }
}
