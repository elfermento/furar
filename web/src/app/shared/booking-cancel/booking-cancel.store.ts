// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../api/api';
import { cancelBooking } from '../../api/fn/bookings/cancel-booking';
import { getBooking } from '../../api/fn/bookings/get-booking';
import type { ManagedBookingDetailResponse } from '../../api/models/managed-booking-detail-response';

export type { ManagedBookingDetailResponse };

/** Unha canle pola que avisar ao cancelar. */
export type CancelChannel = ManagedBookingDetailResponse['cancelChannels'][number];

/**
 * Cancelar unha reserva desde fóra da páxina de reservas (o día ocupado dunha tempada ou dunha
 * actividade): o seu detalle e a cancelación coa versión que se ve.
 */
@Injectable({ providedIn: 'root' })
export class BookingCancelStore {
  private readonly api = inject(Api);

  booking(slug: string, id: string): Promise<ManagedBookingDetailResponse> {
    return this.api.invoke(getBooking, { slug, id });
  }

  cancel(slug: string, booking: ManagedBookingDetailResponse, channels: CancelChannel[]): Promise<unknown> {
    return this.api.invoke(cancelBooking, {
      slug,
      id: booking.id,
      'If-Match': `"${booking.version}"`,
      body: { channels },
    });
  }
}
