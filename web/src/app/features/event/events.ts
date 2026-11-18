// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { activityDates, scheduleWhen } from '../../shared/events/event-format';
import type { TagTone } from '../../shared/ui/tag/tag.component';
import type { EventResponse } from './events.store';

/** Traduce unha clave cos seus parámetros. */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/** O estado dun evento na lista: o chip da columna «Estado». */
export type EventStatus = 'PUBLISHED' | 'DRAFT' | 'CANCELLED' | 'PAST';

/** O tipo: dun día ou con horario (ADR-0041). */
export type EventKind = EventResponse['kind'];

export const EVENT_STATUSES: readonly EventStatus[] = ['PUBLISHED', 'DRAFT', 'CANCELLED', 'PAST'];
export const EVENT_KINDS: readonly EventKind[] = ['ONE_DAY', 'SCHEDULED'];

/** Cor de cada estado (paleta de estados: o positivo verde, o negativo vermello). */
export const STATUS_TONE: Record<EventStatus, TagTone> = {
  PUBLISHED: 'success',
  DRAFT: 'info',
  CANCELLED: 'danger',
  PAST: 'muted',
};

/** Borrador en contorno; o resto, chip cheo. */
export const STATUS_LOOK: Record<EventStatus, 'solid' | 'outline'> = {
  PUBLISHED: 'solid',
  DRAFT: 'outline',
  CANCELLED: 'solid',
  PAST: 'solid',
};

/**
 * Cancelado, pasado (un evento dun día xa empezado; unha actividade xa rematada), publicado ou
 * borrador.
 */
export function statusOf(event: EventResponse, now: Date = new Date()): EventStatus {
  if (event.cancelledAt) return 'CANCELLED';
  const over = event.kind === 'SCHEDULED' ? event.endsAt : event.startsAt;
  if (new Date(over).getTime() <= now.getTime()) return 'PAST';
  return event.published ? 'PUBLISHED' : 'DRAFT';
}

/** Aínda se pode cambiar: nin cancelado nin pasado. */
export function isOpen(event: EventResponse, now: Date = new Date()): boolean {
  const status = statusOf(event, now);
  return status === 'PUBLISHED' || status === 'DRAFT';
}

/** «162 de 200», «23 apuntadas» ou «—» (ninguén e sen límite), e « · 3 en espera» se hai lista. */
export function seatsText(event: EventResponse, t: Translate): string {
  const seats = event.capacity
    ? t('event.manage.seatsOf', { count: event.confirmed, total: event.capacity })
    : event.confirmed
      ? t('event.manage.joined', { count: event.confirmed })
      : '—';
  const waiting = (event.waiting ?? 0) + (event.notified ?? 0);
  return waiting ? `${seats} · ${t('event.manage.waiting', { count: waiting })}` : seats;
}

/**
 * «Cando» na lista: un día, «dom. 20 dec. · 11:00» (sen segunda liña); unha actividade, «Luns e
 * mércores · 17:00» e debaixo as datas, «Do 1 out. ao 19 xuñ.».
 */
export function whenLines(
  event: EventResponse,
  lang: string,
  t: Translate,
  oneDay: (startsAt: string) => string,
): { main: string; sub: string | null } {
  if (event.kind === 'SCHEDULED' && event.schedule) {
    return { main: scheduleWhen(event.schedule, lang, t, true), sub: activityDates(event, lang, t, true) };
  }
  return { main: oneDay(event.startsAt), sub: null };
}

/** Os da lista, cos estados e tipos escollidos (todos se non hai ningún). */
export function filterEvents(
  events: readonly EventResponse[],
  statuses: readonly string[],
  kinds: readonly string[] = [],
  now: Date = new Date(),
): EventResponse[] {
  return events.filter(
    (event) =>
      (!statuses.length || statuses.includes(statusOf(event, now))) && (!kinds.length || kinds.includes(event.kind)),
  );
}

/** Os estados que hai na lista, co seu número, para o filtro da cabeceira. */
export function statusCounts(events: readonly EventResponse[], now: Date = new Date()): Map<EventStatus, number> {
  const counts = new Map<EventStatus, number>();
  for (const event of events) {
    const status = statusOf(event, now);
    counts.set(status, (counts.get(status) ?? 0) + 1);
  }
  return counts;
}

/** Os tipos que hai na lista, co seu número. */
export function kindCounts(events: readonly EventResponse[]): Map<EventKind, number> {
  const counts = new Map<EventKind, number>();
  for (const event of events) counts.set(event.kind, (counts.get(event.kind) ?? 0) + 1);
  return counts;
}

/** «Piscina municipal · Vaso pequeno»: a instalación co uso e a parte da actividade, ou o outro sitio. */
export function placeText(event: EventResponse): string {
  if (!event.facility) return event.place ?? '';
  return [event.facility.name, event.useName, event.partName].filter(Boolean).join(' · ');
}
