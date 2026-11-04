// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { changeFacilityStatus } from '../../../api/fn/facilities/change-facility-status';
import { createFacility } from '../../../api/fn/facilities/create-facility';
import { listFacilities } from '../../../api/fn/facilities/list-facilities';
import type { CreateFacilityRequest } from '../../../api/models/create-facility-request';
import type { FacilityResponse } from '../../../api/models/facility-response';
import type { FacilitySummaryResponse } from '../../../api/models/facility-summary-response';
import { ApiProblem } from '../../../core/errors/api-problem';
import type { BookingDecision } from '../affected-bookings/affected-bookings.store';

export type FacilitySummary = FacilitySummaryResponse;
export type FacilityStatus = FacilitySummary['status'];
export type { CreateFacilityRequest };

/** O máximo que acepta a API por páxina. Un concello ten poucas instalacións: cárganse todas. */
export const PAGE_SIZE = 100;

export type ListState = 'loading' | 'ready' | 'error';

/** O que precisa o listado dunha instalación, sacado da ficha enteira que devolven os cambios. */
export function toSummary(facility: FacilityResponse): FacilitySummary {
  const { id, name, address, status, missing, version, published } = facility;
  const cover = facility.images[0] ?? facility.suggestedImage ?? null;
  return { id, name, address, status, missing, version, published, sports: facility.details.sports, cover };
}

/**
 * Listado de instalacións (D1-20): todas, na orde do servidor (alfabética no idioma de quen
 * mira, `Accept-Language`), cambio de estado coa `version` da tarxeta e alta. Un por páxina.
 */
@Injectable()
export class FacilityStore {
  private readonly api = inject(Api);
  private readonly list = signal<readonly FacilitySummary[]>([]);
  private readonly loadState = signal<ListState>('loading');
  private readonly loadError = signal<ApiProblem | null>(null);
  /** Para que unha carga vella non pise a nova (p. ex. ao cambiar de idioma). */
  private loads = 0;

  readonly facilities = this.list.asReadonly();
  readonly state = this.loadState.asReadonly();
  readonly problem = this.loadError.asReadonly();

  /** Carga todas as páxinas. Con `quiet`, o listado actual queda á vista mentres tanto. */
  async load(slug: string, { quiet = false } = {}): Promise<void> {
    const load = ++this.loads;
    if (!quiet) this.loadState.set('loading');
    this.loadError.set(null);
    try {
      const items: FacilitySummary[] = [];
      for (let page = 0; ; page++) {
        const result = await this.api.invoke(listFacilities, { slug, page, size: PAGE_SIZE });
        items.push(...result.items);
        if (result.items.length < PAGE_SIZE || items.length >= result.totalItems) break;
      }
      if (load !== this.loads) return;
      this.list.set(items);
      this.loadState.set('ready');
    } catch (error) {
      if (load !== this.loads) return;
      this.loadError.set(error instanceof ApiProblem ? error : null);
      this.loadState.set('error');
    }
  }

  /**
   * Cambia o estado dunha instalación co `If-Match` da súa tarxeta. `decisions` só no segundo
   * paso do modal de reservas afectadas. Ao gardar, a tarxeta pasa á versión nova.
   */
  async changeStatus(
    slug: string,
    facility: FacilitySummary,
    status: FacilityStatus,
    decisions?: BookingDecision[],
  ): Promise<FacilitySummary> {
    const current = this.list().find((f) => f.id === facility.id) ?? facility;
    const saved = toSummary(
      await this.api.invoke(changeFacilityStatus, {
        slug,
        id: facility.id,
        'If-Match': `"${current.version}"`,
        body: { status, decisions },
      }),
    );
    this.list.update((all) => all.map((f) => (f.id === saved.id && saved.version >= f.version ? saved : f)));
    return saved;
  }

  /** Alta con nome e enderezo: a instalación nace en borrador. */
  create(slug: string, body: CreateFacilityRequest): Promise<FacilityResponse> {
    return this.api.invoke(createFacility, { slug, body });
  }
}
