// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef, OnInit } from '@angular/core';
import { ChangeDetectionStrategy, Component, computed, inject, signal, viewChild } from '@angular/core';
import type { FormControl } from '@angular/forms';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { toSignal } from '@angular/core/rxjs-interop';
import { asProblem } from '../../../core/errors/problem.interceptor';
import { LanguageService } from '../../../core/i18n/language.service';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { LeavableTab } from '../../../core/unsaved-changes/leave-tab.guard';
import { CustomFieldsFormComponent } from '../../../shared/custom-fields/custom-fields-form.component';
import type { CustomFieldsForm } from '../../../shared/custom-fields/custom-field-controls';
import {
  applyServerErrors,
  buildCustomFieldsForm,
  customFieldValuesOf,
} from '../../../shared/custom-fields/custom-field-controls';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { CardComponent } from '../../../shared/ui/card/card.component';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { FormErrorsSummaryComponent } from '../../../shared/ui/form-errors-summary.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import { StatusStateComponent } from '../../../shared/ui/status-state/status-state.component';
import { LanguagesFieldsetComponent } from '../languages-fieldset.component';
import type { LanguagesForm } from '../municipality-form';
import { languagesForm, languagesOf, nameControl } from '../municipality-form';
import type { CustomFieldDefinition, MunicipalityResponse } from '../municipality.store';
import { MunicipalityStore } from '../municipality.store';
import { OpeningChecklistComponent } from './opening-checklist.component';
import { tabForm } from './tab-form';
import { TabSave } from './tab-save';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

type GeneralForm = FormGroup<{
  name: FormControl<string>;
  languages: LanguagesForm;
  customFields: CustomFieldsForm;
}>;

/**
 * «Xeral» da sección «Concello» (maqueta 49): nome, idiomas e idioma por defecto e os datos
 * propios (campos personalizados do concello). Enriba, «Para abrir as reservas» (maqueta 48), só
 * mentres falte algo. Garda co `PUT` do concello e o seu `If-Match`; o
 * tema e os datos legais van coma estaban (cada pestana garda o seu).
 */
@Component({
  selector: 'furar-municipality-general-tab',
  imports: [
    RequiredNoteComponent,
    ButtonDirective,
    CardComponent,
    CustomFieldsFormComponent,
    FieldComponent,
    FormErrorsSummaryComponent,
    InputDirective,
    LanguagesFieldsetComponent,
    OpeningChecklistComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    RouterLink,
    StatusStateComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: { class: 'f-section__col' },
  templateUrl: './general.tab.html',
})
export class GeneralTab implements OnInit, LeavableTab {
  private readonly store = inject(MunicipalityStore);
  private readonly current = inject(CurrentMunicipality);
  private readonly transloco = inject(TranslocoService);
  private readonly translation = toSignal(this.transloco.selectTranslation());
  private readonly formElement = viewChild<ElementRef<HTMLElement>>('formElement');
  protected readonly language = inject(LanguageService);

  protected readonly tab = tabForm<GeneralForm>('general');
  protected readonly saver = new TabSave();
  protected readonly definitions = signal<CustomFieldDefinition[]>([]);
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  readonly dirty = this.tab.dirty;
  readonly tabName = computed(() => {
    this.translation();
    return this.transloco.translate('section.tab.general');
  });

  ngOnInit(): void {
    void this.load();
  }

  private async load(): Promise<void> {
    const municipality = this.current.value();
    if (!municipality) return;
    try {
      this.definitions.set(await this.store.definitions(municipality.slug));
      this.reset(municipality);
    } catch (error: unknown) {
      this.saver.problem.set(asProblem(error));
    }
  }

  save(): Promise<boolean> {
    const form = this.tab.form();
    const municipality = this.current.value();
    if (!form || !municipality) return Promise.resolve(false);
    return this.saver.run(
      form,
      this.formElement(),
      async () => {
        const { name } = form.getRawValue();
        const updated = await this.store.update(municipality, {
          name: name.trim(),
          languages: languagesOf(form.controls.languages),
          defaultLanguage: form.controls.languages.controls.defaultLanguage.value,
          theme: municipality.theme,
          customFields: customFieldValuesOf(this.definitions(), form.controls.customFields),
        });
        this.current.set(updated);
        this.reset(updated);
      },
      (problem) => {
        const rest = applyServerErrors(form.controls.customFields, problem.fieldErrors);
        for (const error of rest) {
          const control = form.get(error.field);
          control?.setErrors({ [error.code]: error.params });
          control?.markAsTouched();
        }
        return problem.fieldErrors.length;
      },
    );
  }

  discard(): void {
    const municipality = this.current.value();
    if (municipality) this.reset(municipality);
    this.saver.clear();
  }

  protected async reload(): Promise<void> {
    if (await this.saver.reloadMunicipality()) {
      const fresh = this.current.value();
      if (fresh) this.reset(fresh);
    }
  }

  private reset(municipality: MunicipalityResponse): void {
    this.tab.set(
      new FormGroup({
        name: nameControl(municipality.name),
        languages: languagesForm(municipality.languages, municipality.defaultLanguage),
        customFields: buildCustomFieldsForm(this.definitions(), municipality.customFields),
      }),
    );
  }
}
