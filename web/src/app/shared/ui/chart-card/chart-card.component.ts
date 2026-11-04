// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, ElementRef, computed, inject, input, model } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import type { ChartPattern, ChartTone } from '../chart/chart-options';

/** Unha entrada da lenda: o nome da serie, o seu ton e, se a leva, a súa trama (a do gráfico). */
export interface ChartLegendItem {
  readonly label: string;
  readonly tone: ChartTone;
  readonly pattern?: ChartPattern;
}

let nextId = 0;

/**
 * Tarxeta dun gráfico (D3-07; docs/deseno.md §8): título, frase coa conclusión, «Táboa» (cambia
 * o gráfico pola súa táboa), accións de quen o usa (`[furarChartCardActions]`, p. ex. Exportar),
 * o gráfico nun `<figure role="img">` co título e a conclusión como nome, a lenda en texto e a táboa.
 * Unha nota que vai co gráfico e non co ton dunha serie (a escala do mapa de calor) proxéctase con
 * `[furarChartCardNote]`: agóchase coa táboa, coma a lenda.
 *
 * ```html
 * <furar-chart-card [heading]="t" [conclusion]="c" [legend]="l">
 *   <furar-export-menu furarChartCardActions [csv]="…" [pdf]="…" />
 *   <furar-chart [chart]="spec" />
 *   <furar-data-table [caption]="t" [columns]="cols" [rows]="rows" />
 * </furar-chart-card>
 * ```
 *
 * «Táboa» leva `aria-pressed` e `aria-controls`; ao premelo o foco queda nel. O que non se ve
 * agóchase con `hidden` (a folla de impresión mostra a táboa no PDF dunha tarxeta).
 */
@Component({
  selector: 'furar-chart-card',
  imports: [TranslocoPipe, ButtonDirective],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-chart-card',
    role: 'region',
    '[attr.aria-labelledby]': 'titleId',
    '[attr.data-view]': 'table() ? "table" : "chart"',
  },
  template: `
    <header class="f-chart-card__head">
      <div class="f-chart-card__titles">
        @if (headingLevel() === 2) {
          <h2 class="f-chart-card__title" [id]="titleId">{{ heading() }}</h2>
        } @else {
          <h3 class="f-chart-card__title" [id]="titleId">{{ heading() }}</h3>
        }
        @if (conclusion()) {
          <p class="f-chart-card__conclusion">{{ conclusion() }}</p>
        }
      </div>
      <div class="f-chart-card__actions">
        <button
          type="button"
          furarButton
          size="sm"
          class="f-chart-card__toggle"
          [attr.aria-pressed]="table()"
          [attr.aria-controls]="tableId"
          [attr.aria-label]="'ui.chartCard.tableFor' | transloco: { title: heading() }"
          (click)="table.set(!table())"
        >
          {{ 'ui.chartCard.table' | transloco }}
        </button>
        <ng-content select="[furarChartCardActions]" />
      </div>
    </header>
    <div class="f-chart-card__chart" [hidden]="table()">
      <figure class="f-chart-card__figure" role="img" [attr.aria-label]="name()">
        <ng-content select="furar-chart" />
      </figure>
      @if (legend().length) {
        <ul class="f-chart-card__legend">
          @for (item of legend(); track item.label) {
            <li>
              <span
                class="f-chart-card__swatch"
                aria-hidden="true"
                [attr.data-tone]="item.tone"
                [attr.data-pattern]="item.pattern ?? null"
              ></span>
              {{ item.label }}
            </li>
          }
        </ul>
      }
      <ng-content select="[furarChartCardNote]" />
    </div>
    <div class="f-chart-card__table" [id]="tableId" [hidden]="!table()">
      <ng-content select="furar-data-table" />
    </div>
  `,
})
export class ChartCardComponent {
  /** O elemento da tarxeta: o destino do alcance de impresión (`printWithScope(…, 'card', el)`). */
  readonly element: HTMLElement = inject<ElementRef<HTMLElement>>(ElementRef).nativeElement;

  readonly heading = input.required<string>();
  readonly headingLevel = input<2 | 3>(3);
  /** A conclusión nunha frase, xa traducida: «O Pavillón enche as tardes». */
  readonly conclusion = input('');
  readonly legend = input<readonly ChartLegendItem[]>([]);
  /** Vese a táboa en vez do gráfico. */
  readonly table = model(false);

  protected readonly titleId = `furar-chart-card-${nextId}`;
  protected readonly tableId = `furar-chart-card-${nextId++}-table`;
  /** Nome do gráfico: o título e a conclusión. */
  protected readonly name = computed(() => [this.heading(), this.conclusion()].filter(Boolean).join('. '));
}
