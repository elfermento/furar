// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { WritableSignal } from '@angular/core';
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { cancelMyOrganizationBooking } from '../../../api/fn/bookings/cancel-my-organization-booking';
import { createMySeriesRequest } from '../../../api/fn/bookings/create-my-series-request';
import { listMyOrganizationBookings } from '../../../api/fn/bookings/list-my-organization-bookings';
import { listMyOrganizations } from '../../../api/fn/bookings/list-my-organizations';
import { listMySeriesRequests } from '../../../api/fn/bookings/list-my-series-requests';
import { withdrawMySeriesRequest } from '../../../api/fn/bookings/withdraw-my-series-request';
import type { MyBookingResponse } from '../../../api/models/my-booking-response';
import type { MyOrganizationResponse } from '../../../api/models/my-organization-response';
import type { MySeriesRequestResponse } from '../../../api/models/my-series-request-response';
import type { SeriesRequestRequest } from '../../../api/models/series-request-request';
import type { BookingList } from '../mine/my-bookings.store';
import { PAGE_SIZE } from '../mine/my-bookings.store';
import type { BookingScope } from '../mine/my-bookings';

export type { MyBookingResponse, MyOrganizationResponse, MySeriesRequestResponse, SeriesRequestRequest };

const EMPTY: BookingList = { items: [], total: 0, page: -1, state: 'idle', loadingMore: false };

/**
 * As entidades das que a persoa é responsable no concello (ADR-0039, punto 10): `GET
 * /me/organizations`, pedido unha vez por concello e compartido (as pestanas «Eu · ‹Entidade›» de
 * «As miñas reservas» e «Reservar como» da páxina de reservar). Baleiro se non é responsable ou é
 * unha conta de traballo; se falla, baleiro (a persoa segue reservando coma sempre).
 */
@Injectable({ providedIn: 'root' })
export class MyOrganizationsStore {
  private readonly api = inject(Api);
  private readonly cache = new Map<string, Promise<MyOrganizationResponse[]>>();

  readonly items = signal<readonly MyOrganizationResponse[]>([]);

  async load(slug: string): Promise<readonly MyOrganizationResponse[]> {
    let pending = this.cache.get(slug);
    if (!pending) {
      pending = this.api.invoke(listMyOrganizations, { slug }).catch(() => {
        this.cache.delete(slug);
        return [];
      });
      this.cache.set(slug, pending);
    }
    const items = await pending;
    this.items.set(items);
    return items;
  }
}

/**
 * As reservas e as solicitudes dunha entidade na pestana da entidade (`GET
 * /me/organizations/{id}/bookings?scope=`, por páxinas como «Eu»; `GET …/series-requests`), cancelar
 * unha sesión ou unha reserva (`POST …/cancel` coa versión en `If-Match`), solicitar tempada e
 * retirar unha solicitude pendente. Un por pestana.
 */
@Injectable()
export class OrganizationBookingsStore {
  private readonly api = inject(Api);

  readonly lists: Record<BookingScope, WritableSignal<BookingList>> = {
    UPCOMING: signal(EMPTY),
    HISTORY: signal(EMPTY),
  };
  readonly requests = signal<readonly MySeriesRequestResponse[]>([]);

  async load(slug: string, organizationId: string, scope: BookingScope, force = false): Promise<void> {
    const list = this.lists[scope];
    if (!force && list().state !== 'idle' && list().state !== 'error') return;
    list.set({ ...EMPTY, state: 'loading' });
    try {
      const page = await this.api.invoke(listMyOrganizationBookings, {
        slug,
        organizationId,
        scope,
        page: 0,
        size: PAGE_SIZE,
      });
      list.set({ items: page.items, total: page.totalItems, page: 0, state: 'ready', loadingMore: false });
    } catch {
      list.set({ ...EMPTY, state: 'error' });
    }
  }

  async loadMore(slug: string, organizationId: string, scope: BookingScope): Promise<void> {
    const list = this.lists[scope];
    const current = list();
    if (current.state !== 'ready' || current.loadingMore || current.items.length >= current.total) return;
    list.set({ ...current, loadingMore: true });
    try {
      const next = current.page + 1;
      const page = await this.api.invoke(listMyOrganizationBookings, {
        slug,
        organizationId,
        scope,
        page: next,
        size: PAGE_SIZE,
      });
      const seen = new Set(current.items.map((item) => item.id));
      list.set({
        items: [...current.items, ...page.items.filter((item) => !seen.has(item.id))],
        total: page.totalItems,
        page: next,
        state: 'ready',
        loadingMore: false,
      });
    } catch {
      list.update((value) => ({ ...value, loadingMore: false }));
    }
  }

  async loadRequests(slug: string, organizationId: string): Promise<void> {
    try {
      this.requests.set(await this.api.invoke(listMySeriesRequests, { slug, organizationId }));
    } catch {
      this.requests.set([]);
    }
  }

  /** Cancela coa versión que se ve; a tarxeta pasa a ser a resposta e o historial pídese de novo. */
  async cancel(slug: string, organizationId: string, booking: MyBookingResponse): Promise<MyBookingResponse> {
    const cancelled = await this.api.invoke(cancelMyOrganizationBooking, {
      slug,
      organizationId,
      id: booking.id,
      'If-Match': `"${booking.version}"`,
    });
    for (const list of Object.values(this.lists)) {
      list.update((value) => ({
        ...value,
        items: value.items.map((item) => (item.id === cancelled.id ? cancelled : item)),
      }));
    }
    this.lists.HISTORY.update((value) => (value.state === 'ready' ? { ...value, state: 'idle' } : value));
    return cancelled;
  }

  async request(slug: string, organizationId: string, body: SeriesRequestRequest): Promise<MySeriesRequestResponse> {
    const created = await this.api.invoke(createMySeriesRequest, { slug, organizationId, body });
    this.requests.update((items) => [created, ...items]);
    return created;
  }

  async withdraw(slug: string, organizationId: string, request: MySeriesRequestResponse): Promise<void> {
    await this.api.invoke(withdrawMySeriesRequest, { slug, organizationId, id: request.id });
    this.requests.update((items) => items.filter((item) => item.id !== request.id));
  }
}
