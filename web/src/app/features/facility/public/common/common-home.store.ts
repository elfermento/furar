// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject, signal } from '@angular/core';
import { Api } from '../../../../api/api';
import { listCatalogAvailability } from '../../../../api/fn/catalog/list-catalog-availability';
import { listCatalogFacilities } from '../../../../api/fn/catalog/list-catalog-facilities';
import type { FacilityFreeSlotsResponse } from '../list/public-list.store';
import type { CatalogFacility } from './common-home';

/** Concellos por páxina (o máximo que acepta a API). */
export const CATALOG_PAGE = 50;
/** Tope de páxinas que se piden seguidas (2 500 concellos). */
const CATALOG_PAGES_MAX = 50;

/**
 * API da portada común (ADR-0030): as instalacións de todos os concellos que saen nela (decídeo a
 * API) e os ocos dun día, as dúas por páxinas de concellos.
 */
@Injectable()
export class CommonHomeStore {
  private readonly api = inject(Api);

  /** A carga das instalacións (para o esqueleto e o erro con «Tentar de novo»). */
  readonly state = signal<'loading' | 'ready' | 'error'>('loading');
  /** As instalacións xa lidas. */
  readonly list = signal<CatalogFacility[]>([]);
  /** Os ocos do día (e dos deportes) pedidos por último; sen eles (tamén se fallan), baleiro. */
  readonly slots = signal<ReadonlyMap<string, FacilityFreeSlotsResponse>>(new Map());

  /** Le as instalacións (tamén «Tentar de novo» tras un fallo); as xa lidas seguen á vista. */
  load(): void {
    this.state.set(this.list().length ? 'ready' : 'loading');
    this.facilities().then(
      (facilities) => {
        this.list.set(facilities);
        this.state.set('ready');
      },
      () => this.state.set('error'),
    );
  }

  /** Pide os ocos; só se gardan se seguen sendo os pedidos (`current`) cando chegan. */
  loadSlots(date: string, sports: string, current: () => boolean): void {
    this.availability(date, sports ? sports.split(',') : []).then(
      (slots) => {
        if (current()) this.slots.set(slots);
      },
      () => this.slots.set(new Map()),
    );
  }

  /** Todas as instalacións, concello a concello (por nome) e, dentro, na orde alfabética do idioma. */
  async facilities(): Promise<CatalogFacility[]> {
    const facilities: CatalogFacility[] = [];
    for (let page = 0; page < CATALOG_PAGES_MAX; page++) {
      const response = await this.api.invoke(listCatalogFacilities, { page, size: CATALOG_PAGE });
      for (const { slug, name, facilities: own } of response.items) {
        for (const facility of own) facilities.push({ ...facility, municipality: { slug, name } });
      }
      if (!response.hasNext) break;
    }
    return facilities;
  }

  /**
   * Ocos libres do día por instalación que se reserva por internet (id → cantos, as tres primeiras
   * horas e o uso). Con `sports`, os do primeiro uso de cada instalación con algún deles.
   */
  async availability(date: string, sports: readonly string[] = []): Promise<Map<string, FacilityFreeSlotsResponse>> {
    const free = new Map<string, FacilityFreeSlotsResponse>();
    for (let page = 0; page < CATALOG_PAGES_MAX; page++) {
      const response = await this.api.invoke(listCatalogAvailability, {
        date,
        page,
        size: CATALOG_PAGE,
        ...(sports.length ? { sport: [...sports] } : {}),
      });
      for (const item of response.items) free.set(item.facilityId, item);
      if (!response.hasNext) break;
    }
    return free;
  }
}
