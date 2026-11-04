// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ScheduleExceptionResponse } from './exceptions.store';
import { FACILITY_URL } from '../schedule.fixture';

export const EXCEPTIONS_URL = `${FACILITY_URL}/schedule-exceptions`;

/** Excepción de proba: Noiteboa aberta de 9:00 a 14:00, versión 2. */
export function exception(overrides: Partial<ScheduleExceptionResponse> = {}): ScheduleExceptionResponse {
  return {
    id: 'e1',
    facilityId: 'f1',
    date: '2026-12-24',
    open: true,
    startMinute: 540,
    endMinute: 840,
    reason: 'Noiteboa',
    version: 2,
    createdAt: '2026-10-25T10:00:00Z',
    updatedAt: '2026-10-25T10:00:00Z',
    ...overrides,
  };
}

/** Festa local pechada, o 12/10/2026 (no mes de «hoxe» do horario de proba), versión 1. */
export const LOCAL_HOLIDAY = exception({
  id: 'e2',
  date: '2026-10-12',
  open: false,
  startMinute: undefined,
  endMinute: undefined,
  reason: 'Festa local',
  version: 1,
});
