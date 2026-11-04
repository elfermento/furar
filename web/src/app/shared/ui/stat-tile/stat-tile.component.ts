// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, input, output, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';

/** Sentido do cambio co período anterior: sobe (verde), baixa (vermello) ou igual (gris). */
export type StatTrend = 'up' | 'down' | 'same';

const ARROWS: Record<StatTrend, string> = { up: '↑', down: '↓', same: '→' };

/**
 * Cifra principal dos indicadores (D3-07; docs/deseno.md §8): un `<button>` que abre o detalle
 * (`aria-haspopup="dialog"`) co rótulo, o valor, o cambio co período anterior e a minigráfica
 * (contido proxectado, p. ex. `<furar-chart size="spark">`). Os textos chegan xa formatados.
 *
 * Os anacos levan un espazo ao final para que o nome do botón non os pegue («Reservas 1.234»).
 * O cambio vai sempre en texto co signo («+12 %», «−3 p.p.») e a frecha, ademais do ton (non só
 * cor, 1.4.1). Sen `trend`, non hai período anterior: sae `noPrevious` ou o texto por defecto.
 */
@Component({
  selector: 'furar-stat-tile',
  imports: [TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-stat-tile' },
  template: `
    <button #button type="button" class="f-stat-tile__button" aria-haspopup="dialog" (click)="open.emit()">
      <span class="f-stat-tile__label">{{ label() }} </span>
      <span class="f-stat-tile__value">{{ value() }} </span>
      <span class="f-stat-tile__change">
        @if (trend(); as t) {
          <span class="f-stat-tile__delta" [attr.data-trend]="t">
            <span aria-hidden="true">{{ arrow() }}</span>
            {{ change() }}
          </span>
          {{ comparison() }}
        } @else {
          {{ noPrevious() || ('ui.statTile.noPrevious' | transloco) }}
        }
      </span>
      <span class="f-stat-tile__spark"><ng-content /></span>
      <span class="f-stat-tile__more">
        {{ 'ui.statTile.more' | transloco }}
        <span aria-hidden="true">→</span>
      </span>
    </button>
  `,
})
export class StatTileComponent {
  private readonly button = viewChild.required<ElementRef<HTMLButtonElement>>('button');

  /** Que se conta: «Reservas». */
  readonly label = input.required<string>();
  /** A cifra xa formatada: «1.234», «61 %». */
  readonly value = input.required<string>();
  /** O cambio co signo: «+12 %» ou «+3 p.p.». */
  readonly change = input('');
  /** Sentido do cambio; `null` = sen período anterior. */
  readonly trend = input<StatTrend | null>(null);
  /** Con que se compara: «respecto a setembro». */
  readonly comparison = input('');
  /** Texto sen período anterior; por defecto, `ui.statTile.noPrevious`. */
  readonly noPrevious = input('');
  /** Pídese o detalle (a evolución nun modal). */
  readonly open = output();

  protected readonly arrow = computed(() => {
    const trend = this.trend();
    return trend ? ARROWS[trend] : '';
  });

  /** O botón: orixe da transición cara ao modal e onde volve o foco ao pechalo. */
  element(): HTMLButtonElement {
    return this.button().nativeElement;
  }
}
