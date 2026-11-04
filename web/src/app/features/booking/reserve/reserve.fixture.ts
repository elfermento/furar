// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2

import type { MyBookingResponse } from './confirmed-booking.store';
import type { AvailabilityResponse, AvailableSlotResponse, PublicFacilityDetailResponse } from './reserve.store';

/** Datos de proba da páxina de reservar (specs do store e da páxina). */
export const BASE = '/api/v1/municipalities/naron';
export const FACILITY_URL = `${BASE}/public/facilities/f1`;
export const AVAILABILITY_URL = `${BASE}/public/facilities/f1/availability`;
export const NOTES_URL = `${BASE}/payment-notes`;
export const BOOKINGS_URL = `${BASE}/me/bookings`;
export const FIELDS_URL = `${BASE}/custom-fields`;

export const NOTES = {
  payment: { gl: 'No mostrador, ao chegar', es: 'En el mostrador, al llegar' },
  paymentDefault: true,
  refund: { gl: 'O concello dirache como cha devolve', es: 'El ayuntamiento te dirá cómo te lo devuelve' },
  refundDefault: true,
};

/** O 28 de outubro de 2026 xa é horario de inverno: Europe/Madrid = UTC+1. */
export const DAY = '2026-10-28';

export function facility(overrides: Partial<PublicFacilityDetailResponse> = {}): PublicFacilityDetailResponse {
  return {
    id: 'f1',
    name: 'Campo de fútbol 1 de O Alto',
    address: {
      street: 'Rúa Buxo',
      streetNumber: '16',
      postalCode: '15572',
      locality: 'Narón',
      latitude: 43.5,
      longitude: -8.2,
    },
    amenities: [],
    bookable: true,
    temporarilyClosed: false,
    openingHours: [],
    closures: [],
    images: [
      {
        kind: 'DRAWING',
        id: 'football_field',
        alt: { gl: 'Campo', es: 'Campo' },
        width: 1200,
        height: 800,
        large: '/x/large',
        thumb: '/x/thumb',
      },
    ],
    uses: [
      {
        id: 'u1',
        name: 'Fútbol 7',
        rentalMode: 'PARTS',
        parts: [
          { id: 'p1', name: 'Metade norte' },
          { id: 'p2', name: 'Metade sur' },
        ],
        slotMinutes: 60,
        availability: 'ALWAYS',
        periods: [],
        weekdays: 127,
        price: { kind: 'PAID', amountCents: 3000, per: 'BOOKING' },
        rules: { maxSlots: 2 },
      },
      {
        id: 'u2',
        name: 'Campo enteiro',
        rentalMode: 'WHOLE',
        parts: [],
        slotMinutes: 90,
        availability: 'ALWAYS',
        periods: [],
        weekdays: 127,
        price: { kind: 'PAID', amountCents: 6000, per: 'BOOKING' },
        rules: {},
      },
    ],
    ...overrides,
  };
}

/** Un oco ás `hhmm` (hora de Madrid) do día de proba. */
export function slot(minute: number, maxSlots: number, freeParts?: string[]): AvailableSlotResponse {
  const start = new Date(Date.UTC(2026, 9, 28, 0, minute - 60)).toISOString();
  return { start, startMinute: minute, maxSlots, ...(freeParts ? { freeParts } : {}) };
}

/** Fútbol 7: 17:00 (só a norte), 18:00 (as dúas), 19:00 (só a sur, ata o peche). */
export function availability(slots = [slot(1020, 2, ['p1']), slot(1080, 2, ['p1', 'p2']), slot(1140, 1, ['p2'])]) {
  return { date: DAY, today: '2026-10-26', now: '2026-10-26T09:00:00Z', bookable: true, slots } as AvailabilityResponse;
}

/** Campo enteiro: 18:00 e 19:30. */
export function wholeAvailability() {
  return availability([slot(1080, 1), slot(1170, 1)]);
}

export function booking(overrides: Partial<MyBookingResponse> = {}): MyBookingResponse {
  return {
    id: 'b1',
    code: 'FUR-7K2Q',
    canCancel: true,
    cancelUntil: '2026-10-26T17:00:00Z',
    createdAt: '2026-10-26T09:00:00Z',
    customFields: {},
    done: false,
    start: '2026-10-28T17:00:00Z',
    end: '2026-10-28T18:00:00Z',
    facilityId: 'f1',
    facilityName: 'Campo de fútbol 1 de O Alto',
    partId: 'p2',
    partName: 'Metade sur',
    paymentStatus: 'PENDING',
    price: { kind: 'PAID', amountCents: 3000 },
    qr: { size: 21, path: 'M0 0h7v7H0z' },
    rentalMode: 'PARTS',
    slots: 1,
    status: 'CONFIRMED',
    useId: 'u1',
    useName: 'Fútbol 7',
    version: 0,
    ...overrides,
  };
}
