// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../core/i18n/language.service';
import { printWithScope } from '../shared/print/print-scope';
import { CardComponent } from '../shared/ui/card/card.component';
import type { ChartLegendItem } from '../shared/ui/chart-card/chart-card.component';
import { ChartCardComponent } from '../shared/ui/chart-card/chart-card.component';
import type { ChartSpec } from '../shared/ui/chart/chart-options';
import { columns, sparkline } from '../shared/ui/chart/chart-options';
import { ChartComponent } from '../shared/ui/chart/chart.component';
import type { DataTableColumn, DataTableRow } from '../shared/ui/data-table/data-table.component';
import { DataTableComponent } from '../shared/ui/data-table/data-table.component';
import { downloadCsv } from '../shared/ui/export-menu/csv';
import { ExportMenuComponent } from '../shared/ui/export-menu/export-menu.component';
import { StatTileComponent } from '../shared/ui/stat-tile/stat-tile.component';

/** Textos de exemplo que se usan en TypeScript (cabeceiras e datos). */
interface SampleTexts {
  readonly months: string;
  readonly days: string;
}

const BOOKINGS = [312, 298, 351, 368, 402, 415];
const HOURS = ['9', '10', '11', '12', '13', '16', '17', '18', '19', '20', '21'];

/**
 * Sección «Indicadores» do catálogo (só desenvolvemento): tres `furar-stat-tile` (sobe, baixa e
 * sen anterior), un `furar-chart-card` con lenda, Exportar e a súa táboa, e un `furar-data-table`
 * ancho que a 320 px se move dentro da súa rexión. Exportar fai de verdade o CSV e o PDF.
 */
@Component({
  selector: 'furar-catalog-indicators',
  imports: [
    TranslocoPipe,
    CardComponent,
    ChartCardComponent,
    ChartComponent,
    DataTableComponent,
    ExportMenuComponent,
    StatTileComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card [heading]="'catalog.indicators.title' | transloco" id="indicadores">
      <p class="text-muted mb-item">{{ 'catalog.indicators.intro' | transloco }}</p>
      @if (sample(); as s) {
        <div class="f-stat-tiles mb-item">
          <furar-stat-tile
            [label]="'catalog.indicators.bookings' | transloco"
            [value]="s.total"
            [change]="s.up"
            trend="up"
            [comparison]="'catalog.indicators.comparison' | transloco"
            (open)="opened.set('catalog.indicators.bookings')"
          >
            <furar-chart [chart]="spark" size="spark" />
          </furar-stat-tile>
          <furar-stat-tile
            [label]="'catalog.indicators.occupancy' | transloco"
            [value]="s.occupancy"
            [change]="s.down"
            trend="down"
            [comparison]="'catalog.indicators.comparison' | transloco"
            (open)="opened.set('catalog.indicators.occupancy')"
          />
          <furar-stat-tile
            [label]="'catalog.indicators.people' | transloco"
            [value]="s.people"
            (open)="opened.set('catalog.indicators.people')"
          />
        </div>
        <p role="status" class="text-support mb-item">
          @if (opened(); as key) {
            {{ 'catalog.indicators.opened' | transloco: { label: (key | transloco) } }}
          }
        </p>
        <furar-chart-card
          #card
          class="mb-item"
          [heading]="'catalog.indicators.chart' | transloco"
          [conclusion]="'catalog.indicators.conclusion' | transloco"
          [legend]="legend()"
        >
          <furar-export-menu
            furarChartCardActions
            [subject]="'catalog.indicators.chart' | transloco"
            [pdfHint]="'catalog.indicators.pdfHint' | transloco"
            [csvHint]="'catalog.indicators.csvHint' | transloco"
            [pdf]="printOf(card.element)"
            [csv]="csvOf(s.columns, s.rows)"
          />
          <furar-chart [chart]="s.chart" size="sm" />
          <furar-data-table [caption]="'catalog.indicators.chart' | transloco" [columns]="s.columns" [rows]="s.rows" />
        </furar-chart-card>
        <furar-data-table
          [caption]="'catalog.indicators.heat' | transloco"
          [columns]="s.heatColumns"
          [rows]="s.heatRows"
        />
      }
    </furar-card>
  `,
})
export class CatalogIndicatorsComponent {
  private readonly document = inject(DOCUMENT);
  private readonly language = inject(LanguageService);
  private readonly transloco = inject(TranslocoService);
  private readonly texts = toSignal(this.transloco.selectTranslateObject<SampleTexts>('catalog.charts.data'));

  protected readonly opened = signal('');
  protected readonly spark: ChartSpec = (t) => sparkline(t, BOOKINGS);
  protected readonly legend = computed<ChartLegendItem[]>(() => {
    this.texts();
    return [{ label: this.transloco.translate('catalog.indicators.bookings'), tone: 'primary' }];
  });

  protected readonly sample = computed(() => {
    const texts = this.texts();
    if (!texts) return null;
    const lang = this.language.lang();
    const number = new Intl.NumberFormat(lang === 'gl' ? 'es' : lang);
    const t = (key: string, params?: Record<string, string>) => this.transloco.translate(key, params);
    const months = texts.months.split(',');
    const days = texts.days.split(',');
    const tableColumns: DataTableColumn[] = [
      { label: t('catalog.indicators.month') },
      { label: t('catalog.indicators.bookings'), numeric: true },
    ];
    return {
      total: number.format(BOOKINGS.reduce((sum, value) => sum + value, 0)),
      occupancy: `${number.format(61.5)} %`,
      people: number.format(1240),
      up: `+${number.format(12)} %`,
      down: t('catalog.indicators.down', { value: number.format(3) }),
      chart: ((tokens) =>
        columns(tokens, {
          categories: months,
          series: { name: t('catalog.indicators.bookings'), values: BOOKINGS },
        })) as ChartSpec,
      columns: tableColumns,
      rows: months.map((month, i): DataTableRow => [month, number.format(BOOKINGS[i] ?? 0)]),
      heatColumns: [
        { label: t('catalog.indicators.day') },
        ...HOURS.map((hour) => ({ label: hour, numeric: true })),
      ] as DataTableColumn[],
      heatRows: days.map((day, y): DataTableRow => [
        day,
        ...HOURS.map((_, x) => String(Math.min(100, 20 + x * 7 + y * 3))),
      ]),
    };
  });

  protected printOf(card: HTMLElement): () => Promise<void> {
    return () => printWithScope(this.document, 'card', card);
  }

  protected csvOf(head: readonly DataTableColumn[], rows: readonly DataTableRow[]): () => void {
    return () => downloadCsv(this.document, 'indicadores-catalogo.csv', [head.map((column) => column.label), ...rows]);
  }
}
