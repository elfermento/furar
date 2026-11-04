// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { CustomFieldDefinitionResponse } from '../app/api/models/custom-field-definition-response';
import type { LegalDetailsResponse } from '../app/api/models/legal-details-response';
import type { MunicipalityResponse } from '../app/api/models/municipality-response';
import type { CustomFieldVisibility } from '../app/shared/custom-fields/custom-field.types';

/** Datos de exemplo (CC0 como os de demo): Narón co tema por defecto e as páxinas legais de Furar. */
export function municipality(
  overrides: Partial<Omit<MunicipalityResponse, 'legal'>> & { legal?: Partial<LegalDetailsResponse> } = {},
): MunicipalityResponse {
  const { legal, ...rest } = overrides;
  return {
    id: '019a0b6e-0000-7000-8000-000000000001',
    slug: 'naron',
    name: 'Narón',
    languages: ['gl', 'es'],
    defaultLanguage: 'gl',
    theme: { primary: '#0b5c8e', onPrimary: '#ffffff', accent: '#8a4b00', background: '#ffffff', text: '#1a1a1a' },
    customFields: {},
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    version: 3,
    ...rest,
    legal: { privacyMode: 'FURAR', accessibilityMode: 'FURAR', legalNoticeMode: 'FURAR', ...legal },
  };
}

/** Definición tal como a ve un formulario do concello: a resposta co obrigatorio e a visibilidade do seu uso. */
export type DefinitionFixture = CustomFieldDefinitionResponse & {
  readonly required: boolean;
  readonly visibility: CustomFieldVisibility;
};

export function definition(overrides: Partial<DefinitionFixture> = {}): DefinitionFixture {
  const required = overrides.required ?? false;
  const visibility = overrides.visibility ?? 'PUBLIC';
  return {
    id: '019a0b6e-0000-7000-8000-0000000000aa',
    code: 'phone',
    type: 'TEXT',
    label: { gl: 'Teléfono', es: 'Teléfono' },
    help: { gl: 'Para avisos', es: 'Para avisos' },
    rules: {},
    options: [],
    status: 'ACTIVE',
    personalData: false,
    usages: [{ target: 'MUNICIPALITY', required, visibility }],
    createdAt: '2026-10-01T10:00:00Z',
    updatedAt: '2026-10-01T10:00:00Z',
    version: 1,
    ...overrides,
    required,
    visibility,
  };
}

export const SELECT_OPTIONS = [
  { code: 'pool', label: { gl: 'Piscina', es: 'Piscina' }, inactive: false },
  { code: 'gym', label: { gl: 'Ximnasio', es: 'Gimnasio' }, inactive: false },
  { code: 'old', label: { gl: 'Vella', es: 'Vieja' }, inactive: true },
];
