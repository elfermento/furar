// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { toSignal } from '@angular/core/rxjs-interop';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { BilingualFieldComponent } from '../../../../shared/ui/bilingual-field/bilingual-field.component';
import { describedBy, fieldError } from '../../../../shared/ui/field-errors';
import { FieldComponent } from '../../../../shared/ui/field.component';
import { InputDirective } from '../../../../shared/ui/input/input.directive';
import { LongTextDirective } from '../../../../shared/ui/long-text/long-text.directive';
import { OptionsPipe } from '../../../../shared/ui/select/options.pipe';
import { yesNoOptions } from '../../../../shared/ui/select/select-option';
import { SelectComponent } from '../../../../shared/ui/select/select.component';
import { FACILITY_TYPES } from '../../data-card/data-form';
import { SportsFieldComponent } from '../../data-card/sports-field.component';
import { DESCRIPTION_MAX } from '../../description/description-form';
import { STEP } from '../wizard';
import { WizardStore } from '../wizard.store';

/**
 * Paso 1, «Nome e deportes»: nome (obrigatorio), deportes (fai falta un para publicar), tipo,
 * cuberta e descrición en gl e es. Os mesmos campos ca a ficha.
 */
@Component({
  selector: 'furar-wizard-data-step',
  imports: [
    BilingualFieldComponent,
    FieldComponent,
    InputDirective,
    LongTextDirective,
    OptionsPipe,
    ReactiveFormsModule,
    SelectComponent,
    SportsFieldComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.data.';
    @let d = 'facility.data.';
    <div class="gap-form grid">
      <furar-field fieldId="wizard-name" [label]="t + 'name' | transloco" [required]="true" [error]="nameError()">
        <input
          furarInput
          id="wizard-name"
          furarLongText
          type="text"
          [placeholder]="t + 'namePlaceholder' | transloco"
          autocomplete="off"
          aria-required="true"
          [formControl]="store.name"
          [attr.aria-invalid]="nameError() ? 'true' : null"
          [attr.aria-describedby]="describedBy('wizard-name', false, nameError())"
        />
      </furar-field>

      <furar-facility-sports-field required [control]="store.data.controls.sports" [lang]="lang()" />

      <div class="gap-form grid" [formGroup]="store.data">
        <furar-field fieldId="wizard-type" [label]="d + 'type.label' | transloco">
          <furar-select
            selectId="wizard-type"
            formControlName="type"
            [label]="d + 'type.label' | transloco"
            [options]="
              [{ value: '', label: (d + 'type.none' | transloco) }].concat(types | furarOptions: d + 'type.' : lang())
            "
          />
        </furar-field>
        <!-- Cuberta: sen dato, si ou non (tres valores, un despregable coma na ficha). -->
        <furar-field fieldId="wizard-covered" [label]="d + 'covered.label' | transloco">
          <furar-select
            selectId="wizard-covered"
            formControlName="covered"
            [label]="d + 'covered.label' | transloco"
            [options]="coveredOptions()"
          />
        </furar-field>
      </div>

      <furar-bilingual-field
        fieldId="wizard-description"
        multiline
        longText
        [rows]="3"
        [maxlength]="descriptionMax"
        [submitted]="tried()"
        [label]="'facility.description.title' | transloco"
        [tip]="'facility.description.help' | transloco: { max: descriptionMax }"
        [gl]="store.description.controls.gl"
        [es]="store.description.controls.es"
      />
    </div>
  `,
})
export class DataStepComponent {
  protected readonly store = inject(WizardStore);
  private readonly language = inject(LanguageService);
  protected readonly lang = this.language.lang;
  protected readonly types = FACILITY_TYPES;
  private readonly transloco = inject(TranslocoService);
  private readonly translations = toSignal(this.transloco.langChanges$);
  /** «Cuberta»: sen dato, si ou non (tres valores, un despregable). */
  protected readonly coveredOptions = computed(() => {
    this.translations();
    return yesNoOptions((key) => this.transloco.translate(key));
  });
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly describedBy = describedBy;
  protected readonly tried = computed(() => this.store.tried().has(STEP.data));
  protected readonly nameError = computed(() => {
    this.store.ok();
    return fieldError(this.store.name, this.tried());
  });
}
