// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import {
  ChangeDetectionStrategy,
  Component,
  booleanAttribute,
  computed,
  effect,
  inject,
  input,
  model,
  numberAttribute,
  signal,
  untracked,
} from '@angular/core';
import type { FormControl } from '@angular/forms';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../core/i18n/language.service';
import type { Language } from '../../../core/i18n/languages';
import { LANGUAGES } from '../../../core/i18n/languages';
import type { ControlError } from '../../custom-fields/custom-field-controls';
import { fieldError } from '../field-errors';
import { InputDirective } from '../input/input.directive';
import { RequiredMarkComponent } from '../required/required.component';
import { SegmentedDirective } from '../segmented/segmented.directive';

/**
 * Texto en galego e castelán nun só campo (docs/deseno.md §8): o rótulo, un conmutador GL/ES
 * (`[furarSegmented]` con `aria-pressed`) e un único campo que mostra e edita o idioma escollido,
 * co seu `lang`; a axuda entre a cabeceira e o campo, e o erro debaixo. Cada idioma é o seu `FormControl` (`[gl]` e `[es]`), así os validadores e os erros
 * da API seguen indo ao seu control. O erro vese debaixo co idioma diante («Castelán: …»); se o
 * idioma á vista está ben e o outro ten erro, o campo pasa ao outro para que o foco chegue a el.
 * Comeza no idioma de quen mira.
 */
@Component({
  selector: 'furar-bilingual-field',
  imports: [InputDirective, ReactiveFormsModule, RequiredMarkComponent, SegmentedDirective, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-bilingual' },
  template: `
    <div class="f-bilingual__head">
      <label class="f-label" [class.sr-only]="hideLabel()" [for]="fieldId()">
        {{ label() }}
        @if (required()) {
          <furar-required-mark />
        }
      </label>
      <div furarSegmented role="group" [attr.aria-label]="'ui.bilingual.language' | transloco: { label: label() }">
        @for (l of langs; track l) {
          <button type="button" [attr.aria-pressed]="lang() === l" [attr.data-lang]="l" (click)="lang.set(l)">
            {{ 'lang.short.' + l | transloco }}
            <span class="sr-only" [attr.lang]="l">{{ 'lang.endonym.' + l | transloco }}</span>
          </button>
        }
      </div>
    </div>
    @if (help()) {
      <span class="f-help" [id]="fieldId() + '-help'">{{ help() }}</span>
    }
    @if (multiline()) {
      <textarea
        furarInput
        [id]="fieldId()"
        [rows]="rows()"
        [attr.lang]="lang()"
        [attr.maxlength]="maxlength()"
        [formControl]="control()"
        [attr.aria-required]="required() || null"
        [attr.aria-invalid]="errors()[lang()] ? 'true' : null"
        [attr.aria-describedby]="describedBy()"
      ></textarea>
    } @else {
      <input
        furarInput
        autocomplete="off"
        [id]="fieldId()"
        [attr.lang]="lang()"
        [attr.maxlength]="maxlength()"
        [formControl]="control()"
        [attr.aria-required]="required() || null"
        [attr.aria-invalid]="errors()[lang()] ? 'true' : null"
        [attr.aria-describedby]="describedBy()"
      />
    }
    @if (shownErrors().length) {
      <span class="f-field-error" [id]="fieldId() + '-error'">
        @for (e of shownErrors(); track e.lang) {
          <span class="block">
            {{ 'ui.bilingual.errorIn' | transloco: { lang: 'lang.' + e.lang | transloco } }}
            {{ e.error.key | transloco: e.error.params }}
          </span>
        }
      </span>
    }
  `,
})
export class BilingualFieldComponent {
  readonly fieldId = input.required<string>();
  readonly label = input.required<string>();
  readonly help = input('');
  readonly required = input(false, { transform: booleanAttribute });
  /** O rótulo só para lectores, cando un título xusto enriba xa di o mesmo. */
  readonly hideLabel = input(false, { transform: booleanAttribute });
  readonly gl = input.required<FormControl<string>>();
  readonly es = input.required<FormControl<string>>();
  /** Área de texto (descricións) ou campo dunha liña (nomes). */
  readonly multiline = input(false, { transform: booleanAttribute });
  readonly rows = input(4, { transform: numberAttribute });
  readonly maxlength = input<number | null>(null);
  /** Xa se intentou enviar: os erros vense aínda que o campo non se tocase. */
  readonly submitted = input(false, { transform: booleanAttribute });
  /** Idioma á vista. */
  readonly lang = model<Language>(inject(LanguageService).lang());

  protected readonly langs = LANGUAGES;
  protected readonly control = computed(() => (this.lang() === 'es' ? this.es() : this.gl()));
  private readonly changes = signal(0);

  protected readonly errors = computed<Record<Language, ControlError | null>>(() => {
    this.changes();
    return { gl: fieldError(this.gl(), this.submitted()), es: fieldError(this.es(), this.submitted()) };
  });
  protected readonly shownErrors = computed(() =>
    LANGUAGES.flatMap((lang) => {
      const error = this.errors()[lang];
      return error ? [{ lang, error }] : [];
    }),
  );
  protected readonly describedBy = computed(
    () =>
      [this.help() ? `${this.fieldId()}-help` : '', this.shownErrors().length ? `${this.fieldId()}-error` : '']
        .filter(Boolean)
        .join(' ') || null,
  );

  constructor() {
    effect((onCleanup) => {
      const subscriptions = [this.gl(), this.es()].map((control) =>
        control.events.subscribe(() => this.changes.update((n) => n + 1)),
      );
      onCleanup(() => subscriptions.forEach((s) => s.unsubscribe()));
    });
    // Se o idioma á vista está ben e o outro non, pásase a el: o foco ao primeiro erro chega.
    effect(() => {
      const errors = this.errors();
      const shown = untracked(this.lang);
      const other: Language = shown === 'gl' ? 'es' : 'gl';
      if (!errors[shown] && errors[other]) untracked(() => this.lang.set(other));
    });
  }
}
