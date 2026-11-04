// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  Directive,
  ElementRef,
  booleanAttribute,
  computed,
  contentChildren,
  forwardRef,
  inject,
  input,
  model,
  signal,
} from '@angular/core';
import type { ControlValueAccessor } from '@angular/forms';
import { NG_VALUE_ACCESSOR } from '@angular/forms';
import { BOTH_AXES, rovingIndex } from '../roving-index';

/**
 * Modo radio de `[furarSegmented]` (plan do dominio 1, D1-05c; docs/deseno.md P1-11): un
 * `radiogroup` real para escoller un modo entre 2 e 5 dentro dun formulario («Se aluga», «Cada
 * reserva dura»). Cada opción é un `<button [furarSegmentedOption]="valor">` con `role="radio"`.
 *
 * Teclado (APG Radio Group): o grupo é unha soa parada de tabulador (a opción escollida ou, se
 * non hai, a primeira dispoñible); as frechas moven o foco e escollen, dando a volta; Inicio e
 * Fin van á primeira e á última; Espazo escolle a enfocada. Funciona con `formControlName`
 * (ControlValueAccessor) ou con `[(value)]`. O nome do grupo pono quen o usa (`aria-label` ou
 * `aria-labelledby`). O modo `aria-pressed` de sempre (selector de idioma) segue igual.
 */
@Directive({
  selector: '[furarSegmented][mode=radio]',
  providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => SegmentedRadioDirective), multi: true }],
  host: {
    role: 'radiogroup',
    '[attr.aria-disabled]': 'isDisabled() || null',
    '(keydown)': 'onKeydown($event)',
    '(focusout)': 'onFocusOut($event)',
  },
})
export class SegmentedRadioDirective<T = unknown> implements ControlValueAccessor {
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly options = contentChildren<SegmentedOptionDirective<T>>(SegmentedOptionDirective, {
    descendants: true,
  });

  /** Valor escollido (o de `furarSegmentedOption` da opción marcada); `null` se non hai ningunha. */
  readonly value = model<T | null>(null);
  private readonly disabledByForm = signal(false);
  readonly isDisabled = this.disabledByForm.asReadonly();

  /** A opción que recibe o tabulador: a escollida e dispoñible ou, se non, a primeira dispoñible. */
  readonly tabStop = computed(() => {
    const enabled = this.options().filter((option) => !option.unavailable());
    return enabled.find((option) => option.value() === this.value()) ?? enabled[0] ?? null;
  });

  private onChange: (value: T | null) => void = () => undefined;
  private onTouched: () => void = () => undefined;

  writeValue(value: T | null): void {
    this.value.set(value);
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

  /** Escolle unha opción (clic, Espazo ou frecha); non avisa se xa estaba escollida. */
  select(option: SegmentedOptionDirective<T>): void {
    if (this.isDisabled() || option.unavailable() || option.value() === this.value()) return;
    this.value.set(option.value());
    this.onChange(option.value());
  }

  protected onKeydown(event: KeyboardEvent): void {
    if (this.isDisabled()) return;
    const enabled = this.options().filter((option) => !option.unavailable());
    const current = enabled.findIndex((option) => option.element.nativeElement === event.target);
    const target = enabled[rovingIndex(event.key, current, enabled.length, BOTH_AXES) ?? -1];
    if (!target) return;
    event.preventDefault();
    target.element.nativeElement.focus();
    this.select(target);
  }

  /** «Tocado» cando o foco sae do grupo enteiro, non ao pasar dunha opción a outra. */
  protected onFocusOut(event: FocusEvent): void {
    const to = event.relatedTarget as Node | null;
    if (!to || !this.host.nativeElement.contains(to)) this.onTouched();
  }
}

/** Unha opción do modo radio de `[furarSegmented]`: `<button type="button" [furarSegmentedOption]="'WHOLE'">`. */
@Directive({
  selector: 'button[furarSegmentedOption]',
  host: {
    role: 'radio',
    '[attr.aria-checked]': 'checked()',
    '[attr.tabindex]': 'group.tabStop() === this ? 0 : -1',
    '[disabled]': 'unavailable()',
    '(click)': 'group.select(this)',
  },
})
export class SegmentedOptionDirective<T = unknown> {
  protected readonly group = inject<SegmentedRadioDirective<T>>(SegmentedRadioDirective);
  readonly element = inject<ElementRef<HTMLButtonElement>>(ElementRef);

  /** Valor da opción (obrigatorio; `null` só mentres Angular non fixa as entradas). */
  readonly value = input<T | null>(null, { alias: 'furarSegmentedOption' });
  /** Opción non dispoñible por si soa; o grupo enteiro desactívase desde o formulario. */
  readonly disabled = input(false, { transform: booleanAttribute });
  /** Non se pode escoller: ela soa ou o grupo enteiro. */
  readonly unavailable = computed(() => this.disabled() || this.group.isDisabled());
  readonly checked = computed(() => this.group.value() === this.value());
}
