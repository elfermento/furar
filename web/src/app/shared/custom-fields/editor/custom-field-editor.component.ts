// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ElementRef } from '@angular/core';
import {
  afterNextRender,
  ChangeDetectionStrategy,
  Component,
  computed,
  DestroyRef,
  effect,
  inject,
  Injector,
  input,
  output,
  signal,
  viewChild,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import { map } from 'rxjs';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { LanguageService } from '../../../core/i18n/language.service';
import { ButtonDirective } from '../../ui/button/button.directive';
import { CalloutComponent } from '../../ui/callout/callout.component';
import { CardComponent } from '../../ui/card/card.component';
import { countInvalid, describedBy, fieldError, focusFirstInvalid } from '../../ui/field-errors';
import { FormErrorsSummaryComponent } from '../../ui/form-errors-summary.component';
import { ProblemAlertComponent } from '../../ui/problem-alert.component';
import { RequiredMarkComponent, RequiredNoteComponent } from '../../ui/required/required.component';
import { OptionsPipe } from '../../ui/select/options.pipe';
import { SelectComponent } from '../../ui/select/select.component';
import type { CustomFieldDefinitionDraft, CustomFieldDefinitionWithUsages } from '../custom-field.types';
import { CUSTOM_FIELD_TYPES } from '../custom-field.types';
import { CustomFieldPreviewComponent } from './custom-field-preview.component';
import {
  PERSONAL_DATA_PUBLIC,
  controlForField,
  definitionForm,
  isChoice,
  linkPersonalData,
  patchDefinition,
  toDraft,
  toPreview,
} from './editor-form';
import { OptionsEditorComponent } from './options-editor.component';
import { PlaceEditorComponent } from './place-editor.component';
import { RulesEditorComponent } from './rules-editor.component';

/**
 * Editor de definicións para o xestor (ADR-0011; maqueta 57): primeiro onde se usa (un campo, un
 * sitio, co seu obrigatorio e quen o ve), despois textos en gl e es, código e tipo, dato persoal,
 * regras e opcións, con vista previa en vivo.
 */
@Component({
  selector: 'furar-custom-field-editor',
  imports: [
    ReactiveFormsModule,
    TranslocoPipe,
    CustomFieldPreviewComponent,
    OptionsEditorComponent,
    PlaceEditorComponent,
    RulesEditorComponent,
    ProblemAlertComponent,
    FormErrorsSummaryComponent,
    ButtonDirective,
    CalloutComponent,
    CardComponent,
    OptionsPipe,
    RequiredMarkComponent,
    RequiredNoteComponent,
    SelectComponent,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './custom-field-editor.component.html',
})
export class CustomFieldEditorComponent {
  private readonly injector = inject(Injector);
  private readonly formElement = viewChild.required<ElementRef<HTMLFormElement>>('editorForm');

  /** Definición gardada (edición) ou null (alta). */
  readonly definition = input<CustomFieldDefinitionWithUsages | null>(null);
  readonly problem = input<ApiProblem | null>(null);
  readonly saving = input(false);
  readonly save = output<CustomFieldDefinitionDraft>();
  readonly cancelled = output();

  protected readonly form = definitionForm();
  protected readonly types = CUSTOM_FIELD_TYPES;
  protected readonly lang = inject(LanguageService).lang;
  protected readonly submitted = signal(false);
  protected readonly errorCount = signal(0);
  protected readonly fieldError = fieldError;
  protected readonly describedBy = describedBy;

  private readonly value = toSignal(this.form.valueChanges.pipe(map(() => this.form.getRawValue())), {
    initialValue: this.form.getRawValue(),
  });
  protected readonly type = computed(() => this.value().type);
  protected readonly choice = computed(() => isChoice(this.type()));
  /** Dato persoal: aviso do art. 9 RGPD e PUBLIC desactivado. */
  protected readonly personalData = computed(() => this.value().personalData);
  protected readonly target = computed(() => this.value().target);
  /** O campo xa ten valores: non cambia de sitio. */
  protected readonly placeLocked = computed(() => this.definition()?.hasValues === true);
  /** Un campo público cun dato persoal: só se marca tras tocar ou enviar. */
  protected readonly personalPublic = computed(() => {
    this.value();
    return this.form.hasError(PERSONAL_DATA_PUBLIC) && (this.submitted() || this.form.controls.visibility.touched);
  });
  /** Erro da API en «Quen o ve»; vai fóra ao cambiar a visibilidade (o control perde o erro). */
  private readonly visibilityProblem = signal<string | null>(null);
  protected readonly visibilityError = computed(() => {
    this.value();
    const code = this.visibilityProblem();
    return code && this.form.controls.visibility.hasError(code) ? code : null;
  });
  protected readonly preview = computed(() => {
    this.value();
    return toPreview(toDraft(this.form));
  });

  constructor() {
    const link = linkPersonalData(this.form);
    inject(DestroyRef).onDestroy(() => link.unsubscribe());
    effect(() => {
      const definition = this.definition();
      if (definition) {
        patchDefinition(this.form, definition);
      }
    });
    effect(() => this.showServerErrors(this.problem()));
  }

  protected submit(): void {
    // O botón queda con aria-disabled (non disabled) mentres garda, para non perder o foco (2.4.3).
    if (this.saving()) {
      return;
    }
    this.submitted.set(true);
    this.form.markAllAsTouched();
    const invalid = countInvalid(this.form);
    this.errorCount.set(invalid);
    if (invalid > 0) {
      afterNextRender(() => focusFirstInvalid(this.formElement().nativeElement), { injector: this.injector });
      return;
    }
    this.save.emit(toDraft(this.form));
  }

  /** Erros do formulario enteiro (sen opcións, opción repetida). */
  protected readonly formErrors = computed(() => {
    this.value();
    const errors = Object.entries(this.form.errors ?? {}).filter(([code]) => code !== PERSONAL_DATA_PUBLIC);
    return errors.map(([code, params]) => ({
      key: `errors.${code}`,
      params: typeof params === 'object' ? (params as Record<string, unknown>) : {},
    }));
  });

  private showServerErrors(problem: ApiProblem | null): void {
    if (!problem) {
      return;
    }
    for (const error of problem.fieldErrors) {
      controlForField(this.form, error.field)?.setErrors({ [error.code]: error.params });
    }
    if (problem.code.startsWith('customfield.code-')) {
      this.form.controls.code.setErrors({ [problem.code]: problem.params });
    }
    if (problem.code === PERSONAL_DATA_PUBLIC || problem.code === 'customfield.visibility-not-allowed') {
      this.form.controls.visibility.setErrors({ [problem.code]: problem.params });
      this.visibilityProblem.set(problem.code);
    }
    this.form.markAllAsTouched();
    afterNextRender(() => focusFirstInvalid(this.formElement().nativeElement), { injector: this.injector });
  }
}
