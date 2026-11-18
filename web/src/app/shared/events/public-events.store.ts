// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { confirmEventSeat } from '../../api/fn/events/confirm-event-seat';
import { getMyEventRegistration } from '../../api/fn/events/get-my-event-registration';
import { getPublicEvent } from '../../api/fn/events/get-public-event';
import { joinEvent } from '../../api/fn/events/join-event';
import { leaveEvent } from '../../api/fn/events/leave-event';
import { listMyEventRegistrations } from '../../api/fn/events/list-my-event-registrations';
import { listMyEvents } from '../../api/fn/events/list-my-events';
import { listUpcomingEvents } from '../../api/fn/events/list-upcoming-events';
import type { JoinEventRequest } from '../../api/models/join-event-request';
import type { MyEventRegistrationResponse } from '../../api/models/my-event-registration-response';
import type { PublicEventResponse } from '../../api/models/public-event-response';

export type { JoinEventRequest, MyEventRegistrationResponse, PublicEventResponse };

/**
 * A única porta ao cliente xerado dos eventos do concello para a veciñanza (ADR-0038, ADR-0041):
 * os próximos (portada), a ficha, as inscricións da conta (a propia e as das persoas a cargo),
 * apuntarse ou entrar na lista de espera, confirmar a praza avisada, desapuntarse ou saír da lista e
 * as súas próximas («As miñas reservas»). `dependentId` baleiro: a propia persoa. Compartida: a usan a portada, «As miñas reservas» e a ficha, que
 * son features distintas.
 */
@Injectable({ providedIn: 'root' })
export class PublicEvents {
  private readonly api = inject(Api);

  upcoming(slug: string): Promise<PublicEventResponse[]> {
    return this.api.invoke(listUpcomingEvents, { slug });
  }

  get(slug: string, id: string): Promise<PublicEventResponse> {
    return this.api.invoke(getPublicEvent, { slug, id });
  }

  /** A inscrición da persoa ou dunha persoa a cargo (sen ela, `status` baleiro) co evento. */
  mine(slug: string, id: string, dependentId?: string): Promise<MyEventRegistrationResponse> {
    return this.api.invoke(getMyEventRegistration, { slug, id, dependentId });
  }

  /** As inscricións da conta no evento: a propia e as das persoas a cargo (só as que hai). */
  mineAll(slug: string, id: string): Promise<MyEventRegistrationResponse[]> {
    return this.api.invoke(listMyEventRegistrations, { slug, id });
  }

  /** Apuntarse, pedir praza ou entrar na lista de espera (a persoa ou unha a cargo). */
  join(slug: string, id: string, body?: JoinEventRequest): Promise<MyEventRegistrationResponse> {
    return this.api.invoke(joinEvent, { slug, id, body });
  }

  /** Confirmar a praza libre da que se avisou desde a lista de espera. */
  confirm(slug: string, id: string, dependentId?: string): Promise<MyEventRegistrationResponse> {
    return this.api.invoke(confirmEventSeat, { slug, id, dependentId });
  }

  /** Desapuntarse ou saír da lista de espera. */
  leave(slug: string, id: string, dependentId?: string): Promise<void> {
    return this.api.invoke(leaveEvent, { slug, id, dependentId });
  }

  /** As inscricións da conta (tamén esperas e avisos) en eventos e actividades que non remataron. */
  upcomingMine(slug: string): Promise<MyEventRegistrationResponse[]> {
    return this.api.invoke(listMyEvents, { slug });
  }
}
