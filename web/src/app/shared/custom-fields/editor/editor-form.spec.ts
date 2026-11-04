// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { SELECT_OPTIONS, definition } from '../../../../testing/fixtures';
import {
  PERSONAL_DATA_PUBLIC,
  controlForField,
  definitionForm,
  linkPersonalData,
  optionForm,
  patchDefinition,
  toDraft,
  toPreview,
} from './editor-form';

describe('formulario de definición', () => {
  /** Usado nas instalacións. */
  function filled() {
    const form = definitionForm();
    form.patchValue({
      code: 'has_defib',
      labelGl: ' Desfibrilador ',
      labelEs: 'Desfibrilador',
      type: 'TEXT',
      target: 'FACILITY',
    });
    return form;
  }

  it('código co patrón do contrato e etiquetas obrigatorias nos dous idiomas', () => {
    const form = definitionForm();
    expect(form.valid).toBe(false);
    form.patchValue({ code: '1mal', labelGl: 'x', labelEs: 'x' });
    expect(form.controls.code.hasError('pattern')).toBe(true);
    form.patchValue({ code: 'ben_feito' });
    expect(form.valid).toBe(true);
  });

  it('un campo, un sitio: o concello por defecto, opcional e só persoal', () => {
    const form = definitionForm();
    expect([form.controls.target.value, form.controls.required.value, form.controls.visibility.value]).toEqual([
      'MUNICIPALITY',
      false,
      'STAFF',
    ]);
  });

  it('selección precisa unha opción activa e sen repetir', () => {
    const form = filled();
    form.patchValue({ type: 'SELECT' });
    expect(form.hasError('customfield.too-few-options')).toBe(true);
    form.controls.options.push(optionForm({ code: 'a', label: { gl: 'A', es: 'A' } }));
    form.controls.options.push(optionForm({ code: 'a', label: { gl: 'B', es: 'B' } }));
    form.updateValueAndValidity();
    expect(form.hasError('customfield.duplicate-option')).toBe(true);
    expect(form.controls.options.at(0).controls.saved.value).toBe(true);
    form.controls.options.at(0).controls.inactive.setValue(true);
    form.controls.options.at(1).controls.inactive.setValue(true);
    form.updateValueAndValidity();
    expect(form.hasError('customfield.too-few-options')).toBe(true);
  });

  it('toDraft envía só as regras do tipo, recorta os textos e leva un só uso', () => {
    const form = filled();
    form.controls.rules.patchValue({ maxLength: 20, min: 3, format: 'EMAIL' });
    expect(toDraft(form)).toEqual({
      code: 'has_defib',
      type: 'TEXT',
      label: { gl: 'Desfibrilador', es: 'Desfibrilador' },
      rules: { maxLength: 20, format: 'EMAIL' },
      options: [],
      personalData: false,
      usages: [{ target: 'FACILITY', required: false, visibility: 'STAFF' }],
    });
    form.patchValue({ type: 'NUMBER', helpGl: 'Axuda', required: true, visibility: 'PUBLIC' });
    const number = toDraft(form);
    expect(number.rules).toEqual({ min: 3 });
    expect(number.help).toEqual({ gl: 'Axuda', es: '' });
    expect(number.usages).toEqual([{ target: 'FACILITY', required: true, visibility: 'PUBLIC' }]);
    expect(toPreview({ ...number, code: '' })).toMatchObject({
      code: 'preview',
      status: 'ACTIVE',
      required: true,
      visibility: 'PUBLIC',
    });
  });

  it('dato persoal e PUBLIC son un erro do formulario', () => {
    const form = filled();
    form.patchValue({ visibility: 'PUBLIC', personalData: true });
    expect(form.hasError(PERSONAL_DATA_PUBLIC)).toBe(true);
    expect(form.valid).toBe(false);
    form.patchValue({ visibility: 'OWNER' });
    expect(form.valid).toBe(true);
  });

  it('dato persoal: PUBLIC pasa a STAFF; levalo á reserva: PUBLIC pasa a OWNER', () => {
    const form = filled();
    const link = linkPersonalData(form);
    form.patchValue({ visibility: 'PUBLIC' });
    form.controls.personalData.setValue(true);
    expect(form.controls.visibility.value).toBe('STAFF');
    form.controls.personalData.setValue(false);
    form.patchValue({ visibility: 'PUBLIC' });
    form.controls.target.setValue('BOOKING');
    expect(form.controls.visibility.value).toBe('OWNER');
    form.patchValue({ visibility: 'STAFF' });
    form.controls.target.setValue('MUNICIPALITY');
    expect(form.controls.visibility.value).toBe('STAFF');
    link.unsubscribe();
  });

  it('carga unha definición gardada e bloquea código e tipo; con valores, tamén o sitio', () => {
    const form = definitionForm();
    patchDefinition(
      form,
      definition({
        type: 'SELECT',
        options: SELECT_OPTIONS,
        rules: { minItems: 1 },
        usages: [{ target: 'BOOKING', required: true, visibility: 'OWNER' }],
      }),
    );
    expect(form.controls.code.disabled).toBe(true);
    expect(form.controls.type.disabled).toBe(true);
    expect(form.controls.target.disabled).toBe(false);
    expect(form.controls.options.length).toBe(3);
    const draft = toDraft(form);
    expect(draft.code).toBe('phone');
    expect(draft.options.map((o) => [o.code, o.inactive])).toEqual([
      ['pool', false],
      ['gym', false],
      ['old', true],
    ]);
    expect(draft.usages).toEqual([{ target: 'BOOKING', required: true, visibility: 'OWNER' }]);

    const used = definitionForm();
    patchDefinition(
      used,
      definition({
        hasValues: true,
        usages: [{ target: 'MUNICIPALITY', required: false, visibility: 'STAFF' }],
      }),
    );
    expect(used.controls.target.disabled).toBe(true);
    expect(toDraft(used).usages).toEqual([{ target: 'MUNICIPALITY', required: false, visibility: 'STAFF' }]);
  });

  it('asocia as rutas de erro da API cos controis', () => {
    const form = definitionForm();
    form.controls.options.push(optionForm());
    expect(controlForField(form, 'label.es')).toBe(form.controls.labelEs);
    expect(controlForField(form, 'rules.maxLength')).toBe(form.controls.rules.controls.maxLength);
    expect(controlForField(form, 'options[0].label.gl')).toBe(form.controls.options.at(0).controls.labelGl);
    expect(controlForField(form, 'options[0].code')).toBe(form.controls.options.at(0).controls.code);
    expect(controlForField(form, 'options[9].code')).toBeNull();
    expect(controlForField(form, 'rules.nada')).toBeNull();
    expect(controlForField(form, 'outro')).toBeNull();
    expect(controlForField(form, 'usages[0].visibility')).toBe(form.controls.visibility);
    expect(controlForField(form, 'usages[0].target')).toBe(form.controls.target);
    expect(controlForField(form, 'usages')).toBe(form.controls.target);
  });
});
