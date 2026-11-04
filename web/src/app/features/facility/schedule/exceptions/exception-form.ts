// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { ApiProblem } from '../../../../core/errors/api-problem';
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';
import { applyFieldErrors } from '../../../../shared/ui/edit-section/section-errors';
import { FIRST_INTERVAL } from '../week-form';
import type { ExceptionBody, ScheduleException } from './exceptions.store';

/** Os mesmos límites ca `ScheduleException` da API. */
export const REASON_MAX = 200;
export const MINUTE_STEP = 15;
/** Códigos dos erros que tamén pon o servidor, para que o texto sexa o mesmo. */
export const DATE_TAKEN = 'facility.exception-date-taken';
export const INTERVAL_INVALID = 'facility.interval-invalid';

export type ExceptionForm = FormGroup<{
  date: FormControl<IsoDate | null>;
  open: FormControl<boolean>;
  startMinute: FormControl<number | null>;
  endMinute: FormControl<number | null>;
  reason: FormControl<string>;
}>;

/** Data xa usada por outra excepción da instalación (a API tampouco o deixa: `unique`). */
function dateFree(taken: () => ReadonlySet<IsoDate>): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null =>
    control.value && taken().has(control.value) ? { [DATE_TAKEN]: { date: control.value } } : null;
}

/** Aberto: o tramo remata despois de empezar (o selector xa non pasa das 24:00). Erro do grupo. */
function intervalRule(control: AbstractControl): ValidationErrors | null {
  const { open, startMinute: start, endMinute: end } = (control as ExceptionForm).getRawValue();
  return open && start !== null && end !== null && end <= start ? { [INTERVAL_INVALID]: { step: MINUTE_STEP } } : null;
}

/**
 * Formulario dunha excepción (D1-23b; especificación, bloque 2, «Excepcións»): data, aberto ou
 * pechado, o tramo se está aberto (un só, P9) e o motivo. Unha nova nace pechada, que é o caso
 * máis común (festa local, Noiteboa); ao abrila propón 9:00–14:00 coma a semana. `taken` son as
 * datas das outras excepcións.
 */
export function exceptionForm(exception: ScheduleException | null, taken: () => ReadonlySet<IsoDate>): ExceptionForm {
  const open = exception?.open ?? false;
  const form: ExceptionForm = new FormGroup(
    {
      date: new FormControl<IsoDate | null>(exception?.date ?? null, [Validators.required, dateFree(taken)]),
      open: new FormControl(open, { nonNullable: true }),
      startMinute: new FormControl<number | null>(
        exception?.startMinute ?? FIRST_INTERVAL.startMinute,
        Validators.required,
      ),
      endMinute: new FormControl<number | null>(exception?.endMinute ?? FIRST_INTERVAL.endMinute, Validators.required),
      reason: new FormControl(exception?.reason ?? '', {
        nonNullable: true,
        validators: Validators.maxLength(REASON_MAX),
      }),
    },
    { validators: intervalRule },
  );
  setOpen(form, open);
  form.controls.open.valueChanges.subscribe((value) => setOpen(form, value));
  return form;
}

/** Pechado desactiva o tramo sen borralo: se se volve abrir, aparece o que había. */
function setOpen(form: ExceptionForm, open: boolean): void {
  for (const control of [form.controls.startMinute, form.controls.endMinute]) {
    if (open) control.enable({ emitEvent: false });
    else control.disable({ emitEvent: false });
  }
  form.updateValueAndValidity({ emitEvent: false });
}

/** Corpo do `POST`/`PUT`: o tramo só se está aberto; o motivo baleiro non se manda. */
export function exceptionBody(form: ExceptionForm): ExceptionBody {
  const { date, open, startMinute, endMinute, reason } = form.getRawValue();
  const text = reason.trim();
  return {
    date: date ?? '',
    open,
    ...(open ? { startMinute: startMinute ?? 0, endMinute: endMinute ?? 0 } : {}),
    ...(text ? { reason: text } : {}),
  };
}

/**
 * Pon no seu campo os erros da API; a data repetida (`409` sen campo) vai á data. Devolve se
 * quedou todo no formulario (se non, a sección amosa o aviso).
 */
export function applyExceptionErrors(form: ExceptionForm, problem: ApiProblem): boolean {
  const unmatched = applyFieldErrors(form, problem, () => null);
  if (problem.code === DATE_TAKEN && problem.fieldErrors.length === 0) {
    const date = form.controls.date;
    date.setErrors({ ...date.errors, [DATE_TAKEN]: problem.params });
    date.markAsTouched();
    return true;
  }
  return unmatched === 0 && problem.fieldErrors.length > 0;
}
