// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { listFacilitiesAvailability } from '../../../api/fn/bookings/list-facilities-availability';
import { listPublicFacilities } from '../../../api/fn/facilities/list-public-facilities';
import type { PublicFacilityResponse } from '../../../api/models/public-facility-response';
import type { SportResponse } from '../../../api/models/sport-response';

export type { PublicFacilityResponse, SportResponse };

/** Instalacións por páxina de ocos (a API admite ata 50; 12 é unha reixa). */
export const AVAILABILITY_PAGE = 12;
/** Tope de páxinas de ocos que se piden seguidas (600 instalacións con reserva por internet). */
const AVAILABILITY_PAGES_MAX = 50;

/** Acceso á API da portada (único sitio da portada que usa o cliente xerado). */
@Injectable({ providedIn: 'root' })
export class HomeStore {
  private readonly api = inject(Api);

  /** Instalacións publicadas do concello, na orde alfabética do idioma (Accept-Language). */
  facilities(slug: string): Promise<PublicFacilityResponse[]> {
    return this.api.invoke(listPublicFacilities, { slug });
  }

  /**
   * Ocos libres dun día por instalación que se reserva por internet (`GET …/public/availability`):
   * id → cantos. Pide as páxinas seguidas ata que unha vén incompleta.
   */
  async freeSlots(slug: string, date: string): Promise<Map<string, number>> {
    const free = new Map<string, number>();
    for (let page = 0; page < AVAILABILITY_PAGES_MAX; page++) {
      const response = await this.api.invoke(listFacilitiesAvailability, { slug, date, page, size: AVAILABILITY_PAGE });
      for (const item of response.items) free.set(item.facilityId, item.freeCount);
      if (response.items.length < AVAILABILITY_PAGE) break;
    }
    return free;
  }
}
