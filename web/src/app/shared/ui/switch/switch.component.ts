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
import { NgTemplateOutlet } from '@angular/common';
import { TranslocoPipe } from '@jsverse/transloco';
import { HelpTipComponent } from '../help-tip/help-tip.component';

let nextId = 0;

/**
 * Interruptor acendido/apagado (docs/deseno.md §8): un
 * `<button role="switch">` con `aria-checked` e o rótulo visible á súa esquerda nun `<label for>` (que
 * lle dá nome). Picar no rótulo tamén cambia. Espazo e Intro
 * cambian (botón nativo). Funciona con `formControlName` (valor `boolean`).
 *
 * Úsase para un si/non que se aplica tal cal («Reserva por internet»). Para escoller entre dúas
 * cousas con nome, o segmentado (`[furarSegmented]` radio).
 *
 * Con `onText` e `offText`, á vista vai o estado («Aberto» / «Pechado») e o rótulo
 * queda só como nome accesible: así un apagado non se le «Aberto ese día · Pechado».
 *
 * Con `tip`, a axuda vai nun «?» (`furar-help-tip`) despois do rótulo, nunca nunha liña debaixo.
 *
 * Unha fila (decisión do mantedor, en toda a aplicación): o rótulo e o «?» á esquerda e o
 * interruptor á dereita do todo; o contido proxectado (p. ex. «Vista previa») vai debaixo, nunha
 * liña propia aliñada co rótulo (un botón non comparte liña co interruptor). Con `hideLabel` e sen `tip`, só o interruptor, empuxado á dereita da súa fila.
 */
@Component({
  selector: 'furar-switch',
  imports: [HelpTipComponent, NgTemplateOutlet, TranslocoPipe],
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SwitchComponent), multi: true }],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-switch', '[attr.data-disabled]': 'isDisabled() ? "" : null' },
  template: `
    <!-- Rótulo e «?» nun mesmo texto: se o rótulo parte en dúas liñas, o «?» segue á última palabra. -->
    <span class="f-switch__text">
      @if (state(); as text) {
        <!-- O nome vai en aria-label; o estado xa o di aria-checked. O <label for> só é para picar. -->
        <label class="f-switch__label" aria-hidden="true" data-switch-state [for]="switchId()">{{ text }}</label>
      } @else if (glued(); as parts) {
        <!--
          Co «?», a última palabra e o «?» van xuntos (nunca baixa só o «?»): o rótulo
          en dous <label for> (ao picar calquera, prémese) e o nome enteiro no botón (aria-label).
        -->
        @if (parts.head) {
          <label class="f-switch__label" [for]="switchId()">{{ parts.head }} </label>
        }
        <span class="f-switch__glue">
          <label class="f-switch__label" [for]="switchId()">{{ parts.last }}</label>
          <ng-container *ngTemplateOutlet="help" />
        </span>
      } @else {
        <!-- Un <label for> nomea o botón e, ao picalo, prémeo (o botón é «labelable»). -->
        <label class="f-switch__label" [class.sr-only]="hideLabel()" [for]="switchId()">{{ label() }}</label>
      }
      @if (tip() && !glued()) {
        <ng-container *ngTemplateOutlet="help" />
      }
      <ng-template #help>
        <furar-help-tip
          [label]="'ui.helpTip.label' | transloco: { label: label() }"
          [text]="tip()"
          [textId]="switchId() + '-help'"
        />
      </ng-template>
    </span>
    <button
      type="button"
      role="switch"
      class="f-switch__control"
      [id]="switchId()"
      [attr.aria-checked]="checked()"
      [attr.aria-label]="state() === null && !glued() ? null : label()"
      [attr.aria-describedby]="describedIds()"
      [disabled]="isDisabled()"
      (click)="toggle()"
      (blur)="onTouched()"
    >
      <span class="f-switch__thumb"></span>
    </button>
    <!-- Debaixo, nunha liña propia (e despois do interruptor na orde do foco). -->
    <span class="f-switch__extra"><ng-content /></span>
  `,
})
export class SwitchComponent implements ControlValueAccessor {
  /** Rótulo visible (xa traducido): di que se acende, non o estado («Reserva por internet»). */
  readonly label = input.required<string>();
  /** Esconde o rótulo á vista (segue sendo o nome) cando o contexto xa o di, p. ex. nunha fila. */
  readonly hideLabel = input(false, { transform: booleanAttribute });
  /** Id da axuda ou do erro que describe o interruptor. */
  readonly describedBy = input<string | null>(null);
  /** Axuda nun «?» tras o rótulo, coma en `furar-field` (id `<switchId>-help`, que describe o botón). */
  readonly tip = input('');
  /** Id do botón. */
  readonly switchId = input(`furar-switch-${nextId++}`);
  /** Texto visible acendido («Aberto»); con `offText`, substitúe o rótulo á vista. */
  readonly onText = input<string | null>(null);
  /** Texto visible apagado («Pechado»). */
  readonly offText = input<string | null>(null);

  protected readonly checked = signal(false);
  protected readonly describedIds = computed(
    () => [this.tip() ? `${this.switchId()}-help` : '', this.describedBy() ?? ''].filter(Boolean).join(' ') || null,
  );
  /** Co «?» e un rótulo visible: o texto antes da última palabra e a última (que vai co «?»). */
  protected readonly glued = computed<{ head: string; last: string } | null>(() => {
    if (!this.tip() || this.hideLabel() || this.state() !== null) return null;
    const words = this.label().trim().split(/\s+/);
    const last = words.pop() ?? '';
    return { head: words.join(' '), last };
  });
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
