// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ScheduleResponse, SeasonResponse } from './schedule.store';
import { DAYS } from './schedule.store';

export const FACILITY_URL = '/api/v1/municipalities/naron/facilities/f1';
export const SCHEDULE_URL = `${FACILITY_URL}/schedule`;
export const SEASONS_URL = `${FACILITY_URL}/seasons`;

const h = (hours: number) => hours * 60;

/** Semana da maqueta: de luns a venres 8–23, sábado 9–14 e 16–20, domingo 9–14, festivos pechado. */
const WINTER_WEEK = [
  [[h(8), h(23)]],
  [[h(8), h(23)]],
  [[h(8), h(23)]],
  [[h(8), h(23)]],
  [[h(8), h(23)]],
  [
    [h(9), h(14)],
    [h(16), h(20)],
  ],
  [[h(9), h(14)]],
  [],
];

/** Tempada de proba: «Inverno», do 1/9/2026 ao 30/6/2027, versión 4. */
export function season(overrides: Partial<SeasonResponse> = {}): SeasonResponse {
  return {
    id: 's1',
    facilityId: 'f1',
    name: 'Inverno',
    startDate: '2026-09-01',
    endDate: '2027-06-30',
    version: 4,
    week: DAYS.map((day, i) => ({
      day,
      intervals: WINTER_WEEK[i].map(([startMinute, endMinute]) => ({ startMinute, endMinute })),
    })),
    createdAt: '2026-10-25T10:00:00Z',
    updatedAt: '2026-10-25T10:00:00Z',
    ...overrides,
  };
}

/** «Verán», do 1/7/2027 ao 31/8/2027, todo pechado. */
export const SUMMER = season({
  id: 's2',
  name: 'Verán',
  startDate: '2027-07-01',
  endDate: '2027-08-31',
  version: 1,
  week: DAYS.map((day) => ({ day, intervals: [] })),
});

/** Horario de proba: hoxe é o sábado 31/10/2026 (en Inverno). */
export function schedule(overrides: Partial<ScheduleResponse> & { today?: string } = {}): ScheduleResponse {
  return {
    seasons: [season(), SUMMER],
    currentSeasonId: 's1',
    year: 2026,
    holidays: [{ date: '2026-12-25', name: { gl: 'Nadal', es: 'Navidad' }, source: 'BOE' }],
    today: '2026-10-31',
    ...overrides,
  } as ScheduleResponse;
}
