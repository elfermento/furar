// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, FormArray, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import type { DayForm } from './week-form';
import { dayIntervals, weekForm } from './week-form';
import type { Season, SeasonBody } from './schedule.store';
import { DAYS } from './schedule.store';

/** O mesmo límite ca `Season` da API. */
export const SEASON_NAME_MAX = 100;

export type SeasonDraftForm = FormGroup<{
  name: FormControl<string>;
  startDate: FormControl<IsoDate | null>;
  endDate: FormControl<IsoDate | null>;
  /** Sen data de fin (o interruptor de «Ata» apagado): «Ata» queda baleiro e desactivado no formulario e non se pinta. */
  noEnd: FormControl<boolean>;
  week: FormArray<DayForm>;
}>;

/** Unha tempada co seu formulario (para `clashing`). */
export interface SeasonDraft {
  readonly key: string;
  readonly saved: Season | null;
  readonly form: SeasonDraftForm;
}

const required = (control: AbstractControl): ValidationErrors | null =>
  control.value ? null : { 'validation.required': {} };

function name(control: AbstractControl): ValidationErrors | null {
  const length = typeof control.value === 'string' ? control.value.trim().length : 0;
  if (length === 0) return { 'validation.required': {} };
  return length > SEASON_NAME_MAX ? { 'validation.size': { min: 1, max: SEASON_NAME_MAX } } : null;
}

/** «Ata» non vai antes de «Desde» (extremos incluídos); o erro vai no campo «Ata», coma na API. */
function range(group: AbstractControl): ValidationErrors | null {
  const { startDate, endDate } = (group as SeasonDraftForm).controls;
  const bad = !!startDate.value && !!endDate.value && endDate.value < startDate.value;
  const errors = { ...(endDate.errors ?? {}) };
  if (bad) errors['validation.range'] = {};
  else delete errors['validation.range'];
  const next = Object.keys(errors).length ? errors : null;
  if (JSON.stringify(next) !== JSON.stringify(endDate.errors)) endDate.setErrors(next, { emitEvent: false });
  return null;
}

/**
 * «Sen data de fin» baleira e desactiva «Ata» (un control desactivado non se valida); ao quitala,
 * «Ata» volve ser obrigatoria.
 */
export function linkNoEnd<T>(noEnd: FormControl<boolean>, endDate: FormControl<T | null>): void {
  const apply = (open: boolean): void => {
    if (open) {
      endDate.setValue(null, { emitEvent: false });
      endDate.disable({ emitEvent: false });
    } else {
      endDate.enable({ emitEvent: false });
    }
  };
  apply(noEnd.value);
  noEnd.valueChanges.subscribe((open) => {
    apply(open);
    endDate.markAsDirty();
    endDate.parent?.updateValueAndValidity();
  });
}

/** Formulario dunha tempada: nome, datas (sen fin, se non ten) e a semana. */
export function seasonDraftForm(season: Pick<Season, 'name' | 'startDate' | 'endDate' | 'week'>): SeasonDraftForm {
  const form: SeasonDraftForm = new FormGroup(
    {
      name: new FormControl(season.name, { nonNullable: true, validators: name }),
      startDate: new FormControl<IsoDate | null>(season.startDate || null, required),
      endDate: new FormControl<IsoDate | null>(season.endDate || null, required),
      noEnd: new FormControl(!season.endDate, { nonNullable: true }),
      week: weekForm(season as Season).controls.week,
    },
    { validators: range },
  );
  linkNoEnd(form.controls.noEnd, form.controls.endDate);
  return form;
}

/**
 * A tempada pendente coa que chocan as datas dunha nova, se a hai. Sen fin = ata sempre (a mesma
 * comparación ca `Season.overlaps` da API).
 */
export function clashing(
  drafts: readonly SeasonDraft[],
  value: { readonly startDate: string; readonly endDate: string | null },
): SeasonDraft | undefined {
  const last = (end: string | null): string => end ?? '9999-12-31';
  return drafts.find((draft) => {
    const { startDate, endDate, noEnd } = draft.form.getRawValue();
    const end = noEnd ? null : endDate;
    return !!startDate && (noEnd || !!endDate) && value.startDate <= last(end) && startDate <= last(value.endDate);
  });
}

/** Corpo do `PUT` da tempada desde o seu formulario. */
export function draftBody(form: SeasonDraftForm): SeasonBody {
  const value = form.getRawValue();
  return {
    name: value.name.trim(),
    startDate: value.startDate ?? '',
    endDate: value.noEnd ? undefined : (value.endDate ?? ''),
    week: form.controls.week.controls.map((day, i) => ({ day: DAYS[i], intervals: dayIntervals(day) })),
  };
}

/** As datas do formulario para velas: sen fin, `endDate` nulo; aínda sen escoller, baleiro. */
export function draftDates(form: SeasonDraftForm): Pick<Season, 'startDate' | 'endDate'> {
  const { startDate, endDate, noEnd } = form.getRawValue();
  return { startDate: startDate ?? '', endDate: noEnd ? null : (endDate ?? '') };
}
