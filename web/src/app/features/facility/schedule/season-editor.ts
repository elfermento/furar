// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, FormArray, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import type { ApiProblem } from '../../../core/errors/api-problem';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import type { SeasonDraftForm } from './season-drafts';
import { clashing, draftBody, seasonDraftForm } from './season-drafts';
import type { DayHours, Interval, Season, SeasonBody } from './schedule.store';
import { DAYS } from './schedule.store';
import type { DayForm, WeekForm } from './week-form';
import { applyWeekErrors, dayIntervals, weekForm } from './week-form';

export type ScheduleMode = 'SAME' | 'DAYS';
/** O horario que se propón: de 8:00 a 23:00 todos os días. */
export const DEFAULT_HOURS: Interval = { startMinute: 8 * 60, endMinute: 23 * 60 };
const WEEKDAYS = DAYS.filter((day) => day !== 'HOLIDAYS');

/** O mesmo tramo de luns a domingo; os festivos, pechados. */
export function sameWeek(hours: Interval): DayHours[] {
  return DAYS.map((day) => ({ day, intervals: day === 'HOLIDAYS' ? [] : [{ ...hours }] }));
}

/**
 * Como se ve unha semana gardada: «Mesmo horario todos os días» se de luns a domingo ten o mesmo
 * tramo único e os festivos pechados; se non, «Por días».
 */
export function modeOf(week: readonly DayHours[]): { mode: ScheduleMode; same: Interval } {
  const rows = new Map(week.map((row) => [row.day, row.intervals]));
  const first = rows.get('MONDAY')?.[0];
  const same =
    !!first &&
    (rows.get('HOLIDAYS') ?? []).length === 0 &&
    WEEKDAYS.every((day) => {
      const intervals = rows.get(day) ?? [];
      return (
        intervals.length === 1 &&
        intervals[0].startMinute === first.startMinute &&
        intervals[0].endMinute === first.endMinute
      );
    });
  const open = week.find((row) => row.intervals.length)?.intervals[0];
  return { mode: same ? 'SAME' : 'DAYS', same: first && same ? { ...first } : { ...(open ?? DEFAULT_HOURS) } };
}

export type SameHoursForm = FormGroup<{ startMinute: FormControl<number>; endMinute: FormControl<number> }>;

const SEASON_OVERLAP = 'facility.season-overlap';

function endAfterStart(group: AbstractControl): ValidationErrors | null {
  const { startMinute, endMinute } = (group as SameHoursForm).getRawValue();
  return endMinute <= startMinute ? { 'schedule.end-before-start': { index: 0 } } : null;
}

/** As 8 filas dunha semana, como formulario. */
export function weekRows(week: readonly DayHours[]): FormArray<DayForm> {
  const rows = DAYS.map((day) => week.find((row) => row.day === day) ?? { day, intervals: [] });
  return weekForm({ id: '', name: '', startDate: '', endDate: '', version: 0, week: rows }).controls.week;
}

/** Unha tempada ao empezar a editala: a gardada (co seu id) ou unha nova. */
export interface SeasonStart extends Pick<Season, 'name' | 'week'> {
  readonly id?: string | null;
  readonly startDate: IsoDate | '';
  readonly endDate: IsoDate | null;
}

/**
 * Unha tempada en edición, no paso «Horario» do asistente e no modo edición da
 * ficha: nome e datas (`SeasonDraftForm`), «Ata» cun interruptor (apagado = sen data de fin) e as
 * horas: o mesmo tramo todos os días ou día a día. Ao cambiar de modo, a semana parte do tramo
 * único e ao revés. Pínta a `furar-facility-season-list`.
 */
export class SeasonEditor {
  /** Id na API; nulo mentres non se crea. */
  id: string | null;
  readonly form: SeasonDraftForm;
  /** «Ata» acendido: a tempada ten data de fin (o contrario de `noEnd`). */
  readonly hasEnd: FormControl<boolean>;
  readonly mode: FormControl<ScheduleMode>;
  readonly sameHours: SameHoursForm;
  /** Todo xunto: eventos, «sucio» e «tocado». */
  readonly all: FormGroup;
  /** Engadida con «Engadir tempada»: hai que creala aínda que non se toque. */
  readonly added: boolean;
  /** A tempada da que se copiou o horario («Empezar co horario de»). */
  copiedFrom: string | null = null;
  /** Cambiou o modo: a semana mudou aínda que ningún campo estea sucio. */
  private converted = false;
  private current: ScheduleMode;

  constructor(
    readonly key: string,
    season: SeasonStart,
    { added = false, hasEnd = !!season.endDate } = {},
  ) {
    this.id = season.id ?? null;
    this.added = added;
    this.form = seasonDraftForm({ ...season, startDate: season.startDate as IsoDate });
    if (hasEnd === this.form.controls.noEnd.value) this.form.controls.noEnd.setValue(!hasEnd);
    this.hasEnd = new FormControl(hasEnd, { nonNullable: true });
    this.hasEnd.valueChanges.subscribe((on) => this.form.controls.noEnd.setValue(!on));
    const { mode, same } = modeOf(season.week);
    this.current = mode;
    this.mode = new FormControl<ScheduleMode>(mode, { nonNullable: true });
    this.sameHours = new FormGroup(
      {
        startMinute: new FormControl(same.startMinute, { nonNullable: true }),
        endMinute: new FormControl(same.endMinute, { nonNullable: true }),
      },
      { validators: endAfterStart },
    );
    this.all = new FormGroup({ season: this.form, hasEnd: this.hasEnd, mode: this.mode, sameHours: this.sameHours });
    this.all.markAsPristine();
    this.mode.valueChanges.subscribe((next) => {
      if (next !== this.current) this.convert(next);
      this.current = next;
    });
  }

  get week(): FormArray<DayForm> {
    return this.form.controls.week;
  }

  /** O nome escrito (sen espazos de máis). */
  get name(): string {
    return this.form.controls.name.value.trim();
  }

  get dirty(): boolean {
    return this.all.dirty || this.converted || (this.added && !this.id);
  }

  /**
   * Nome, datas e horas ben (sen mirar se pisa outra). Co asistente, polo menos un día aberto; na
   * ficha (`requireOpenDay` falso) pode haber tempadas pechadas enteiras.
   */
  ok(requireOpenDay = true): boolean {
    const c = this.form.controls;
    const dates = c.startDate.valid && (c.endDate.disabled || c.endDate.valid);
    const open = !requireOpenDay || !this.noOpenDay();
    const hours = this.mode.value === 'SAME' ? this.sameHours.valid : this.week.valid && open;
    return c.name.valid && dates && hours;
  }

  /** «Por días» sen ningún día aberto. */
  noOpenDay(): boolean {
    return this.mode.value === 'DAYS' && !this.week.controls.some((day) => day.controls.open.value);
  }

  /** A semana que se garda: «Mesmo horario» de luns a domingo (festivos pechados) ou día a día. */
  weekBody(): DayHours[] {
    if (this.mode.value === 'SAME') return sameWeek(this.sameHours.getRawValue());
    return DAYS.map((day, i) => ({ day, intervals: this.week.at(i) ? dayIntervals(this.week.at(i)) : [] }));
  }

  /** Corpo do `PUT` da tempada. */
  body(): SeasonBody {
    const week = this.weekBody().map((row) => ({ day: row.day, intervals: [...row.intervals] }));
    return { ...draftBody(this.form), week };
  }

  /** As datas tal como están (sen fin, `endDate` nulo; sen «Ata» aínda, o día de inicio). */
  dates(): { startDate: string; endDate: string | null } | null {
    const { startDate, endDate, noEnd } = this.form.getRawValue();
    if (!startDate) return null;
    return { startDate, endDate: noEnd ? null : (endDate ?? startDate) };
  }

  /** Empeza co horario doutra: o seu modo, o tramo único e a semana. */
  copyHours(from: SeasonEditor): void {
    this.copiedFrom = from.key;
    this.current = from.mode.value;
    this.mode.setValue(from.mode.value);
    this.sameHours.setValue(from.sameHours.getRawValue());
    const week = DAYS.map((day, i) => ({ day, intervals: dayIntervals(from.week.at(i)) }));
    this.form.setControl('week', weekRows(week));
  }

  touchAll(): void {
    this.all.markAllAsTouched();
  }

  markPristine(): void {
    this.all.markAsPristine();
    this.converted = false;
  }

  /** Un erro da API no seu campo: o choque, en «Desde»; os dun tramo, na súa fila. */
  showProblem(problem: ApiProblem): void {
    if (problem.code === SEASON_OVERLAP) {
      const start = this.form.controls.startDate;
      start.setErrors({ [SEASON_OVERLAP]: problem.params });
      start.markAsTouched();
      return;
    }
    applyWeekErrors(this.form as unknown as WeekForm, problem);
  }

  /** «Mesmo horario» ↔ «Por días». */
  private convert(mode: ScheduleMode): void {
    if (mode === 'DAYS') {
      if (!this.week.controls.some((day) => day.controls.open.value)) {
        this.form.setControl('week', weekRows(sameWeek(this.sameHours.getRawValue())));
      }
    } else {
      const open = this.week.controls.map((day) => dayIntervals(day)).find((intervals) => intervals.length);
      if (open) this.sameHours.setValue({ ...open[0] });
    }
    this.converted = true;
  }
}

/** A tempada anterior da lista coa que pisa as datas desta, se a hai (coma `Season.overlaps`). */
export function overlapOf(list: readonly SeasonEditor[], season: SeasonEditor): SeasonEditor | null {
  const dates = season.dates();
  const before = list.slice(0, list.indexOf(season));
  if (!dates || !before.length) return null;
  const clash = clashing(
    before.map((other) => ({ key: other.key, saved: null, form: other.form })),
    dates,
  );
  return before.find((other) => other.key === clash?.key) ?? null;
}
