// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import type { CurrentMunicipality } from '../../core/municipality/current-municipality';
import type { MunicipalityStore, NoShowPolicyRequest, NoShowPolicyResponse } from './municipality.store';

interface Controls {
  enabled: FormControl<boolean>;
  threshold: FormControl<string>;
  windowDays: FormControl<string>;
  blockDays: FormControl<string>;
}

/** Tarxeta «Non presentados» (ADR-0032): o interruptor e os tres números, como texto. */
export class NoShowForm extends FormGroup<Controls> {
  /** Os tres números, na orde da tarxeta. */
  readonly numbers = ['threshold', 'windowDays', 'blockDays'] as const;
}

/** A penalización dun concello que aínda non a ten gardada: apagada, 3 faltas en 30 días, 15 de bloqueo. */
const DEFAULTS: NoShowPolicyResponse = { enabled: false, threshold: 3, windowDays: 30, blockDays: 15 };

/** Un número enteiro de 1 a `max` (o erro é o de rango de sempre). */
function whole(max: number): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const text = String(control.value ?? '').trim();
    const value = /^\d{1,4}$/.test(text) ? Number(text) : NaN;
    return value >= 1 && value <= max ? null : { min: { min: 1, max } };
  };
}

export function noShowForm(policy: NoShowPolicyResponse | undefined): NoShowForm {
  const saved = policy ?? DEFAULTS;
  const number = (value: number, max: number) =>
    new FormControl(String(value), { nonNullable: true, validators: whole(max) });
  return new NoShowForm({
    enabled: new FormControl(saved.enabled, { nonNullable: true }),
    threshold: number(saved.threshold, 20),
    windowDays: number(saved.windowDays, 365),
    blockDays: number(saved.blockDays, 365),
  });
}

/**
 * Apagada, os números non se ven: o que estea a medio escribir (e non valla) volve ao gardado, para
 * que un número agochado non impida gardar.
 */
export function settleHiddenNumbers(form: NoShowForm, policy: NoShowPolicyResponse | undefined): void {
  if (form.controls.enabled.value || form.valid) return;
  form.reset({ ...noShowForm(policy).getRawValue(), enabled: false });
}

/** Se o formulario difire da penalización gardada. */
export function noShowChanged(form: NoShowForm, policy: NoShowPolicyResponse | undefined): boolean {
  return JSON.stringify(form.getRawValue()) !== JSON.stringify(noShowForm(policy).getRawValue());
}

export function noShowBody(form: NoShowForm): NoShowPolicyRequest {
  const { enabled, threshold, windowDays, blockDays } = form.getRawValue();
  return {
    enabled,
    threshold: Number(threshold),
    windowDays: Number(windowDays),
    blockDays: Number(blockDays),
  };
}

/** O seu propio `PUT` dentro do «Gardar» de Concello › Xeral, coa versión que deixou o paso anterior. */
export async function saveNoShowPolicy(
  store: Pick<MunicipalityStore, 'updateNoShowPolicy'>,
  current: CurrentMunicipality,
  form: NoShowForm,
): Promise<void> {
  const municipality = current.value();
  if (!municipality || !noShowChanged(form, municipality.noShowPolicy)) return;
  const saved = await store.updateNoShowPolicy(municipality.slug, municipality.version, noShowBody(form));
  current.set({ ...municipality, version: saved.version, noShowPolicy: saved.body });
}
