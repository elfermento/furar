// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { describedBy, fieldError } from '../../shared/ui/field-errors';
import { FieldComponent } from '../../shared/ui/field.component';
import { InputDirective } from '../../shared/ui/input/input.directive';
import type { LegalFieldKey, LegalForm } from './legal-form';

type TextFieldKey =
  Exclude<LegalFieldKey, `${string}Mode` | `${string}Url`> | 'eOfficeUrl' | 'accessibilityComplaintUrl';

interface FieldMeta {
  readonly type: 'text' | 'email' | 'url' | 'tel';
  readonly autocomplete?: string;
  /** Leva axuda (`settings.legal.help.<campo>`). */
  readonly help?: boolean;
  readonly numeric?: boolean;
  /** Códigos en maiúsculas (NIF, DIR3): sen corrector nin minúsculas automáticas. */
  readonly code?: boolean;
}

const META: Readonly<Record<TextFieldKey, FieldMeta>> = {
  legalEntityName: { type: 'text', autocomplete: 'organization' },
  eOfficeUrl: { type: 'url' },
  ineCode: { type: 'text', help: true, numeric: true },
  dir3Code: { type: 'text', help: true, code: true },
  nif: { type: 'text', code: true },
  address: { type: 'text', autocomplete: 'street-address' },
  generalEmail: { type: 'email', autocomplete: 'email' },
  dpoEmail: { type: 'email', autocomplete: 'email' },
  dpoPhone: { type: 'tel', autocomplete: 'tel' },
  processor: { type: 'text', autocomplete: 'off', help: true },
  accessibilityEmail: { type: 'email', autocomplete: 'email' },
  accessibilityUnit: { type: 'text', help: true },
  accessibilityComplaintUrl: { type: 'url', help: true },
};
const URL_PLACEHOLDER = 'https://';

/**
 * Campos de texto dos datos legais (maqueta 33), en filas: unha fila de dous parte en dúas
 * columnas se cabe (`.f-field-row`, cos controis á mesma altura). Rótulo enriba, axuda e erro
 * debaixo, ligados (`describedBy`).
 */
@Component({
  selector: 'furar-legal-fields',
  imports: [FieldComponent, InputDirective, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'contents' },
  template: `
    @for (row of rows(); track $index) {
      <div [class.f-field-row]="row.length > 1" [class.contents]="row.length === 1">
        @for (key of row; track key) {
          @let meta = metaOf(key);
          @let id = 'settings-legal-' + key;
          @let control = form().controls[key];
          @let error = fieldError(control, submitted());
          <furar-field
            [fieldId]="id"
            [label]="'settings.legal.field.' + key | transloco"
            [help]="meta.help ? ('settings.legal.help.' + key | transloco) : ''"
            [error]="error"
          >
            <input
              furarInput
              [id]="id"
              [type]="meta.type"
              [formControl]="control"
              [attr.autocomplete]="meta.autocomplete ?? 'off'"
              [attr.inputmode]="meta.numeric ? 'numeric' : null"
              [attr.maxlength]="meta.numeric ? 5 : null"
              [attr.autocapitalize]="meta.code ? 'characters' : null"
              [attr.spellcheck]="meta.code || meta.type === 'url' ? 'false' : null"
              [attr.placeholder]="meta.type === 'url' ? placeholder : null"
              [attr.aria-invalid]="error ? 'true' : null"
              [attr.aria-describedby]="describedBy(id, !!meta.help, error)"
            />
          </furar-field>
        }
      </div>
    }
  `,
})
export class LegalFieldsComponent {
  readonly form = input.required<LegalForm>();
  readonly rows = input.required<readonly (readonly LegalFieldKey[])[]>();
  readonly submitted = input(false);

  protected readonly placeholder = URL_PLACEHOLDER;
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  protected metaOf(key: LegalFieldKey): FieldMeta {
    return META[key as TextFieldKey] ?? { type: 'text' };
  }
}
