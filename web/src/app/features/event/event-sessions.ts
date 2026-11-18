// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../shared/ui/calendar/local-date';
import type { EventSessionResponse } from './events.store';

/** Cantas sesións se ven antes de «Ver todas». */
export const SESSIONS_SHOWN = 5;

/** Unha sesión na lista: cancelada, de hoxe ou doutro día, e o que se pode facer con ela. */
export interface SessionRow {
  readonly session: EventSessionResponse;
  readonly state: 'cancelled' | 'today' | 'other';
  /** Xa empezou (pasada ou de hoxe): pódese pasar ou corrixir a lista. */
  readonly past: boolean;
  /** Pódese cancelar: sen empezar e sen lista. */
  readonly cancellable: boolean;
}

/** As sesións en orde, co seu estado. */
export function sessionRows(sessions: readonly EventSessionResponse[], today: IsoDate, now: Date): SessionRow[] {
  return [...sessions]
    .sort((a, b) => a.startsAt.localeCompare(b.startsAt))
    .map((session) => {
      const cancelled = !!session.cancelledAt;
      const started = new Date(session.startsAt).getTime() <= now.getTime();
      return {
        session,
        state: cancelled ? 'cancelled' : session.date === today ? 'today' : 'other',
        past: !cancelled && session.date < today,
        cancellable: !cancelled && !started && !session.attendanceTaken,
      };
    });
}

/** As que se ven primeiro: desde a última anterior a hoxe, `count` seguidas. */
export function sessionWindow(rows: readonly SessionRow[], count: number, today: IsoDate): SessionRow[] {
  const next = rows.findIndex((row) => row.session.date >= today);
  const start = Math.max(0, (next === -1 ? rows.length : next) - 1);
  return rows.slice(start, start + count);
}
