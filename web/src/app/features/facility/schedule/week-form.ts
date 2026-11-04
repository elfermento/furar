// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormArray, FormControl, FormGroup } from '@angular/forms';
import type { ApiProblem } from '../../../core/errors/api-problem';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { applyFieldErrors } from '../../../shared/ui/edit-section/section-errors';
import { END_OF_DAY } from '../../../shared/ui/time-picker/time-format';
import type { DayHours, Interval, Season } from './schedule.store';

/** Os mesmos límites ca `WeekHours` da API. */
export const INTERVALS_MAX = 10;
/** Tramo que se propón ao abrir un día pechado (o da maqueta). */
export const FIRST_INTERVAL: Interval = { startMinute: 9 * 60, endMinute: 14 * 60 };

export type IntervalForm = FormGroup<{
  startMinute: FormControl<number | null>;
  endMinute: FormControl<number | null>;
}>;
export type DayForm = FormGroup<{ open: FormControl<boolean>; intervals: FormArray<IntervalForm> }>;
export type WeekForm = FormGroup<{ week: FormArray<DayForm> }>;

/** Erros do día que pon a pantalla (os da API traen o seu código `facility.*`). */
const NO_INTERVALS = 'schedule.no-intervals';
const END_BEFORE_START = 'schedule.end-before-start';
const OVERLAP = 'schedule.overlap';

const ROW_ERROR_KEYS: Record<string, string> = {
  [NO_INTERVALS]: 'facility.schedule.errors.noIntervals',
  [END_BEFORE_START]: 'facility.schedule.errors.endBeforeStart',
  [OVERLAP]: 'facility.schedule.errors.overlap',
};

/**
 * Regras dun día aberto (especificación, bloque 2, «Validación»): polo menos un tramo, cada un
 * remata despois de empezar (o selector xa non pasa das 24:00) e sen tramos que se pisen. Leva o
 * índice do tramo que falla para marcalo.
 */
function dayRules(control: AbstractControl): ValidationErrors | null {
  const { open, intervals } = (control as DayForm).getRawValue();
  if (!open) return null;
  if (intervals.length === 0) return { [NO_INTERVALS]: {} };
  const bad = intervals.findIndex(
    ({ startMinute: s, endMinute: e }) => s === null || e === null || e <= s || e > END_OF_DAY,
  );
  if (bad >= 0) return { [END_BEFORE_START]: { index: bad } };
  const overlap = intervals.findIndex((a, j) =>
    intervals
      .slice(0, j)
      .some((b) => (a.startMinute ?? 0) < (b.endMinute ?? 0) && (b.startMinute ?? 0) < (a.endMinute ?? 0)),
  );
  return overlap >= 0 ? { [OVERLAP]: { index: overlap } } : null;
}

export function intervalForm(interval: Interval = FIRST_INTERVAL): IntervalForm {
  return new FormGroup({
    startMinute: new FormControl<number | null>(interval.startMinute),
    endMinute: new FormControl<number | null>(interval.endMinute),
  });
}

function dayForm(hours: DayHours): DayForm {
  const open = hours.intervals.length > 0;
  const form: DayForm = new FormGroup(
    {
      open: new FormControl(open, { nonNullable: true }),
      intervals: new FormArray(hours.intervals.map((i) => intervalForm(i))),
    },
    { validators: dayRules },
  );
  if (!open) form.controls.intervals.disable({ emitEvent: false });
  form.controls.open.valueChanges.subscribe((value) => setOpen(form, value));
  return form;
}

/**
 * Formulario da semana dunha tempada (`FormArray` de 8 filas). Pechar un día desactiva os seus
 * tramos sen borralos: se se volve abrir, aparecen os que había.
 */
export function weekForm(season: Season): WeekForm {
  return new FormGroup({ week: new FormArray(season.week.map(dayForm)) });
}

/** Interruptor aberto/pechado: abrir un día sen tramos propón o primeiro. */
export function setOpen(day: DayForm, open: boolean): void {
  const intervals = day.controls.intervals;
  if (open) {
    intervals.enable({ emitEvent: false });
    if (intervals.length === 0) intervals.push(intervalForm(), { emitEvent: false });
  } else {
    intervals.disable({ emitEvent: false });
  }
  day.updateValueAndValidity();
}

/** «+ Tramo»: unha hora despois do último, catro horas (sen pasar das 24:00). */
export function addInterval(day: DayForm): void {
  const intervals = day.controls.intervals;
  const last = intervals.at(intervals.length - 1)?.getRawValue();
  const start = last ? Math.min((last.endMinute ?? 0) + 60, END_OF_DAY - 60) : FIRST_INTERVAL.startMinute;
  const next = last ? { startMinute: start, endMinute: Math.min(start + 240, END_OF_DAY) } : FIRST_INTERVAL;
  intervals.push(intervalForm(next));
  day.markAsDirty();
}

export function removeInterval(day: DayForm, index: number): void {
  day.controls.intervals.removeAt(index);
  day.markAsDirty();
}

/** Tramos dun día tal como están no formulario (os pechados, ningún), por orde de inicio. */
export function dayIntervals(day: DayForm): Interval[] {
  const { open, intervals } = day.getRawValue();
  if (!open) return [];
  return intervals
    .filter((i): i is Interval => i.startMinute !== null && i.endMinute !== null)
    .map(({ startMinute, endMinute }) => ({ startMinute, endMinute }))
    .sort((a, b) => a.startMinute - b.startMinute);
}

/**
 * O erro que se amosa nunha fila e o tramo que marca: primeiro as regras do día; despois, os da
 * API que chegaron a un tramo (`week[i].intervals[j]`) ou á lista de tramos.
 */
export function dayError(day: DayForm): { error: ControlError; index: number | null } | null {
  const own = Object.entries(day.errors ?? {})[0];
  if (own) {
    const [code, params] = own as [string, { index?: number } | null];
    const index = params?.index ?? null;
    return { error: { key: ROW_ERROR_KEYS[code] ?? `errors.${code}`, params: { n: (index ?? 0) + 1 } }, index };
  }
  const intervals = day.controls.intervals;
  const listError = Object.entries(intervals.errors ?? {})[0];
  if (listError) return { error: { key: `errors.${listError[0]}`, params: listError[1] ?? {} }, index: null };
  const index = intervals.controls.findIndex((interval) => interval.errors);
  if (index < 0) return null;
  const [code, params] = Object.entries(intervals.at(index).errors ?? {})[0];
  return { error: { key: `errors.${code}`, params: (params as Record<string, unknown>) ?? {} }, index };
}

/**
 * Pon no seu tramo os erros da API (`week[2].intervals[1]`) e devolve os índices dos días que
 * quedaron con erro, para abrilos. O que non ten campo (`season-overlap`…) vai ao aviso.
 */
export function applyWeekErrors(form: WeekForm, problem: ApiProblem): { days: number[]; unmatched: number } {
  const unmatched = applyFieldErrors(form, problem, () => null);
  const days = problem.fieldErrors
    .map((error) => /^week\[(\d+)\]/.exec(error.field)?.[1])
    .filter((day): day is string => day !== undefined)
    .map(Number);
  return { days: [...new Set(days)], unmatched };
}
