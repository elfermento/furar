// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../../api/api';
import { listFacilitiesAvailability } from '../../../../api/fn/bookings/list-facilities-availability';
import { listPublicFacilities } from '../../../../api/fn/facilities/list-public-facilities';
import type { FacilityFreeSlotsResponse } from '../../../../api/models/facility-free-slots-response';
import type { PublicFacilityResponse } from '../../../../api/models/public-facility-response';

export type { FacilityFreeSlotsResponse, PublicFacilityResponse };

/** Instalacións por páxina de ocos (a API admite ata 50; 12 é unha reixa). */
export const AVAILABILITY_PAGE = 12;
/** Tope de páxinas de ocos que se piden seguidas (600 instalacións con reserva por internet). */
const AVAILABILITY_PAGES_MAX = 50;

/**
 * API do listado público (maqueta 16): as instalacións publicadas (co punto do mapa no enderezo) e
 * os ocos dun día por instalación (`GET …/public/availability`, por páxinas de 12).
 */
@Injectable()
export class PublicListStore {
  private readonly api = inject(Api);

  /** Instalacións publicadas, na orde alfabética do idioma (Accept-Language). */
  facilities(slug: string): Promise<PublicFacilityResponse[]> {
    return this.api.invoke(listPublicFacilities, { slug });
  }

  /** Ocos libres do día por instalación que se reserva por internet (id → cantos e as tres primeiras horas). */
  async availability(slug: string, date: string): Promise<Map<string, FacilityFreeSlotsResponse>> {
    const free = new Map<string, FacilityFreeSlotsResponse>();
    for (let page = 0; page < AVAILABILITY_PAGES_MAX; page++) {
      const response = await this.api.invoke(listFacilitiesAvailability, { slug, date, page, size: AVAILABILITY_PAGE });
      for (const item of response.items) free.set(item.facilityId, item);
      if (response.items.length < AVAILABILITY_PAGE) break;
    }
    return free;
  }
}
