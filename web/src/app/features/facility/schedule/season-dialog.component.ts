// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DIALOG_DATA, DialogRef } from '@angular/cdk/dialog';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  inject,
  signal,
} from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { TranslocoPipe } from '@jsverse/transloco';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { ButtonDirective } from '../../../shared/ui/button/button.directive';
import { DateFieldComponent } from '../../../shared/ui/date-field/date-field.component';
import { DialogComponent } from '../../../shared/ui/dialog/dialog.component';
import { applyFieldErrors, asProblem, focusFirstError } from '../../../shared/ui/edit-section/section-errors';
import { describedBy, fieldError } from '../../../shared/ui/field-errors';
import { FieldComponent } from '../../../shared/ui/field.component';
import { InputDirective } from '../../../shared/ui/input/input.directive';
import type { SelectOption } from '../../../shared/ui/select/select.component';
import { SelectComponent } from '../../../shared/ui/select/select.component';
import { ProblemAlertComponent } from '../../../shared/ui/problem-alert.component';
import type { Season } from './schedule.store';
import { linkNoEnd } from './season-drafts';
import { RequiredNoteComponent } from '../../../shared/ui/required/required.component';

/** O mesmo límite ca `Season` da API. */
export const SEASON_NAME_MAX = 100;

export interface SeasonDialogValue {
  readonly name: string;
  readonly startDate: string;
  /** `null`: sen data de fin. */
  readonly endDate: string | null;
  /** «Empezar co horario de»: outra tempada, ou `null` para empezar todo pechado. */
  readonly copyFrom: string | null;
}

export interface SeasonDialogData {
  /** Tempadas da instalación, para «empezar co horario de». */
  readonly seasons: readonly Season[];
  /** Opción escollida de entrada en «empezar co horario de». */
  readonly copyFrom: string | null;
  /** Crea ou garda; `null` se o xestor volveu atrás no modal de reservas; rexeita co erro da API. */
  readonly save: (value: SeasonDialogValue) => Promise<Season | null>;
}

const required = (control: AbstractControl): ValidationErrors | null =>
  control.value ? null : { 'validation.required': {} };

function name(control: AbstractControl): ValidationErrors | null {
  const length = typeof control.value === 'string' ? control.value.trim().length : 0;
  if (length === 0) return { 'validation.required': {} };
  return length > SEASON_NAME_MAX ? { 'validation.size': { min: 1, max: SEASON_NAME_MAX } } : null;
}

/** «Ata» non vai antes de «Desde» (extremos incluídos). Amósase no campo «Ata», como na API. */
function range(group: AbstractControl): ValidationErrors | null {
  const { startDate, endDate } = (group as SeasonForm).controls;
  return startDate.value && endDate.value && endDate.value < startDate.value ? { 'validation.range': {} } : null;
}

type SeasonForm = FormGroup<{
  name: FormControl<string>;
  startDate: FormControl<string | null>;
  endDate: FormControl<string | null>;
  noEnd: FormControl<boolean>;
  copyFrom: FormControl<string>;
}>;

/**
 * Diálogo de tempada (D1-23a; especificación, bloque 2): nome, desde e ata (dous
 * `furar-date-field`, P14; «Sen data de fin» deixa «Ata» baleiro, maqueta 54) e, ao crear, «empezar co horario de» (outra tempada ou todo pechado).
 * Un solape co servidor (409 `season-overlap`) sae no aviso, que xa suxire unha excepción para
 * días soltos; os erros de campo, no seu campo. Resolve coa tempada gardada.
 */
@Component({
  selector: 'furar-facility-season-dialog',
  imports: [
    RequiredNoteComponent,
    ButtonDirective,
    DateFieldComponent,
    DialogComponent,
    FieldComponent,
    InputDirective,
    ProblemAlertComponent,
    ReactiveFormsModule,
    SelectComponent,
    TranslocoPipe,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './season-dialog.component.html',
})
export class SeasonDialogComponent {
  protected readonly data = inject<SeasonDialogData>(DIALOG_DATA);
  protected readonly ref = inject<DialogRef<Season>>(DialogRef);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);
  private readonly injector = inject(Injector);

  protected readonly id = 'facility-season-form';
  /** As tempadas das que copiar a semana (o «Non copiar» vai na plantilla, traducido). */
  protected readonly seasonOptions: SelectOption<string>[] = this.data.seasons.map((s) => ({
    value: s.id,
    label: s.name,
  }));
  protected readonly describedBy = describedBy;
  protected readonly form: SeasonForm = new FormGroup(
    {
      name: new FormControl('', { nonNullable: true, validators: name }),
      startDate: new FormControl<string | null>(null, required),
      endDate: new FormControl<string | null>(null, required),
      noEnd: new FormControl(false, { nonNullable: true }),
      copyFrom: new FormControl(this.data.copyFrom ?? '', { nonNullable: true }),
    },
    { validators: range },
  );
  protected readonly busy = signal(false);
  protected readonly problem = signal<ApiProblem | null>(null);
  private readonly events = toSignal(this.form.events);
  protected readonly errors = computed(() => {
    this.events();
    const c = this.form.controls;
    return {
      name: fieldError(c.name, false),
      startDate: fieldError(c.startDate, false),
      endDate: fieldError(c.endDate, false) ?? (c.endDate.touched ? fieldError(this.form, true) : null),
      copyFrom: fieldError(c.copyFrom, false),
    };
  });
  protected readonly noEnd = computed(() => {
    this.events();
    return this.form.controls.noEnd.value;
  });
  protected readonly start = computed(() => {
    this.events();
    return this.form.controls.startDate.value;
  });

  constructor() {
    linkNoEnd(this.form.controls.noEnd, this.form.controls.endDate);
  }

  protected async submit(event: Event): Promise<void> {
    event.preventDefault();
    if (this.busy()) return;
    this.problem.set(null);
    this.form.markAllAsTouched();
    if (this.form.invalid) return this.focusError();
    const value = this.form.getRawValue();
    this.busy.set(true);
    try {
      const saved = await this.data.save({
        name: value.name.trim(),
        startDate: value.startDate ?? '',
        endDate: value.noEnd ? null : (value.endDate ?? ''),
        copyFrom: value.copyFrom || null,
      });
      this.busy.set(false);
      if (saved) this.ref.close(saved);
    } catch (error) {
      this.busy.set(false);
      const problem = asProblem(error);
      const unmatched = applyFieldErrors(this.form, problem, () => null);
      if (this.form.invalid) this.focusError();
      if (problem.fieldErrors.length === 0 || unmatched > 0) this.problem.set(problem);
    }
  }

  private focusError(): void {
    afterNextRender(() => focusFirstError(this.host.nativeElement), { injector: this.injector });
  }
}
