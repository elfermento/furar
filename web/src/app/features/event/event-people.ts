// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inMadrid } from '../../shared/ui/calendar/madrid-time';
import type { TagTone } from '../../shared/ui/tag/tag.component';
import type { Translate } from './events';
import type { EventAttendanceSummaryResponse, EventPayment, EventRegistrationResponse } from './events.store';

/** Cor de cada estado do pago, coma nas reservas. */
export const PAYMENT_TONE: Record<EventPayment, TagTone> = { PENDING: 'warn', PAID: 'success', WAIVED: 'info' };
export const PAYMENTS: readonly EventPayment[] = ['WAIVED', 'PENDING', 'PAID'];

/** Unha persoa con praza, xa preparada para a táboa. */
export interface PersonRow {
  readonly id: string;
  readonly name: string;
  /** Quen a apuntou, se é unha persoa ao seu cargo. */
  readonly dependent: string | null;
  /** O ano de nacemento (só nun evento con límite de idade). */
  readonly born: string | null;
  readonly absent: number | null;
  readonly sessions: number | null;
  /** «2 de 14» (faltas de sesións con lista) ou «—». */
  readonly absences: string;
  readonly payment: EventPayment | null;
}

/** Unha persoa na lista de espera. */
export interface WaitRow {
  readonly id: string;
  readonly position: number;
  readonly name: string;
  /** O chip do estado («Avisada · só ela ata as 18:42», «Avisada»); esperando, ningún. */
  readonly chip: string | null;
}

/** O nome que se ve: a persoa a cargo, a titular, o correo ou «Persoa sen datos». */
function nameOf(person: EventRegistrationResponse, t: Translate): string {
  return person.dependentName ?? person.name ?? person.email ?? t('event.people.anonymized');
}

/** As persoas con praza, en orde de nome, coas súas faltas (nas actividades). */
export function peopleRows(
  people: readonly EventRegistrationResponse[],
  summary: readonly EventAttendanceSummaryResponse[],
  t: Translate,
): PersonRow[] {
  const byId = new Map(summary.map((item) => [item.registrationId, item]));
  return people
    .filter((person) => person.status === 'CONFIRMED')
    .map((person) => {
      const attendance = byId.get(person.id);
      const year = person.birthYear ?? (person.birthDate ? Number(person.birthDate.slice(0, 4)) : null);
      return {
        id: person.id,
        name: nameOf(person, t),
        dependent: person.dependentName ? (person.name ?? person.email ?? null) : null,
        born: year ? String(year) : null,
        absent: attendance ? attendance.absent : null,
        sessions: attendance ? attendance.sessions : null,
        absences: attendance
          ? t('event.people.absencesOf', { count: attendance.absent, total: attendance.sessions })
          : '—',
        payment: person.payment ?? null,
      };
    })
    .sort((a, b) => a.name.localeCompare(b.name, 'gl'));
}

/** Ordenadas polas faltas (máis ou menos primeiro); sen orde, coma veñen. */
export function sortByAbsences(rows: readonly PersonRow[], direction: '' | 'desc' | 'asc'): PersonRow[] {
  if (!direction) return [...rows];
  const sign = direction === 'desc' ? -1 : 1;
  return [...rows].sort((a, b) => sign * ((a.absent ?? 0) - (b.absent ?? 0)));
}

/** A lista de espera, polo posto; a avisada, co chip e, mentres dura a súa hora, ata cando. */
export function waitRows(
  people: readonly EventRegistrationResponse[],
  t: Translate,
  now: Date = new Date(),
): WaitRow[] {
  return people
    .filter((person) => person.status === 'WAITING' || person.status === 'NOTIFIED')
    .sort((a, b) => (a.position ?? 0) - (b.position ?? 0))
    .map((person, index) => {
      const until = person.exclusiveUntil;
      const exclusive = !!until && new Date(until).getTime() > now.getTime();
      const chip =
        person.status !== 'NOTIFIED'
          ? null
          : until && exclusive
            ? t('event.people.notifiedUntil', { time: inMadrid(until).time })
            : t('event.people.notified');
      return { id: person.id, position: person.position ?? index + 1, name: nameOf(person, t), chip };
    });
}
