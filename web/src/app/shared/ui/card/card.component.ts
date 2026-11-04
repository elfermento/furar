// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';

let nextId = 0;

/**
 * Card (docs/deseno.md P1-4). Con `heading` é unha rexión co título como nome
 * (role="region" + aria-labelledby, o mesmo ca <section> con título).
 */
@Component({
  selector: 'furar-card',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    class: 'f-card',
    '[attr.data-tone]': 'tone() === "tint" ? "tint" : null',
    '[attr.role]': 'heading() ? "region" : null',
    '[attr.aria-labelledby]': 'heading() ? headingId : null',
  },
  template: `
    @if (heading()) {
      @if (headingLevel() === 3) {
        <h3 class="f-card__title" [id]="headingId">{{ heading() }}</h3>
      } @else {
        <h2 class="f-card__title" [id]="headingId">{{ heading() }}</h2>
      }
    }
    <ng-content />
  `,
})
export class CardComponent {
  readonly heading = input('');
  readonly headingLevel = input<2 | 3>(2);
  readonly tone = input<'surface' | 'tint'>('surface');
  protected readonly headingId = `furar-card-${nextId++}`;
}
