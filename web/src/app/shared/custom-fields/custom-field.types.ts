// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/**
 * Forma dos campos personalizados que usa a interface (ADR-0011). Reflicte o contrato
 * (CustomFieldDefinitionResponse/Request): as features pasan os tipos xerados e, se o
 * contrato cambia, a asignación deixa de compilar.
 */
export type CustomFieldType = 'TEXT' | 'LONG_TEXT' | 'NUMBER' | 'DATE' | 'BOOLEAN' | 'SELECT' | 'MULTI_SELECT';
export type CustomFieldVisibility = 'PUBLIC' | 'OWNER' | 'STAFF';
export type CustomFieldFormat = 'EMAIL' | 'PHONE' | 'URL';
export type CustomFieldStatus = 'ACTIVE' | 'INACTIVE';

export const CUSTOM_FIELD_TYPES: readonly CustomFieldType[] = [
  'TEXT',
  'LONG_TEXT',
  'NUMBER',
  'DATE',
  'BOOLEAN',
  'SELECT',
  'MULTI_SELECT',
];
export const CUSTOM_FIELD_VISIBILITIES: readonly CustomFieldVisibility[] = ['PUBLIC', 'OWNER', 'STAFF'];
export const CUSTOM_FIELD_FORMATS: readonly CustomFieldFormat[] = ['EMAIL', 'PHONE', 'URL'];

/**
 * Sitios onde se pode usar un campo, na orde do selector do editor (un campo, un sitio). A API non
 * publica a lista de destinos rexistrados; un destino gardado que non estea aquí amósase co seu
 * código e consérvase igual (editor-form).
 */
export const CUSTOM_FIELD_TARGETS: readonly string[] = ['MUNICIPALITY', 'FACILITY', 'BOOKING'];

/** Quen pode ver un campo en cada sitio: a reserva é dunha persoa e non ten campos públicos. */
export function visibilitiesFor(target: string): readonly CustomFieldVisibility[] {
  return target === 'BOOKING' ? ['OWNER', 'STAFF'] : CUSTOM_FIELD_VISIBILITIES;
}

export interface LocalizedText {
  readonly gl: string;
  readonly es: string;
}

export interface CustomFieldOption {
  readonly code: string;
  readonly label: LocalizedText;
  /** Desactivada: non se escolle nun valor novo, pero un gardado conserva a súa etiqueta. */
  readonly inactive?: boolean;
  /** Só para o xestor: algún valor a escolleu (non se pode eliminar, só desactivar). */
  readonly hasValues?: boolean | null;
}

/** As regras sen valor poden chegar ausentes ou a null (ver present()). */
export interface CustomFieldRules {
  readonly minLength?: number | null;
  readonly maxLength?: number | null;
  readonly min?: number | null;
  readonly max?: number | null;
  readonly decimals?: number | null;
  readonly minDate?: string | null;
  readonly maxDate?: string | null;
  readonly minItems?: number | null;
  readonly maxItems?: number | null;
  readonly format?: CustomFieldFormat | null;
}

/** Regras para enviar á API: só as que teñen valor. */
export type CustomFieldRulesInput = { readonly [K in keyof CustomFieldRules]?: NonNullable<CustomFieldRules[K]> };

/** Onde se usa un campo, co seu obrigatorio e visibilidade nese sitio (especificación 1b). */
export interface CustomFieldUsage {
  readonly target: string;
  readonly required: boolean;
  readonly visibility: CustomFieldVisibility;
}

/**
 * O que precisa o formulario para pintar un campo nun destino: o obrigatorio e a visibilidade son
 * os do uso dese destino (forTarget). Sen orde manual nin grupos: a API devolve a orde alfabética.
 */
export interface CustomFieldDefinition {
  readonly code: string;
  readonly type: CustomFieldType;
  readonly label: LocalizedText;
  readonly help?: LocalizedText | null;
  readonly required: boolean;
  readonly rules: CustomFieldRules;
  readonly options: readonly CustomFieldOption[];
  readonly visibility: CustomFieldVisibility;
  readonly status: CustomFieldStatus;
  readonly personalData: boolean;
}

/**
 * Definición tal como a edita o xestor: co seu uso (un campo, un sitio) e, se a API o di, se xa ten
 * valores (entón o sitio non cambia e non se elimina).
 */
export type CustomFieldDefinitionWithUsages = Omit<CustomFieldDefinition, 'required' | 'visibility'> & {
  readonly usages: readonly CustomFieldUsage[];
  readonly hasValues?: boolean | null;
};

/** Corpo que o editor entrega para crear ou editar unha definición. */
export interface CustomFieldDefinitionDraft {
  readonly code: string;
  readonly type: CustomFieldType;
  readonly label: LocalizedText;
  readonly help?: LocalizedText;
  readonly rules: CustomFieldRulesInput;
  readonly options: CustomFieldOption[];
  readonly personalData: boolean;
  /** Onde se usa, co obrigatorio e a visibilidade: un só sitio. */
  readonly usages: CustomFieldUsage[];
}

/**
 * A definición tal como se usa nun destino: engade o obrigatorio e a visibilidade do seu uso. Sen uso
 * nese destino, opcional e só persoal (o máis restritivo).
 */
export function forTarget<T extends { readonly usages: readonly CustomFieldUsage[] }>(
  definition: T,
  target: string,
): T & { readonly required: boolean; readonly visibility: CustomFieldVisibility } {
  const usage = definition.usages.find((u) => u.target === target);
  return { ...definition, required: usage?.required ?? false, visibility: usage?.visibility ?? 'STAFF' };
}

export type CustomFieldValue = string | number | boolean | string[] | null;
/** Valores por código, tal como viaxan no DTO (`customFields`). */
export type CustomFieldValues = Readonly<Record<string, unknown>>;
