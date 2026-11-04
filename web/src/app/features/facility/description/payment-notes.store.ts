// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { updateFacilityPaymentNotes } from '../../../api/fn/facilities/update-facility-payment-notes';
import { getPaymentNotes } from '../../../api/fn/municipalities/get-payment-notes';
import type { PaymentNotesResponse } from '../../../api/models/payment-notes-response';
import type { UpdateFacilityPaymentNotesRequest } from '../../../api/models/update-facility-payment-notes-request';
import type { Facility, FacilityTarget } from '../detail/facility-detail.store';

export type { PaymentNotesResponse };

/**
 * Pago e devolución da instalación (D2-11): se o concello os pon por instalación (`GET
 * …/payment-notes`, `scope`; unha vez por ficha, aínda que a ficha se recargue) e o `PUT
 * …/facilities/{id}/payment-notes` (a versión ponna a cola da ficha, `FacilityDetailStore`). Un por
 * ficha.
 */
@Injectable()
export class PaymentNotesStore {
  private readonly api = inject(Api);
  private notes: Promise<PaymentNotesResponse | null> | null = null;

  /** Os textos do concello (co ámbito e os de Furar); `null` se non chegan. */
  municipalityNotes(slug: string): Promise<PaymentNotesResponse | null> {
    this.notes ??= this.api.invoke(getPaymentNotes, { slug }).catch(() => null);
    return this.notes;
  }

  update({ slug, id, ifMatch }: FacilityTarget, body: UpdateFacilityPaymentNotesRequest): Promise<Facility> {
    return this.api.invoke(updateFacilityPaymentNotes, { slug, id, 'If-Match': ifMatch, body });
  }
}
