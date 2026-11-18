// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { listFacilities } from '../../../api/fn/facilities/list-facilities';
import { updateFacilityAddress } from '../../../api/fn/facilities/update-facility-address';
import type { AddressRequest } from '../../../api/models/address-request';
import type { MapPoint } from '../../../shared/ui/map/map-point';
import { pointOf } from '../../../shared/ui/map/map-point';
import type { Facility, FacilityTarget } from '../detail/facility-detail.store';
import type { AddressMatch, AddressSuggestion } from './address-search';
import { ADDRESS_SEARCH } from './address-search';

export type { AddressRequest };

/** Instalacións que se len para centrar o mapa (o máximo dunha páxina da API). */
const AREA_PAGE = 100;

/**
 * Localización da instalación: `PUT …/facilities/{id}/address` (a versión ponna a cola da ficha),
 * a busca de enderezos do concello e onde están as outras instalacións (para centrar o mapa).
 */
@Injectable({ providedIn: 'root' })
export class LocationStore {
  private readonly api = inject(Api);
  private readonly search = inject(ADDRESS_SEARCH);

  update({ slug, id, ifMatch }: FacilityTarget, body: AddressRequest): Promise<Facility> {
    return this.api.invoke(updateFacilityAddress, { slug, id, 'If-Match': ifMatch, body });
  }

  suggest(query: string): Promise<readonly AddressSuggestion[]> {
    return this.search.suggest(query);
  }

  resolve(id: string): Promise<AddressMatch> {
    return this.search.resolve(id);
  }

  /**
   * Os puntos das instalacións do concello, para centrar nel o mapa dunha instalación sen punto.
   * O concello non garda coordenadas propias: sen instalacións con punto (ou se falla), ningún.
   */
  async facilityPoints(slug: string): Promise<readonly MapPoint[]> {
    try {
      const page = await this.api.invoke(listFacilities, { slug, page: 0, size: AREA_PAGE });
      return page.items.map((f) => pointOf(f.address)).filter((p): p is MapPoint => p !== null);
    } catch {
      return [];
    }
  }
}
