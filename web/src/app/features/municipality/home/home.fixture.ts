// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { PublicFacilityResponse } from './home.store';

/** Instalacións publicadas de exemplo (CC0 coma os datos de demo). */
export function publicFacility(overrides: Partial<PublicFacilityResponse> = {}): PublicFacilityResponse {
  const id = overrides.id ?? '019a0b6e-0000-7000-8000-0000000000f1';
  return {
    id,
    name: 'Pista de pádel 1 de O Alto',
    address: { street: 'Rúa Buxo', postalCode: '15570', locality: 'Narón', latitude: 43.5167, longitude: -8.1903 },
    sports: [{ code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } }],
    temporarilyClosed: false,
    cover: {
      kind: 'PHOTO',
      id: `${id}-photo`,
      large: `/api/v1/municipalities/naron/photos/${id}-photo/large`,
      thumb: `/api/v1/municipalities/naron/photos/${id}-photo/thumb`,
      alt: { gl: 'Pista de pádel coa rede', es: 'Pista de pádel con la red' },
      width: 1600,
      height: 1067,
    },
    ...overrides,
  };
}

export const FACILITIES: PublicFacilityResponse[] = [
  publicFacility({
    id: 'f-campo',
    name: 'Campo de fútbol 1 de O Alto',
    address: {
      street: 'Rúa Buxo',
      streetNumber: '2',
      postalCode: '15570',
      locality: 'Narón',
      latitude: 43.5167,
      longitude: -8.1903,
    },
    sports: [
      { code: 'football', label: { gl: 'Fútbol 11', es: 'Fútbol 11' } },
      { code: 'football_7', label: { gl: 'Fútbol 7', es: 'Fútbol 7' } },
    ],
    temporarilyClosed: true,
  }),
  publicFacility({ id: 'f-padel' }),
  publicFacility({
    id: 'f-pavillon',
    name: 'Pavillón de Piñeiros',
    address: {
      street: 'Estrada de Castela',
      postalCode: '15570',
      locality: 'Narón',
      latitude: 43.5167,
      longitude: -8.1903,
    },
    sports: [
      { code: 'futsal', label: { gl: 'Fútbol sala', es: 'Fútbol sala' } },
      { customSportId: 'c-1', label: { gl: 'Bádminton', es: 'Bádminton' } },
    ],
    cover: {
      kind: 'DRAWING',
      id: 'sports_hall',
      name: { gl: 'Pavillón polideportivo', es: 'Pabellón polideportivo' },
      large: '/api/v1/municipalities/naron/drawings/sports_hall/large',
      thumb: '/api/v1/municipalities/naron/drawings/sports_hall/thumb',
      alt: { gl: 'Debuxo dun pavillón polideportivo', es: 'Dibujo de un pabellón polideportivo' },
      width: 1200,
      height: 800,
    },
  }),
];
