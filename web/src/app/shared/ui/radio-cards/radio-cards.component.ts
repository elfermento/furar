// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, forwardRef, input, model, output, signal } from '@angular/core';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { IconComponent } from '../icon/icon.component';

let nextId = 0;

/** Unha liña da tarxeta: o que inclúe (✓) ou non (✕) esa opción, xa traducido. */
export interface RadioCardItem {
  readonly text: string;
  readonly included: boolean;
}

/** Unha tarxeta: o valor que se garda, o nome (xa traducido) e a lista do que inclúe. */
export interface RadioCardOption<T = unknown> {
  readonly value: T;
  readonly label: string;
  readonly items: readonly RadioCardItem[];
}

/**
 * Tarxetas para escoller unha opción entre poucas, cada unha co que inclúe e o que non (o rol ao
 * invitar ou ao cambialo). Debaixo son radios nativos co mesmo `name` dentro dun `radiogroup`:
 * unha parada de tabulador, frechas para mover e escoller, Espazo e picar en calquera parte da
 * tarxeta. O nome de cada radio é o da tarxeta e a descrición, a súa lista («Si: Reservas e
 * cobros. Non: Cancelar reservas…»): o ✓ e o ✕ son decorativos. A escollida vese polo punto do
 * radio e o bordo (non só a cor, 1.4.1). Funciona con `formControlName` (ControlValueAccessor) ou
 * con `[(value)]`; os valores compáranse con `===`. Sen lista, a tarxeta é só o nome e o radio. O nome do grupo pono quen o usa
 * (`labelledBy`, o id do seu rótulo visible).
 */
@Component({
  selector: 'furar-radio-cards',
  imports: [IconComponent, TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => RadioCardsComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-radio-cards' },
  template: `
    <div class="f-radio-cards__group" role="radiogroup" [attr.aria-labelledby]="labelledBy()">
      @for (option of options(); track $index; let i = $index) {
        <label class="f-radio-card" [attr.data-checked]="option.value === value() ? '' : null">
          <span class="f-radio-card__top">
            <span class="f-radio-card__label" [id]="id + '-' + i">{{ option.label }}</span>
            <input
              type="radio"
              class="f-radio-card__radio"
              [name]="id"
              [checked]="option.value === value()"
              [disabled]="isDisabled()"
              [attr.aria-labelledby]="id + '-' + i"
              [attr.aria-describedby]="option.items.length ? id + '-' + i + '-items' : null"
              [attr.data-value]="option.value"
              (change)="pick(option.value)"
              (blur)="onTouched()"
            />
          </span>
          @if (option.items.length) {
            <ul class="f-radio-card__items" [id]="id + '-' + i + '-items'">
              @for (item of option.items; track $index) {
                <li class="f-radio-card__item" [attr.data-included]="item.included">
                  <span class="f-radio-card__mark" aria-hidden="true">
                    <furar-icon [name]="item.included ? 'check' : 'close'" />
                  </span>
                  <span
                    ><span class="sr-only">{{ (item.included ? 'common.yes' : 'common.no') | transloco }}: </span
                    >{{ item.text }}</span
                  >
                </li>
              }
            </ul>
          }
        </label>
      }
    </div>
  `,
})
export class RadioCardsComponent<T = unknown> implements ControlValueAccessor {
  readonly options = input.required<readonly RadioCardOption<T>[]>();
  /** Id do rótulo visible que nomea o grupo. */
  readonly labelledBy = input.required<string>();
  /** Valor escollido; `null` se non hai ningún. */
  readonly value = model<T | null>(null);
  /** Valor escollido pola persoa (tamén sen formulario). */
  readonly changed = output<T>();

  protected readonly id = `furar-radio-cards-${nextId++}`;
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = this.disabledByForm.asReadonly();

  private onChange: (value: T | null) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  writeValue(value: T | null): void {
    this.value.set(value ?? null);
  }

  registerOnChange(fn: (value: T | null) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabledByForm.set(disabled);
  }

  protected pick(value: T): void {
    if (value === this.value()) return;
    this.value.set(value);
    this.onChange(value);
    this.changed.emit(value);
  }
}
