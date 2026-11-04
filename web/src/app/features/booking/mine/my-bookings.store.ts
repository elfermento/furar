// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { WritableSignal } from '@angular/core';
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { cancelMyBooking } from '../../../api/fn/bookings/cancel-my-booking';
import { listMyBookings } from '../../../api/fn/bookings/list-my-bookings';
import { getFacilityPaymentNotes } from '../../../api/fn/facilities/get-facility-payment-notes';
import { getPaymentNotes } from '../../../api/fn/municipalities/get-payment-notes';
import type { FacilityPaymentNotesResponse } from '../../../api/models/facility-payment-notes-response';
import type { NoteResponse } from '../../../api/models/note-response';
import { effectivePaymentTexts } from '../../../core/municipality/payment-notes';
import { getSportsContact } from '../../../api/fn/municipalities/get-sports-contact';
import type { PaymentNotesResponse } from '../../../api/models/payment-notes-response';
import type { SportsContactResponse } from '../../../api/models/sports-contact-response';
import type { MyBookingResponse } from '../reserve/confirmed-booking.store';
import type { BookingScope } from './my-bookings';

export type { NoteResponse, PaymentNotesResponse, SportsContactResponse };

/** Unha pestana: as reservas pedidas ata agora e en que estado está. */
export interface BookingList {
  readonly items: readonly MyBookingResponse[];
  readonly total: number;
  /** Última páxina pedida (−1: ningunha). */
  readonly page: number;
  readonly state: 'idle' | 'loading' | 'ready' | 'error';
  readonly loadingMore: boolean;
}

const EMPTY: BookingList = { items: [], total: 0, page: -1, state: 'idle', loadingMore: false };
/** Reservas por páxina. */
export const PAGE_SIZE = 20;

/**
 * «As miñas reservas» (D2-13): as próximas e o historial (`GET /me/bookings?scope=`), cada
 * pestana pedida a primeira vez que se abre e por páxinas («Ver máis»); os textos do concello
 * (nota de devolución, `GET …/payment-notes`) e o contacto do servizo de deportes
 * (`GET …/sports-contact`), que nunca van no código. Cancelar (`POST …/cancel` coa versión en
 * `If-Match`) substitúe a tarxeta pola resposta, sen recargar, e deixa o historial para pedir de
 * novo. Un por páxina.
 */
@Injectable()
export class MyBookingsStore {
  private readonly api = inject(Api);

  readonly lists: Record<BookingScope, WritableSignal<BookingList>> = {
    UPCOMING: signal(EMPTY),
    HISTORY: signal(EMPTY),
  };
  readonly notes = signal<PaymentNotesResponse | null>(null);
  private readonly facilityNotes = new Map<string, Promise<FacilityPaymentNotesResponse | null>>();
  readonly contact = signal<SportsContactResponse | null>(null);

  /** Textos do concello: se non chegan, as pantallas seguen sen eles. */
  loadMunicipalityTexts(slug: string): void {
    this.api.invoke(getPaymentNotes, { slug }).then(
      (notes) => this.notes.set(notes),
      () => this.notes.set(null),
    );
    this.api.invoke(getSportsContact, { slug }).then(
      (contact) => this.contact.set(contact),
      () => this.contact.set(null),
    );
  }

  /**
   * O texto da devolución para unha reserva: o do concello ou, se o concello os pon por
   * instalación, o da súa instalación (sen el, o de Furar). Os da instalación pídense unha vez.
   */
  async refundNote(slug: string, facilityId: string): Promise<NoteResponse | null> {
    const notes = this.notes();
    if (notes?.scope !== 'FACILITY') return notes?.refund ?? null;
    let facility = this.facilityNotes.get(facilityId);
    if (!facility) {
      facility = this.api.invoke(getFacilityPaymentNotes, { slug, id: facilityId }).catch(() => null);
      this.facilityNotes.set(facilityId, facility);
    }
    return effectivePaymentTexts(notes, await facility).refund;
  }

  /** A primeira páxina, se aínda non se pediu (ou `force`: recargar). */
  async load(slug: string, scope: BookingScope, force = false): Promise<void> {
    const list = this.lists[scope];
    if (!force && list().state !== 'idle' && list().state !== 'error') return;
    list.set({ ...EMPTY, state: 'loading' });
    try {
      const page = await this.api.invoke(listMyBookings, { slug, scope, page: 0, size: PAGE_SIZE });
      list.set({ items: page.items, total: page.totalItems, page: 0, state: 'ready', loadingMore: false });
    } catch {
      list.set({ ...EMPTY, state: 'error' });
    }
  }

  /** A páxina seguinte, engadida ao final. */
  async loadMore(slug: string, scope: BookingScope): Promise<void> {
    const list = this.lists[scope];
    const current = list();
    if (current.state !== 'ready' || current.loadingMore || current.items.length >= current.total) return;
    list.set({ ...current, loadingMore: true });
    try {
      const next = current.page + 1;
      const page = await this.api.invoke(listMyBookings, { slug, scope, page: next, size: PAGE_SIZE });
      const seen = new Set(current.items.map((item) => item.id));
      list.set({
        items: [...current.items, ...page.items.filter((item) => !seen.has(item.id))],
        total: page.totalItems,
        page: next,
        state: 'ready',
        loadingMore: false,
      });
    } catch {
      list.update((value) => ({ ...value, loadingMore: false }));
    }
  }

  /**
   * Cancela coa versión que se ve (`If-Match`). Ben: a tarxeta pasa a ser a resposta (cancelada)
   * e o historial pídese de novo ao abrilo. Mal (412 se cambiou noutro sitio, 422 fóra de
   * prazo…): rexeita co erro e non toca nada.
   */
  async cancel(slug: string, booking: MyBookingResponse): Promise<MyBookingResponse> {
    const cancelled = await this.api.invoke(cancelMyBooking, {
      slug,
      id: booking.id,
      'If-Match': `"${booking.version}"`,
    });
    for (const list of Object.values(this.lists)) {
      list.update((value) => ({
        ...value,
        items: value.items.map((item) => (item.id === cancelled.id ? cancelled : item)),
      }));
    }
    this.lists.HISTORY.update((value) => (value.state === 'ready' ? { ...value, state: 'idle' } : value));
    return cancelled;
  }
}
