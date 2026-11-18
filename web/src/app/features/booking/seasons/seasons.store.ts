// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, Injector, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { approveSeriesRequest } from '../../../api/fn/bookings/approve-series-request';
import { changeBookingSeriesPayment } from '../../../api/fn/bookings/change-booking-series-payment';
import { createBookingSeries } from '../../../api/fn/bookings/create-booking-series';
import { createOrganization } from '../../../api/fn/bookings/create-organization';
import { deleteOrganization } from '../../../api/fn/bookings/delete-organization';
import { endBookingSeries } from '../../../api/fn/bookings/end-booking-series';
import { getBooking } from '../../../api/fn/bookings/get-booking';
import { getBookingSeries } from '../../../api/fn/bookings/get-booking-series';
import { getOrganization } from '../../../api/fn/bookings/get-organization';
import { getSeriesRequest } from '../../../api/fn/bookings/get-series-request';
import { listBookingSeries } from '../../../api/fn/bookings/list-booking-series';
import { listBookings } from '../../../api/fn/bookings/list-bookings';
import { listOrganizations } from '../../../api/fn/bookings/list-organizations';
import { listSeriesRequests } from '../../../api/fn/bookings/list-series-requests';
import { previewBookingSeries } from '../../../api/fn/bookings/preview-booking-series';
import { rejectSeriesRequest } from '../../../api/fn/bookings/reject-series-request';
import { updateOrganization } from '../../../api/fn/bookings/update-organization';
import type { CreateSeriesRequest } from '../../../api/models/create-series-request';
import type { ManagedBookingDetailResponse } from '../../../api/models/managed-booking-detail-response';
import type { ManagedBookingResponse } from '../../../api/models/managed-booking-response';
import type { OrganizationResponse } from '../../../api/models/organization-response';
import type { OrganizationRowResponse } from '../../../api/models/organization-row-response';
import type { SeriesPreviewResponse } from '../../../api/models/series-preview-response';
import type { SeriesRequestCardResponse } from '../../../api/models/series-request-card-response';
import type { SeriesResponse } from '../../../api/models/series-response';
import { PendingSeriesRequests } from '../../../core/organizations/pending-series-requests';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import type { SeriesPayment } from './seasons';

import type { OrganizationRequest } from '../../../api/models/organization-request';
import type { SeriesConflictResponse } from '../../../api/models/series-conflict-response';
import type { SeriesRequestRequest } from '../../../api/models/series-request-request';

export type {
  CreateSeriesRequest,
  ManagedBookingResponse,
  OrganizationRequest,
  OrganizationResponse,
  OrganizationRowResponse,
  SeriesConflictResponse,
  SeriesPreviewResponse,
  SeriesRequestCardResponse,
  SeriesRequestRequest,
  SeriesResponse,
};

/** Cantas sesións próximas se piden de cada vez para o detalle dunha tempada (o máximo da busca). */
export const SESSIONS_SIZE = 50;

/**
 * As entidades e as súas tempadas na xestión (ADR-0039, permiso das entidades): as
 * entidades, as solicitudes, a vista previa, crear (cunha `Idempotency-Key` por intención, coma
 * reservar), rematar e o pago dunha tempada, e as sesións próximas dunha tempada. Un por páxina.
 */
@Injectable()
export class SeasonsStore {
  private readonly api = inject(Api);
  private readonly injector = inject(Injector);

  /** A intención de crear (ou aprobar) aberta: o que se pide e a súa clave. */
  private intent: { request: string; key: string } | null = null;
  private seriesLoaded = false;

  readonly organizations = signal<readonly OrganizationRowResponse[]>([]);
  readonly organizationsState = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');
  readonly requests = signal<readonly SeriesRequestCardResponse[]>([]);
  readonly activeSeries = signal<readonly SeriesResponse[]>([]);

  /**
   * Cunha lista xa á vista (tras gardar ou eliminar), actualízaa sen pasar por «cargando»: as filas
   * seguen no DOM e o foco pode volver á que abriu o diálogo (WCAG 2.4.3).
   */
  async loadOrganizations(slug: string): Promise<void> {
    if (this.organizationsState() !== 'ready') this.organizationsState.set('loading');
    try {
      const [organizations, requests] = await Promise.all([
        this.api.invoke(listOrganizations, { slug, archived: false }),
        this.api.invoke(listSeriesRequests, { slug, status: 'PENDING' }),
      ]);
      this.organizations.set(organizations);
      this.requests.set(requests);
      this.organizationsState.set('ready');
    } catch {
      this.organizationsState.set('error');
    }
  }

  getOrganization(slug: string, id: string): Promise<OrganizationResponse> {
    return this.api.invoke(getOrganization, { slug, id });
  }

  async saveOrganization(
    slug: string,
    current: OrganizationResponse | null,
    body: OrganizationRequest,
  ): Promise<OrganizationResponse> {
    const saved = current
      ? await this.api.invoke(updateOrganization, { slug, id: current.id, 'If-Match': `"${current.version}"`, body })
      : await this.api.invoke(createOrganization, { slug, body });
    void this.loadOrganizations(slug);
    return saved;
  }

  /**
   * Eliminada (ou arquivada, se tiña historia), a súa fila sae xa da lista, sen agardar a recarga:
   * así, ao pechar o diálogo, a páxina sabe que o foco quedou sen sitio e lévao ao título.
   */
  async deleteOrganization(slug: string, organization: OrganizationResponse): Promise<void> {
    await this.api.invoke(deleteOrganization, { slug, id: organization.id, 'If-Match': `"${organization.version}"` });
    this.organizations.update((list) => list.filter((row) => row.id !== organization.id));
    void this.loadOrganizations(slug);
    this.refreshPending();
  }

  getRequest(slug: string, id: string): Promise<SeriesRequestCardResponse> {
    return this.api.invoke(getSeriesRequest, { slug, id });
  }

  async reject(slug: string, id: string, message: string): Promise<SeriesRequestCardResponse> {
    const rejected = await this.api.invoke(rejectSeriesRequest, { slug, id, body: { message } });
    this.refreshPending();
    return rejected;
  }

  preview(slug: string, body: Omit<CreateSeriesRequest, 'skip'>): Promise<SeriesPreviewResponse> {
    return this.api.invoke(previewBookingSeries, { slug, body });
  }

  /** Crea a tempada ou, con `requestId`, aproba a solicitude creándoa. */
  async create(slug: string, body: CreateSeriesRequest, requestId: string | null = null): Promise<SeriesResponse> {
    const request = JSON.stringify([requestId, body]);
    if (this.intent?.request !== request) this.intent = { request, key: globalThis.crypto.randomUUID() };
    const key = this.intent.key;
    try {
      const series = requestId
        ? await this.api.invoke(approveSeriesRequest, { slug, id: requestId, 'Idempotency-Key': key, body })
        : await this.api.invoke(createBookingSeries, { slug, 'Idempotency-Key': key, body });
      this.intent = null;
      if (requestId) this.refreshPending();
      return series;
    } catch (error) {
      this.intent = null;
      throw error;
    }
  }

  /** As tempadas en curso: pídense unha vez por páxina (a portada de «Reservas» vai e volve). */
  async loadSeries(slug: string): Promise<void> {
    if (this.seriesLoaded) return;
    this.seriesLoaded = true;
    try {
      this.activeSeries.set(await this.api.invoke(listBookingSeries, { slug, status: 'ACTIVE' }));
    } catch {
      this.seriesLoaded = false;
      this.activeSeries.set([]);
    }
  }

  /** Unha tempada cambiou (o pago, as sesións); rematada, sae das en curso. */
  seriesChanged(updated: SeriesResponse): void {
    this.activeSeries.update((items) =>
      updated.endedFrom
        ? items.filter((item) => item.id !== updated.id)
        : items.map((item) => (item.id === updated.id ? updated : item)),
    );
  }

  getSeries(slug: string, id: string): Promise<SeriesResponse> {
    return this.api.invoke(getBookingSeries, { slug, id });
  }

  setPayment(slug: string, series: SeriesResponse, status: SeriesPayment): Promise<SeriesResponse> {
    return this.api.invoke(changeBookingSeriesPayment, {
      slug,
      id: series.id,
      'If-Match': `"${series.version}"`,
      body: { status },
    });
  }

  end(slug: string, series: SeriesResponse, from: IsoDate): Promise<SeriesResponse> {
    return this.api.invoke(endBookingSeries, { slug, id: series.id, body: { from } });
  }

  /**
   * As sesións confirmadas que quedan dunha tempada: a busca de sempre (o nome da entidade, a
   * instalación e desde hoxe), páxina a páxina ata o final, quedando só as desta serie.
   */
  async sessions(slug: string, series: SeriesResponse, today: IsoDate): Promise<ManagedBookingResponse[]> {
    const found: ManagedBookingResponse[] = [];
    for (let page = 0; ; page++) {
      const result = await this.api.invoke(listBookings, {
        slug,
        q: series.organizationName,
        facilityId: [series.facilityId],
        status: ['CONFIRMED'],
        from: today,
        sort: 'START',
        page,
        size: SESSIONS_SIZE,
      });
      found.push(...result.items);
      if (!result.items.length || (page + 1) * SESSIONS_SIZE >= result.totalItems) break;
    }
    return found.filter((booking) => booking.organization?.seriesId === series.id);
  }

  /** O detalle dunha reserva (para cancelala co diálogo de sempre). */
  booking(slug: string, id: string): Promise<ManagedBookingDetailResponse> {
    return this.api.invoke(getBooking, { slug, id });
  }

  /** O número da pestana «Entidades» (o contador só se crea cando fai falta). */
  private refreshPending(): void {
    this.injector.get(PendingSeriesRequests).refresh();
  }
}
