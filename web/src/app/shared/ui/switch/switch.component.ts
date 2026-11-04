// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  forwardRef,
  input,
  signal,
} from '@angular/core';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';

let nextId = 0;

/**
 * Interruptor acendido/apagado (D1-05a; docs/deseno.md §8): un
 * `<button role="switch">` con `aria-checked` e o rótulo visible ao lado nun `<label for>` (que
 * lle dá nome). Picar no rótulo tamén cambia. Espazo e Intro
 * cambian (botón nativo). Funciona con `formControlName` (valor `boolean`).
 *
 * Úsase para un si/non que se aplica tal cal («Reserva por internet»). Para escoller entre dúas
 * cousas con nome, o segmentado (`[furarSegmented]` radio).
 *
 * Con `onText` e `offText` (maqueta 54), á vista vai o estado («Aberto» / «Pechado») e o rótulo
 * queda só como nome accesible: así un apagado non se le «Aberto ese día · Pechado».
 */
@Component({
  selector: 'furar-switch',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SwitchComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-switch', '[attr.data-disabled]': 'isDisabled() ? "" : null' },
  template: `
    <button
      type="button"
      role="switch"
      class="f-switch__control"
      [id]="switchId()"
      [attr.aria-checked]="checked()"
      [attr.aria-label]="state() === null ? null : label()"
      [attr.aria-describedby]="describedBy() || null"
      [disabled]="isDisabled()"
      (click)="toggle()"
      (blur)="onTouched()"
    >
      <span class="f-switch__thumb"></span>
    </button>
    @if (state(); as text) {
      <!-- O nome vai en aria-label; o estado xa o di aria-checked. O <label for> só é para picar. -->
      <label class="f-switch__label" aria-hidden="true" data-switch-state [for]="switchId()">{{ text }}</label>
    } @else {
      <!-- Un <label for> nomea o botón e, ao picalo, prémeo (o botón é «labelable»). -->
      <label class="f-switch__label" [class.sr-only]="hideLabel()" [for]="switchId()">{{ label() }}</label>
    }
  `,
})
export class SwitchComponent implements ControlValueAccessor {
  /** Rótulo visible (xa traducido): di que se acende, non o estado («Reserva por internet»). */
  readonly label = input.required<string>();
  /** Esconde o rótulo á vista (segue sendo o nome) cando o contexto xa o di, p. ex. nunha fila. */
  readonly hideLabel = input(false, { transform: booleanAttribute });
  /** Id da axuda ou do erro que describe o interruptor. */
  readonly describedBy = input<string | null>(null);
  /** Id do botón. */
  readonly switchId = input(`furar-switch-${nextId++}`);
  /** Texto visible acendido («Aberto»); con `offText`, substitúe o rótulo á vista. */
  readonly onText = input<string | null>(null);
  /** Texto visible apagado («Pechado»). */
  readonly offText = input<string | null>(null);

  protected readonly checked = signal(false);
  protected readonly state = computed(() => {
    const on = this.onText();
    const off = this.offText();
    if (on === null || off === null) return null;
    return this.checked() ? on : off;
  });
  private readonly disabledByForm = signal(false);
  protected readonly isDisabled = this.disabledByForm.asReadonly();

  private onChange: (value: boolean) => void = () => undefined;
  protected onTouched: () => void = () => undefined;

  writeValue(value: boolean | null): void {
    this.checked.set(value === true);
  }

  registerOnChange(fn: (value: boolean) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(disabled: boolean): void {
    this.disabledByForm.set(disabled);
  }

  protected toggle(): void {
    if (this.isDisabled()) {
      return;
    }
    const next = !this.checked();
    this.checked.set(next);
    this.onChange(next);
    this.onTouched();
  }
}
