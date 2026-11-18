// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { FormControl, FormGroup, Validators } from '@angular/forms';
import { Api } from '../../../api/api';
import { changeBooking } from '../../../api/fn/bookings/change-booking';
import { createBooking } from '../../../api/fn/bookings/create-booking';
import { getBooking } from '../../../api/fn/bookings/get-booking';
import { readHolderDependents } from '../../../api/fn/bookings/read-holder-dependents';
import { listFacilities } from '../../../api/fn/facilities/list-facilities';
import type { BookForRequest } from '../../../api/models/book-for-request';
import type { BookingHolderRequest } from '../../../api/models/booking-holder-request';
import type { ChangeBookingRequest } from '../../../api/models/change-booking-request';
import type { FacilitySummaryResponse } from '../../../api/models/facility-summary-response';
import type { HolderDependentResponse } from '../../../api/models/holder-dependent-response';
import type { ManagedBookingDetailResponse } from '../../../api/models/managed-booking-detail-response';
import type { StaffBookingParticipantsRequest } from '../../../api/models/staff-booking-participants-request';

export type { HolderDependentResponse, StaffBookingParticipantsRequest };

/** As instalacións que se piden para o selector (as do concello caben nunha páxina). */
const FACILITIES_SIZE = 100;

/** Un correo con forma de correo (a API comproba o resto). */
export const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/;

/** O teléfono, como o garda a API. */
export const PHONE_PATTERN = /^[0-9+() .-]+$/;

/** «¿Para quen?»: o nome e, se os dá, o correo e o teléfono. */
export type HolderForm = FormGroup<{
  name: FormControl<string>;
  email: FormControl<string>;
  phone: FormControl<string>;
}>;

export function holderForm(): HolderForm {
  return new FormGroup({
    name: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(200)] }),
    email: new FormControl('', {
      nonNullable: true,
      validators: [Validators.pattern(EMAIL_PATTERN), Validators.maxLength(254)],
    }),
    phone: new FormControl('', {
      nonNullable: true,
      validators: [Validators.minLength(3), Validators.maxLength(30), Validators.pattern(PHONE_PATTERN)],
    }),
  });
}

/** O que se manda de «¿Para quen?»: sen os baleiros. */
export function holderBody(form: HolderForm): BookingHolderRequest {
  const { name, email, phone } = form.getRawValue();
  return {
    name: name.trim(),
    ...(email.trim() ? { email: email.trim() } : {}),
    ...(phone.trim() ? { phone: phone.trim() } : {}),
  };
}

/** Só se reserva nas publicadas e abertas (a dispoñibilidade é a mesma ca na web pública). */
export function bookableFacilities(items: readonly FacilitySummaryResponse[]): FacilitySummaryResponse[] {
  return items.filter((facility) => facility.published && facility.status === 'ACTIVE');
}

/**
 * Reservar no nome doutra persoa e cambiar unha reserva desde a xestión: as
 * instalacións do selector, a reserva que se cambia (`GET /bookings/{id}`) e gardar: `POST
 * /bookings` cunha `Idempotency-Key` por intención (como a reserva da persoa) ou `PUT
 * /bookings/{id}` coa versión que se ve (`If-Match`). Un por páxina.
 */
@Injectable()
export class BookForStore {
  private readonly api = inject(Api);

  /** A intención de reservar aberta: o que se pide e a súa clave. */
  private intent: { request: string; key: string } | null = null;

  readonly facilities = signal<readonly FacilitySummaryResponse[]>([]);
  readonly original = signal<ManagedBookingDetailResponse | null>(null);
  /** As persoas a cargo da conta do correo da reserva (baleira sen conta ou sen fichas). */
  readonly holderDependents = signal<readonly HolderDependentResponse[]>([]);
  /** O que se pediu por última vez, para non repetir a lectura (queda no rexistro e ten máximo). */
  private asked: string | null = null;

  /**
   * As persoas a cargo da conta dun correo (ou da reserva que se cambia): `POST
   * /bookings/holder-dependents`. Sen conta ou sen fichas, a mesma lista baleira. Cada lectura
   * queda no rexistro de actividade: só se pide cando cambia o correo.
   */
  async loadHolderDependents(slug: string, of: { email: string } | { bookingId: string } | null): Promise<void> {
    const key = of ? JSON.stringify(of).toLowerCase() : null;
    if (key === this.asked) return;
    this.asked = key;
    this.holderDependents.set([]);
    if (!of) return;
    const found = await this.api.invoke(readHolderDependents, { slug, body: of });
    if (this.asked === key) this.holderDependents.set(found);
  }

  async loadFacilities(slug: string): Promise<void> {
    const page = await this.api.invoke(listFacilities, { slug, page: 0, size: FACILITIES_SIZE });
    this.facilities.set(bookableFacilities(page.items));
  }

  async loadOriginal(slug: string, id: string): Promise<ManagedBookingDetailResponse> {
    const booking = await this.api.invoke(getBooking, { slug, id });
    this.original.set(booking);
    return booking;
  }

  /** Reserva nova; a mesma petición repetida leva a mesma clave (a API devolve a xa feita). */
  async create(slug: string, body: BookForRequest): Promise<ManagedBookingDetailResponse> {
    const request = JSON.stringify(body);
    if (this.intent?.request !== request) {
      this.intent = { request, key: globalThis.crypto.randomUUID() };
    }
    const created = await this.api.invoke(createBooking, { slug, 'Idempotency-Key': this.intent.key, body });
    this.intent = null;
    return created;
  }

  async change(slug: string, body: ChangeBookingRequest): Promise<ManagedBookingDetailResponse> {
    const original = this.original();
    if (!original) throw new Error('No booking to change');
    const changed = await this.api.invoke(changeBooking, {
      slug,
      id: original.id,
      'If-Match': `"${original.version}"`,
      body,
    });
    this.original.set(changed);
    return changed;
  }
}
