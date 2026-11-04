// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef, OnInit } from '@angular/core';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  inject,
  Injector,
  input,
  signal,
  viewChild,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { TranslocoPipe } from '@jsverse/transloco';
import type { CustomFieldDefinition } from './@name-kebab@.store'; // @cf
import type { @Name@Response } from './@name-kebab@.store';
import type { ApiProblem } from '../../core/errors/api-problem';
import { asProblem } from '../../core/errors/problem.interceptor';
import { LanguageService } from '../../core/i18n/language.service'; // @cf
import { ToastService } from '../../shared/ui/toast/toast.service';
import { CurrentMunicipality } from '../../core/municipality/current-municipality';
import { CustomFieldsFormComponent } from '../../shared/custom-fields/custom-fields-form.component'; // @cf
import type { CustomFieldsForm } from '../../shared/custom-fields/custom-field-controls'; // @cf
// @cf{
import {
  applyServerErrors,
  buildCustomFieldsForm,
  customFieldValuesOf,
} from '../../shared/custom-fields/custom-field-controls';
// @cf}
import { countInvalid, describedBy, fieldError, focusFirstInvalid } from '../../shared/ui/field-errors';
import { FieldComponent } from '../../shared/ui/field.component';
import { FormErrorsSummaryComponent } from '../../shared/ui/form-errors-summary.component';
import { ProblemAlertComponent } from '../../shared/ui/problem-alert.component';
import { @Name@Store } from './@name-kebab@.store';

const NAME_MAX = 100;

/**
 * Alta (/novo) e edición (/:id). Edición con bloqueo optimista: If-Match coa versión lida;
 * un 412 ofrece recargar. Erros do cliente e da API no seu campo (aria-describedby) e foco
 * no primeiro.
 */
@Component({
  selector: 'furar-@name-kebab@-edit-page',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    CustomFieldsFormComponent, // @cf
    FieldComponent,
    FormErrorsSummaryComponent,
    ProblemAlertComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './@name-kebab@-edit.page.html',
})
export class @Name@EditPage implements OnInit {
  private readonly injector = inject(Injector);
  private readonly store = inject(@Name@Store);
  private readonly router = inject(Router);
  private readonly toast = inject(ToastService);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly formElement = viewChild<ElementRef<HTMLFormElement>>('editForm');
  protected readonly language = inject(LanguageService); // @cf
  /** Parámetro :id da ruta (ausente en /novo). */
  readonly id = input<string>();

  protected readonly form = new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(NAME_MAX)] }),
  });
  protected readonly current = signal<@Name@Response | null>(null);
  protected readonly ready = signal(false);
  protected readonly definitions = signal<CustomFieldDefinition[]>([]); // @cf
  protected readonly customFields = signal<CustomFieldsForm>(buildCustomFieldsForm([])); // @cf
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly loadProblem = signal<ApiProblem | null>(null);
  protected readonly submitted = signal(false);
  protected readonly errorCount = signal(0);
  protected readonly saving = signal(false);
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  ngOnInit(): void {
    void this.load();
  }

  protected async load(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) {
      return;
    }
    try {
      this.definitions.set(await this.store.definitions(slug)); // @cf
      const id = this.id();
      this.reset(id ? await this.store.get(slug, id) : null);
      this.problem.set(null);
      this.ready.set(true);
    } catch (error: unknown) {
      this.loadProblem.set(asProblem(error));
    }
  }

  protected async save(): Promise<void> {
    const slug = this.municipality.slug();
    if (!slug) {
      return;
    }
    this.submitted.set(true);
    this.problem.set(null);
    this.form.markAllAsTouched();
    this.customFields().markAllAsTouched(); // @cf
    const invalid = countInvalid(this.form); // @nocf
    const invalid = countInvalid(this.form) + countInvalid(this.customFields()); // @cf
    this.errorCount.set(invalid);
    if (invalid > 0) {
      this.focusError();
      return;
    }
    this.saving.set(true);
    try {
      const body = {
        customFields: customFieldValuesOf(this.definitions(), this.customFields()), // @cf
        name: this.form.controls.name.value.trim(),
      };
      const current = this.current();
      const saved = current ? await this.store.update(slug, current, body) : await this.store.create(slug, body);
      this.toast.show('@name@.saved', { name: saved.name });
      await this.back();
    } catch (error: unknown) {
      this.showProblem(asProblem(error));
    } finally {
      this.saving.set(false);
    }
  }

  protected back(): Promise<boolean> {
    return this.router.navigate(['/', this.municipality.slug() ?? '', '@route@']);
  }

  private reset(item: @Name@Response | null): void {
    this.current.set(item);
    this.form.reset({ name: item?.name ?? '' });
    this.customFields.set(buildCustomFieldsForm(this.definitions(), item?.customFields)); // @cf
    this.submitted.set(false);
    this.errorCount.set(0);
  }

  private showProblem(problem: ApiProblem): void {
    this.problem.set(problem);
    const rest = problem.fieldErrors; // @nocf
    const rest = applyServerErrors(this.customFields(), problem.fieldErrors); // @cf
    for (const error of rest) {
      this.form.get(error.field)?.setErrors({ [error.code]: error.params });
    }
    if (problem.code === '@module@.name-invalid') {
      this.form.controls.name.setErrors({ [problem.code]: problem.params });
    }
    if (problem.fieldErrors.length || problem.code === '@module@.name-invalid') {
      this.errorCount.set(Math.max(problem.fieldErrors.length, 1));
      this.focusError();
    }
  }

  private focusError(): void {
    afterNextRender(
      () => {
        const element = this.formElement()?.nativeElement;
        if (element) {
          focusFirstInvalid(element);
        }
      },
      { injector: this.injector },
    );
  }
}
