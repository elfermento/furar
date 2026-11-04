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
import { TabDirective, TabsComponent } from '../../shared/ui/tabs/tabs.component';
import type { LegalTextForm } from './legal-form';

/**
 * «Texto propio» dunha páxina legal (maqueta 33): pestanas gl/es co editor común
 * (`furar-rich-text-editor`), a liña que avisa de que os datos xa non se insiren sós, a vista
 * previa e «Volver ao texto de Furar» (confírmao quen o usa). O erro dun idioma marca a súa
 * pestana.
 */
@Component({
  selector: 'furar-legal-text-editor',
  imports: [
    ButtonDirective,
    IconComponent,
    ReactiveFormsModule,
    RichTextEditorComponent,
    TabDirective,
    TabsComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-legal__text' },
  template: `
    @let id = 'settings-legal-' + page() + '-text';
    @let hintId = id + '-hint';
    <p class="f-legal__hint" [id]="hintId">{{ 'settings.legal.textHint' | transloco }}</p>
    <furar-tabs [label]="'settings.legal.textLanguage' | transloco" [(selected)]="lang">
      @for (code of languages; track code) {
        <button type="button" [furarTab]="code" [attr.data-lang]="code">
          {{ 'lang.' + code | transloco }}
          @if (errorLangs().includes(code)) {
            <span class="f-legal__tab-error" aria-hidden="true"></span>
            <span class="sr-only">{{ 'settings.legal.withErrors' | transloco }}</span>
          }
        </button>
      }
      @for (code of [lang()]; track code) {
        @let control = form().controls[code];
        @let error = fieldError(control, submitted());
        @let editorId = id + '-' + code;
        <furar-rich-text-editor
          [formControl]="control"
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
          [describedBy]="error ? hintId + ' ' + editorId + '-error' : hintId"
          [invalid]="!!error"
        />
        @if (error; as e) {
          <p class="f-field-error" [id]="editorId + '-error'">{{ e.key | transloco: e.params }}</p>
        }
      }
    </furar-tabs>
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
  protected readonly fieldError = fieldError;
  /** Sobe con cada cambio do formulario: as marcas de erro das pestanas sígueno (OnPush). */
  private readonly revision = signal(0);
  protected readonly errorLangs = computed(() => {
    this.revision();
    const form = this.form();
    const submitted = this.submitted();
    return this.languages.filter((code) => form.controls[code].invalid && (form.controls[code].touched || submitted));
  });

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
