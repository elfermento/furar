// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { updateFacilityAmenities } from '../../../api/fn/facilities/update-facility-amenities';
import type { Facility, FacilityTarget } from '../detail/facility-detail.store';
import type { Amenity } from './amenities';

/** `PUT …/facilities/{id}/amenities` (lista enteira). A versión ponna a cola da ficha. */
@Injectable({ providedIn: 'root' })
export class AmenitiesStore {
  private readonly api = inject(Api);

  update({ slug, id, ifMatch }: FacilityTarget, amenities: Amenity[]): Promise<Facility> {
    return this.api.invoke(updateFacilityAmenities, { slug, id, 'If-Match': ifMatch, body: { amenities } });
  }
}
