// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type {
  BookingOpenMatchResponse,
  MyOpenMatchResponse,
  OpenMatchCardResponse,
  OpenMatchResponse,
} from '../app/shared/open-matches/open-matches.store';

/** Datos de exemplo inventados: unha partida de fútbol sala, 4 de 10, mínimo 6. */
export function matchCard(overrides: Partial<OpenMatchCardResponse> = {}): OpenMatchCardResponse {
  return {
    id: 'm1',
    facilityId: 'f1',
    facilityName: 'Pavillón da Gándara',
    facilityAddress: 'Rúa da Gándara, 12',
    useName: 'Fútbol sala',
    partName: 'Pista enteira',
    start: '2099-11-20T19:00:00Z',
    end: '2099-11-20T20:30:00Z',
    level: 'INTERMEDIATE',
    title: 'Pachanga de venres',
    organizerName: 'Uxía Pereira',
    total: 4,
    minimum: 6,
    maximum: 10,
    approval: false,
    cover: {
      id: 'c1',
      kind: 'DRAWING',
      alt: { gl: '', es: '' },
      width: 1600,
      height: 900,
      large: '/api/v1/municipalities/naron/drawings/futsal/large',
      thumb: '/api/v1/municipalities/naron/drawings/futsal/thumb',
    },
    ...overrides,
  };
}

export function publicMatch(overrides: Partial<OpenMatchResponse> = {}): OpenMatchResponse {
  return {
    ...matchCard(),
    description: 'Partido mixto e sen présa.',
    deadline: '2099-11-20T07:00:00Z',
    status: 'OPEN',
    ...overrides,
  };
}

export function myMatch(overrides: Partial<MyOpenMatchResponse> = {}): MyOpenMatchResponse {
  return {
    ...publicMatch(),
    me: { role: 'NONE' },
    hidden: false,
    knownCount: 3,
    players: [],
    requests: [],
    version: 2,
    ...overrides,
  };
}

export function bookingMatch(overrides: Partial<BookingOpenMatchResponse> = {}): BookingOpenMatchResponse {
  return {
    id: 'm1',
    status: 'OPEN',
    hidden: false,
    total: 4,
    minimum: 6,
    maximum: 10,
    confirmed: 1,
    pending: 0,
    ...overrides,
  };
}
