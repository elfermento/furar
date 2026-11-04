// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { FacilityFreeSlotsResponse, PublicFacilityResponse } from './public-list.store';

/** Instalacións publicadas de exemplo (CC0 coma os datos de demo); só o pavillón di que é cuberto. */
function facility(
  id: string,
  name: string,
  sports: PublicFacilityResponse['sports'],
  street = 'Rúa Buxo',
  covered?: boolean,
) {
  return {
    id,
    name,
    ...(covered === undefined ? {} : { covered }),
    address: {
      street,
      streetNumber: '16',
      postalCode: '15570',
      locality: 'Narón',
      latitude: 43.5167,
      longitude: -8.1903,
    },
    sports,
    temporarilyClosed: false,
    cover: {
      kind: 'PHOTO',
      id: `${id}-photo`,
      large: `/api/v1/municipalities/naron/photos/${id}-photo/large`,
      thumb: `/api/v1/municipalities/naron/photos/${id}-photo/thumb`,
      alt: { gl: `Foto de ${name}`, es: `Foto de ${name}` },
      width: 1600,
      height: 1067,
    },
  } satisfies PublicFacilityResponse;
}

export const LIST_FACILITIES: PublicFacilityResponse[] = [
  facility('f-campo', 'Campo de fútbol 1 de O Alto', [
    { code: 'football', label: { gl: 'Fútbol 11', es: 'Fútbol 11' } },
    { code: 'football_7', label: { gl: 'Fútbol 7', es: 'Fútbol 7' } },
  ]),
  facility('f-padel', 'Pista de pádel 1 de O Alto', [{ code: 'padel', label: { gl: 'Pádel', es: 'Pádel' } }]),
  facility(
    'f-pavillon',
    'Pavillón de Piñeiros',
    [
      { code: 'futsal', label: { gl: 'Fútbol sala', es: 'Fútbol sala' } },
      { customSportId: 'c-1', label: { gl: 'Bádminton', es: 'Bádminton' } },
    ],
    'Estrada de Castela',
    true,
  ),
];

/** Ocos do día: o campo con dous (19:00 e 21:00), o pádel con catro e o pavillón sen ningún. */
export const LIST_SLOTS: FacilityFreeSlotsResponse[] = [
  {
    facilityId: 'f-campo',
    freeCount: 2,
    firstStarts: [
      { start: '2026-10-26T18:00:00Z', startMinute: 1140 },
      { start: '2026-10-26T20:00:00Z', startMinute: 1260 },
    ],
  },
  {
    facilityId: 'f-padel',
    freeCount: 4,
    firstStarts: [
      { start: '2026-10-26T08:30:00Z', startMinute: 570 },
      { start: '2026-10-26T18:00:00Z', startMinute: 1140 },
      { start: '2026-10-26T19:30:00Z', startMinute: 1230 },
    ],
  },
  { facilityId: 'f-pavillon', freeCount: 0, firstStarts: [] },
];

/** Respostas de `GET …/public/availability` para un día. */
export function availabilityPage(date: string, items: FacilityFreeSlotsResponse[] = LIST_SLOTS, page = 0) {
  return { date, today: date, now: `${date}T08:00:00Z`, page, size: 12, items };
}
