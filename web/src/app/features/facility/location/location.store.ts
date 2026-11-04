// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { updateFacilityAddress } from '../../../api/fn/facilities/update-facility-address';
import type { AddressRequest } from '../../../api/models/address-request';
import type { Facility, FacilityTarget } from '../detail/facility-detail.store';
import type { AddressMatch, AddressSuggestion } from './address-search';
import { ADDRESS_SEARCH } from './address-search';

export type { AddressRequest };

/**
 * Localización da instalación: `PUT …/facilities/{id}/address` (a versión ponna a cola da ficha)
 * e a busca de enderezos do concello.
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
}
