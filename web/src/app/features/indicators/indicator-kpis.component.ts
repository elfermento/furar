// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ChartComponent } from '../../shared/ui/chart/chart.component';
import { StatTileComponent } from '../../shared/ui/stat-tile/stat-tile.component';
import type { KpiId, KpiView } from './indicator-kpis';

/** Unha cifra pedida: cal e o seu botón (orixe da transición e destino do foco ao pechar). */
export interface KpiOpen {
  readonly id: KpiId;
  readonly element: HTMLButtonElement;
}

/**
 * As cinco cifras do panel (maqueta 31): cada `furar-stat-tile` coa súa minigráfica. Ao premela
 * emite `open` co botón; o modal da evolución é D3-09.
 */
@Component({
  selector: 'furar-indicator-kpis',
  imports: [TranslocoPipe, ChartComponent, StatTileComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="f-stat-tiles" [attr.aria-label]="'indicators.kpi.title' | transloco">
      @for (kpi of kpis(); track kpi.id) {
        <furar-stat-tile
          #tile
          [attr.data-kpi]="kpi.id"
          [label]="kpi.label"
          [value]="kpi.value"
          [change]="kpi.change"
          [trend]="kpi.trend"
          [comparison]="kpi.comparison"
          (open)="open.emit({ id: kpi.id, element: tile.element() })"
        >
          <furar-chart size="spark" [chart]="kpi.spark" />
        </furar-stat-tile>
      }
    </section>
  `,
})
export class IndicatorKpisComponent {
  readonly kpis = input.required<readonly KpiView[]>();
  readonly open = output<KpiOpen>();
}
