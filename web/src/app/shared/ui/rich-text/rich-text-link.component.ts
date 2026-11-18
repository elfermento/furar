// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  ChangeDetectionStrategy,
  Component,
  afterNextRender,
  input,
  linkedSignal,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ControlError } from '../../custom-fields/custom-field-controls';
import { ButtonDirective } from '../button/button.directive';
import { FieldComponent } from '../field.component';
import { InputDirective } from '../input/input.directive';
import { RICH_TEXT_LINK } from './rich-text-link';

/**
 * Campo da ligazón do editor de texto, baixo a barra: enderezo (https ou correo), «Engadir» e
 * «Cancelar». Intro engade, Esc cancela (só o campo, non a capa onde estea o editor). Sae con
 * `done`: o enderezo ou `null` se se cancela.
 */
@Component({
  selector: 'furar-rich-text-link',
  imports: [ButtonDirective, FieldComponent, InputDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-rte__link' },
  template: `
    <furar-field
      class="f-rte__link-field"
      [fieldId]="fieldId()"
      [label]="'ui.richText.linkUrl' | transloco"
      [error]="error()"
    >
      <input
        #input
        furarInput
        type="url"
        inputmode="url"
        autocomplete="off"
        spellcheck="false"
        [id]="fieldId()"
        [value]="value()"
        [attr.aria-invalid]="error() ? 'true' : null"
        [attr.aria-describedby]="error() ? fieldId() + '-error' : null"
        (input)="onInput($event)"
        (keydown.enter)="add($event)"
        (keydown.escape)="cancel($event)"
      />
    </furar-field>
    <button type="button" furarButton="primary" size="sm" (click)="add()">
      {{ 'ui.richText.linkAdd' | transloco }}
    </button>
    <button type="button" furarButton="quiet" size="sm" (click)="cancel()">
      {{ 'ui.richText.linkCancel' | transloco }}
    </button>
  `,
})
export class RichTextLinkComponent {
  private readonly input = viewChild.required<ElementRef<HTMLInputElement>>('input');

  readonly fieldId = input.required<string>();
  /** A ligazón que xa hai onde está o cursor, para cambiala. */
  readonly href = input('');
  readonly done = output<string | null>();

  protected readonly value = linkedSignal(() => this.href());
  protected readonly error = signal<ControlError | null>(null);

  constructor() {
    afterNextRender(() => this.input().nativeElement.focus());
  }

  protected onInput(event: Event): void {
    this.value.set((event.target as HTMLInputElement).value);
    if (this.error()) this.error.set(this.check());
  }

  protected add(event?: Event): void {
    event?.preventDefault();
    const error = this.check();
    this.error.set(error);
    if (error) {
      this.input().nativeElement.focus();
      return;
    }
    this.done.emit(this.value().trim());
  }

  protected cancel(event?: Event): void {
    event?.preventDefault();
    event?.stopPropagation();
    this.done.emit(null);
  }

  private check(): ControlError | null {
    const value = this.value().trim();
    if (!value) return { key: 'ui.richText.linkRequired', params: {} };
    return RICH_TEXT_LINK.test(value) ? null : { key: 'ui.richText.linkInvalid', params: {} };
  }
}
