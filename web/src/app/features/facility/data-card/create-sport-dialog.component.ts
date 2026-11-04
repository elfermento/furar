// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { applyFieldErrors, asProblem, focusFirstError } from '../../../shared/ui/edit-section/section-errors';
import { BilingualFieldComponent } from '../../../shared/ui/bilingual-field/bilingual-field.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { SportResponse } from './data-form';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

/** Igual ca `CustomSport.LABEL_MAX` da API. */
export const SPORT_NAME_MAX = 60;
const SPORT_NAME_INVALID = 'facility.sport-name-invalid';

export interface CreateSportDialogData {
  /** O que se escribiu no buscador: vale de nome nos dous idiomas para empezar. */
  readonly name: string;
  readonly create: (label: { gl: string; es: string }) => Promise<SportResponse>;
}

const sportName = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value.trim() : '';
  return value === '' || value.length > SPORT_NAME_MAX ? { [SPORT_NAME_INVALID]: { max: SPORT_NAME_MAX } } : null;
};

/**
 * «+ crear» deporte (D1-21d; P26): o nome en galego e en castelán nun só campo co conmutador
 * GL/ES (`furar-bilingual-field`), porque a veciñanza busca por eles. Crea o deporte no concello e devolve o novo; a instalación só o leva cando se garda a
 * tarxeta Datos. Un nome repetido sae no aviso e o escrito queda.
 */
@Component({
  selector: 'furar-facility-create-sport-dialog',
  imports: [
    RequiredNoteComponent,
    BilingualFieldComponent,
    ButtonDirective,
    DialogComponent,
    ProblemAlertComponent,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let t = 'facility.data.createSport.';
    <furar-dialog size="sm" [heading]="t + 'title' | transloco" [busy]="busy()">
      <form
        class="gap-form grid"
        novalidate
        [id]="formId"
        [formGroup]="form"
        [attr.inert]="busy() ? '' : null"
        (ngSubmit)="submit()"
      >
        <furar-required-note />
        <furar-bilingual-field
          fieldId="facility-new-sport"
          required
          [label]="t + 'name' | transloco"
          [submitted]="submitted()"
          [gl]="form.controls.gl"
          [es]="form.controls.es"
        />
        <furar-problem-alert [problem]="problem()" />
      </form>
      <ng-container dialogActions>
        <button type="button" furarButton="quiet" data-cancel (click)="ref.close()">
          {{ t + 'cancel' | transloco }}
        </button>
        <button type="submit" furarButton="primary" data-save [attr.form]="formId" [busy]="busy()">
          {{ (busy() ? t + 'creating' : t + 'create') | transloco }}
        </button>
      </ng-container>
    </furar-dialog>
  `,
})
export class CreateSportDialogComponent {
  private readonly data = inject<CreateSportDialogData>(DIALOG_DATA);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  protected readonly ref = inject<DialogRef<SportResponse>>(DialogRef);

  protected readonly formId = 'facility-new-sport-form';
  protected readonly form = new FormGroup({
    gl: new FormControl(this.data.name, { nonNullable: true, validators: sportName }),
    es: new FormControl(this.data.name, { nonNullable: true, validators: sportName }),
  });
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  protected readonly submitted = signal(false);

  protected async submit(): Promise<void> {
    if (this.busy()) return;
    this.submitted.set(true);
    this.problem.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      queueMicrotask(() => focusFirstError(this.host.nativeElement));
      return;
    }
    this.busy.set(true);
    const { gl, es } = this.form.getRawValue();
    try {
      const sport = await this.data.create({ gl: gl.trim(), es: es.trim() });
      this.busy.set(false);
      this.ref.close(sport);
    } catch (error) {
      const problem = asProblem(error);
      const unmatched = applyFieldErrors(this.form, problem, (field) =>
        field === 'label.gl' ? this.form.controls.gl : field === 'label.es' ? this.form.controls.es : null,
      );
      if (unmatched > 0 || this.form.valid) this.problem.set(problem);
      this.busy.set(false);
    }
  }
}
