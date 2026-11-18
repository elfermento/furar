// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import type { HourOption, PartsUseSlots } from './reserve';
import { hourOptions, offeredSlots, useMatches, withPartHints } from './reserve';
import type { PublicUseResponse } from './reserve.store';
import { ReserveStore } from './reserve.store';
import { UseAvailabilityStore } from './use-availability.store';

/**
 * As horas ocupadas da páxina de reservar (ADR-0033): o selector mestura as libres do
 * `ReserveStore` coas ocupadas que manda a API, e `?hora=` pode ser unha ocupada (no canto de
 * «Continuar», «Avísame se se libera»). Só na páxina da persoa: o mostrador non o ten e segue
 * vendo só as libres. Unha hora ocupada que está libre noutro uso que vale (o terzo da pista; cun
 * deporte buscado, só os usos del) leva «Só unha parte» (ou o nome dese uso, se non é por partes)
 * e, ao tocala, pasa a ese uso. Un por páxina.
 */
@Injectable()
export class ReserveHours {
  private readonly store = inject(ReserveStore);
  private readonly slots = inject(UseAvailabilityStore);
  private readonly municipality = inject(CurrentMunicipality);

  /** Os ocos dos usos por partes, para as horas ocupadas do uso principal. */
  private readonly partsSlots = signal<{ readonly key: string; readonly uses: readonly PartsUseSlots[] } | null>(null);
  private request = 0;

  /**
   * Que pedir: cun uso enteiro á vista, os outros usos enteiros ou por partes que valen (cos
   * deportes buscados, `?deporte=`; sen busca, cun deporte en común).
   */
  private readonly partsKey = computed(
    () => {
      const slug = this.municipality.slug();
      const facility = this.store.facility();
      const use = this.store.use();
      const { date, booking, sports } = this.store.selection();
      if (!slug || !facility || !use || booking || use.rentalMode !== 'WHOLE') return null;
      const uses = facility.uses.filter(
        (each) =>
          each.id !== use.id &&
          BY_SLOTS.includes(each.rentalMode) &&
          (sports?.length ? useMatches(each, sports) : sharesSport(each, use)),
      );
      if (!uses.length) return null;
      return { slug, facilityId: facility.id, uses, date, key: `${facility.id}|${use.id}|${date ?? ''}` };
    },
    { equal: (a, b) => a === b || (!!a && !!b && a.slug === b.slug && a.key === b.key) },
  );

  /** As horas do selector: as libres e as ocupadas, por hora. */
  readonly hours = computed<HourOption[]>(() => {
    const availability = this.store.availability();
    const busy = availability?.bookable && !this.store.slotsStale() ? (availability.busy ?? []) : [];
    const hours = hourOptions(this.store.offered(), busy);
    const key = this.partsKey()?.key;
    const parts = this.partsSlots();
    return key && parts?.key === key ? withPartHints(hours, parts.uses) : hours;
  });
  /** A hora escollida, se está ocupada. */
  readonly chosenBusy = computed(
    () => this.hours().find((hour) => !hour.offered && hour.time === this.store.selection().time) ?? null,
  );
  /** Pediuse esperar por unha hora que xa estaba libre: o texto do aviso (punto 15). */
  readonly freed = signal<string | null>(null);

  constructor() {
    // O aviso é da hora escollida: con outro uso, día ou hora, quítase.
    effect(() => {
      const { useId, date, time } = this.store.selection();
      void [useId, date, time];
      untracked(() => this.freed.set(null));
    });
    effect(() => {
      const key = this.partsKey();
      if (key) untracked(() => void this.loadParts(key.slug, key.facilityId, key.uses, key.date, key.key));
    });
  }

  /** Os ocos dunha franxa de cada uso por partes ese día (só vale a última petición). */
  private async loadParts(
    slug: string,
    facilityId: string,
    uses: readonly PublicUseResponse[],
    date: IsoDate | null,
    key: string,
  ): Promise<void> {
    const request = ++this.request;
    const found = await Promise.all(
      uses.map(async (use) => {
        const availability = await this.slots.other(slug, facilityId, use.id, date);
        const slots = availability?.bookable ? availability.slots : [];
        return {
          useId: use.id,
          label: use.rentalMode === 'PARTS' ? null : use.name,
          offered: offeredSlots(slots, 1, use.slotMinutes),
        };
      }),
    );
    if (request === this.request) this.partsSlots.set({ key, uses: found });
  }

  /** A hora ocupada xa está libre: vólvense pedir as horas, queda escollida e avísase. */
  async freedNow(notice: string): Promise<void> {
    const slug = this.municipality.slug();
    const facility = this.store.facility();
    const use = this.store.use();
    if (slug && facility && use) await this.slots.load(slug, facility.id, use.id, this.store.selection().date);
    this.freed.set(notice);
  }
}

/** Os usos que van por franxas enteiras ou partes (os por prazas non se suxiren). */
const BY_SLOTS: readonly string[] = ['WHOLE', 'PARTS'];

/** Se dous usos teñen algún deporte en común (sen deportes na ficha, si). */
function sharesSport(a: { readonly sports?: readonly string[] }, b: { readonly sports?: readonly string[] }): boolean {
  if (!a.sports?.length || !b.sports?.length) return true;
  return a.sports.some((sport) => b.sports?.includes(sport));
}
