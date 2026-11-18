// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../api/api';
import { getPublicFacility } from '../../api/fn/facilities/get-public-facility';
import { listPublicFacilities } from '../../api/fn/facilities/list-public-facilities';
import type { PublicFacilityDetailResponse } from '../../api/models/public-facility-detail-response';
import type { PublicFacilityResponse } from '../../api/models/public-facility-response';

export type { PublicFacilityDetailResponse, PublicFacilityResponse };

/**
 * As instalacións do formulario dun horario (unha tempada ou unha actividade): as que se poden reservar (a lista pública, igual
 * para a xestión e para a entidade) e, da escollida, os seus usos e partes (a ficha pública). Se
 * falla, sen opcións. Un por formulario.
 */
@Injectable()
export class ScheduleFacilitiesStore {
  private readonly api = inject(Api);

  readonly facilities = signal<readonly PublicFacilityResponse[]>([]);
  readonly facility = signal<PublicFacilityDetailResponse | null>(null);
  /** A última pedida: a resposta dunha anterior que chega tarde non se pinta. */
  private wanted: string | null = null;

  async loadAll(slug: string): Promise<void> {
    try {
      this.facilities.set(await this.api.invoke(listPublicFacilities, { slug }));
    } catch {
      this.facilities.set([]);
    }
  }

  /** A ficha da instalación (a que xa hai, se é a mesma); `null` sen instalación ou se falla. */
  async load(slug: string, facilityId: string | null): Promise<PublicFacilityDetailResponse | null> {
    this.wanted = facilityId;
    if (!facilityId) {
      this.facility.set(null);
      return null;
    }
    const current = this.facility();
    if (current?.id === facilityId) return current;
    try {
      const facility = await this.api.invoke(getPublicFacility, { slug, id: facilityId });
      if (this.wanted !== facilityId) return null;
      this.facility.set(facility);
      return facility;
    } catch {
      if (this.wanted === facilityId) this.facility.set(null);
      return null;
    }
  }
}
