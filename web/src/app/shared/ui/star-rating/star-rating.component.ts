// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, input, model, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';
import { RequiredMarkComponent } from '../required/required.component';
import { STAR_POSITIONS } from '../stars/stars.component';

let nextId = 0;

/**
 * Escoller unha nota de 1 a 5 (ADR-0037): un grupo de cinco radios nativos (`fieldset`
 * co rótulo como `legend`), cada un debuxado coma unha estrela nunha zona de toque de 44 px e co
 * seu nome oculto («3 estrelas»). As frechas moven a nota coma en calquera grupo de radios; ao pasar
 * o rato, as estrelas mostran a que quedaría. O erro (`error`) vai debaixo, ligado ao grupo.
 */
@Component({
  selector: 'furar-star-rating',
  imports: [IconComponent, RequiredMarkComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-star-rating' },
  template: `
    <fieldset
      class="f-star-rating__group"
      [attr.aria-describedby]="error() ? errorId : null"
      [attr.aria-invalid]="error() ? 'true' : null"
      (pointerleave)="hover.set(null)"
    >
      <legend class="f-label">
        {{ label() }}
        @if (required()) {
          <furar-required-mark />
        }
      </legend>
      <div class="f-star-rating__stars">
        @for (n of positions; track n) {
          <label class="f-star-rating__star" [attr.data-on]="n <= shown() || null" (pointerenter)="hover.set(n)">
            <input
              type="radio"
              class="f-star-rating__input"
              [name]="name"
              [value]="n"
              [checked]="value() === n"
              [attr.data-star]="n"
              (change)="value.set(n)"
            />
            <furar-icon [name]="n <= shown() ? 'star-fill' : 'star'" [size]="24" />
            <span class="sr-only">{{
              (n === 1 ? 'ui.stars.optionOne' : 'ui.stars.option') | transloco: { count: n }
            }}</span>
          </label>
        }
      </div>
    </fieldset>
    @if (error(); as e) {
      <span class="f-field-error" [id]="errorId">{{ e }}</span>
    }
  `,
})
export class StarRatingComponent {
  /** Texto do rótulo visible («Nota»). */
  readonly label = input.required<string>();
  readonly required = input(false);
  /** Texto do erro, xa traducido; baleiro, sen erro. */
  readonly error = input('');
  /** A nota escollida; nula, ningunha. */
  readonly value = model<number | null>(null);

  protected readonly positions = STAR_POSITIONS;
  protected readonly name = `furar-star-rating-${nextId++}`;
  protected readonly errorId = `${this.name}-error`;
  protected readonly hover = signal<number | null>(null);
  protected readonly shown = computed(() => this.hover() ?? this.value() ?? 0);
}
