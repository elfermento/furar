// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { getPublicFacility } from '../../../api/fn/facilities/get-public-facility';
import { loadPaymentTexts } from '../../../core/municipality/payment-notes';
import type { NoteResponse } from '../../../api/models/note-response';
import type { PublicFacilityDetailResponse } from '../../../api/models/public-facility-detail-response';
import { ApiProblem } from '../../../core/errors/api-problem';

export type PublicFacility = PublicFacilityDetailResponse;
export type PublicUse = PublicFacility['uses'][number];
export type OpeningDay = PublicFacility['openingHours'][number];

/**
 * Ficha pública dunha instalación (`GET …/public/facilities/{id}`, sen sesión) e a nota de pago:
 * a do concello (`GET …/payment-notes`) ou, se o concello as pon por instalación, a dela (`GET
 * …/public/facilities/{id}/payment-notes`); `facility` non depende de `municipality`, así que a web
 * le as dúas. Se a nota non chega, a ficha vese igual, sen ela. Un por páxina.
 */
@Injectable()
export class PublicFacilityStore {
  private readonly api = inject(Api);
  private readonly current = signal<PublicFacility | null>(null);
  private readonly loadState = signal<'loading' | 'ready' | 'error'>('loading');
  private readonly loadError = signal<ApiProblem | null>(null);
  private readonly payment = signal<NoteResponse | null>(null);

  readonly facility = this.current.asReadonly();
  readonly state = this.loadState.asReadonly();
  readonly problem = this.loadError.asReadonly();
  readonly paymentNote = this.payment.asReadonly();

  async load(slug: string, id: string): Promise<void> {
    this.loadState.set('loading');
    this.loadError.set(null);
    // A nota do concello ou, se van por instalación, a desta instalación (D2-11).
    const notes = loadPaymentTexts(this.api, slug, id).then((texts) => this.payment.set(texts.payment));
    try {
      this.current.set(await this.api.invoke(getPublicFacility, { slug, id }));
      await notes;
      this.loadState.set('ready');
    } catch (error) {
      this.loadError.set(error instanceof ApiProblem ? error : null);
      this.loadState.set('error');
    }
  }
}
