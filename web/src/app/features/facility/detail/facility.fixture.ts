// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Facility } from './facility-detail.store';

/** Debuxo suxerido polo nome «Campo de fútbol…»: o que se ve sen imaxes gardadas. */
export const SUGGESTED: Facility['images'][number] = {
  kind: 'DRAWING',
  id: 'football_field',
  name: { gl: 'Campo de fútbol', es: 'Campo de fútbol' },
  alt: { gl: 'Debuxo do campo de fútbol', es: 'Dibujo del campo de fútbol' },
  large: '/api/v1/municipalities/naron/drawings/football_field/large',
  thumb: '/api/v1/municipalities/naron/drawings/football_field/thumb',
  width: 1200,
  height: 800,
};

/** Enderezo da instalación de proba. */
export const ADDRESS: NonNullable<Facility['address']> = {
  street: 'Rúa do Alto',
  streetNumber: '12',
  postalCode: '15570',
  locality: 'Narón',
  ineCode: '15054',
  latitude: 43.5,
  longitude: -8.19,
};

/** Instalación de proba para os specs da ficha: «Campo de fútbol 1 de O Alto», en borrador. */
export function facility(overrides: Partial<Facility> = {}): Facility {
  return {
    id: 'f1',
    name: 'Campo de fútbol 1 de O Alto',
    status: 'ACTIVE',
    version: 3,
    missing: ['USES', 'SCHEDULE'],
    published: true,
    deletable: true,
    amenities: [],
    images: [SUGGESTED],
    suggestedImage: SUGGESTED,
    address: ADDRESS,
    details: { onlineBooking: true, showFreeSlots: false, sports: [], customFields: {} },
    paymentNotes: {},
    createdAt: '2026-10-25T10:00:00Z',
    updatedAt: '2026-10-25T10:00:00Z',
    ...overrides,
  };
}
