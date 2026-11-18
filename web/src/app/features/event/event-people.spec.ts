// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { peopleRows, sortByAbsences, waitRows } from './event-people';
import type { EventRegistrationResponse } from './events.store';

const t = (key: string, params?: Record<string, unknown>) => (params ? `${key}:${JSON.stringify(params)}` : key);

function person(id: string, overrides: Partial<EventRegistrationResponse> = {}): EventRegistrationResponse {
  return { id, status: 'CONFIRMED', createdAt: '2026-11-01T10:00:00Z', ...overrides };
}

describe('event-people: as persoas dun evento', () => {
  it('as con praza, por nome: a persoa a cargo co nome de quen a apuntou, o ano e as faltas', () => {
    const rows = peopleRows(
      [
        person('r1', { name: 'Marta Souto', dependentName: 'Lúa Souto', birthYear: 2015, payment: 'PAID' }),
        person('r2', { name: 'Antía Rial', birthDate: '2014-03-02', payment: 'PENDING' }),
        person('r3', { status: 'WAITING', name: 'Breixo' }),
        person('r4', { email: 'sen@nome.example' }),
      ],
      [{ registrationId: 'r1', present: 12, absent: 2, sessions: 14 }],
      t,
    );
    expect(rows.map((r) => [r.name, r.dependent, r.born, r.absences, r.payment])).toEqual([
      ['Antía Rial', null, '2014', '—', 'PENDING'],
      ['Lúa Souto', 'Marta Souto', '2015', 'event.people.absencesOf:{"count":2,"total":14}', 'PAID'],
      ['sen@nome.example', null, null, '—', null],
    ]);
  });

  it('ordenar polas faltas, en cada sentido', () => {
    const rows = peopleRows(
      [person('a', { name: 'A' }), person('b', { name: 'B' })],
      [
        { registrationId: 'a', present: 1, absent: 1, sessions: 2 },
        { registrationId: 'b', present: 0, absent: 2, sessions: 2 },
      ],
      t,
    );
    expect(sortByAbsences(rows, 'desc').map((r) => r.id)).toEqual(['b', 'a']);
    expect(sortByAbsences(rows, 'asc').map((r) => r.id)).toEqual(['a', 'b']);
    expect(sortByAbsences(rows, '').map((r) => r.id)).toEqual(['a', 'b']);
  });

  it('a lista de espera polo posto: a avisada, «só ela ata» mentres dura a súa hora', () => {
    const now = new Date('2026-11-18T16:00:00Z');
    const rows = waitRows(
      [
        person('w2', { status: 'WAITING', name: 'Uxía', position: 2 }),
        person('w1', { status: 'NOTIFIED', name: 'Breixo', position: 1, exclusiveUntil: '2026-11-18T17:42:00Z' }),
        person('w3', { status: 'NOTIFIED', name: 'Roi', position: 3, exclusiveUntil: '2026-11-18T15:00:00Z' }),
      ],
      t,
      now,
    );
    expect(rows.map((r) => [r.position, r.name, r.chip])).toEqual([
      [1, 'Breixo', 'event.people.notifiedUntil:{"time":"18:42"}'],
      [2, 'Uxía', null],
      [3, 'Roi', 'event.people.notified'],
    ]);
  });
});
