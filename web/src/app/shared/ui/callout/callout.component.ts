// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, booleanAttribute, input, output } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { ButtonDirective } from '../button/button.directive';
import { IconComponent } from '../icon/icon.component';

export type CalloutTone = 'info' | 'warn' | 'danger' | 'success';
/**
 * Aviso (docs/deseno.md P1-6): tinte suave e icona do ton, e a palabra do ton para lectores
 * (ui.tone.*), así o estado non vai só pola cor (1.4.1). `live` só se o aviso aparece xunto coa
 * rexión; se a rexión ten que existir antes (role="alert" baleiro), ponna quen chama.
 *
 * Con `dismissible`, unha ✕ ao final («Pechar o aviso») avisa con `dismissed`: quen o usa quítao.
 * A icona mide o alto dunha liña de texto, así unha liña vai centrada con ela e varias, coa primeira.
 */
@Component({
  selector: 'furar-callout',
  imports: [ButtonDirective, IconComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-callout',
    '[attr.data-tone]': 'tone()',
    '[attr.role]': 'live() === "off" ? null : live()',
  },
  template: `
    <furar-icon class="f-callout__icon" [name]="tone()" />
    <div class="f-callout__body">
      <span class="sr-only">{{ 'ui.tone.' + tone() | transloco }}</span>
      @if (heading()) {
        <p class="f-callout__title">{{ heading() }}</p>
      }
      <ng-content />
    </div>
    @if (dismissible()) {
      <button
        type="button"
        class="f-callout__close"
        furarButton="quiet"
        size="sm"
        round
        data-callout-close
        [attr.aria-label]="'ui.callout.close' | transloco"
        (click)="dismissed.emit()"
      >
        <furar-icon name="close" />
      </button>
    }
  `,
})
export class CalloutComponent {
  readonly tone = input<CalloutTone>('info');
  readonly heading = input('');
  readonly live = input<'off' | 'status' | 'alert'>('off');
  /** Leva unha ✕ para pechalo. */
  readonly dismissible = input(false, { transform: booleanAttribute });
  /** Pulsouse a ✕. */
  readonly dismissed = output();
}
