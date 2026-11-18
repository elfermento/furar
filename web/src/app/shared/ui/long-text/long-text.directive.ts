// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Directive, ElementRef, computed, inject, input, output, signal } from '@angular/core';
import type { FormControl } from '@angular/forms';
import { NgControl } from '@angular/forms';
import type { Language } from '../../../core/i18n/languages';
import { DialogService, SHEET_QUERY } from '../dialog/dialog.service';
import type { LongTextData, LongTextResult } from './long-text-editor.component';
import { LongTextEditorComponent } from './long-text-editor.component';

/** Os dous textos dun campo bilingüe. */
export interface LongTextLanguages {
  readonly gl: FormControl<string>;
  readonly es: FormControl<string>;
}

/**
 * Campo de texto longo (nome, enderezo, notas, descricións) nun `<input>` ou `<textarea>`
 * `furarInput` dun formulario. No móbil (< 640 px) o campo non se escribe no sitio: tocalo (ou
 * Intro) abre unha folla desde abaixo co texto enteiro e «Confirmar», que o leva ao control;
 * pechar, Esc ou picar fóra descartan. No escritorio, o campo de sempre.
 *
 * `furarLongText` é o título da folla (o rótulo do campo); baleiro, o texto do `<label>` do campo;
 * `null`, apagado. Bilingüe: `[furarLongTextLanguages]` cos dous controis e `furarLongTextLang`, o
 * idioma á vista; a folla leva o GL/ES e confirma os dous.
 */
@Directive({
  selector: 'input[furarLongText], textarea[furarLongText]',
  host: {
    '[readOnly]': 'active()',
    '[attr.data-long-text]': 'active() ? "" : null',
    '(click)': 'open()',
    '(keydown.enter)': 'onEnter($event)',
  },
})
export class LongTextDirective {
  readonly heading = input<string | null | undefined>('', { alias: 'furarLongText' });
  readonly furarLongTextLanguages = input<LongTextLanguages | null>(null);
  readonly furarLongTextLang = input<Language>('gl');
  /** O idioma que quedou á vista na folla ao confirmar. */
  readonly furarLongTextLangChange = output<Language>();

  private readonly element = inject<ElementRef<HTMLInputElement | HTMLTextAreaElement>>(ElementRef).nativeElement;
  private readonly ngControl = inject(NgControl, { self: true, optional: true });
  private readonly dialog = inject(DialogService);
  private readonly narrow = signal(false);
  private opened = false;

  /** No móbil e acendido: o campo ábrese na folla. */
  protected readonly active = computed(() => this.narrow() && this.heading() !== null);

  constructor() {
    const media = inject(DOCUMENT).defaultView?.matchMedia?.(SHEET_QUERY);
    this.narrow.set(media?.matches ?? false);
    const onChange = (event: MediaQueryListEvent) => this.narrow.set(event.matches);
    media?.addEventListener?.('change', onChange);
    inject(DestroyRef).onDestroy(() => media?.removeEventListener?.('change', onChange));
  }

  protected onEnter(event: Event): void {
    if (!this.active()) return;
    // Nun `<input>` dun formulario, Intro enviaríao.
    event.preventDefault();
    this.open();
  }

  /** Abre a folla co texto enteiro (só no móbil). */
  open(): void {
    if (!this.active() || this.opened || this.element.disabled) return;
    const languages = this.furarLongTextLanguages();
    const data: LongTextData = {
      heading: this.heading() || this.labelText(),
      texts: languages
        ? { gl: languages.gl.value ?? '', es: languages.es.value ?? '' }
        : { value: String(this.ngControl?.value ?? this.element.value ?? '') },
      key: languages ? this.furarLongTextLang() : 'value',
      singleLine: this.element instanceof HTMLInputElement,
      maxlength: this.element.maxLength > 0 ? this.element.maxLength : null,
    };
    this.opened = true;
    const ref = this.dialog.open<LongTextResult, LongTextData>(LongTextEditorComponent, {
      data,
      from: this.element,
      restoreFocus: this.element,
    });
    ref.closed.subscribe((result) => {
      this.opened = false;
      if (result) this.apply(result);
    });
  }

  private apply({ texts, key }: LongTextResult): void {
    const languages = this.furarLongTextLanguages();
    if (languages) {
      for (const lang of ['gl', 'es'] as const) set(languages[lang], texts[lang]);
      if (key !== 'value') this.furarLongTextLangChange.emit(key);
      return;
    }
    const control = this.ngControl?.control;
    if (control) set(control, texts.value);
    else if (texts.value !== undefined) this.element.value = texts.value;
  }

  /** O rótulo do campo, sen a marca de obrigatorio nin o texto só para lectores. */
  private labelText(): string {
    const label = this.element.labels?.[0];
    if (!label) return this.element.getAttribute('aria-label') ?? '';
    const copy = label.cloneNode(true) as HTMLElement;
    copy.querySelectorAll('furar-required-mark, .sr-only').forEach((node) => node.remove());
    return copy.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }
}

/** Leva o texto ao control se cambiou, como se se escribise (sucio e tocado). */
function set(
  control: { value: unknown; setValue(value: string): void; markAsDirty(): void; markAsTouched(): void },
  text: string | undefined,
): void {
  if (text === undefined) return;
  control.markAsTouched();
  if (text === (control.value ?? '')) return;
  control.setValue(text);
  control.markAsDirty();
}
