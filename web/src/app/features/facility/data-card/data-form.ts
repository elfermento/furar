// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormGroup, FormRecord, Validators } from '@angular/forms';
import type { ControlError, CustomFieldsForm } from '../../../shared/custom-fields/custom-field-controls';
import type { CustomFieldValue } from '../../../shared/custom-fields/custom-field.types';
import { fieldError } from '../../../shared/ui/field-errors';
import type { Facility } from '../detail/facility-detail.store';
import type { DetailsRequest, SportResponse } from './data-card.store';

export type { DetailsRequest, SportResponse };
export type FacilityType = NonNullable<DetailsRequest['type']>;

/** Tipos da instalación, na orde da especificación (bloque 1). */
export const FACILITY_TYPES: readonly FacilityType[] = [
  'FIELD',
  'SPORTS_HALL',
  'PADEL_COURT',
  'POOL',
  'OUTDOOR_COURT',
  'OTHER',
];

/** O mesmo límite ca `Facility.NAME_MAX` da API. */
export const NAME_MAX = 100;
/** Os mesmos límites ca `FacilityDetails` da API. */
export const SPORTS_MAX = 30;
export const RIDEGA_MAX = 30;
export const EMAIL_MAX = 254;
const PHONE = /^\+?[0-9 ]{6,20}$/;
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/;

/** Códigos dos erros: os mesmos ca os da API (`validation.*`), co mesmo texto. */
const SIZE = 'validation.size';
const PATTERN = 'validation.pattern';

export type DataForm = FormGroup<{
  /** Só na ficha: o nome edítase en «Datos» (gárdase co `PUT …/name`). */
  name?: FormControl<string>;
  sports: FormControl<SportResponse[]>;
  type: FormControl<FacilityType | ''>;
  ridegaCode: FormControl<string>;
  phone: FormControl<string>;
  email: FormControl<string>;
  onlineBooking: FormControl<boolean>;
  showFreeSlots: FormControl<boolean>;
  covered: FormControl<boolean | null>;
  /** Con control de entrada: valídase o QR ao chegar (ADR-0032). */
  entryControl: FormControl<boolean>;
  customFields: CustomFieldsForm;
}>;

/** Valor do formulario: `type` `''` = sen indicar; `covered` `null` = sen dato. */
export type DataValue = ReturnType<DataForm['getRawValue']>;

const trimmed = (control: AbstractControl): string => (typeof control.value === 'string' ? control.value.trim() : '');

export type ContactField = 'phone' | 'email' | 'ridegaCode';

/**
 * Erro do teléfono, do correo ou do código RIDEGA que di o que se admite (sen liña de axuda, o
 * erro é o único sitio onde se explica): o teléfono, cifras e espazos cun «+» opcional; o correo,
 * completo e sen pasar de `EMAIL_MAX`; o RIDEGA, ata `RIDEGA_MAX`. Outros erros (da API), tal cal.
 */
export function contactError(name: ContactField, control: AbstractControl, submitted: boolean): ControlError | null {
  const error = fieldError(control, submitted);
  if (error?.key === `errors.${PATTERN}`) {
    if (name === 'phone') return { key: 'facility.data.phone.invalid', params: {} };
    if (name === 'email') {
      return trimmed(control).length > EMAIL_MAX
        ? { key: 'facility.data.tooLong', params: { max: EMAIL_MAX } }
        : { key: 'facility.data.email.invalid', params: {} };
    }
  }
  if (error?.key === `errors.${SIZE}` && name === 'ridegaCode') {
    return { key: 'facility.data.tooLong', params: { max: RIDEGA_MAX } };
  }
  return error;
}

/** Obrigatorio coma no servidor, que quita os espazos dos extremos antes de comprobar. */
function notBlank(control: AbstractControl): ValidationErrors | null {
  return typeof control.value === 'string' && control.value.trim() === '' ? { required: true } : null;
}

/** Nome da instalación: obrigatorio e ata `NAME_MAX` caracteres (asistente e ficha). */
export function nameControl(value = ''): FormControl<string> {
  return new FormControl(value, { nonNullable: true, validators: [notBlank, Validators.maxLength(NAME_MAX)] });
}

/** Coma o servidor: texto baleiro = sen dato; se hai, ata `max` caracteres. */
function maxText(max: number): ValidatorFn {
  return (control): ValidationErrors | null => (trimmed(control).length > max ? { [SIZE]: { min: 1, max } } : null);
}

/** Coma o servidor: texto baleiro = sen dato; se hai, co formato e ata `max` caracteres. */
function pattern(regex: RegExp, max = Number.MAX_SAFE_INTEGER): ValidatorFn {
  return (control): ValidationErrors | null => {
    const value = trimmed(control);
    return value !== '' && (value.length > max || !regex.test(value)) ? { [PATTERN]: {} } : null;
  };
}

const maxSports: ValidatorFn = (control): ValidationErrors | null =>
  Array.isArray(control.value) && control.value.length > SPORTS_MAX ? { [SIZE]: { min: 0, max: SPORTS_MAX } } : null;

export function dataForm(options: { withName?: boolean } = {}): DataForm {
  const form: DataForm = new FormGroup({
    ...(options.withName ? { name: nameControl() } : {}),
    sports: new FormControl<SportResponse[]>([], { nonNullable: true, validators: maxSports }),
    type: new FormControl<FacilityType | ''>('', { nonNullable: true }),
    ridegaCode: new FormControl('', { nonNullable: true, validators: maxText(RIDEGA_MAX) }),
    phone: new FormControl('', { nonNullable: true, validators: pattern(PHONE) }),
    email: new FormControl('', { nonNullable: true, validators: pattern(EMAIL, EMAIL_MAX) }),
    onlineBooking: new FormControl(true, { nonNullable: true }),
    showFreeSlots: new FormControl(false, { nonNullable: true }),
    covered: new FormControl<boolean | null>(null),
    entryControl: new FormControl(false, { nonNullable: true }),
    customFields: new FormRecord<FormControl<CustomFieldValue>>({}),
  });
  // Nunca as dúas cousas á vez (`facility.booking-mode-invalid`): con reserva por internet, os
  // ocos libres apáganse e o interruptor desaparece.
  form.controls.onlineBooking.valueChanges.subscribe((online) => {
    if (online && form.controls.showFreeSlots.value) form.controls.showFreeSlots.setValue(false);
  });
  return form;
}

/** Valor do formulario para a instalación; os campos propios, só os que o formulario ten. */
export function dataValue(facility: Facility | null, customFieldCodes: readonly string[]): DataValue | null {
  if (!facility) return null;
  const { details } = facility;
  const customFields: Record<string, CustomFieldValue> = {};
  for (const code of customFieldCodes) {
    customFields[code] = normalize(details.customFields[code]);
  }
  return {
    sports: details.sports,
    type: details.type ?? '',
    ridegaCode: details.ridegaCode ?? '',
    phone: details.phone ?? '',
    email: details.email ?? '',
    onlineBooking: details.onlineBooking,
    showFreeSlots: details.showFreeSlots,
    covered: details.covered ?? null,
    entryControl: details.entryControl,
    customFields,
  };
}

function normalize(value: unknown): CustomFieldValue {
  if (Array.isArray(value)) return value.filter((item): item is string => typeof item === 'string');
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') return value;
  return null;
}

/** Corpo do `PUT …/details`: textos sen espazos nos extremos e baleiros fóra. */
export function detailsBody(value: DataValue, customFields: Record<string, unknown>): DetailsRequest {
  const text = (v: string): string | undefined => v.trim() || undefined;
  return {
    sports: value.sports.map((sport) => (sport.code ? { code: sport.code } : { customSportId: sport.customSportId })),
    type: value.type || undefined,
    ridegaCode: text(value.ridegaCode),
    phone: text(value.phone),
    email: text(value.email),
    onlineBooking: value.onlineBooking,
    showFreeSlots: !value.onlineBooking && value.showFreeSlots,
    covered: value.covered ?? undefined,
    entryControl: value.entryControl,
    customFields,
  };
}
