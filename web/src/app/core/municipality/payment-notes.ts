// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Api } from '../../api/api';
import { getFacilityPaymentNotes } from '../../api/fn/facilities/get-facility-payment-notes';
import { getPaymentNotes } from '../../api/fn/municipalities/get-payment-notes';
import type { FacilityPaymentNotesResponse } from '../../api/models/facility-payment-notes-response';
import type { NoteResponse } from '../../api/models/note-response';
import type { PaymentNotesResponse } from '../../api/models/payment-notes-response';

/** «Como se paga» e «Como se devolve» que ve a persoa nunha instalación. */
export interface PaymentTexts {
  readonly payment: NoteResponse | null;
  readonly refund: NoteResponse | null;
}

export const NO_PAYMENT_TEXTS: PaymentTexts = { payment: null, refund: null };

/**
 * De onde sae cada texto (D2-11, revisión do 2026-11-03): «Igual para todas as instalacións», os
 * do concello (ou os de Furar, se non puxo); «Distinto por instalación», os da ficha e, se non ten,
 * os de Furar (nunca os do concello). Os textos nunca van no código: saen da API.
 */
export function effectivePaymentTexts(
  municipality: PaymentNotesResponse | null,
  facility: FacilityPaymentNotesResponse | null,
): PaymentTexts {
  if (!municipality) return NO_PAYMENT_TEXTS;
  if (municipality.scope !== 'FACILITY') return { payment: municipality.payment, refund: municipality.refund };
  return {
    payment: facility?.payment ?? municipality.furarPayment,
    refund: facility?.refund ?? municipality.furarRefund,
  };
}

/**
 * Le os textos que tocan para unha instalación: os do concello e, se van por instalación, os
 * dela. Se algo non chega, o que se saiba (sen textos, a pantalla segue igual).
 */
export async function loadPaymentTexts(api: Api, slug: string, facilityId: string): Promise<PaymentTexts> {
  let municipality: PaymentNotesResponse;
  try {
    municipality = await api.invoke(getPaymentNotes, { slug });
  } catch {
    return NO_PAYMENT_TEXTS;
  }
  if (municipality.scope !== 'FACILITY') return effectivePaymentTexts(municipality, null);
  const facility = await api.invoke(getFacilityPaymentNotes, { slug, id: facilityId }).catch(() => null);
  return effectivePaymentTexts(municipality, facility);
}
