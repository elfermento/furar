// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../api/api';
import { getUseAvailability } from '../../../api/fn/bookings/get-use-availability';
import type { AvailabilityResponse } from '../../../api/models/availability-response';
import type { PublicFacilityDetailResponse } from '../../../api/models/public-facility-detail-response';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { offeredSlots } from './reserve';

/**
 * Os ocos libres dun uso nun día (`GET …/public/facilities/{id}/availability`, sen sesión). Só
 * vale a última petición: se a persoa cambia de día antes de que chegue a anterior, esa descártase.
 * Un por páxina.
 */
@Injectable()
export class UseAvailabilityStore {
  private readonly api = inject(Api);
  private request = 0;

  readonly availability = signal<AvailabilityResponse | null>(null);
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');

  async load(slug: string, id: string, useId: string, date: IsoDate | null): Promise<void> {
    const request = ++this.request;
    this.state.set('loading');
    try {
      const availability = await this.fetch(slug, id, useId, date);
      if (request !== this.request) return;
      this.availability.set(availability);
      this.state.set('ready');
    } catch {
      if (request !== this.request) return;
      this.availability.set(null);
      this.state.set('error');
    }
  }

  /**
   * O primeiro uso que ten libre a hora `time` dun franxa ese día (o listado só leva día e hora);
   * `null` se ningún.
   */
  async useWithTime(
    slug: string,
    facility: PublicFacilityDetailResponse,
    date: IsoDate | null,
    time: string,
  ): Promise<string | null> {
    for (const use of facility.uses) {
      const availability = await this.fetch(slug, facility.id, use.id, date).catch(() => null);
      const slots = availability?.bookable ? availability.slots : [];
      if (offeredSlots(slots, 1, use.slotMinutes).some((slot) => slot.time === time)) return use.id;
    }
    return null;
  }

  private fetch(slug: string, id: string, useId: string, date: IsoDate | null): Promise<AvailabilityResponse> {
    return this.api.invoke(getUseAvailability, { slug, id, useId, ...(date ? { date } : {}) });
  }
}
