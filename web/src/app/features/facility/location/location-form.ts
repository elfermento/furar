// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import type { ControlError } from '../../../shared/custom-fields/custom-field-controls';
import { fieldError } from '../../../shared/ui/field-errors';
import type { MapPoint } from '../../../shared/ui/map/map-point';
import { pointOf } from '../../../shared/ui/map/map-point';
import type { Facility } from '../detail/facility-detail.store';
import type { AddressMatch } from './address-search';
import type { AddressRequest } from './location.store';

/** Os mesmos límites ca `Address` da API. */
export const STREET_MAX = 200;
export const NUMBER_MAX = 20;
export const LOCALITY_MAX = 100;
const FIVE_DIGITS = /^[0-9]{5}$/;

export type AddressTextField = 'street' | 'streetNumber' | 'postalCode' | 'locality';
export const ADDRESS_TEXT_FIELDS: readonly AddressTextField[] = ['street', 'streetNumber', 'postalCode', 'locality'];

export type LocationForm = FormGroup<{
  street: FormControl<string>;
  streetNumber: FormControl<string>;
  postalCode: FormControl<string>;
  locality: FormControl<string>;
  /** Non se escribe: vén da instalación ou do buscador (código INE do concello). */
  ineCode: FormControl<string>;
  point: FormControl<MapPoint | null>;
}>;
export type LocationValue = ReturnType<LocationForm['getRawValue']>;

/** Obrigatorio coma no servidor, que quita os espazos dos extremos antes de comprobar. */
function notBlank(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim() === '' ? { required: true } : null;
}

const text = (validators: ((c: AbstractControl) => ValidationErrors | null)[]) =>
  new FormControl('', { nonNullable: true, validators });

export function locationForm(): LocationForm {
  return new FormGroup({
    street: text([notBlank, Validators.maxLength(STREET_MAX)]),
    streetNumber: text([Validators.maxLength(NUMBER_MAX)]),
    postalCode: text([notBlank, Validators.pattern(FIVE_DIGITS)]),
    locality: text([notBlank, Validators.maxLength(LOCALITY_MAX)]),
    ineCode: text([]),
    point: new FormControl<MapPoint | null>(null, { validators: Validators.required }),
  });
}

export function locationValue(facility: Facility | null): LocationValue | null {
  if (!facility) return null;
  const address = facility.address;
  if (!address) return { ...EMPTY_LOCATION };
  const { street, streetNumber, postalCode, locality, ineCode } = address;
  return {
    street,
    streetNumber: streetNumber ?? '',
    postalCode,
    locality,
    ineCode,
    point: pointOf(address),
  };
}

/** Sen enderezo (borrador): todo baleiro. */
const EMPTY_LOCATION: LocationValue = {
  street: '',
  streetNumber: '',
  postalCode: '',
  locality: '',
  ineCode: '',
  point: null,
};

/** O corpo do `PUT`: textos sen espazos nos extremos e sen número se está baleiro («s/n»). */
export function locationBody(value: LocationValue): AddressRequest {
  const number = value.streetNumber.trim();
  return {
    street: value.street.trim(),
    ...(number ? { streetNumber: number } : {}),
    postalCode: value.postalCode.trim(),
    locality: value.locality.trim(),
    ineCode: value.ineCode,
    latitude: value.point?.latitude,
    longitude: value.point?.longitude,
  };
}

/**
 * Enche o formulario co enderezo do buscador. Só pisa o que trae: se vén a vía, o número vai con
 * ela (aínda que veña baleiro); o resto, campo a campo. O formulario queda sucio.
 */
export function applyMatch(form: LocationForm, match: AddressMatch): void {
  const patch: Partial<LocationValue> = {};
  if (match.street) {
    patch.street = match.street;
    patch.streetNumber = match.streetNumber ?? '';
  }
  if (match.postalCode) patch.postalCode = match.postalCode;
  if (match.locality) patch.locality = match.locality;
  if (match.ineCode) patch.ineCode = match.ineCode;
  if (typeof match.latitude === 'number' && typeof match.longitude === 'number') {
    patch.point = { latitude: match.latitude, longitude: match.longitude };
  }
  form.patchValue(patch);
  form.markAsDirty();
}

/** Control dun erro da API («address.street», «address.latitude»…). */
export function locationControl(form: LocationForm, field: string): AbstractControl | null {
  const name = field.replace(/^address\./, '');
  if (name === 'latitude' || name === 'longitude') return form.controls.point;
  return (ADDRESS_TEXT_FIELDS as readonly string[]).includes(name) ? form.get(name) : null;
}

/** Erros de cada campo do enderezo e do punto, para mostrar ao pé de cada un. */
export type AddressErrors = Record<AddressTextField | 'point', ControlError | null>;

export function addressErrors(form: LocationForm, submitted: boolean): AddressErrors {
  const out = {} as AddressErrors;
  for (const name of [...ADDRESS_TEXT_FIELDS, 'point'] as const) {
    out[name] = fieldError(form.controls[name], submitted);
  }
  return out;
}
