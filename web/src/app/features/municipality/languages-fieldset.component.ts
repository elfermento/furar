// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { Language } from '../../core/i18n/languages';
import { FieldLineComponent } from '../../shared/ui/field-line/field-line.component';
import { fieldError } from '../../shared/ui/field-errors';
import { FieldComponent } from '../../shared/ui/field.component';
import { SegmentedOptionDirective, SegmentedRadioDirective } from '../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../shared/ui/segmented/segmented.directive';
import { SelectComponent } from '../../shared/ui/select/select.component';
import type { LanguagesForm } from './municipality-form';

/**
 * «Idiomas» do concello (sistema de campos): un despregable de selección múltiple (Galego,
 * Castelán) no grupo de campos de quen o usa (`furar-field-group`), co erro «polo menos un» que
 * pinta o grupo. O idioma por defecto vai fóra do grupo, nunha fila solta
 * (`furar-default-language-line`).
 */
@Component({
  selector: 'furar-languages-fieldset',
  imports: [FieldComponent, SelectComponent, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid' },
  template: `
    @let error = fieldError(form(), submitted());
    @let id = idPrefix() + '-languages';
    <furar-field
      data-languages-row
      [fieldId]="id"
      [label]="'settings.languages' | transloco"
      [required]="true"
      [error]="error"
    >
      <furar-select
        multiple
        required
        data-languages-options
        [selectId]="id"
        [label]="'settings.languages' | transloco"
        [options]="[
          { value: 'gl', label: 'lang.gl' | transloco },
          { value: 'es', label: 'lang.es' | transloco },
        ]"
        [value]="chosen()"
        [invalid]="!!error"
        [describedBy]="error ? id + '-error' : null"
        (changedMany)="choose($event)"
      />
    </furar-field>
  `,
})
export class LanguagesFieldsetComponent {
  readonly form = input.required<LanguagesForm>();
  readonly submitted = input(false);
  readonly idPrefix = input('languages');
  protected readonly fieldError = fieldError;

  protected chosen(): Language[] {
    const { gl, es } = this.form().getRawValue();
    return [...(gl ? (['gl'] as const) : []), ...(es ? (['es'] as const) : [])];
  }

  protected choose(languages: readonly string[]): void {
    const form = this.form();
    form.controls.gl.setValue(languages.includes('gl'));
    form.controls.es.setValue(languages.includes('es'));
    form.markAsDirty();
    form.markAsTouched();
  }
}

/** «Idioma por defecto»: fila solta co segmentado Galego · Castelán á dereita. */
@Component({
  selector: 'furar-default-language-line',
  imports: [
    FieldLineComponent,
    ReactiveFormsModule,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid' },
  template: `
    <furar-field-line
      #line
      data-default-language-field
      [label]="'settings.defaultLanguage' | transloco"
      [labelId]="idPrefix() + '-default-language'"
    >
      <div
        furarSegmented
        mode="radio"
        [formControl]="form().controls.defaultLanguage"
        [attr.aria-labelledby]="line.labelId()"
      >
        @for (code of languages; track code) {
          <button type="button" [furarSegmentedOption]="code" [attr.data-default-language]="code">
            {{ 'lang.' + code | transloco }}
          </button>
        }
      </div>
    </furar-field-line>
  `,
})
export class DefaultLanguageLineComponent {
  readonly form = input.required<LanguagesForm>();
  readonly idPrefix = input('languages');
  protected readonly languages = ['gl', 'es'] as const;
}

/** «Idiomas» (no grupo de campos) e «Idioma por defecto» (fila solta), para os `imports`. */
export const LANGUAGE_FIELDS = [LanguagesFieldsetComponent, DefaultLanguageLineComponent] as const;
