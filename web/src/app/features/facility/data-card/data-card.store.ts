// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { listCustomFields } from '../../../api/fn/custom-fields/list-custom-fields';
import { createSport } from '../../../api/fn/facilities/create-sport';
import { listSports } from '../../../api/fn/facilities/list-sports';
import { renameFacility } from '../../../api/fn/facilities/rename-facility';
import { updateFacilityDetails } from '../../../api/fn/facilities/update-facility-details';
import type { SportResponse } from '../../../api/models/sport-response';
import type { UpdateFacilityDetailsRequest } from '../../../api/models/update-facility-details-request';
import type { CustomFieldDefinition } from '../../../shared/custom-fields/custom-field.types';
import { forTarget } from '../../../shared/custom-fields/custom-field.types';
import type { Facility, FacilityTarget } from '../detail/facility-detail.store';

export type { SportResponse };
export type DetailsRequest = UpdateFacilityDetailsRequest;

export const FACILITY_TARGET = 'FACILITY';

/**
 * Peticións da tarxeta Datos (D1-21d): `PUT …/facilities/{id}/name` e `PUT …/details` (a versión
 * ponna a cola da ficha), o catálogo de deportes co do concello (`GET/POST …/sports`) e os campos propios das
 * instalacións (`GET …/custom-fields?target=FACILITY`, co obrigatorio e a visibilidade do uso).
 */
@Injectable({ providedIn: 'root' })
export class DataCardStore {
  private readonly api = inject(Api);

  update({ slug, id, ifMatch }: FacilityTarget, body: DetailsRequest): Promise<Facility> {
    return this.api.invoke(updateFacilityDetails, { slug, id, 'If-Match': ifMatch, body });
  }

  rename({ slug, id, ifMatch }: FacilityTarget, name: string): Promise<Facility> {
    return this.api.invoke(renameFacility, { slug, id, 'If-Match': ifMatch, body: { name } });
  }

  sports(slug: string): Promise<SportResponse[]> {
    return this.api.invoke(listSports, { slug });
  }

  createSport(slug: string, label: { gl: string; es: string }): Promise<SportResponse> {
    return this.api.invoke(createSport, { slug, body: label });
  }

  async definitions(slug: string): Promise<CustomFieldDefinition[]> {
    const list = await this.api.invoke(listCustomFields, { slug, target: FACILITY_TARGET });
    return list.map((definition) => forTarget(definition, FACILITY_TARGET));
  }
}
