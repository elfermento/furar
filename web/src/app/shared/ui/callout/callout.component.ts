// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';

export type CalloutTone = 'info' | 'warn' | 'danger' | 'success';
/**
 * Aviso (docs/deseno.md P1-6): tinte suave e icona do ton, e a palabra do ton para lectores
 * (ui.tone.*), así o estado non vai só pola cor (1.4.1). `live` só se o aviso aparece xunto coa
 * rexión; se a rexión ten que existir antes (role="alert" baleiro), ponna quen chama.
 */
@Component({
  selector: 'furar-callout',
  imports: [IconComponent, TranslocoPipe],
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
  `,
})
export class CalloutComponent {
  readonly tone = input<CalloutTone>('info');
  readonly heading = input('');
  readonly live = input<'off' | 'status' | 'alert'>('off');
}
