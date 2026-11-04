// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { FormControl } from '@angular/forms';
import { SELECT_OPTIONS, definition } from '../../../testing/fixtures';
import {
  activeDefinitions,
  applyServerErrors,
  buildCustomFieldsForm,
  customFieldValidator,
  customFieldValuesOf,
  firstError,
} from './custom-field-controls';
import type { CustomFieldDefinition, CustomFieldValue } from './custom-field.types';

const errorsOf = (def: CustomFieldDefinition, value: CustomFieldValue) =>
  customFieldValidator(def)(new FormControl<CustomFieldValue>(value));

describe('validación dos campos personalizados (mesmos códigos que a API)', () => {
  it('obrigatorio: baleiro, espazos e listas baleiras', () => {
    const required = definition({ required: true });
    expect(errorsOf(required, '')).toEqual({ 'customfield.required': {} });
    expect(errorsOf(required, null)).toEqual({ 'customfield.required': {} });
    expect(errorsOf(definition({ type: 'MULTI_SELECT', required: true }), [])).toEqual({ 'customfield.required': {} });
    expect(errorsOf(definition(), '')).toBeNull();
  });

  it('texto: lonxitude (por defecto 500 e 5.000) e formatos', () => {
    expect(errorsOf(definition({ rules: { minLength: 3 } }), 'ab')).toEqual({ 'customfield.too-short': { min: 3 } });
    expect(errorsOf(definition(), 'x'.repeat(501))).toEqual({ 'customfield.too-long': { max: 500 } });
    expect(errorsOf(definition({ type: 'LONG_TEXT' }), 'x'.repeat(5000))).toBeNull();
    expect(errorsOf(definition({ rules: { format: 'EMAIL' } }), 'non-correo')).toEqual({
      'customfield.invalid-format': { format: 'EMAIL' },
    });
    expect(errorsOf(definition({ rules: { format: 'EMAIL' } }), 'a@b.gal')).toBeNull();
    expect(errorsOf(definition({ rules: { format: 'PHONE' } }), '+34 981 000 000')).toBeNull();
    expect(errorsOf(definition({ rules: { format: 'URL' } }), 'ftp://x')).not.toBeNull();
    expect(errorsOf(definition(), 5)).toEqual({ 'customfield.invalid-type': {} });
  });

  it('número: mínimo, máximo e decimais', () => {
    const number = definition({ type: 'NUMBER', rules: { min: 1, max: 10, decimals: 1 } });
    expect(errorsOf(number, 0)).toEqual({ 'customfield.below-min': { min: 1 } });
    expect(errorsOf(number, 11)).toEqual({ 'customfield.above-max': { max: 10 } });
    expect(errorsOf(number, 2.25)).toEqual({ 'customfield.too-many-decimals': { decimals: 1 } });
    expect(errorsOf(number, 2.5)).toBeNull();
    expect(errorsOf(number, 'dous')).toEqual({ 'customfield.invalid-type': {} });
    expect(errorsOf(definition({ type: 'NUMBER', rules: { decimals: 6 } }), 1e-7)).not.toBeNull();
  });

  it('data: formato e rango', () => {
    const date = definition({ type: 'DATE', rules: { minDate: '2026-01-01', maxDate: '2026-12-31' } });
    expect(errorsOf(date, '2025-12-31')).toEqual({ 'customfield.date-too-early': { min: '2026-01-01' } });
    expect(errorsOf(date, '2027-01-01')).toEqual({ 'customfield.date-too-late': { max: '2026-12-31' } });
    expect(errorsOf(date, '31/12/2026')).toEqual({ 'customfield.invalid-date': {} });
    expect(errorsOf(date, '2026-06-15')).toBeNull();
  });

  it('sí/non, selección e selección múltiple', () => {
    expect(errorsOf(definition({ type: 'BOOLEAN' }), false)).toBeNull();
    expect(errorsOf(definition({ type: 'BOOLEAN' }), 'si')).toEqual({ 'customfield.invalid-type': {} });
    expect(errorsOf(definition({ type: 'SELECT' }), 'pool')).toBeNull();
    expect(errorsOf(definition({ type: 'SELECT' }), 3)).toEqual({ 'customfield.invalid-type': {} });
    const multi = definition({ type: 'MULTI_SELECT', rules: { minItems: 2, maxItems: 2 } });
    expect(errorsOf(multi, ['pool'])).toEqual({ 'customfield.too-few-options': { min: 2 } });
    expect(errorsOf(multi, ['pool', 'gym', 'old'])).toEqual({ 'customfield.too-many-options': { max: 2 } });
    expect(errorsOf(multi, ['pool', 'gym'])).toBeNull();
  });
});

describe('formulario dinámico', () => {
  const defs = [
    definition({ code: 'a_first', type: 'MULTI_SELECT', options: SELECT_OPTIONS }),
    definition({ code: 'b_second' }),
    definition({ code: 'gone', status: 'INACTIVE' }),
    definition({ code: 'count', type: 'NUMBER' }),
  ];

  it('só campos activos, na orde da API, cos valores actuais', () => {
    expect(activeDefinitions(defs).map((d) => d.code)).toEqual(['a_first', 'b_second', 'count']);
    const form = buildCustomFieldsForm(defs, { a_first: ['pool', 7], b_second: 'ola', count: 'x', gone: 'vello' });
    expect(Object.keys(form.controls)).toEqual(['a_first', 'b_second', 'count']);
    expect(form.controls['a_first']?.value).toEqual(['pool']);
    expect(form.controls['count']?.value).toBe('x');
    expect(buildCustomFieldsForm(defs).controls['b_second']?.value).toBeNull();
  });

  it('envía só valores non baleiros dos campos do formulario, sen espazos sobrantes', () => {
    const form = buildCustomFieldsForm(defs, { b_second: '  ola  ', a_first: [] });
    form.controls['count']?.setValue(0);
    expect(customFieldValuesOf(defs, form)).toEqual({ b_second: 'ola', count: 0 });
  });

  it('pon os erros da API no seu campo e devolve os outros', () => {
    const form = buildCustomFieldsForm(defs);
    const rest = applyServerErrors(form, [
      { field: 'customFields.b_second', code: 'customfield.too-long', params: { max: 3 } },
      { field: 'customFields.non_existe', code: 'customfield.unknown-field', params: {} },
      { field: 'name', code: 'validation.required', params: {} },
    ]);
    expect(rest.map((e) => e.field)).toEqual(['customFields.non_existe', 'name']);
    expect(form.controls['b_second']?.touched).toBe(true);
    expect(firstError(form.controls['b_second'])).toEqual({ key: 'errors.customfield.too-long', params: { max: 3 } });
    expect(firstError(form.controls['count'])).toBeNull();
    expect(firstError(null)).toBeNull();
  });
});

describe('regras a null (como as manda a API)', () => {
  it('null equivale a sen regra', () => {
    const nulls = {
      minLength: null,
      maxLength: null,
      min: null,
      max: null,
      decimals: null,
      minItems: null,
      maxItems: null,
      format: null,
    };
    expect(errorsOf({ ...definition({ type: 'NUMBER' }), rules: nulls }, 3.14159)).toBeNull();
    expect(errorsOf({ ...definition(), rules: nulls }, 'ola')).toBeNull();
    expect(errorsOf({ ...definition({ type: 'MULTI_SELECT' }), rules: nulls }, ['a'])).toBeNull();
  });
});
