// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { createMyBookingWait } from '../../../api/fn/bookings/create-my-booking-wait';
import { deleteMyBookingWait } from '../../../api/fn/bookings/delete-my-booking-wait';
import { listMyBookingWaits } from '../../../api/fn/bookings/list-my-booking-waits';
import type { BookingWaitResponse } from '../../../api/models/booking-wait-response';
import { asProblem } from '../../../core/errors/problem.interceptor';

export type { BookingWaitResponse };

/**
 * As esperas da persoa neste concello (ADR-0033): listar (`GET …/me/booking-waits`, unha soa
 * lectura por páxina), esperar por unha hora ocupada (`POST`) e deixar de esperar (`DELETE`; un
 * 404 é éxito: xa non esperaba, por outra pestana ou porque a hora pasou). Úsano a páxina de
 * reservar e «As miñas reservas». Un por páxina.
 */
@Injectable()
export class WaitsStore {
  private readonly api = inject(Api);
  private asked: string | null = null;

  readonly items = signal<readonly BookingWaitResponse[]>([]);
  readonly state = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');

  /** As esperas, se aínda non se pediron para este concello (ou `force`: recargar). */
  async load(slug: string, force = false): Promise<void> {
    if (!force && this.asked === slug) return;
    this.asked = slug;
    this.state.set('loading');
    try {
      const items = await this.api.invoke(listMyBookingWaits, { slug });
      this.items.set(sorted(items));
      this.state.set('ready');
    } catch {
      this.items.set([]);
      this.state.set('error');
    }
  }

  /** Espera por esa hora; se xa esperaba, a API devolve a mesma espera. Mal: rexeita co erro. */
  async join(slug: string, facilityId: string, useId: string, start: string): Promise<BookingWaitResponse> {
    const wait = await this.api.invoke(createMyBookingWait, { slug, body: { facilityId, useId, start } });
    this.items.update((items) => sorted([...items.filter((item) => item.id !== wait.id), wait]));
    return wait;
  }

  /** Deixa de esperar. Un 404 tamén é éxito: a espera desaparece sen erro. */
  async leave(slug: string, id: string): Promise<void> {
    try {
      await this.api.invoke(deleteMyBookingWait, { slug, id });
    } catch (error) {
      if (asProblem(error).status !== 404) throw error;
    }
    this.items.update((items) => items.filter((item) => item.id !== id));
  }
}

function sorted(items: readonly BookingWaitResponse[]): BookingWaitResponse[] {
  return [...items].sort((a, b) => new Date(a.start).getTime() - new Date(b.start).getTime());
}
