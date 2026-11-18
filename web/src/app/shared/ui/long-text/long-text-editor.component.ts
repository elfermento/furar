// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import type { ElementRef } from '@angular/core';
import { ChangeDetectionStrategy, Component, afterNextRender, inject, signal, viewChild } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import type { Language } from '../../../core/i18n/languages';
import { LANGUAGES } from '../../../core/i18n/languages';
import { ButtonDirective } from '../button/button.directive';
import { DialogComponent } from '../dialog/dialog.component';
import { InputDirective } from '../input/input.directive';
import { SegmentedDirective } from '../segmented/segmented.directive';

/** Un texto só (`value`) ou un por idioma (`gl` e `es`). */
export type LongTextKey = 'value' | Language;

export interface LongTextData {
  /** O rótulo do campo: título da folla e nome do texto. */
  readonly heading: string;
  /** Os textos que se editan: `{ value }` ou `{ gl, es }`. */
  readonly texts: Readonly<Partial<Record<LongTextKey, string>>>;
  /** O texto á vista ao abrir (o idioma do campo). */
  readonly key: LongTextKey;
  /** Campo dunha liña (nome, enderezo): sen saltos de liña. */
  readonly singleLine: boolean;
  readonly maxlength: number | null;
}

export interface LongTextResult {
  readonly texts: Readonly<Partial<Record<LongTextKey, string>>>;
  /** O texto que quedou á vista (o idioma escollido ao confirmar). */
  readonly key: LongTextKey;
}

/**
 * Editor dun texto longo (`[furarLongText]`): o texto enteiro nunha área que enche a folla, GL/ES
 * se é bilingüe e «Confirmar». Pechar, Esc ou picar fóra descartan os cambios.
 */
@Component({
  selector: 'furar-long-text-editor',
  imports: [ButtonDirective, DialogComponent, InputDirective, SegmentedDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-dialog class="f-long-text" [heading]="data.heading">
      @if (bilingual) {
        <div class="f-long-text__lang">
          <div
            furarSegmented
            role="group"
            [attr.aria-label]="'ui.bilingual.language' | transloco: { label: data.heading }"
          >
            @for (l of langs; track l) {
              <button type="button" [attr.aria-pressed]="key() === l" [attr.data-lang]="l" (click)="show(l)">
                {{ 'lang.short.' + l | transloco }}
                <span class="sr-only" [attr.lang]="l">{{ 'lang.endonym.' + l | transloco }}</span>
              </button>
            }
          </div>
        </div>
      }
      <textarea
        #input
        furarInput
        class="f-long-text__input"
        data-long-text-input
        [attr.aria-label]="data.heading"
        [attr.lang]="bilingual ? key() : null"
        [attr.maxlength]="data.maxlength"
        (input)="write(input.value)"
        (keydown.enter)="onEnter($event)"
      ></textarea>
      <button dialogActions type="button" furarButton="primary" data-long-text-confirm (click)="confirm()">
        {{ 'ui.longText.confirm' | transloco }}
      </button>
    </furar-dialog>
  `,
})
export class LongTextEditorComponent {
  protected readonly data = inject<LongTextData>(DIALOG_DATA);
  private readonly ref = inject<DialogRef<LongTextResult>>(DialogRef);
  private readonly area = viewChild.required<ElementRef<HTMLTextAreaElement>>('input');

  protected readonly langs = LANGUAGES;
  protected readonly bilingual = this.data.key !== 'value';
  protected readonly key = signal<LongTextKey>(this.data.key);
  private readonly texts: Partial<Record<LongTextKey, string>> = { ...this.data.texts };

  constructor() {
    afterNextRender(() => this.fill());
  }

  /** Cambia de idioma sen perder o escrito no outro. */
  protected show(key: LongTextKey): void {
    this.key.set(key);
    this.fill();
  }

  protected write(value: string): void {
    this.texts[this.key()] = value;
  }

  /** Nun campo dunha liña, Intro non fai salto. */
  protected onEnter(event: Event): void {
    if (this.data.singleLine) event.preventDefault();
  }

  protected confirm(): void {
    const texts = Object.fromEntries(
      Object.entries(this.texts).map(([key, text]) => [key, this.data.singleLine ? oneLine(text ?? '') : text]),
    ) as Partial<Record<LongTextKey, string>>;
    this.ref.close({ texts, key: this.key() });
  }

  private fill(): void {
    this.area().nativeElement.value = this.texts[this.key()] ?? '';
  }
}

/** Os saltos de liña (dun texto pegado) pasan a espazos. */
function oneLine(text: string): string {
  return text.replace(/\s*[\r\n]+\s*/g, ' ');
}
