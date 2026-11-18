// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { LIST_FACILITIES, LIST_SLOTS } from '../list/public-list.fixture';
import type { FacilityFreeSlotsResponse, PublicFacilityResponse } from '../list/public-list.store';
import type { CatalogFacility } from './common-home';

/** Unha pista cuberta noutro concello, lonxe das de Narón no mapa (exemplo, CC0 coma os datos de demo). */
const CAMBRE_COURT: PublicFacilityResponse = {
  id: 'c-pista',
  name: 'Pista de pádel de Cambre',
  covered: true,
  address: {
    street: 'Rúa do Río',
    streetNumber: '30',
    postalCode: '15660',
    locality: 'Cambre',
    latitude: 43.2944,
    longitude: -8.3436,
  },
  // O seu «Badminton» propio, sen acento: o mesmo deporte ca o «Bádminton» propio de Narón.
  sports: [
    { code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } },
    { customSportId: 'c-cambre-1', label: { gl: 'Badminton', es: 'Badminton' } },
  ],
  temporarilyClosed: false,
  cover: {
    kind: 'PHOTO',
    id: 'c-pista-photo',
    large: '/api/v1/municipalities/cambre/photos/c-pista-photo/large',
    thumb: '/api/v1/municipalities/cambre/photos/c-pista-photo/thumb',
    alt: { gl: 'Foto da pista', es: 'Foto de la pista' },
    width: 1600,
    height: 1067,
  },
};

/** Unha páxina de `GET /api/v1/catalog/facilities`: Cambre (unha pista) e Narón (as tres do listado). */
export function catalogPage(page = 0, hasNext = false) {
  return {
    page,
    size: 50,
    hasNext,
    items: [
      { slug: 'cambre', name: 'Cambre', facilities: [CAMBRE_COURT] },
      { slug: 'naron', name: 'Narón', facilities: LIST_FACILITIES },
    ],
  };
}

/** As instalacións de {@link catalogPage} co seu concello, como as garda a páxina. */
export const CATALOG_FACILITIES: CatalogFacility[] = catalogPage().items.flatMap(({ slug, name, facilities }) =>
  facilities.map((facility) => ({ ...facility, municipality: { slug, name } })),
);

/** Ocos do día: os de Narón e a pista de Cambre cun (20:00). */
export const CATALOG_SLOTS: FacilityFreeSlotsResponse[] = [
  { facilityId: 'c-pista', freeCount: 1, firstStarts: [{ start: '2026-11-10T19:00:00Z', startMinute: 1200 }] },
  ...LIST_SLOTS,
];

/** Unha páxina de `GET /api/v1/catalog/availability` para un día. */
export function catalogAvailabilityPage(date: string, items: FacilityFreeSlotsResponse[] = CATALOG_SLOTS) {
  return { date, today: date, now: `${date}T08:00:00Z`, page: 0, size: 50, hasNext: false, items };
}
