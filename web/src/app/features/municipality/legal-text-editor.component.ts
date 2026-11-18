// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, effect, input, output, signal, viewChild } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { Language } from '../../core/i18n/languages';
import type { LegalPageKey } from '../../core/municipality/legal-pages';
import { ButtonDirective } from '../../shared/ui/button/button.directive';
import { fieldError } from '../../shared/ui/field-errors';
import { IconComponent } from '../../shared/ui/icon/icon.component';
import { RichTextEditorComponent } from '../../shared/ui/rich-text/rich-text-editor.component';
import { SegmentedDirective } from '../../shared/ui/segmented/segmented.directive';
import type { LegalTextForm } from './legal-form';

/**
 * «Texto propio» dunha páxina legal: o mesmo conmutador GL/ES que
 * `furar-bilingual-field` (á dereita da liña do rótulo, aquí oculto porque «Texto propio» xa está
 * enriba), a liña que avisa de que os datos xa non se insiren sós, o editor común
 * (`furar-rich-text-editor`), o erro co idioma diante («Castelán: …»), a vista previa e «Volver
 * ao texto de Furar» (confírmao quen o usa).
 */
@Component({
  selector: 'furar-legal-text-editor',
  imports: [
    ButtonDirective,
    IconComponent,
    ReactiveFormsModule,
    RichTextEditorComponent,
    SegmentedDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-legal__text' },
  template: `
    @let id = 'settings-legal-' + page() + '-text';
    @let hintId = id + '-hint';
    <div class="f-bilingual">
      <div class="f-bilingual__head">
        <!-- Rótulo á vista coma nos outros campos bilingües; o do editor (con páxina e idioma) dino os lectores. -->
        <span class="f-label" aria-hidden="true">{{ 'settings.legal.textHead' | transloco }}</span>
        <div furarSegmented role="group" [attr.aria-label]="'settings.legal.textLanguage' | transloco">
          @for (code of languages; track code) {
            <button
              type="button"
              [attr.aria-pressed]="lang() === code"
              [attr.data-lang]="code"
              (click)="lang.set(code)"
            >
              {{ 'lang.short.' + code | transloco }}
              <span class="sr-only" [attr.lang]="code">{{ 'lang.endonym.' + code | transloco }}</span>
            </button>
          }
        </div>
      </div>
      <span class="f-help" [id]="hintId">{{ 'settings.legal.textHint' | transloco }}</span>
      @for (code of [lang()]; track code) {
        @let editorId = id + '-' + code;
        <furar-rich-text-editor
          [formControl]="form().controls[code]"
          [fieldId]="editorId"
          [lang]="code"
          [label]="
            'settings.legal.textLabel'
              | transloco
                : {
                    page: ('settings.legal.of.' + page() | transloco),
                    lang: ('settings.legal.langName.' + code | transloco),
                  }
          "
          [describedBy]="errors().length ? hintId + ' ' + id + '-error' : hintId"
          [invalid]="!!errorOf(code)"
        />
      }
      @if (errors().length) {
        <span class="f-field-error" [id]="id + '-error'">
          @for (e of errors(); track e.lang) {
            <span class="block">
              {{ 'ui.bilingual.errorIn' | transloco: { lang: 'lang.' + e.lang | transloco } }}
              {{ e.error.key | transloco: e.error.params }}
            </span>
          }
        </span>
      }
    </div>
    <div class="f-legal__actions">
      <button type="button" furarButton size="sm" data-legal-preview (click)="preview.emit(lang())">
        <furar-icon name="visibility" />
        {{ 'settings.legal.preview' | transloco }}
      </button>
      <button type="button" furarButton="quiet" size="sm" data-legal-reset (click)="restore.emit()">
        {{ 'settings.legal.reset' | transloco }}
      </button>
    </div>
  `,
})
export class LegalTextEditorComponent {
  private readonly editor = viewChild(RichTextEditorComponent);

  readonly page = input.required<LegalPageKey>();
  readonly form = input.required<LegalTextForm>();
  readonly submitted = input(false);
  /** Vista previa do idioma que se está a editar. */
  readonly preview = output<Language>();
  /** «Volver ao texto de Furar» (quen o usa pregunta antes). */
  readonly restore = output();

  protected readonly languages: readonly Language[] = ['gl', 'es'];
  protected readonly lang = signal<Language>('gl');
  /** Sobe con cada cambio do formulario: os erros síguen (OnPush). */
  private readonly revision = signal(0);
  protected readonly errors = computed(() => {
    this.revision();
    const form = this.form();
    const submitted = this.submitted();
    return this.languages.flatMap((lang) => {
      const error = fieldError(form.controls[lang], submitted);
      return error ? [{ lang, error }] : [];
    });
  });

  protected errorOf(lang: Language) {
    return this.errors().find((e) => e.lang === lang)?.error ?? null;
  }

  constructor() {
    effect((onCleanup) => {
      const subscription = this.form().events.subscribe(() => this.revision.update((n) => n + 1));
      onCleanup(() => subscription.unsubscribe());
    });
  }

  /** Foco na área (despois de volver ao texto de Furar). */
  focus(): void {
    this.editor()?.focus();
  }

  /** Vai ao idioma co erro, para que o foco poida chegar a el. */
  showFirstError(): void {
    const bad = this.languages.find((code) => this.form().controls[code].invalid);
    if (bad && !this.form().controls[this.lang()].invalid) this.lang.set(bad);
  }
}
