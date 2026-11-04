// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { CardComponent } from '../shared/ui/card/card.component';
import { FieldComponent } from '../shared/ui/field.component';
import { SelectComponent } from '../shared/ui/select/select.component';

/**
 * Sección «Despregable» do catálogo (só desenvolvemento): `furar-select` con valor e unha opción
 * desactivada, baleiro, con erro e desactivado.
 */
@Component({
  selector: 'furar-catalog-select',
  imports: [TranslocoPipe, ReactiveFormsModule, CardComponent, FieldComponent, SelectComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <furar-card [heading]="'catalog.select' | transloco" id="despregable">
      @let sampleOptions =
        [
          { value: 'a', label: ('catalog.sample.optionA' | transloco) },
          { value: 'b', label: ('catalog.sample.optionB' | transloco) },
          { value: 'c', label: ('catalog.sample.optionC' | transloco), disabled: true },
        ];
      <div class="gap-form grid sm:grid-cols-2" [formGroup]="choices">
        <furar-field
          fieldId="catalog-select"
          [label]="'catalog.sample.selectFilled' | transloco"
          [help]="'catalog.sample.selectHelp' | transloco"
        >
          <furar-select
            selectId="catalog-select"
            formControlName="filled"
            describedBy="catalog-select-help"
            [label]="'catalog.sample.selectFilled' | transloco"
            [options]="sampleOptions"
          />
        </furar-field>
        <furar-field fieldId="catalog-select-empty" [label]="'catalog.sample.selectEmpty' | transloco">
          <furar-select
            selectId="catalog-select-empty"
            formControlName="empty"
            [label]="'catalog.sample.selectEmpty' | transloco"
            [placeholder]="'catalog.sample.selectPlaceholder' | transloco"
            [options]="sampleOptions"
          />
        </furar-field>
        <furar-field
          fieldId="catalog-select-error"
          [label]="'catalog.sample.selectError' | transloco"
          [error]="requiredError"
        >
          <furar-select
            selectId="catalog-select-error"
            formControlName="error"
            describedBy="catalog-select-error-error"
            invalid
            [label]="'catalog.sample.selectError' | transloco"
            [placeholder]="'catalog.sample.selectPlaceholder' | transloco"
            [options]="sampleOptions"
          />
        </furar-field>
        <furar-field fieldId="catalog-select-off" [label]="'catalog.sample.selectDisabled' | transloco">
          <furar-select
            selectId="catalog-select-off"
            formControlName="off"
            [label]="'catalog.sample.selectDisabled' | transloco"
            [options]="sampleOptions"
          />
        </furar-field>
      </div>
    </furar-card>
  `,
})
export class CatalogSelectComponent {
  protected readonly requiredError = { key: 'errors.validation.required', params: {} };
  protected readonly choices = new FormGroup({
    filled: new FormControl('a'),
    empty: new FormControl<string | null>(null),
    error: new FormControl<string | null>(null),
    off: new FormControl({ value: 'b', disabled: true }),
  });
}
