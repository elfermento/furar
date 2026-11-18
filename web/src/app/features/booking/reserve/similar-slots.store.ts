// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { listMySimilarSlots } from '../../../api/fn/bookings/list-my-similar-slots';
import { listSimilarSlots } from '../../../api/fn/bookings/list-similar-slots';
import type { SimilarSlotResponse } from '../../../api/models/similar-slot-response';

export type { SimilarSlotResponse };

/** A hora ocupada á que se lle buscan ocos parecidos. */
export interface SimilarQuery {
  readonly slug: string;
  readonly facilityId: string;
  readonly useId: string;
  readonly start: string;
  readonly slots: number;
  /** Con sesión, tamén coas normas da persoa. */
  readonly mine: boolean;
}

/**
 * «Ocos parecidos» a unha hora ocupada (ADR-0033, punto 9): `GET …/similar-slots`, coa sesión
 * (`…/me/…`, tamén coas normas da persoa) ou sen ela. Só se pide ao escoller unha ocupada e só
 * vale a última petición. Un por páxina.
 */
@Injectable()
export class SimilarSlotsStore {
  private readonly api = inject(Api);
  private request = 0;

  readonly items = signal<readonly SimilarSlotResponse[]>([]);
  readonly state = signal<'idle' | 'loading' | 'ready' | 'error'>('idle');

  async load(query: SimilarQuery): Promise<void> {
    const request = ++this.request;
    this.items.set([]);
    this.state.set('loading');
    const params = {
      slug: query.slug,
      id: query.facilityId,
      useId: query.useId,
      start: query.start,
      slots: query.slots,
    };
    try {
      const found = await this.api.invoke(query.mine ? listMySimilarSlots : listSimilarSlots, params);
      if (request !== this.request) return;
      this.items.set(found.items);
      this.state.set('ready');
    } catch {
      if (request !== this.request) return;
      this.state.set('error');
    }
  }

  /** Xa non hai hora ocupada escollida. */
  clear(): void {
    this.request++;
    this.items.set([]);
    this.state.set('idle');
  }
}
