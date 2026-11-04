// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { AbstractControl, ValidationErrors } from '@angular/forms';
import { FormArray, FormControl, FormGroup, Validators } from '@angular/forms';
import type { Subscription } from 'rxjs';
import type {
  CustomFieldDefinition,
  CustomFieldDefinitionDraft,
  CustomFieldDefinitionWithUsages,
  CustomFieldFormat,
  CustomFieldRules,
  CustomFieldRulesInput,
  CustomFieldType,
  CustomFieldVisibility,
} from '../custom-field.types';
import { CUSTOM_FIELD_TARGETS, visibilitiesFor } from '../custom-field.types';

/** Mesmos patróns que o contrato (CustomFieldDefinitionRequest). */
export const CODE_PATTERN = /^[a-z][a-z0-9_]{1,39}$/;
export const OPTION_CODE_PATTERN = /^[a-z0-9][a-z0-9_]{0,39}$/;
const TEXT_MAX = 500;

export type OptionForm = FormGroup<{
  code: FormControl<string>;
  labelGl: FormControl<string>;
  labelEs: FormControl<string>;
  inactive: FormControl<boolean>;
  /** Xa gardada: o código é inmutable e quitala pide confirmación (ADR-0011). */
  saved: FormControl<boolean>;
  /** Gardada e escollida nalgún valor: non se pode eliminar, só desactivar. */
  hasValues: FormControl<boolean>;
}>;

export type RulesForm = FormGroup<{
  minLength: FormControl<number | null>;
  maxLength: FormControl<number | null>;
  min: FormControl<number | null>;
  max: FormControl<number | null>;
  decimals: FormControl<number | null>;
  minDate: FormControl<string | null>;
  maxDate: FormControl<string | null>;
  minItems: FormControl<number | null>;
  maxItems: FormControl<number | null>;
  format: FormControl<CustomFieldFormat | null>;
}>;

export type DefinitionForm = FormGroup<{
  code: FormControl<string>;
  type: FormControl<CustomFieldType>;
  labelGl: FormControl<string>;
  labelEs: FormControl<string>;
  helpGl: FormControl<string>;
  helpEs: FormControl<string>;
  personalData: FormControl<boolean>;
  rules: RulesForm;
  options: FormArray<OptionForm>;
  /** Un campo, un sitio: onde se usa, se é obrigatorio aí e quen o ve aí. */
  target: FormControl<string>;
  required: FormControl<boolean>;
  visibility: FormControl<CustomFieldVisibility>;
}>;

const text = (value = '', ...validators: ((c: AbstractControl) => ValidationErrors | null)[]) =>
  new FormControl(value, { nonNullable: true, validators });
const num = () => new FormControl<number | null>(null);

export function isChoice(type: CustomFieldType): boolean {
  return type === 'SELECT' || type === 'MULTI_SELECT';
}

export function optionForm(option?: {
  code: string;
  label: { gl: string; es: string };
  inactive?: boolean;
  hasValues?: boolean | null;
}): OptionForm {
  const saved = option !== undefined;
  return new FormGroup({
    code: text(option?.code ?? '', Validators.required, Validators.pattern(OPTION_CODE_PATTERN)),
    labelGl: text(option?.label.gl ?? '', Validators.required, Validators.maxLength(TEXT_MAX)),
    labelEs: text(option?.label.es ?? '', Validators.required, Validators.maxLength(TEXT_MAX)),
    inactive: new FormControl(option?.inactive ?? false, { nonNullable: true }),
    saved: new FormControl(saved, { nonNullable: true }),
    hasValues: new FormControl(option?.hasValues === true, { nonNullable: true }),
  });
}

/** Código do erro (o mesmo da API, 422) cando un dato persoal queda público. */
export const PERSONAL_DATA_PUBLIC = 'customfield.personal-data-public';
/**
 * Selección: polo menos unha opción activa. Dato persoal: nunca PUBLIC (ADR-0011; o dominio e a
 * base tamén o rexeitan).
 */
function definitionRules(control: AbstractControl): ValidationErrors | null {
  const form = control as DefinitionForm;
  const { type, personalData, visibility } = form.getRawValue();
  const errors: ValidationErrors = {};
  if (personalData && visibility === 'PUBLIC') {
    errors[PERSONAL_DATA_PUBLIC] = {};
  }
  if (isChoice(type) && form.controls.options.controls.filter((o) => !o.controls.inactive.value).length === 0) {
    errors['customfield.too-few-options'] = { min: 1 };
  }
  const codes = form.controls.options.controls.map((o) => o.controls.code.value);
  if (new Set(codes).size !== codes.length) {
    errors['customfield.duplicate-option'] = {};
  }
  return Object.keys(errors).length ? errors : null;
}

export function definitionForm(): DefinitionForm {
  return new FormGroup(
    {
      code: text('', Validators.required, Validators.pattern(CODE_PATTERN)),
      type: new FormControl<CustomFieldType>('TEXT', { nonNullable: true }),
      labelGl: text('', Validators.required, Validators.maxLength(TEXT_MAX)),
      labelEs: text('', Validators.required, Validators.maxLength(TEXT_MAX)),
      helpGl: text('', Validators.maxLength(TEXT_MAX)),
      helpEs: text('', Validators.maxLength(TEXT_MAX)),
      personalData: new FormControl(false, { nonNullable: true }),
      rules: new FormGroup({
        minLength: num(),
        maxLength: num(),
        min: num(),
        max: num(),
        decimals: num(),
        minDate: new FormControl<string | null>(null),
        maxDate: new FormControl<string | null>(null),
        minItems: num(),
        maxItems: num(),
        format: new FormControl<CustomFieldFormat | null>(null),
      }),
      options: new FormArray<OptionForm>([]),
      target: new FormControl(CUSTOM_FIELD_TARGETS[0] ?? 'MUNICIPALITY', { nonNullable: true }),
      required: new FormControl(false, { nonNullable: true }),
      // Privacidade por defecto (RGPD art. 25.2): o xestor fai público un campo a propósito.
      visibility: new FormControl<CustomFieldVisibility>('STAFF', { nonNullable: true }),
    },
    { validators: definitionRules },
  );
}

/**
 * Ao marcar «dato persoal», un campo público pasa a «Só persoal» (o modelo desactiva PUBLIC); ao
 * levalo á reserva, que non ten campos públicos, pasa a «A persoa que reserva e o persoal».
 * Devolve a subscrición para que o compoñente a peche.
 */
export function linkPersonalData(form: DefinitionForm): Subscription {
  const personal = form.controls.personalData.valueChanges.subscribe((marked) => {
    if (marked && form.controls.visibility.value === 'PUBLIC') {
      form.controls.visibility.setValue('STAFF');
    }
  });
  personal.add(
    form.controls.target.valueChanges.subscribe((target) => {
      if (!visibilitiesFor(target).includes(form.controls.visibility.value)) {
        form.controls.visibility.setValue('OWNER');
      }
    }),
  );
  return personal;
}

/**
 * Carga unha definición gardada. Código e tipo quedan bloqueados (inmutables); o sitio, tamén se o
 * campo xa ten valores.
 */
export function patchDefinition(form: DefinitionForm, definition: CustomFieldDefinitionWithUsages): void {
  form.controls.options.clear();
  for (const option of definition.options) {
    form.controls.options.push(optionForm(option));
  }
  const usage = definition.usages[0];
  form.patchValue({
    target: usage?.target ?? CUSTOM_FIELD_TARGETS[0] ?? 'MUNICIPALITY',
    required: usage?.required ?? false,
    visibility: usage?.visibility ?? 'STAFF',
    code: definition.code,
    type: definition.type,
    labelGl: definition.label.gl,
    labelEs: definition.label.es,
    helpGl: definition.help?.gl ?? '',
    helpEs: definition.help?.es ?? '',
    personalData: definition.personalData,
  });
  form.controls.rules.reset();
  form.controls.rules.patchValue({ ...definition.rules });
  form.controls.code.disable();
  form.controls.type.disable();
  if (definition.hasValues === true) {
    form.controls.target.disable();
  }
}

/** Regras que ten sentido enviar para cada tipo. */
const RULES_BY_TYPE: Record<CustomFieldType, readonly (keyof CustomFieldRules)[]> = {
  TEXT: ['minLength', 'maxLength', 'format'],
  LONG_TEXT: ['minLength', 'maxLength'],
  NUMBER: ['min', 'max', 'decimals'],
  DATE: ['minDate', 'maxDate'],
  BOOLEAN: [],
  SELECT: [],
  MULTI_SELECT: ['minItems', 'maxItems'],
};

export function rulesFor(type: CustomFieldType): readonly (keyof CustomFieldRules)[] {
  return RULES_BY_TYPE[type];
}

function trimmed(value: string): string {
  return value.trim();
}

/** Corpo para a API a partir do formulario (inclúe código e tipo aínda que estean bloqueados). */
export function toDraft(form: DefinitionForm): CustomFieldDefinitionDraft {
  const value = form.getRawValue();
  const rules: Record<string, unknown> = {};
  for (const key of rulesFor(value.type)) {
    const rule = value.rules[key];
    if (rule !== null && rule !== '') {
      rules[key] = rule;
    }
  }
  const help =
    value.helpGl.trim() || value.helpEs.trim() ? { gl: trimmed(value.helpGl), es: trimmed(value.helpEs) } : undefined;
  return {
    code: value.code,
    type: value.type,
    label: { gl: trimmed(value.labelGl), es: trimmed(value.labelEs) },
    ...(help ? { help } : {}),
    rules: rules as CustomFieldRulesInput,
    options: isChoice(value.type)
      ? value.options.map((option) => ({
          code: option.code,
          label: { gl: trimmed(option.labelGl), es: trimmed(option.labelEs) },
          inactive: option.inactive,
        }))
      : [],
    personalData: value.personalData,
    usages: [{ target: value.target, required: value.required, visibility: value.visibility }],
  };
}

/** Definición para a vista previa (mesmo compoñente que verá o veciño), co seu sitio. */
export function toPreview(draft: CustomFieldDefinitionDraft): CustomFieldDefinition {
  const { usages, ...rest } = draft;
  const first = usages[0];
  return {
    ...rest,
    code: draft.code || 'preview',
    status: 'ACTIVE',
    required: first?.required ?? false,
    visibility: first?.visibility ?? 'STAFF',
  };
}

/** Ruta dun erro da API (`label.gl`, `options[0].code`...) → control do formulario. */
export function controlForField(form: DefinitionForm, field: string): AbstractControl | null {
  const direct: Record<string, AbstractControl> = {
    code: form.controls.code,
    type: form.controls.type,
    'label.gl': form.controls.labelGl,
    'label.es': form.controls.labelEs,
    'help.gl': form.controls.helpGl,
    'help.es': form.controls.helpEs,
  };
  if (direct[field]) {
    return direct[field];
  }
  // Un só sitio: usages[0] (ou `usages`, a lista enteira) é o selector do sitio.
  const usage = /^usages(?:\[\d+\]\.(target|required|visibility))?$/.exec(field);
  if (usage) {
    return form.controls[(usage[1] ?? 'target') as 'target' | 'required' | 'visibility'];
  }
  const rule = /^rules\.(\w+)$/.exec(field)?.[1];
  if (rule && rule in form.controls.rules.controls) {
    return form.controls.rules.get(rule);
  }
  const option = /^options\[(\d+)\]\.(code|label\.gl|label\.es)$/.exec(field);
  if (option) {
    const group = form.controls.options.at(Number(option[1]));
    const name = option[2] === 'code' ? 'code' : option[2] === 'label.gl' ? 'labelGl' : 'labelEs';
    return group?.get(name) ?? null;
  }
  return null;
}
