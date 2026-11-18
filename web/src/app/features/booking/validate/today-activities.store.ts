// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { listTodayEventSessions } from '../../../api/fn/events/list-today-event-sessions';
import type { EventTodaySessionResponse } from '../../../api/models/event-today-session-response';

export type { EventTodaySessionResponse };

/** As sesións de hoxe das actividades do concello (ADR-0041, punto 8), para pasar lista. */
@Injectable({ providedIn: 'root' })
export class TodayActivitiesStore {
  private readonly api = inject(Api);

  today(slug: string): Promise<EventTodaySessionResponse[]> {
    return this.api.invoke(listTodayEventSessions, { slug });
  }
}
