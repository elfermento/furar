// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import type { OpenMatchPolicyRequest, OpenMatchPolicyResponse } from './municipality.store';

/** «Cancelar se non hai mínimo»: de 1 a 72 horas antes (ADR-0040). */
export const CANCEL_HOURS = { min: 1, max: 72 } as const;

export type OpenMatchPolicyForm = FormGroup<{
  enabled: FormControl<boolean>;
  cancelHours: FormControl<string>;
}>;

/** A política dun concello que aínda non a ten gardada: apagada, 12 horas. */
const DEFAULTS: OpenMatchPolicyResponse = { enabled: false, cancelHours: 12 };

function hours(control: AbstractControl): ValidationErrors | null {
  const text = String(control.value ?? '').trim();
  const value = /^\d{1,3}$/.test(text) ? Number(text) : NaN;
  return value >= CANCEL_HOURS.min && value <= CANCEL_HOURS.max ? null : { min: { ...CANCEL_HOURS } };
}

/** «Buscar xente para xogar» en Concello › Xeral: o interruptor e as horas, como texto. */
export function openMatchPolicyForm(policy: OpenMatchPolicyResponse | undefined): OpenMatchPolicyForm {
  const saved = policy ?? DEFAULTS;
  return new FormGroup({
    enabled: new FormControl(saved.enabled, { nonNullable: true }),
    cancelHours: new FormControl(String(saved.cancelHours), { nonNullable: true, validators: hours }),
  });
}

export function openMatchPolicyChanged(
  form: OpenMatchPolicyForm,
  policy: OpenMatchPolicyResponse | undefined,
): boolean {
  return JSON.stringify(form.getRawValue()) !== JSON.stringify(openMatchPolicyForm(policy).getRawValue());
}

export function openMatchPolicyBody(form: OpenMatchPolicyForm): OpenMatchPolicyRequest {
  const { enabled, cancelHours } = form.getRawValue();
  return { enabled, cancelHours: Number(cancelHours) };
}
