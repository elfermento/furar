// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { OpenMatchLevel } from '../../../shared/open-matches/open-match-format';
import type { MyOpenMatchResponse, OpenMatchRequest } from '../../../shared/open-matches/open-matches.store';

/** Límites da API (ADR-0040, punto 3). */
export const MAX_PEOPLE = 40;
export const TITLE_MAX = 80;
export const DESCRIPTION_MAX = 1000;

/** «Sen indicar» no despregable do nivel. */
export type LevelChoice = OpenMatchLevel | 'NONE';

export type OpenMatchForm = FormGroup<{
  level: FormControl<LevelChoice>;
  title: FormControl<string>;
  description: FormControl<string>;
  knownCount: FormControl<string>;
  minimum: FormControl<string>;
  maximum: FormControl<string>;
  approval: FormControl<boolean>;
}>;

const number = (control: AbstractControl): number => {
  const text = String(control.value ?? '').trim();
  return /^\d{1,3}$/.test(text) ? Number(text) : NaN;
};

/** Enteiro de `min` a `max` (o erro é o de rango de sempre). */
function whole(min: number, max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = number(control);
    return value >= min && value <= max ? null : { min: { min, max } };
  };
}

/**
 * Contas entre campos: o máximo non pode ser menor ca o mínimo, e «Xa somos» ten que ser menor ca o
 * máximo. O erro vai no campo que se corrixe (o máximo, «Xa somos»), só se os dous números valen.
 */
function counts(form: AbstractControl): null {
  const group = form as OpenMatchForm;
  const { knownCount, minimum, maximum } = group.controls;
  const known = number(knownCount);
  const min = number(minimum);
  const max = number(maximum);
  setOwn(maximum, 'openMatch.maximum-below-minimum', !isNaN(min) && !isNaN(max) && max < min, { min });
  setOwn(knownCount, 'openMatch.known-not-below-maximum', !isNaN(known) && !isNaN(max) && known >= max, { max });
  return null;
}

/** Pon ou quita un erro propio sen tocar os dos validadores do campo. */
function setOwn(control: AbstractControl, code: string, on: boolean, params: Record<string, unknown>): void {
  const errors = { ...(control.errors ?? {}) };
  if (on) {
    if (Object.keys(errors).some((key) => key !== code)) return;
    control.setErrors({ ...errors, [code]: params });
  } else if (code in errors) {
    const rest = Object.fromEntries(Object.entries(errors).filter(([key]) => key !== code));
    control.setErrors(Object.keys(rest).length ? rest : null);
  }
}

/**
 * O formulario de «Buscar xente para xogar»: o que xa hai (editar) ou, ao abrir, «Xa somos» coas
 * prazas da reserva (1 nunha pista enteira) e o resto baleiro.
 */
export function openMatchForm(existing: MyOpenMatchResponse | null, seats: number): OpenMatchForm {
  const text = (value: string | undefined, max: number) =>
    new FormControl(value ?? '', { nonNullable: true, validators: Validators.maxLength(max) });
  const count = (value: number | undefined, min: number, max: number) =>
    new FormControl(value === undefined ? '' : String(value), {
      nonNullable: true,
      validators: [Validators.required, whole(min, max)],
    });
  return new FormGroup(
    {
      level: new FormControl<LevelChoice>(existing?.level ?? 'NONE', { nonNullable: true }),
      title: text(existing?.title, TITLE_MAX),
      description: text(existing?.description, DESCRIPTION_MAX),
      knownCount: count(existing?.knownCount ?? Math.max(1, seats), 1, MAX_PEOPLE - 1),
      minimum: count(existing?.minimum, 2, MAX_PEOPLE),
      maximum: count(existing?.maximum, 2, MAX_PEOPLE),
      approval: new FormControl(existing?.approval ?? false, { nonNullable: true }),
    },
    { validators: counts },
  );
}

export function openMatchBody(form: OpenMatchForm): OpenMatchRequest {
  const value = form.getRawValue();
  const title = value.title.trim();
  const description = value.description.trim();
  return {
    knownCount: Number(value.knownCount),
    minimum: Number(value.minimum),
    maximum: Number(value.maximum),
    approval: value.approval,
    ...(value.level === 'NONE' ? {} : { level: value.level }),
    ...(title ? { title } : {}),
    ...(description ? { description } : {}),
  };
}
