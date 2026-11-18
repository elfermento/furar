// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { EventResponse, EventSessionResponse } from './events.store';

export { publicEvent } from '../../shared/events/public-event.fixture';

/** Un evento da xestión de exemplo (datos inventados). */
export function managedEvent(overrides: Partial<EventResponse> = {}): EventResponse {
  return {
    id: 'e1',
    name: { gl: 'Carreira popular de Nadal', es: 'Carrera popular de Navidad' },
    description: { gl: '5 km.', es: '5 km.' },
    startsAt: '2099-12-20T10:00:00Z',
    endsAt: '2099-12-20T10:00:00Z',
    kind: 'ONE_DAY',
    pricePeriod: 'ONCE',
    facility: { id: 'f1', name: 'Pista de atletismo', address: 'Estrada do Alto, 15570 Narón' },
    capacity: 200,
    published: true,
    approval: false,
    confirmed: 162,
    pending: 0,
    notified: 0,
    waiting: 0,
    version: 0,
    ...overrides,
  };
}

/** Unha actividade con horario de exemplo: luns e mércores de 17:00 a 18:00 (datos inventados). */
export function managedActivity(overrides: Partial<EventResponse> = {}): EventResponse {
  return managedEvent({
    id: 'a1',
    kind: 'SCHEDULED',
    name: { gl: 'Natación · Iniciación', es: 'Natación · Iniciación' },
    startsAt: '2099-10-05T15:00:00Z',
    endsAt: '2100-06-16T16:00:00Z',
    schedule: {
      weekdays: [1, 3],
      startMinute: 17 * 60,
      endMinute: 18 * 60,
      firstDate: '2099-10-05',
      lastDate: '2100-06-16',
      useId: 'u1',
    },
    facility: { id: 'f2', name: 'Piscina municipal' },
    useName: 'Vaso pequeno',
    capacity: 20,
    confirmed: 20,
    waiting: 3,
    priceCents: 2500,
    pricePeriod: 'QUARTER',
    birthYearFrom: 2014,
    birthYearTo: 2016,
    ...overrides,
  });
}

/** Unha sesión de 17:00 a 18:00 (hora de inverno) dese día. */
export function session(date: string, overrides: Partial<EventSessionResponse> = {}): EventSessionResponse {
  return {
    id: 's-' + date,
    date,
    startsAt: `${date}T16:00:00Z`,
    endsAt: `${date}T17:00:00Z`,
    attendanceTaken: false,
    present: 0,
    absent: 0,
    ...overrides,
  };
}
