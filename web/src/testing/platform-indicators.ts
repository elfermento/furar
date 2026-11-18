// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type {
  PlatformIndicatorsResponse,
  PlatformMunicipalityIndicatorsResponse,
} from '../app/features/indicators/platform/platform-indicators';
import { naronPanel } from './indicators';

/** Unha fila dun concello: ceros e seis horas abertas, cos valores indicados. */
export function row(
  name: string,
  values: Partial<Omit<PlatformMunicipalityIndicatorsResponse, 'name'>> = {},
): PlatformMunicipalityIndicatorsResponse {
  return {
    slug: name.toLowerCase(),
    name,
    bookings: 0,
    occupancy: { reservedMinutes: 0, openMinutes: 600 },
    reservedMinutes: 0,
    people: 0,
    paidCents: 0,
    ...values,
  };
}

/** Os totais dun panel do concello coma se fosen os da plataforma, con tres concellos. */
export function platformResponse(
  municipalities: PlatformMunicipalityIndicatorsResponse[] = [
    row('Narón', {
      bookings: 1284,
      occupancy: { reservedMinutes: 580, openMinutes: 1000 },
      reservedMinutes: 90720,
      people: 642,
      paidCents: 987000,
    }),
    row('Ferrol', {
      bookings: 2105,
      occupancy: { reservedMinutes: 630, openMinutes: 1000 },
      reservedMinutes: 143280,
      people: 1031,
      paidCents: 1542050,
    }),
    row('A Capela', {
      bookings: 1,
      occupancy: { reservedMinutes: 0, openMinutes: 0 },
      reservedMinutes: 60,
      people: 1,
      paidCents: 0,
    }),
  ],
): PlatformIndicatorsResponse {
  const panel = naronPanel();
  const { bookings, occupancy, reservedHours, paidCents } = panel.kpis;
  return {
    period: panel.period,
    previous: panel.previous,
    comparison: panel.comparison,
    kpis: { bookings, occupancy, reservedHours, paidCents },
    municipalities,
  };
}
