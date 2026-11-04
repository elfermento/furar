// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormArray, FormControl, FormGroup } from '@angular/forms';
import type { ApiProblem } from '../../../core/errors/api-problem';
import { applyFieldErrors } from '../../../shared/ui/edit-section/section-errors';
import { ALL_WEEKDAYS } from '../../../shared/ui/weekday-picker/weekday-picker.component';
import type { PriceForm, RulesForm } from './use-terms';
import {
  PRICE_INVALID,
  WEEKDAYS_INVALID,
  priceBody,
  priceForm,
  rulesBody,
  rulesForm,
  weekdaysValidator,
} from './use-terms';
import type { Availability, RentalMode, Use, UseBody } from './uses.store';

/** Os mesmos límites ca `FacilityUse` da API. */
export const USE_LIMITS = {
  nameMax: 100,
  partsMin: 2,
  partsMax: 20,
  periodsMax: 50,
  capacityMin: 2,
  capacityMax: 10_000,
} as const;
export const RENTAL_MODES: readonly RentalMode[] = ['WHOLE', 'PARTS', 'SEATS'];
export const SLOT_MINUTES: readonly number[] = [30, 60, 90, 120];
export const AVAILABILITIES: readonly Availability[] = ['ALWAYS', 'DATES'];

export type PartForm = FormGroup<{ id: FormControl<string | null>; name: FormControl<string> }>;
export type PeriodForm = FormGroup<{ from: FormControl<string | null>; to: FormControl<string | null> }>;
export type UseForm = FormGroup<{
  name: FormControl<string>;
  rentalMode: FormControl<RentalMode>;
  parts: FormArray<PartForm>;
  capacity: FormControl<number | null>;
  slotMinutes: FormControl<number>;
  availability: FormControl<Availability>;
  periods: FormArray<PeriodForm>;
  blocksOthers: FormControl<boolean>;
  weekdays: FormControl<number>;
  price: PriceForm;
  rules: RulesForm;
}>;

const trimmed = (value: unknown): string => (typeof value === 'string' ? value.trim() : '');

/** Texto obrigatorio de ata `max` caracteres: baleiro, «obrigatorio»; longo, `validation.size` coma a API. */
function text(max: number): ValidatorFn {
  return (control) => {
    const length = trimmed(control.value).length;
    if (length === 0) return { 'validation.required': {} };
    return length > max ? { 'validation.size': { min: 1, max } } : null;
  };
}

function capacity(control: AbstractControl): ValidationErrors | null {
  const value = control.value as number | null;
  if (value === null || value === undefined || (value as unknown) === '')
    return { 'facility.use-capacity-required': {} };
  const { capacityMin: min, capacityMax: max } = USE_LIMITS;
  return !Number.isInteger(value) || value < min || value > max ? { 'validation.range': { min, max } } : null;
}

/** Polo menos dúas partes, ata vinte, e sen dous nomes iguais (sen distinguir maiúsculas). */
function parts(control: AbstractControl): ValidationErrors | null {
  const names = (control.value as { name: string }[]).map((part) => trimmed(part.name).toLocaleLowerCase());
  const { partsMin: min, partsMax: max } = USE_LIMITS;
  if (names.length < min) return { 'facility.use-parts-required': { min } };
  if (names.length > max) return { 'validation.size': { min, max } };
  const filled = names.filter(Boolean);
  return new Set(filled).size !== filled.length ? { 'facility.use-part-name-taken': {} } : null;
}

/**
 * Polo menos un período, ata cincuenta, e sen que dous se pisen. O solape vai co índice do período
 * que pisa a un anterior (`facility.use-period-overlap`, como a API), para amosalo na súa fila.
 */
function periods(control: AbstractControl): ValidationErrors | null {
  const values = (control as FormArray<PeriodForm>).getRawValue();
  if (values.length === 0) return { 'facility.use-dates-required': {} };
  if (values.length > USE_LIMITS.periodsMax) return { 'validation.size': { min: 1, max: USE_LIMITS.periodsMax } };
  const index = values.findIndex(
    ({ from, to }, j) =>
      !!from && !!to && values.slice(0, j).some((o) => !!o.from && !!o.to && from <= o.to && o.from <= to),
  );
  return index >= 0 ? { 'facility.use-period-overlap': { index } } : null;
}

const required = (control: AbstractControl): ValidationErrors | null =>
  control.value ? null : { 'validation.required': {} };

/** «Ata» non pode ir antes de «Desde» (extremos incluídos: un día = desde e ata iguais). */
function period(control: AbstractControl): ValidationErrors | null {
  const { from, to } = control.value as { from: string | null; to: string | null };
  return from && to && to < from ? { 'validation.range': {} } : null;
}

export function partForm(id: string | null = null, name = ''): PartForm {
  return new FormGroup({
    id: new FormControl<string | null>(id),
    name: new FormControl(name, { nonNullable: true, validators: text(USE_LIMITS.nameMax) }),
  });
}

export function periodForm(from: string | null = null, to: string | null = null): PeriodForm {
  return new FormGroup(
    {
      from: new FormControl<string | null>(from, required),
      to: new FormControl<string | null>(to, required),
    },
    { validators: period },
  );
}

/**
 * Formulario dun uso (novo ou existente) coas regras da API. Os campos que non tocan co modo
 * (prazas, partes) ou con «Cando» (períodos, «bloquea») quedan desactivados: non validan nin van no
 * corpo, pero conservan o escrito se o xestor volve a ese modo.
 */
export function useForm(use?: Use): UseForm {
  const form: UseForm = new FormGroup({
    name: new FormControl(use?.name ?? '', { nonNullable: true, validators: text(USE_LIMITS.nameMax) }),
    rentalMode: new FormControl<RentalMode>(use?.rentalMode ?? 'WHOLE', { nonNullable: true }),
    parts: new FormArray(use?.parts.map((part) => partForm(part.id, part.name)) ?? [], parts),
    capacity: new FormControl<number | null>(use?.capacity ?? null, capacity),
    slotMinutes: new FormControl(use?.slotMinutes ?? 60, { nonNullable: true }),
    availability: new FormControl<Availability>(use?.availability ?? 'ALWAYS', { nonNullable: true }),
    periods: new FormArray(use?.periods.map((p) => periodForm(p.from, p.to)) ?? [], periods),
    blocksOthers: new FormControl(use?.blocksOthers ?? false, { nonNullable: true }),
    weekdays: new FormControl(use?.weekdays ?? ALL_WEEKDAYS, { nonNullable: true, validators: weekdaysValidator }),
    price: priceForm(use?.price),
    rules: rulesForm(use?.rules),
  });
  const applyMode = (mode: RentalMode): void => {
    toggle(form.controls.capacity, mode === 'SEATS');
    toggle(form.controls.parts, mode === 'PARTS');
    if (mode === 'PARTS' && form.controls.parts.length === 0) {
      form.controls.parts.push(partForm());
      form.controls.parts.push(partForm());
    }
  };
  const applyAvailability = (availability: Availability): void => {
    toggle(form.controls.periods, availability === 'DATES');
    toggle(form.controls.blocksOthers, availability === 'DATES');
    if (availability === 'DATES' && form.controls.periods.length === 0) form.controls.periods.push(periodForm());
  };
  applyMode(form.controls.rentalMode.value);
  applyAvailability(form.controls.availability.value);
  form.controls.rentalMode.valueChanges.subscribe(applyMode);
  form.controls.availability.valueChanges.subscribe(applyAvailability);
  return form;
}

function toggle(control: AbstractControl, enabled: boolean): void {
  if (enabled && control.disabled) control.enable({ emitEvent: false });
  if (!enabled && control.enabled) control.disable({ emitEvent: false });
}

/**
 * O corpo do `POST`/`PUT`: só o que toca co modo e con «Cando», sen espazos nos extremos, e
 * sempre os días da semana, o prezo e as normas (só as que teñen valor: baleira = non se aplica).
 */
export function useBody(form: UseForm): UseBody {
  const value = form.getRawValue();
  const dates = value.availability === 'DATES';
  return {
    name: value.name.trim(),
    rentalMode: value.rentalMode,
    slotMinutes: value.slotMinutes,
    availability: value.availability,
    blocksOthers: dates && value.blocksOthers,
    weekdays: value.weekdays,
    price: priceBody(form.controls.price),
    rules: rulesBody(form.controls.rules),
    ...(value.rentalMode === 'SEATS' ? { capacity: value.capacity ?? undefined } : {}),
    ...(value.rentalMode === 'PARTS'
      ? { parts: value.parts.map((part) => ({ ...(part.id ? { id: part.id } : {}), name: part.name.trim() })) }
      : {}),
    ...(dates ? { periods: value.periods.map((p) => ({ from: p.from ?? '', to: p.to ?? '' })) } : {}),
  };
}

/**
 * Pon os erros da API nos campos do uso. Os que non traen campo van a un sitio fixo: o nome
 * repetido ao nome, a parte repetida ás partes, o solape con outro uso que bloquea aos períodos,
 * os días baleiros aos días e o prezo ao importe (as normas veñen co seu campo).
 * Devolve `false` se algo quedou sen campo (p. ex. `use-has-bookings`): vai ao aviso do uso.
 */
export function applyUseErrors(form: UseForm, problem: ApiProblem): boolean {
  const unmatched = applyFieldErrors(form, problem, () => null);
  const target = problem.fieldErrors.length === 0 ? controlForCode(form, problem.code) : null;
  if (target) {
    target.setErrors({ ...target.errors, [problem.code]: problem.params });
    target.markAsTouched();
  }
  return unmatched === 0 && (target !== null || form.invalid);
}

function controlForCode(form: UseForm, code: string): AbstractControl | null {
  switch (code) {
    case 'facility.use-name-taken':
      return form.controls.name;
    case 'facility.use-part-name-taken':
      return form.controls.parts;
    case 'facility.exclusive-use-overlap':
      return form.controls.periods;
    case WEEKDAYS_INVALID:
      return form.controls.weekdays;
    case PRICE_INVALID:
      return form.controls.price.controls.amountCents;
    default:
      return null;
  }
}
