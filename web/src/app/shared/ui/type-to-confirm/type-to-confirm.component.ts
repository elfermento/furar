// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input, model, output, signal } from '@angular/core';
import { TranslocoPipe } from '@jsverse/transloco';
import { InputDirective } from '../input/input.directive';

let nextId = 0;

/**
 * O texto para comparar coa frase: sen maiúsculas, sen tildes nin outros diacríticos, sen espazos
 * nos extremos e cun só espazo entre palabras. «Conserxe», « CONSERXE » e «conserxé» valen igual.
 */
export function normalizePhrase(text: string): string {
  return text.normalize('NFD').replace(/\p{M}/gu, '').trim().replace(/\s+/g, ' ').toLocaleLowerCase();
}

/** Se o escrito é a frase, sen distinguir maiúsculas, tildes nin espazos de máis. */
export function phraseMatches(typed: string, phrase: string): boolean {
  const expected = normalizePhrase(phrase);
  return expected !== '' && normalizePhrase(typed) === expected;
}

/**
 * Escribir unha palabra para confirmar (docs/deseno.md §8): para o que custa desfacer (eliminar
 * unha instalación, desactivar unha persoa, baixar un xestor a conserxe). Un campo co seu rótulo
 * visible («Para confirmar, escribe «desactivar»») e `matched` cando coincide. Quen o usa deixa a
 * acción con `[unavailable]` (`aria-disabled`, patrón `furarAction`) mentres non coincida e
 * apunta a ela co `aria-describedby` do rótulo (`labelId`), para que o lector diga por que está
 * bloqueada. Intro no campo emite `entered`. Se o campo desaparece e volve, quen o usa pon
 * `matched` a falso: o campo volve baleiro.
 */
@Component({
  selector: 'furar-type-to-confirm',
  imports: [InputDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="gap-unit grid">
      <label class="f-label" [id]="labelId" [for]="inputId">
        {{ 'ui.confirm.typeToConfirm' | transloco: { phrase: phrase() } }}
      </label>
      <input
        furarInput
        [id]="inputId"
        autocomplete="off"
        autocapitalize="off"
        spellcheck="false"
        data-confirm-phrase
        [attr.aria-describedby]="describedBy() || null"
        [value]="typed()"
        (input)="onType($event)"
        (keydown.enter)="entered.emit()"
      />
    </div>
  `,
})
export class TypeToConfirmComponent {
  /** A palabra ou frase que hai que escribir, xa traducida. */
  readonly phrase = input.required<string>();
  /** Ids que describen o campo (p. ex. o que pasará), ademais do rótulo. */
  readonly describedBy = input<string | null>(null);
  /** Se o escrito coincide coa frase. */
  readonly matched = model(false);
  /** Intro no campo. */
  readonly entered = output();

  private readonly id = nextId++;
  readonly inputId = `furar-type-to-confirm-${this.id}`;
  /** Id do rótulo, para o `aria-describedby` da acción bloqueada. */
  readonly labelId = `furar-type-to-confirm-${this.id}-label`;
  protected readonly typed = signal('');

  protected onType(event: Event): void {
    const value = (event.target as HTMLInputElement).value;
    this.typed.set(value);
    this.matched.set(phraseMatches(value, this.phrase()));
  }
}
