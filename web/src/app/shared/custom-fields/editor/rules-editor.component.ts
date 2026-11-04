// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { map } from 'rxjs';
import { FieldsetDirective } from '../../ui/card/fieldset.directive';
import type { SelectOption } from '../../ui/select/select.component';
import { SelectComponent } from '../../ui/select/select.component';
import type { CustomFieldFormat, CustomFieldRules, CustomFieldType } from '../custom-field.types';
import { CUSTOM_FIELD_FORMATS } from '../custom-field.types';
import type { RulesForm } from './editor-form';
import { rulesFor } from './editor-form';

/** Regras de validación que ten cada tipo (sen regex libre: ADR-0011). */
@Component({
  selector: 'furar-rules-editor',
  imports: [ReactiveFormsModule, TranslocoPipe, FieldsetDirective, SelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (rules().length) {
      <fieldset furarFieldset class="mb-group" [formGroup]="form()">
        <legend>{{ 'customFields.editor.rules' | transloco }}</legend>
        <div class="f-field-row grid-cols-1 sm:grid-cols-2">
          @for (rule of rules(); track rule) {
            <div class="f-field">
              <label class="label" [for]="'rule-' + rule">{{ 'customFields.rules.' + rule | transloco }}</label>
              @if (rule === 'format') {
                <furar-select
                  formControlName="format"
                  [selectId]="'rule-' + rule"
                  [label]="'customFields.rules.' + rule | transloco"
                  [options]="formatOptions()"
                />
              } @else if (rule === 'minDate' || rule === 'maxDate') {
                <input type="date" class="input" [id]="'rule-' + rule" [formControlName]="rule" />
              } @else {
                <input type="number" class="input" [id]="'rule-' + rule" [formControlName]="rule" [step]="step(rule)" />
              }
            </div>
          }
        </div>
      </fieldset>
    }
  `,
})
export class RulesEditorComponent {
  readonly form = input.required<RulesForm>();
  readonly type = input.required<CustomFieldType>();
  /** «Ningún» (null) e os formatos, no idioma activo. */
  protected readonly formatOptions = toSignal(
    inject(TranslocoService)
      .selectTranslate<string[]>([
        'customFields.format.none',
        ...CUSTOM_FIELD_FORMATS.map((format) => `customFields.format.${format}`),
      ])
      .pipe(
        map((labels): SelectOption<CustomFieldFormat | null>[] =>
          [null, ...CUSTOM_FIELD_FORMATS].map((value, i) => ({ value, label: labels[i] ?? '' })),
        ),
      ),
    { initialValue: [] },
  );
  protected readonly rules = computed(() => rulesFor(this.type()));

  protected step(rule: keyof CustomFieldRules): string {
    return rule === 'min' || rule === 'max' ? 'any' : '1';
  }
}
