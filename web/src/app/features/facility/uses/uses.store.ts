// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { archiveFacilityUse } from '../../../api/fn/facilities/archive-facility-use';
import { createFacilityUse } from '../../../api/fn/facilities/create-facility-use';
import { listFacilityUses } from '../../../api/fn/facilities/list-facility-uses';
import { updateFacilityUse } from '../../../api/fn/facilities/update-facility-use';
import type { SaveUseRequest } from '../../../api/models/save-use-request';
import type { UseResponse } from '../../../api/models/use-response';
import { ApiProblem } from '../../../core/errors/api-problem';
import type { BookingDecision } from '../affected-bookings/affected-bookings.store';
import type { LoadState } from '../detail/facility-detail.store';
import { VersionedElements } from '../detail/versioned-elements';

export type Use = UseResponse;
export type UsePriceBody = NonNullable<SaveUseRequest['price']>;
export type UseRulesBody = NonNullable<SaveUseRequest['rules']>;
export type UseRules = UseResponse['rules'];
export type UseBody = Omit<SaveUseRequest, 'decisions'>;
export type RentalMode = Use['rentalMode'];
export type Availability = Use['availability'];

/**
 * Usos dunha instalación (D1-22): a lista viva da API, cada uso coa súa versión
 * (`VersionedElements`, contrato de estado da ficha). Un por sección (`providers` da lista). É o
 * único sitio da sección que fai HTTP, sempre co cliente xerado.
 */
@Injectable()
export class UsesStore {
  private readonly api = inject(Api);
  private readonly elements = new VersionedElements<Use>();
  private readonly loadState = signal<LoadState>('loading');
  private where: { slug: string; facilityId: string } | null = null;

  readonly uses = this.elements.list;
  readonly empty = this.elements.empty;
  readonly state = this.loadState.asReadonly();

  async load(slug: string, facilityId: string): Promise<void> {
    this.where = { slug, facilityId };
    this.loadState.set('loading');
    try {
      this.elements.set(await this.api.invoke(listFacilityUses, { slug, facilityId }));
      this.loadState.set('ready');
    } catch {
      this.loadState.set('error');
    }
  }

  /** Volve ler a lista (tras un 412): os formularios abertos conservan o escrito. */
  async reload(): Promise<void> {
    const { slug, facilityId } = this.target();
    this.elements.set(await this.api.invoke(listFacilityUses, { slug, facilityId }));
  }

  get(id: string): Use | undefined {
    return this.elements.get(id);
  }

  create(body: UseBody, decisions?: BookingDecision[]): Promise<Use> {
    const { slug, facilityId } = this.target();
    return this.elements.create(() =>
      this.api.invoke(createFacilityUse, { slug, facilityId, body: { ...body, decisions } }),
    );
  }

  update(id: string, body: UseBody, decisions?: BookingDecision[]): Promise<Use> {
    const { slug, facilityId } = this.target();
    return this.elements.save(id, (ifMatch) =>
      this.api.invoke(updateFacilityUse, {
        slug,
        facilityId,
        useId: id,
        'If-Match': ifMatch,
        body: { ...body, decisions },
      }),
    );
  }

  /** Quitar = arquivar: sae da lista (as reservas do dominio 2 seguen apuntando a el). */
  archive(id: string): Promise<void> {
    const { slug, facilityId } = this.target();
    return this.elements.remove(id, (ifMatch) =>
      this.api.invoke(archiveFacilityUse, { slug, facilityId, useId: id, 'If-Match': ifMatch }),
    );
  }

  private target(): { slug: string; facilityId: string } {
    if (!this.where) throw new ApiProblem(0, 'common.unexpected', {}, [], undefined, '');
    return this.where;
  }
}
