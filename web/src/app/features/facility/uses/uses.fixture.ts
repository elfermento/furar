// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Use } from './uses.store';

export const FACILITY_URL = '/api/v1/municipalities/naron/facilities/f1';
export const USES_URL = `${FACILITY_URL}/uses`;

/** Uso de proba: «Fútbol 11», enteira, 90 min, sempre. */
export function use(overrides: Partial<Use> = {}): Use {
  return {
    id: 'u1',
    facilityId: 'f1',
    name: 'Fútbol 11',
    rentalMode: 'WHOLE',
    slotMinutes: 90,
    availability: 'ALWAYS',
    blocksOthers: false,
    parts: [],
    periods: [],
    weekdays: 127,
    price: { kind: 'NONE', per: 'BOOKING' },
    rules: {},
    version: 2,
    createdAt: '2026-10-25T10:00:00Z',
    updatedAt: '2026-10-25T10:00:00Z',
    ...overrides,
  };
}

export const FUTBOL_7 = use({
  id: 'u2',
  name: 'Fútbol 7',
  rentalMode: 'PARTS',
  slotMinutes: 60,
  parts: [
    { id: 'p1', name: 'Campo A' },
    { id: 'p2', name: 'Campo B' },
  ],
});

export const SAMAIN = use({
  id: 'u3',
  name: 'Torneo de Samaín',
  rentalMode: 'SEATS',
  capacity: 30,
  slotMinutes: 120,
  availability: 'DATES',
  blocksOthers: true,
  periods: [{ from: '2026-10-31', to: '2026-10-31' }],
});
