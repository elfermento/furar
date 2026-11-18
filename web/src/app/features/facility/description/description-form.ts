// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup } from '@angular/forms';
import type { Facility } from '../detail/facility-detail.store';

/** Máximo por idioma (o mesmo ca `FacilityDescription.MAX` da API). */
export const DESCRIPTION_MAX = 2000;
/** Código do erro: o mesmo texto ca o da API (`facility.description-invalid`). */
export const DESCRIPTION_INVALID = 'facility.description-invalid';

export type DescriptionLang = 'gl' | 'es';
export const DESCRIPTION_LANGS: readonly DescriptionLang[] = ['gl', 'es'];

export type DescriptionForm = FormGroup<Record<DescriptionLang, FormControl<string>>>;
export type DescriptionValue = Record<DescriptionLang, string>;

const blank = (value: unknown): boolean => typeof value !== 'string' || value.trim() === '';

/**
 * Igual ca o servidor: o texto é opcional, pero se hai, vai nos dous idiomas e con ata `max`
 * caracteres cada un (tras quitar os espazos dos extremos). O erro leva o código da API.
 */
function bilingualText(other: DescriptionLang, max: number, code: string): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = typeof control.value === 'string' ? control.value.trim() : '';
    const missing = value === '' && !blank(control.parent?.get(other)?.value);
    return missing || value.length > max ? { [code]: { max } } : null;
  };
}

/** Texto opcional gl/es (descrición, normas da instalación): cada idioma revalida o outro. */
export function optionalBilingualForm(max: number, code: string): DescriptionForm {
  const form: DescriptionForm = new FormGroup({
    gl: new FormControl('', { nonNullable: true, validators: bilingualText('es', max, code) }),
    es: new FormControl('', { nonNullable: true, validators: bilingualText('gl', max, code) }),
  });
  // Cada texto depende do outro: ao escribir nun, revalídase o outro.
  form.controls.gl.valueChanges.subscribe(() => form.controls.es.updateValueAndValidity({ emitEvent: false }));
  form.controls.es.valueChanges.subscribe(() => form.controls.gl.updateValueAndValidity({ emitEvent: false }));
  return form;
}

export function descriptionForm(): DescriptionForm {
  return optionalBilingualForm(DESCRIPTION_MAX, DESCRIPTION_INVALID);
}

export function descriptionValue(facility: Facility | null): DescriptionValue | null {
  if (!facility) return null;
  return { gl: facility.description?.gl ?? '', es: facility.description?.es ?? '' };
}

/** Máximo das normas da instalación por idioma (`HouseRules.MAX` da API) e o seu código de erro. */
export const HOUSE_RULES_MAX = 2000;
export const HOUSE_RULES_INVALID = 'facility.house-rules-invalid';

export function houseRulesValue(facility: Facility | null): DescriptionValue | null {
  if (!facility) return null;
  return { gl: facility.houseRules?.gl ?? '', es: facility.houseRules?.es ?? '' };
}

/** O corpo do `PUT`: os dous textos sen espazos nos extremos (baleiros = sen texto). */
export function descriptionBody(value: DescriptionValue): DescriptionValue {
  return { gl: value.gl.trim(), es: value.es.trim() };
}
