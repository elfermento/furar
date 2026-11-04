// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { LanguageService } from '../../../../core/i18n/language.service';
import { BilingualFieldComponent } from '../../../../shared/ui/bilingual-field/bilingual-field.component';
import { describedBy, fieldError } from '../../../../shared/ui/field-errors';
import { FieldComponent } from '../../../../shared/ui/field.component';
import { InputDirective } from '../../../../shared/ui/input/input.directive';
import { RequiredNoteComponent } from '../../../../shared/ui/required/required.component';
import {
  SegmentedOptionDirective,
  SegmentedRadioDirective,
} from '../../../../shared/ui/segmented/segmented-radio.directive';
import { SegmentedDirective } from '../../../../shared/ui/segmented/segmented.directive';
import { OptionsPipe } from '../../../../shared/ui/select/options.pipe';
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
    OptionsPipe,
    ReactiveFormsModule,
    RequiredNoteComponent,
    SegmentedDirective,
    SegmentedOptionDirective,
    SegmentedRadioDirective,
    SelectComponent,
    SportsFieldComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.wizard.data.';
    @let d = 'facility.data.';
    <furar-required-note />
    <div class="gap-form grid">
      <furar-field
        fieldId="wizard-name"
        [label]="t + 'name' | transloco"
        [help]="t + 'nameHelp' | transloco"
        [required]="true"
        [error]="nameError()"
      >
        <input
          furarInput
          id="wizard-name"
          type="text"
          autocomplete="off"
          aria-required="true"
          [formControl]="store.name"
          [attr.aria-invalid]="nameError() ? 'true' : null"
          [attr.aria-describedby]="describedBy('wizard-name', true, nameError())"
        />
      </furar-field>

      <furar-facility-sports-field required [control]="store.data.controls.sports" [lang]="lang()" />

      <div class="f-field-row sm:grid-cols-2" [formGroup]="store.data">
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
        <div class="gap-unit grid content-start">
          <span class="f-label" id="wizard-covered">{{ d + 'covered.label' | transloco }}</span>
          <div furarSegmented mode="radio" formControlName="covered" aria-labelledby="wizard-covered">
            <button type="button" [furarSegmentedOption]="true">{{ 'common.yes' | transloco }}</button>
            <button type="button" [furarSegmentedOption]="false">{{ 'common.no' | transloco }}</button>
          </div>
        </div>
      </div>

      <furar-bilingual-field
        fieldId="wizard-description"
        multiline
        [rows]="3"
        [maxlength]="descriptionMax"
        [submitted]="tried()"
        [label]="'facility.description.title' | transloco"
        [help]="'facility.description.help' | transloco: { max: descriptionMax }"
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
  protected readonly descriptionMax = DESCRIPTION_MAX;
  protected readonly describedBy = describedBy;
  protected readonly tried = computed(() => this.store.tried().has(STEP.data));
  protected readonly nameError = computed(() => {
    this.store.ok();
    return fieldError(this.store.name, this.tried());
  });
}
