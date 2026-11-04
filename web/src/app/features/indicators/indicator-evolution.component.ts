// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA } from '@angular/cdk/dialog';
import { DOCUMENT } from '@angular/common';
import { ChangeDetectionStrategy, ChangeDetectorRef, Component, ElementRef, inject, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { printWithChartImages } from '../../shared/print/print-charts';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { ChartComponent } from '../../shared/ui/chart/chart.component';
import { DataTableComponent } from '../../shared/ui/data-table/data-table.component';
import { DialogComponent } from '../../shared/ui/dialog/dialog.component';
import { downloadCsv } from '../../shared/ui/export-menu/csv';
import { ExportMenuComponent } from '../../shared/ui/export-menu/export-menu.component';
import type { EvolutionView } from './indicator-evolution';

/** O que lle pasa a páxina ao modal. */
export interface EvolutionDialogData {
  readonly view: EvolutionView;
  /** A cabeceira impresa (concello e filtros) co momento en que se exporta. */
  readonly header: (generated: Date) => string;
  /** `indicadores-<concello>-<período>-evolucion-<cifra>.csv`. */
  readonly csvName: string;
}

const ARROWS = { up: '↑', down: '↓', same: '→' } as const;

let nextId = 0;

/**
 * A evolución dunha cifra (D3-09; maqueta 31, opción A): `furar-dialog` grande co título («Reservas
 * por meses»), a cifra grande, o cambio co ton e a frecha en texto, a liña con área (etiqueta só
 * no último valor) ou a súa táboa, e no pé «Táboa» e «Exportar» (PDF desta evolución coa táboa e
 * a cabeceira impresa; CSV cos seus datos). Ábrese con `DialogService.open` desde a tarxeta (`from`).
 */
@Component({
  selector: 'furar-indicator-evolution',
  imports: [TranslocoPipe, ButtonDirective, ChartComponent, DataTableComponent, DialogComponent, ExportMenuComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog size="lg" [heading]="view.title" [attr.data-evolution]="view.id">
      <p class="f-print-header">{{ header() }}</p>
      <div class="f-evolution">
        <p class="f-evolution__value">{{ view.value }}</p>
        <p class="f-evolution__change">
          @if (view.trend; as trend) {
            <span class="f-stat-tile__delta" [attr.data-trend]="trend">
              <span aria-hidden="true">{{ arrows[trend] }}</span>
              {{ view.change }}
            </span>
            {{ view.comparison }}
          } @else {
            {{ 'ui.statTile.noPrevious' | transloco }}
          }
        </p>
        <div class="f-evolution__chart" [hidden]="table()">
          <figure class="f-evolution__figure" role="img" [attr.aria-label]="view.name">
            <furar-chart size="lg" [chart]="view.spec" />
          </figure>
        </div>
        <div class="f-evolution__table" [id]="tableId" [hidden]="!table()">
          <furar-data-table captionHidden [caption]="view.title" [columns]="view.columns" [rows]="view.rows" />
        </div>
      </div>
      <button
        dialogActions
        type="button"
        furarButton
        size="sm"
        class="f-evolution__toggle"
        [attr.aria-pressed]="table()"
        [attr.aria-controls]="tableId"
        [attr.aria-label]="'ui.chartCard.tableFor' | transloco: { title: view.title }"
        (click)="table.set(!table())"
      >
        {{ 'ui.chartCard.table' | transloco }}
      </button>
      <furar-export-menu
        dialogActions
        [subject]="view.title"
        [pdfHint]="'indicators.export.evolutionPdf' | transloco"
        [csvHint]="'indicators.export.evolutionCsv' | transloco"
        [pdf]="pdf"
        [csv]="csv"
      />
    </furar-dialog>
  `,
})
export class IndicatorEvolutionComponent {
  private readonly data = inject<EvolutionDialogData>(DIALOG_DATA);
  private readonly document = inject(DOCUMENT);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly cdr = inject(ChangeDetectorRef);

  protected readonly view = this.data.view;
  protected readonly arrows = ARROWS;
  protected readonly table = signal(false);
  protected readonly tableId = `furar-evolution-${nextId++}-table`;
  /** Fíxase ao exportar: «xerado o …» é o momento do PDF ou do CSV. */
  protected readonly header = signal(this.data.header(new Date()));

  protected readonly csv = (): void => {
    this.header.set(this.data.header(new Date()));
    downloadCsv(this.document, this.data.csvName, [[this.header()], [], ...this.view.csv]);
  };

  protected readonly pdf = (): Promise<void> => {
    this.header.set(this.data.header(new Date()));
    return printWithChartImages(this.document, 'dialog', this.host.nativeElement, () => this.cdr.detectChanges());
  };
}
