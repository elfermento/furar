// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { getMyBookingBlock } from '../../../api/fn/bookings/get-my-booking-block';
import { getSportsContact } from '../../../api/fn/municipalities/get-sports-contact';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';

/** O bloqueo por faltas da persoa: o último día bloqueado, as faltas e a quen chamar. */
export interface BookingBlock {
  readonly until: IsoDate;
  readonly faults: number;
  readonly windowDays: number;
  readonly phone: string | null;
}

/**
 * Se a persoa da sesión está bloqueada por faltas neste concello (ADR-0032, punto 7):
 * `GET …/me/booking-block` e, se o está, o teléfono do servizo de deportes (`GET
 * …/sports-contact`). Se a pregunta falla, a páxina segue: a API rexeitaría a reserva. Un por
 * páxina.
 */
@Injectable()
export class BookingBlockStore {
  private readonly api = inject(Api);

  /** `null` se non está bloqueada (ou aínda non se sabe). */
  readonly block = signal<BookingBlock | null>(null);

  async check(slug: string): Promise<void> {
    try {
      const found = await this.api.invoke(getMyBookingBlock, { slug });
      if (!found.blocked || !found.until) {
        this.block.set(null);
        return;
      }
      const phone = await this.api
        .invoke(getSportsContact, { slug })
        .then((contact) => contact.phone ?? null)
        .catch(() => null);
      this.block.set({ until: found.until, faults: found.faults, windowDays: found.windowDays, phone });
    } catch {
      this.block.set(null);
    }
  }
}
