// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { MyBookingResponse } from '../app/api/models/my-booking-response';
import type { MySeriesRequestResponse } from '../app/api/models/my-series-request-response';
import type { OrganizationResponse } from '../app/api/models/organization-response';
import type { OrganizationRowResponse } from '../app/api/models/organization-row-response';
import type { PublicFacilityDetailResponse } from '../app/api/models/public-facility-detail-response';
import type { SeriesPreviewResponse } from '../app/api/models/series-preview-response';
import type { SeriesRequestCardResponse } from '../app/api/models/series-request-card-response';
import type { SeriesResponse } from '../app/api/models/series-response';

/** Datos das tempadas das entidades (ADR-0039) para as specs: entidades, tempadas e solicitudes. */
export const SLUG_BASE = '/api/v1/municipalities/naron';

export function organizationRow(overrides: Partial<OrganizationRowResponse> = {}): OrganizationRowResponse {
  return {
    id: 'o1',
    name: 'Club Baloncesto',
    contactName: 'Iria Lago',
    email: 'club@example.org',
    phone: '981 000 210',
    managers: 2,
    status: 'SEASON',
    archived: false,
    ...overrides,
  };
}

export function organization(overrides: Partial<OrganizationResponse> = {}): OrganizationResponse {
  return {
    id: 'o1',
    name: 'Club Baloncesto',
    contactName: 'Iria Lago',
    email: 'club@example.org',
    phone: '981 000 210',
    managers: ['iria@example.org'],
    archived: false,
    version: 3,
    ...overrides,
  };
}

export function series(overrides: Partial<SeriesResponse> = {}): SeriesResponse {
  return {
    id: 's1',
    organizationId: 'o1',
    organizationName: 'Club Baloncesto',
    facilityId: 'f1',
    facilityName: 'Pavillón',
    useId: 'u1',
    useName: 'Baloncesto',
    partId: 'p1',
    partName: 'Pista central',
    weekdays: [1, 3],
    startMinute: 1140,
    endMinute: 1230,
    firstDate: '2026-11-16',
    lastDate: '2027-06-18',
    paymentStatus: 'PENDING',
    sessions: 57,
    cancelled: 0,
    version: 1,
    ...overrides,
  };
}

export function seriesRequest(overrides: Partial<SeriesRequestCardResponse> = {}): SeriesRequestCardResponse {
  return {
    id: 'r1',
    organizationId: 'o2',
    organizationName: 'Club Patinaxe',
    facilityId: 'f1',
    facilityName: 'Pavillón',
    useId: 'u1',
    useName: 'Patinaxe',
    partId: 'p1',
    partName: 'Pista central',
    weekdays: [5],
    startMinute: 1080,
    endMinute: 1170,
    firstDate: '2026-11-20',
    lastDate: '2027-06-18',
    status: 'PENDING',
    createdAt: '2026-11-12T08:00:00Z',
    note: 'Equipo infantil',
    requestedByEmail: 'xiana@example.org',
    ...overrides,
  };
}

export function mySeriesRequest(overrides: Partial<MySeriesRequestResponse> = {}): MySeriesRequestResponse {
  return {
    id: 'q1',
    facilityId: 'f1',
    facilityName: 'Pavillón',
    useId: 'u1',
    useName: 'Baloncesto',
    partId: 'p1',
    partName: 'Pista central',
    weekdays: [5],
    startMinute: 1080,
    endMinute: 1170,
    firstDate: '2026-11-20',
    lastDate: '2027-06-18',
    status: 'PENDING',
    createdAt: '2026-11-12T08:00:00Z',
    note: 'Equipo infantil',
    ...overrides,
  };
}

export function preview(overrides: Partial<SeriesPreviewResponse> = {}): SeriesPreviewResponse {
  return {
    sessions: 57,
    firstSession: '2026-11-16',
    lastSession: '2027-06-16',
    holidays: ['2027-01-06'],
    useOff: [],
    closed: [],
    conflicts: [
      {
        date: '2026-11-23',
        start: '2026-11-23T18:00:00Z',
        end: '2026-11-23T19:00:00Z',
        kind: 'BOOKING',
        bookingId: 'b9',
        name: 'Roi Varela',
      },
      {
        date: '2026-12-14',
        start: '2026-12-13T23:00:00Z',
        end: '2026-12-14T23:00:00Z',
        kind: 'BLOCK',
        label: 'Torneo de Nadal',
      },
    ],
    ...overrides,
  };
}

/** A ficha pública dunha instalación cun uso por partes, un enteiro e un por prazas. */
export function seasonFacility(): PublicFacilityDetailResponse {
  const use = (id: string, name: string, rentalMode: 'WHOLE' | 'PARTS' | 'SEATS') => ({
    id,
    name,
    rentalMode,
    availability: 'ALWAYS' as const,
    parts: rentalMode === 'PARTS' ? [{ id: 'p1', name: 'Pista central' }] : [],
    periods: [],
    price: { kind: 'FREE' as const },
    rules: {},
    slotMinutes: 30,
    weekdays: 127,
  });
  return {
    id: 'f1',
    name: 'Pavillón',
    uses: [use('u1', 'Baloncesto', 'PARTS'), use('u2', 'Ximnasia', 'SEATS')],
  } as unknown as PublicFacilityDetailResponse;
}

/** Unha sesión da tempada vista pola entidade. */
export function session(overrides: Partial<MyBookingResponse> = {}): MyBookingResponse {
  return {
    id: 'b1',
    code: 'FUR-AB12',
    facilityId: 'f1',
    facilityName: 'Pavillón',
    useId: 'u1',
    useName: 'Baloncesto',
    partName: 'Pista central',
    start: '2026-11-16T18:00:00Z',
    end: '2026-11-16T19:30:00Z',
    cancelUntil: '2026-11-16T18:00:00Z',
    status: 'CONFIRMED',
    paymentStatus: 'NONE',
    price: { kind: 'NONE' },
    canCancel: true,
    done: false,
    seats: 1,
    version: 1,
    qr: { path: 'M0 0h1v1H0z', size: 21 },
    organization: {
      id: 'o1',
      name: 'Club Baloncesto',
      seriesId: 's1',
      series: {
        id: 's1',
        weekdays: [1, 3],
        startMinute: 1140,
        endMinute: 1230,
        firstDate: '2026-11-16',
        lastDate: '2027-06-18',
        sessions: 57,
        paymentStatus: 'PENDING',
      },
    },
    ...overrides,
  } as unknown as MyBookingResponse;
}
