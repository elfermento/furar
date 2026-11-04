// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../core/i18n/language.service';
import { FieldsetDirective } from '../../shared/ui/card/fieldset.directive';
import { fieldError } from '../../shared/ui/field-errors';
import { OptionsPipe } from '../../shared/ui/select/options.pipe';
import { RequiredMarkComponent } from '../../shared/ui/required/required.component';
import { SelectComponent } from '../../shared/ui/select/select.component';
import type { LanguagesForm } from './municipality-form';

/** Idiomas do concello e idioma por defecto. */
@Component({
  selector: 'furar-languages-fieldset',
  imports: [ReactiveFormsModule, TranslocoPipe, FieldsetDirective, OptionsPipe, RequiredMarkComponent, SelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let error = fieldError(form(), submitted());
    <fieldset
      furarFieldset
      class="mb-group"
      [formGroup]="form()"
      [attr.aria-describedby]="error ? idPrefix() + '-languages-error' : null"
    >
      <legend>
        {{ 'settings.languages' | transloco }}
        <furar-required-mark />
      </legend>
      <div class="mb-form gap-x-section flex flex-wrap">
        <label class="inline-flex min-h-11 items-center gap-unit" lang="gl">
          <input type="checkbox" class="size-6" formControlName="gl" [attr.aria-invalid]="error ? 'true' : null" />
          {{ 'lang.gl' | transloco }}
        </label>
        <label class="inline-flex min-h-11 items-center gap-unit" lang="es">
          <input type="checkbox" class="size-6" formControlName="es" />
          {{ 'lang.es' | transloco }}
        </label>
      </div>
      @if (error; as e) {
        <span class="f-field-error mb-form" [id]="idPrefix() + '-languages-error'">{{
          e.key | transloco: e.params
        }}</span>
      }
      <div class="f-field">
        <label class="f-label" [for]="idPrefix() + '-default-language'">{{
          'settings.defaultLanguage' | transloco
        }}</label>
        <furar-select
          formControlName="defaultLanguage"
          [selectId]="idPrefix() + '-default-language'"
          [label]="'settings.defaultLanguage' | transloco"
          [options]="languages | furarOptions: 'lang.' : lang()"
        />
      </div>
    </fieldset>
  `,
})
export class LanguagesFieldsetComponent {
  readonly form = input.required<LanguagesForm>();
  readonly submitted = input(false);
  readonly idPrefix = input('languages');
  protected readonly fieldError = fieldError;
  protected readonly languages = ['gl', 'es'] as const;
  protected readonly lang = inject(LanguageService).lang;
}
