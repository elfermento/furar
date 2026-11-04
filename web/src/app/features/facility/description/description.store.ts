// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { updateFacilityDescription } from '../../../api/fn/facilities/update-facility-description';
import { updateFacilityHouseRules } from '../../../api/fn/facilities/update-facility-house-rules';
import type { UpdateFacilityDescriptionRequest } from '../../../api/models/update-facility-description-request';
import type { Facility, FacilityTarget } from '../detail/facility-detail.store';

export type DescriptionRequest = UpdateFacilityDescriptionRequest;

/**
 * `PUT …/facilities/{id}/description` e `…/house-rules`. A versión ponna a cola da ficha
 * (`FacilityDetailStore`).
 */
@Injectable({ providedIn: 'root' })
export class DescriptionStore {
  private readonly api = inject(Api);

  update({ slug, id, ifMatch }: FacilityTarget, body: DescriptionRequest): Promise<Facility> {
    return this.api.invoke(updateFacilityDescription, { slug, id, 'If-Match': ifMatch, body });
  }

  updateHouseRules({ slug, id, ifMatch }: FacilityTarget, body: DescriptionRequest): Promise<Facility> {
    return this.api.invoke(updateFacilityHouseRules, { slug, id, 'If-Match': ifMatch, body });
  }
}
