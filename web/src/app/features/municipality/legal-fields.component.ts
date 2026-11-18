// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { describedBy, fieldError } from '../../shared/ui/field-errors';
import { FieldGroupComponent } from '../../shared/ui/field-group/field-group.component';
import { FieldComponent } from '../../shared/ui/field.component';
import { InputDirective } from '../../shared/ui/input/input.directive';
import { LongTextDirective } from '../../shared/ui/long-text/long-text.directive';
import { NIF_LENGTH, normaliseNif } from './entity-nif';
import type { LegalFieldKey, LegalForm } from './legal-form';

type TextFieldKey =
  | Exclude<LegalFieldKey, `${string}Mode` | `${string}Url`>
  | 'eOfficeUrl'
  | 'accessibilityComplaintUrl'
  | 'processingRegisterUrl'
  | 'useRegulationUrl';

interface FieldMeta {
  readonly type: 'text' | 'email' | 'url' | 'tel';
  readonly autocomplete?: string;
  /** Leva axuda (`settings.legal.help.<campo>`), no «?» do rótulo. */
  readonly help?: boolean;
  /** O «?» é unha pregunta que explica o dato (`settings.legal.about.<campo>`: `question` e `text`). */
  readonly about?: boolean;
  /** Códigos en maiúsculas (NIF, DIR3): sen corrector nin minúsculas automáticas. */
  readonly code?: boolean;
  /** NIF: maiúsculas e sen separadores mentres se escribe; o erro, ao completalo ou ao saír. */
  readonly nif?: boolean;
  /** Texto longo: no móbil edítase nunha folla co texto enteiro (`[furarLongText]`). */
  readonly long?: boolean;
}

const META: Readonly<Record<TextFieldKey, FieldMeta>> = {
  legalEntityName: { type: 'text', autocomplete: 'organization', help: true, long: true },
  eOfficeUrl: { type: 'url' },
  dir3Code: { type: 'text', help: true, code: true },
  nif: { type: 'text', code: true, nif: true },
  address: { type: 'text', autocomplete: 'street-address', long: true },
  generalEmail: { type: 'email', autocomplete: 'email' },
  dpoEmail: { type: 'email', autocomplete: 'email', about: true },
  dpoPhone: { type: 'tel', autocomplete: 'tel' },
  processor: { type: 'text', autocomplete: 'off', help: true, long: true },
  accessibilityEmail: { type: 'email', autocomplete: 'email' },
  accessibilityUnit: { type: 'text', help: true, long: true },
  accessibilityComplaintUrl: { type: 'url', help: true },
  generalPhone: { type: 'tel', autocomplete: 'tel' },
  processingRegisterUrl: { type: 'url', help: true },
  useRegulationUrl: { type: 'url', help: true },
};
const URL_PLACEHOLDER = 'https://';

/**
 * Campos de texto dos datos legais, nun grupo de campos, fila a fila (`.f-field-cells`):
 * unha fila de dous comparte a liña e parte en dúas se non cabe. O erro, debaixo do grupo; a axuda, nun
 * «?» ao carón do rótulo (`tip` de `furar-field`), ligada ao control (`describedBy`). O do DPD é a
 * pregunta «Que é o DPD?».
 */
@Component({
  selector: 'furar-legal-fields',
  imports: [FieldComponent, FieldGroupComponent, InputDirective, LongTextDirective, ReactiveFormsModule, TranslocoPipe],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'grid' },
  template: `
    <furar-field-group>
      @for (row of rows(); track $index) {
        <div class="f-field-cells" [style.--f-cell]="'14rem'">
          @for (key of row; track key) {
            @let meta = metaOf(key);
            @let id = 'settings-legal-' + key;
            @let control = form().controls[key];
            @let error = fieldError(control, submitted());
            @let label = 'settings.legal.field.' + key | transloco;
            <furar-field
              [fieldId]="id"
              [label]="label"
              [tip]="
                meta.about
                  ? ('settings.legal.about.' + key + '.text' | transloco)
                  : meta.help
                    ? ('settings.legal.help.' + key | transloco)
                    : ''
              "
              [tipLabel]="meta.about ? ('settings.legal.about.' + key + '.question' | transloco) : ''"
              [error]="error"
              [attr.data-about]="meta.about ? key : null"
            >
              <input
                furarInput
                [id]="id"
                [type]="meta.type"
                [formControl]="control"
                [furarLongText]="meta.long ? label : null"
                [attr.autocomplete]="meta.autocomplete ?? 'off'"
                [attr.autocapitalize]="meta.code ? 'characters' : null"
                [attr.spellcheck]="meta.code || meta.type === 'url' ? 'false' : null"
                [attr.placeholder]="meta.type === 'url' ? placeholder : null"
                [attr.aria-invalid]="error ? 'true' : null"
                [attr.aria-describedby]="describedBy(id, !!(meta.help || meta.about), error)"
                (input)="onInput($event, key)"
              />
            </furar-field>
          }
        </div>
      }
    </furar-field-group>
  `,
})
export class LegalFieldsComponent {
  readonly form = input.required<LegalForm>();
  readonly rows = input.required<readonly (readonly LegalFieldKey[])[]>();
  readonly submitted = input(false);

  protected readonly placeholder = URL_PLACEHOLDER;
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  /**
   * O NIF escríbese xa normalizado (o cursor queda onde estaba) e compróbase ao ter os 9
   * caracteres, sen agardar a saír do campo.
   */
  protected onInput(event: Event, key: LegalFieldKey): void {
    if (!this.metaOf(key).nif) return;
    const input = event.target as HTMLInputElement;
    const control = this.form().controls.nif;
    const value = normaliseNif(input.value);
    if (value !== input.value) {
      const caret = normaliseNif(input.value.slice(0, input.selectionStart ?? input.value.length)).length;
      control.setValue(value);
      input.setSelectionRange(caret, caret);
    }
    if (value.length >= NIF_LENGTH) control.markAsTouched();
  }

  protected metaOf(key: LegalFieldKey): FieldMeta {
    return META[key as TextFieldKey] ?? { type: 'text' };
  }
}
