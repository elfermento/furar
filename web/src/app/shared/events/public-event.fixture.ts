// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { MyEventRegistrationResponse, PublicEventResponse } from './public-events.store';

/** Un evento como o ve a veciñanza (datos inventados). */
export function publicEvent(overrides: Partial<PublicEventResponse> = {}): PublicEventResponse {
  return {
    id: 'e1',
    name: { gl: 'Carreira popular de Nadal', es: 'Carrera popular de Navidad' },
    description: { gl: 'Carreira de 5 km.', es: 'Carrera de 5 km.' },
    kind: 'ONE_DAY',
    startsAt: '2099-12-20T10:00:00Z',
    endsAt: '2099-12-20T10:00:00Z',
    facility: { id: 'f1', name: 'Pista de atletismo', address: 'Estrada do Alto, 15570 Narón' },
    capacity: 200,
    free: 38,
    pricePeriod: 'ONCE',
    approval: false,
    open: true,
    waitlist: false,
    waiting: 0,
    cover: {
      large: '/api/v1/municipalities/naron/drawings/athletics_track/large',
      thumb: '/api/v1/municipalities/naron/drawings/athletics_track/thumb',
      alt: { gl: '', es: '' },
    },
    ...overrides,
  };
}

/** Unha actividade con horario (luns e mércores, 17:00–18:00, do 6 de outubro ao 24 de xuño), con límite de idade. */
export function publicActivity(overrides: Partial<PublicEventResponse> = {}): PublicEventResponse {
  return publicEvent({
    id: 'a1',
    name: { gl: 'Natación · Iniciación', es: 'Natación · Iniciación' },
    description: { gl: 'Primeiros pasos na auga.', es: 'Primeros pasos en el agua.' },
    kind: 'SCHEDULED',
    startsAt: '2098-10-06T15:00:00Z',
    endsAt: '2099-06-24T16:00:00Z',
    schedule: {
      weekdays: [1, 3],
      startMinute: 17 * 60,
      endMinute: 18 * 60,
      firstDate: '2098-10-06',
      lastDate: '2099-06-24',
    },
    facility: { id: 'f2', name: 'Piscina municipal', address: 'Rúa da Piscina, 15570 Narón' },
    capacity: 20,
    free: 5,
    priceCents: 2500,
    pricePeriod: 'QUARTER',
    birthYearFrom: 2014,
    birthYearTo: 2016,
    ...overrides,
  });
}

/** Unha inscrición da conta (por defecto, a propia e confirmada) nun evento. */
export function myRegistration(overrides: Partial<MyEventRegistrationResponse> = {}): MyEventRegistrationResponse {
  return { status: 'CONFIRMED', seatCovered: false, nextSessions: [], event: publicEvent(), ...overrides };
}
