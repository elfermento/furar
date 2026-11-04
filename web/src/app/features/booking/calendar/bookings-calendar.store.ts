// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { getBookingsCalendar } from '../../../api/fn/bookings/get-bookings-calendar';
import type { BookingCalendarResponse } from '../../../api/models/booking-calendar-response';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import type { ManagedBookingResponse } from '../manage/manage-bookings';
import type { CalendarMode } from './bookings-calendar';
import { minutes } from './bookings-calendar';

export type { BookingCalendarResponse };

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/** As canceladas non ocupan: a API xa non as manda e, se chegasen, non se pintan. */
const occupies = (booking: ManagedBookingResponse): boolean => booking.status === 'CONFIRMED';

/** A vista rápida aberta: un día (mes) ou unha hora dun día (semana de todas). */
export interface PeekState {
  readonly date: IsoDate;
  readonly hour: number | null;
  readonly state: LoadState;
  readonly items: readonly ManagedBookingResponse[];
}

/** O que se pide: vista, data e instalación (`null`: todas). */
export interface CalendarQuery {
  readonly mode: CalendarMode;
  readonly date: IsoDate;
  readonly facilityId: string | null;
}

/**
 * Calendario do concello (D2-16, maqueta 27): o período (`GET …/bookings/calendar`, só as
 * confirmadas: as canceladas non ocupan) e a vista rápida dun día ou dunha hora (o mesmo, co
 * día). A última petición manda se se cruzan. Un por páxina.
 */
@Injectable()
export class BookingsCalendarStore {
  private readonly api = inject(Api);
  private request = 0;
  private peekRequest = 0;

  readonly calendar = signal<BookingCalendarResponse | null>(null);
  /** Do que é o calendario que chegou. */
  readonly query = signal<CalendarQuery | null>(null);
  readonly state = signal<LoadState>('idle');
  readonly peek = signal<PeekState | null>(null);

  /** Pide o período; `quiet` mantén o que se ve mentres chega (despois de cobrar ou cancelar). */
  async load(slug: string, query: CalendarQuery, quiet = false): Promise<void> {
    const request = ++this.request;
    if (!quiet) this.state.set('loading');
    try {
      const calendar = await this.api.invoke(getBookingsCalendar, {
        slug,
        view: query.mode,
        date: query.date,
        ...(query.facilityId ? { facilityId: query.facilityId } : {}),
      });
      if (request !== this.request) return;
      this.calendar.set({ ...calendar, blocks: calendar.blocks.filter(occupies) });
      this.query.set(query);
      this.state.set('ready');
    } catch {
      if (request === this.request && !quiet) this.state.set('error');
    }
  }

  /** O calendario que chegou, se é o da vista, instalación e data que se piden agora. */
  matching(query: CalendarQuery): BookingCalendarResponse | null {
    const calendar = this.calendar();
    const loaded = this.query();
    if (!calendar || !loaded || this.state() !== 'ready') return null;
    const same = loaded.mode === query.mode && loaded.facilityId === query.facilityId;
    return same && calendar.from <= query.date && query.date <= calendar.to ? calendar : null;
  }

  /** Abre a vista rápida e pide as reservas dese día (e desa hora, se a hai). */
  async openPeek(slug: string, date: IsoDate, hour: number | null, facilityId: string | null): Promise<void> {
    const request = ++this.peekRequest;
    this.peek.set({ date, hour, state: 'loading', items: [] });
    try {
      const day = await this.api.invoke(getBookingsCalendar, {
        slug,
        view: 'DAY',
        date,
        ...(facilityId ? { facilityId } : {}),
      });
      if (request !== this.peekRequest) return;
      const items = day.blocks.filter(
        (b) => occupies(b) && (hour === null || Math.floor(minutes(b).start / 60) === hour),
      );
      this.peek.set({ date, hour, state: 'ready', items });
    } catch {
      if (request === this.peekRequest) this.peek.set({ date, hour, state: 'error', items: [] });
    }
  }

  closePeek(): void {
    this.peekRequest++;
    this.peek.set(null);
  }
}
