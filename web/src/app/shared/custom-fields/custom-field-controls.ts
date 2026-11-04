// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors, ValidatorFn } from '@angular/forms';
import { FormControl, FormRecord } from '@angular/forms';
import type { CustomFieldDefinition, CustomFieldValue, CustomFieldValues } from './custom-field.types';

/**
 * Formularios dinámicos de campos personalizados (ADR-0011). A validación aquí só axuda:
 * quen decide é a API. Os erros usan os mesmos códigos que a API (`customfield.*`) para
 * ter un único texto por erro en gl e es.
 */
export type CustomFieldsForm = FormRecord<FormControl<CustomFieldValue>>;

/** Límites por defecto da API para texto (ADR-0011). */
export const DEFAULT_MAX_LENGTH = { TEXT: 500, LONG_TEXT: 5000 } as const;

const FORMATS: Record<string, RegExp> = {
  EMAIL: /^[^\s@]+@[^\s@]+\.[^\s@]+$/,
  PHONE: /^\+?[0-9 ()-]{6,20}$/,
  URL: /^https?:\/\/\S+$/i,
};

/** Campos activos, na orde da API (alfabética no idioma de quen mira). */
export function activeDefinitions(definitions: readonly CustomFieldDefinition[]): CustomFieldDefinition[] {
  return definitions.filter((definition) => definition.status === 'ACTIVE');
}

export function isEmpty(value: unknown): boolean {
  return value === null || value === undefined || value === '' || (Array.isArray(value) && value.length === 0);
}

/**
 * A API pode mandar as regras sen valor como null (non só ausentes): ambos son «sen regra».
 */
export function present<T>(value: T | null | undefined): value is T {
  return value !== null && value !== undefined;
}

function error(code: string, params: Record<string, unknown> = {}): ValidationErrors {
  return { [code]: params };
}

function decimalsOf(value: number): number {
  const text = String(value);
  if (text.includes('e-')) {
    return Number(text.split('e-')[1]);
  }
  return text.includes('.') ? (text.split('.')[1]?.length ?? 0) : 0;
}

function validateText(definition: CustomFieldDefinition, value: string): ValidationErrors | null {
  const { rules } = definition;
  const max = rules.maxLength ?? DEFAULT_MAX_LENGTH[definition.type as 'TEXT' | 'LONG_TEXT'];
  if (present(rules.minLength) && value.length < rules.minLength) {
    return error('customfield.too-short', { min: rules.minLength });
  }
  if (value.length > max) {
    return error('customfield.too-long', { max });
  }
  const format = rules.format ? FORMATS[rules.format] : undefined;
  if (format && !format.test(value)) {
    return error('customfield.invalid-format', { format: rules.format });
  }
  return null;
}

function validateNumber(definition: CustomFieldDefinition, value: unknown): ValidationErrors | null {
  const { rules } = definition;
  if (typeof value !== 'number' || Number.isNaN(value)) {
    return error('customfield.invalid-type');
  }
  if (present(rules.min) && value < rules.min) {
    return error('customfield.below-min', { min: rules.min });
  }
  if (present(rules.max) && value > rules.max) {
    return error('customfield.above-max', { max: rules.max });
  }
  if (present(rules.decimals) && decimalsOf(value) > rules.decimals) {
    return error('customfield.too-many-decimals', { decimals: rules.decimals });
  }
  return null;
}

function validateDate(definition: CustomFieldDefinition, value: unknown): ValidationErrors | null {
  const { rules } = definition;
  if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value) || Number.isNaN(Date.parse(value))) {
    return error('customfield.invalid-date');
  }
  if (rules.minDate && value < rules.minDate) {
    return error('customfield.date-too-early', { min: rules.minDate });
  }
  if (rules.maxDate && value > rules.maxDate) {
    return error('customfield.date-too-late', { max: rules.maxDate });
  }
  return null;
}

function validateChoices(definition: CustomFieldDefinition, value: unknown): ValidationErrors | null {
  const { rules } = definition;
  const selected = Array.isArray(value) ? value : [];
  if (present(rules.minItems) && selected.length < rules.minItems) {
    return error('customfield.too-few-options', { min: rules.minItems });
  }
  if (present(rules.maxItems) && selected.length > rules.maxItems) {
    return error('customfield.too-many-options', { max: rules.maxItems });
  }
  return null;
}

/** Validador dun campo segundo o seu tipo e regras. */
export function customFieldValidator(definition: CustomFieldDefinition): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value: unknown = control.value;
    if (isEmpty(value)) {
      return definition.required ? error('customfield.required') : null;
    }
    switch (definition.type) {
      case 'TEXT':
      case 'LONG_TEXT':
        return typeof value === 'string' ? validateText(definition, value.trim()) : error('customfield.invalid-type');
      case 'NUMBER':
        return validateNumber(definition, value);
      case 'DATE':
        return validateDate(definition, value);
      case 'MULTI_SELECT':
        return validateChoices(definition, value);
      case 'BOOLEAN':
        return typeof value === 'boolean' ? null : error('customfield.invalid-type');
      case 'SELECT':
        return typeof value === 'string' ? null : error('customfield.invalid-type');
    }
  };
}

function initialValue(definition: CustomFieldDefinition, value: unknown): CustomFieldValue {
  if (definition.type === 'MULTI_SELECT') {
    return Array.isArray(value) ? value.filter((item): item is string => typeof item === 'string') : [];
  }
  if (typeof value === 'string' || typeof value === 'number' || typeof value === 'boolean') {
    return value;
  }
  return null;
}

/** Un control por campo activo, cos valores actuais. */
export function buildCustomFieldsForm(
  definitions: readonly CustomFieldDefinition[],
  values: CustomFieldValues = {},
): CustomFieldsForm {
  const form: CustomFieldsForm = new FormRecord<FormControl<CustomFieldValue>>({});
  for (const definition of activeDefinitions(definitions)) {
    form.addControl(
      definition.code,
      new FormControl<CustomFieldValue>(initialValue(definition, values[definition.code]), {
        validators: customFieldValidator(definition),
      }),
    );
  }
  return form;
}

/**
 * Valores para a API. Os baleiros non se envían; os campos que non están no formulario
 * (desactivados ou non visibles) tampouco: a API conserva o seu valor (ADR-0011).
 */
export function customFieldValuesOf(
  definitions: readonly CustomFieldDefinition[],
  form: CustomFieldsForm,
): Record<string, unknown> {
  const result: Record<string, unknown> = {};
  for (const definition of activeDefinitions(definitions)) {
    const value = form.controls[definition.code]?.value;
    if (!isEmpty(value)) {
      result[definition.code] = typeof value === 'string' ? value.trim() : value;
    }
  }
  return result;
}

export interface FieldErrorLike {
  readonly field: string;
  readonly code: string;
  readonly params: Readonly<Record<string, unknown>>;
}

export const CUSTOM_FIELDS_PREFIX = 'customFields.';

/** Pon no seu campo os erros da API (`customFields.<code>`); devolve os que non teñen campo. */
export function applyServerErrors(form: CustomFieldsForm, errors: readonly FieldErrorLike[]): FieldErrorLike[] {
  const rest: FieldErrorLike[] = [];
  for (const fieldError of errors) {
    const code = fieldError.field.startsWith(CUSTOM_FIELDS_PREFIX)
      ? fieldError.field.slice(CUSTOM_FIELDS_PREFIX.length)
      : undefined;
    const control = code ? form.controls[code] : undefined;
    if (control) {
      control.setErrors({ ...control.errors, [fieldError.code]: fieldError.params });
      control.markAsTouched();
    } else {
      rest.push(fieldError);
    }
  }
  return rest;
}

export interface ControlError {
  readonly key: string;
  readonly params: Readonly<Record<string, unknown>>;
}

/** Primeiro erro dun control como clave de tradución (`errors.<code>`). */
export function firstError(control: AbstractControl | null | undefined): ControlError | null {
  const errors = control?.errors;
  if (!errors) {
    return null;
  }
  const [code, params] = Object.entries(errors)[0] ?? [];
  if (!code) {
    return null;
  }
  return { key: `errors.${code}`, params: typeof params === 'object' && params !== null ? params : {} };
}
