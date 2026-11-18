// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { acceptEventRequest } from '../../api/fn/events/accept-event-request';
import { cancelEvent } from '../../api/fn/events/cancel-event';
import { createEvent } from '../../api/fn/events/create-event';
import { deleteEvent } from '../../api/fn/events/delete-event';
import { listEventRegistrations } from '../../api/fn/events/list-event-registrations';
import { listManagedEvents } from '../../api/fn/events/list-managed-events';
import { rejectEventRequest } from '../../api/fn/events/reject-event-request';
import { setEventPayment } from '../../api/fn/events/set-event-payment';
import { updateEvent } from '../../api/fn/events/update-event';
import { cancelEventSession } from '../../api/fn/events/cancel-event-session';
import { endEvent } from '../../api/fn/events/end-event';
import { getEventAttendance } from '../../api/fn/events/get-event-attendance';
import { getEventAttendanceSummary } from '../../api/fn/events/get-event-attendance-summary';
import { getManagedEvent } from '../../api/fn/events/get-managed-event';
import { listEventSessions } from '../../api/fn/events/list-event-sessions';
import { markEventAttendance } from '../../api/fn/events/mark-event-attendance';
import { previewEventSessions } from '../../api/fn/events/preview-event-sessions';
import type { AttendanceMarkRequest } from '../../api/models/attendance-mark-request';
import type { EventAttendanceResponse } from '../../api/models/event-attendance-response';
import type { EventAttendanceSummaryResponse } from '../../api/models/event-attendance-summary-response';
import type { EventSessionResponse } from '../../api/models/event-session-response';
import type { EventSessionsPreviewRequest } from '../../api/models/event-sessions-preview-request';
import type { EventSessionsPreviewResponse } from '../../api/models/event-sessions-preview-response';
import { listFacilities } from '../../api/fn/facilities/list-facilities';
import type { EventRegistrationResponse } from '../../api/models/event-registration-response';
import type { EventRequest } from '../../api/models/event-request';
import type { EventResponse } from '../../api/models/event-response';
import { PendingEventRequests } from '../../core/events/pending-event-requests';

export type {
  AttendanceMarkRequest,
  EventAttendanceResponse,
  EventAttendanceSummaryResponse,
  EventRegistrationResponse,
  EventRequest,
  EventResponse,
  EventSessionResponse,
  EventSessionsPreviewRequest,
  EventSessionsPreviewResponse,
};
export type EventPayment = NonNullable<EventRegistrationResponse['payment']>;

/**
 * A única porta ao cliente xerado dos eventos na xestión (ADR-0038): a lista, alta, edición e
 * cancelar con If-Match, eliminar, as persoas, aceptar, rexeitar e o pago. Aceptar e rexeitar
 * refrescan o contador do menú.
 */
@Injectable({ providedIn: 'root' })
export class EventsStore {
  private readonly api = inject(Api);
  private readonly counter = inject(PendingEventRequests);

  readonly items = signal<readonly EventResponse[]>([]);
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');

  async load(slug: string): Promise<void> {
    this.state.set(this.items().length ? 'ready' : 'loading');
    try {
      this.items.set(await this.api.invoke(listManagedEvents, { slug }));
      this.state.set('ready');
    } catch {
      this.state.set('error');
    }
  }

  async create(slug: string, body: EventRequest): Promise<EventResponse> {
    const saved = await this.api.invoke(createEvent, { slug, body });
    this.items.update((items) => [saved, ...items]);
    return saved;
  }

  async update(slug: string, event: EventResponse, body: EventRequest): Promise<EventResponse> {
    const saved = await this.api.invoke(updateEvent, { slug, id: event.id, 'If-Match': `"${event.version}"`, body });
    this.replace(saved);
    return saved;
  }

  async cancel(slug: string, event: EventResponse): Promise<EventResponse> {
    const saved = await this.api.invoke(cancelEvent, { slug, id: event.id, 'If-Match': `"${event.version}"` });
    this.replace(saved);
    this.counter.refresh();
    return saved;
  }

  async remove(slug: string, event: EventResponse): Promise<void> {
    await this.api.invoke(deleteEvent, { slug, id: event.id });
    this.items.update((items) => items.filter((item) => item.id !== event.id));
  }

  registrations(slug: string, id: string): Promise<EventRegistrationResponse[]> {
    return this.api.invoke(listEventRegistrations, { slug, id });
  }

  async accept(slug: string, id: string, registrationId: string): Promise<EventRegistrationResponse> {
    const saved = await this.api.invoke(acceptEventRequest, { slug, id, registrationId });
    this.counter.refresh();
    await this.load(slug);
    return saved;
  }

  async reject(slug: string, id: string, registrationId: string): Promise<EventRegistrationResponse> {
    const saved = await this.api.invoke(rejectEventRequest, { slug, id, registrationId });
    this.counter.refresh();
    await this.load(slug);
    return saved;
  }

  pay(slug: string, id: string, registrationId: string, status: EventPayment): Promise<EventRegistrationResponse> {
    return this.api.invoke(setEventPayment, { slug, id, registrationId, body: { status } });
  }

  /** Un evento (o detalle aberto desde fóra da lista ou para refrescalo). */
  async get(slug: string, id: string): Promise<EventResponse> {
    const event = await this.api.invoke(getManagedEvent, { slug, id });
    this.replace(event);
    return event;
  }

  /** As sesións que se crearían cun horario (ADR-0041, punto 4). */
  preview(slug: string, body: EventSessionsPreviewRequest): Promise<EventSessionsPreviewResponse> {
    return this.api.invoke(previewEventSessions, { slug, body });
  }

  sessions(slug: string, id: string): Promise<EventSessionResponse[]> {
    return this.api.invoke(listEventSessions, { slug, id });
  }

  cancelSession(slug: string, id: string, sessionId: string): Promise<EventSessionResponse> {
    return this.api.invoke(cancelEventSession, { slug, id, sessionId });
  }

  /** Rematar unha actividade desde un día: cancela as sesións que quedan. */
  async end(slug: string, id: string, from: string): Promise<EventResponse> {
    const saved = await this.api.invoke(endEvent, { slug, id, body: { from } });
    this.replace(saved);
    return saved;
  }

  attendance(slug: string, id: string, sessionId: string): Promise<EventAttendanceResponse> {
    return this.api.invoke(getEventAttendance, { slug, id, sessionId });
  }

  saveAttendance(
    slug: string,
    id: string,
    sessionId: string,
    marks: AttendanceMarkRequest[],
  ): Promise<EventAttendanceResponse> {
    return this.api.invoke(markEventAttendance, { slug, id, sessionId, body: { marks } });
  }

  /** Por persoa: sesións con lista, presenzas e faltas. */
  attendanceSummary(slug: string, id: string): Promise<EventAttendanceSummaryResponse[]> {
    return this.api.invoke(getEventAttendanceSummary, { slug, id });
  }

  /** As instalacións do concello, para o «Lugar» (por nome). */
  async facilities(slug: string): Promise<{ value: string; label: string }[]> {
    const page = await this.api.invoke(listFacilities, { slug, size: 100 });
    return page.items
      .filter((facility) => facility.status !== 'WITHDRAWN')
      .map((facility) => ({ value: facility.id, label: facility.name }))
      .sort((a, b) => a.label.localeCompare(b.label, 'gl'));
  }

  private replace(saved: EventResponse): void {
    this.items.update((items) => items.map((item) => (item.id === saved.id ? saved : item)));
  }
}
