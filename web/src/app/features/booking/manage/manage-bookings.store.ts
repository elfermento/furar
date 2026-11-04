// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { cancelBooking } from '../../../api/fn/bookings/cancel-booking';
import { changeBookingPayment } from '../../../api/fn/bookings/change-booking-payment';
import { getBooking } from '../../../api/fn/bookings/get-booking';
import { getBookingsSummary } from '../../../api/fn/bookings/get-bookings-summary';
import type { ListBookings$Params } from '../../../api/fn/bookings/list-bookings';
import { listBookings } from '../../../api/fn/bookings/list-bookings';
import { listFacilities } from '../../../api/fn/facilities/list-facilities';
import type { BookingsOverviewResponse } from '../../../api/models/bookings-overview-response';
import type { FacilitySummaryResponse } from '../../../api/models/facility-summary-response';
import type { ManagedBookingDetailResponse } from '../../../api/models/managed-booking-detail-response';
import type { ManagedBookingResponse } from '../../../api/models/managed-booking-response';
import type { CustomFieldDefinition } from '../../../shared/custom-fields/custom-field.types';
import { BookingFieldsStore } from '../booking-fields.store';

export type { BookingsOverviewResponse, FacilitySummaryResponse, ManagedBookingDetailResponse, ManagedBookingResponse };
export type PaymentStatus = ManagedBookingResponse['paymentStatus'];
export type BookingStatus = ManagedBookingResponse['status'];
export type CancelChannel = ManagedBookingDetailResponse['cancelChannels'][number];
export type BookingSort = NonNullable<ListBookings$Params['sort']>;
/** Os filtros de `GET …/bookings` (sen a páxina). */
export type ListParams = Omit<ListBookings$Params, 'page' | 'size'>;

type LoadState = 'idle' | 'loading' | 'ready' | 'error';

/** A lista de «Ver máis», dos filtros ou da busca, por páxinas. */
export interface ManagedList {
  readonly items: readonly ManagedBookingResponse[];
  readonly total: number;
  /** Última páxina pedida (−1: ningunha). */
  readonly page: number;
  readonly state: LoadState;
  readonly loadingMore: boolean;
}

/** O detalle aberto: a reserva escollida (o da lista, mentres chega o completo). */
export interface DetailState {
  readonly id: string;
  readonly state: LoadState;
  readonly detail: ManagedBookingDetailResponse | null;
}

const EMPTY_LIST: ManagedList = { items: [], total: 0, page: -1, state: 'idle', loadingMore: false };

/** Reservas por páxina na lista. */
export const PAGE_SIZE = 20;
/** Filas de cada sección da portada («Últimas reservas» e cada pestana de «Cobros»). */
export const SECTION_SIZE = 3;
/** As instalacións dos filtros e das miniaturas: todas as do concello nunha petición. */
const FACILITIES_SIZE = 100;

/**
 * Reservas do concello (D2-15, maqueta 26): a portada (`GET …/bookings/summary`: as últimas e as
 * pestanas de «Cobros» coas súas contas), a lista con filtros (`GET …/bookings`, por páxinas), o
 * detalle (`GET …/bookings/{id}`) e as instalacións do concello (chips de «Instalación» e
 * miniatura do detalle). Marcar o cobro (`PUT …/payment`) e cancelar (`POST …/cancel`) van coa
 * versión que se ve en `If-Match`; a resposta substitúe o detalle e a fila, e a portada pídese de
 * novo (as contas cambian). Se fallan (412, 422…), rexeitan co erro e non tocan nada. Un por páxina.
 */
@Injectable()
export class ManageBookingsStore {
  private readonly api = inject(Api);
  private listRequest = 0;
  private detailRequest = 0;

  readonly overview = signal<BookingsOverviewResponse | null>(null);
  readonly overviewState = signal<LoadState>('idle');
  readonly list = signal<ManagedList>(EMPTY_LIST);
  readonly detail = signal<DetailState | null>(null);
  readonly facilities = signal<readonly FacilitySummaryResponse[]>([]);
  /** A última reserva que cambiou aquí (cobro ou cancelación): o calendario vólvese pedir con ela. */
  readonly updated = signal<ManagedBookingDetailResponse | null>(null);
  /** Os campos propios da reserva (as etiquetas do detalle), pedidos co primeiro que os ten. */
  readonly fields = signal<CustomFieldDefinition[]>([]);
  private readonly bookingFields = inject(BookingFieldsStore);
  private fieldsOf: string | null = null;

  /** As instalacións: se non chegan, os filtros quedan sen elas e o detalle sen miniatura. */
  async loadFacilities(slug: string): Promise<void> {
    try {
      const page = await this.api.invoke(listFacilities, { slug, page: 0, size: FACILITIES_SIZE });
      this.facilities.set(page.items);
    } catch {
      this.facilities.set([]);
    }
  }

  async loadOverview(slug: string): Promise<void> {
    if (!this.overview()) this.overviewState.set('loading');
    try {
      this.overview.set(await this.api.invoke(getBookingsSummary, { slug, size: SECTION_SIZE }));
      this.overviewState.set('ready');
    } catch {
      if (!this.overview()) this.overviewState.set('error');
    }
  }

  /** A primeira páxina cos filtros dados (a última petición manda se se cruzan). */
  async loadList(params: ListParams): Promise<void> {
    const request = ++this.listRequest;
    this.list.set({ ...EMPTY_LIST, state: 'loading' });
    try {
      const page = await this.api.invoke(listBookings, { ...params, page: 0, size: PAGE_SIZE });
      if (request !== this.listRequest) return;
      this.list.set({ items: page.items, total: page.totalItems, page: 0, state: 'ready', loadingMore: false });
    } catch {
      if (request === this.listRequest) this.list.set({ ...EMPTY_LIST, state: 'error' });
    }
  }

  /** A páxina seguinte, engadida ao final. */
  async loadMore(params: ListParams): Promise<void> {
    const current = this.list();
    if (current.state !== 'ready' || current.loadingMore || current.items.length >= current.total) return;
    const request = this.listRequest;
    this.list.set({ ...current, loadingMore: true });
    try {
      const next = current.page + 1;
      const page = await this.api.invoke(listBookings, { ...params, page: next, size: PAGE_SIZE });
      if (request !== this.listRequest) return;
      const seen = new Set(current.items.map((item) => item.id));
      this.list.set({
        items: [...current.items, ...page.items.filter((item) => !seen.has(item.id))],
        total: page.totalItems,
        page: next,
        state: 'ready',
        loadingMore: false,
      });
    } catch {
      if (request === this.listRequest) this.list.update((value) => ({ ...value, loadingMore: false }));
    }
  }

  clearList(): void {
    this.listRequest++;
    this.list.set(EMPTY_LIST);
  }

  /** Abre o detalle dunha reserva e pide o completo (correo, accións posibles, versión). */
  async open(slug: string, id: string): Promise<void> {
    const request = ++this.detailRequest;
    this.detail.set({ id, state: 'loading', detail: null });
    try {
      const detail = await this.api.invoke(getBooking, { slug, id });
      // As etiquetas dos campos propios, a primeira vez que unha reserva trae algún.
      if (this.fieldsOf !== slug && Object.keys(detail.customFields ?? {}).length) {
        this.fieldsOf = slug;
        void this.bookingFields.load(slug).then((list) => this.fields.set(list));
      }
      if (request === this.detailRequest) this.detail.set({ id, state: 'ready', detail });
    } catch {
      if (request === this.detailRequest) this.detail.set({ id, state: 'error', detail: null });
    }
  }

  close(): void {
    this.detailRequest++;
    this.detail.set(null);
  }

  /** Marca o cobro (P13: tamén desfacer). */
  async setPayment(slug: string, booking: ManagedBookingDetailResponse, status: PaymentStatus): Promise<void> {
    const updated = await this.api.invoke(changeBookingPayment, {
      slug,
      id: booking.id,
      'If-Match': `"${booking.version}"`,
      body: { status },
    });
    this.apply(slug, updated);
  }

  /** Cancelación polo concello, coas canles polas que se avisa (P12). */
  async cancel(slug: string, booking: ManagedBookingDetailResponse, channels: CancelChannel[]): Promise<void> {
    const updated = await this.api.invoke(cancelBooking, {
      slug,
      id: booking.id,
      'If-Match': `"${booking.version}"`,
      body: { channels },
    });
    this.apply(slug, updated);
  }

  /**
   * A resposta pasa ao detalle e á fila da lista; a portada, se se usa (non no calendario), pídese
   * de novo polas contas.
   */
  private apply(slug: string, updated: ManagedBookingDetailResponse): void {
    this.updated.set(updated);
    if (this.detail()?.id === updated.id) this.detail.set({ id: updated.id, state: 'ready', detail: updated });
    this.list.update((value) => ({
      ...value,
      items: value.items.map((item) => (item.id === updated.id ? asRow(updated, item) : item)),
    }));
    if (this.overviewState() !== 'idle') void this.loadOverview(slug);
  }
}

/** A fila da lista a partir do detalle novo (mesmos campos; os do detalle quedan fóra). */
function asRow(detail: ManagedBookingDetailResponse, row: ManagedBookingResponse): ManagedBookingResponse {
  return {
    ...row,
    status: detail.status,
    paymentStatus: detail.paymentStatus,
    price: detail.price,
    done: detail.done,
    version: detail.version,
    ...(detail.cancelledAt ? { cancelledAt: detail.cancelledAt } : {}),
  };
}
