// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Api } from '../../../api/api';
import { resolveAddress } from '../../../api/fn/facilities/resolve-address';
import { suggestAddresses } from '../../../api/fn/facilities/suggest-addresses';
import { ApiProblem } from '../../../core/errors/api-problem';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import type { AddressMatch, AddressSearch, AddressSuggestion } from './address-search';

/**
 * Busca de enderezos do concello actual a través de Furar (D1-14; ADR-0023): `GET
 * …/address-search?q=` e `…/address-search/{id}`. Un 503 (`facility.geocoder-unavailable`)
 * chega como `ApiProblem` e leva a encher a man.
 */
@Injectable({ providedIn: 'root' })
export class AddressSearchStore implements AddressSearch {
  private readonly api = inject(Api);
  private readonly municipality = inject(CurrentMunicipality);

  suggest(query: string): Promise<readonly AddressSuggestion[]> {
    return this.withSlug((slug) => this.api.invoke(suggestAddresses, { slug, q: query }));
  }

  resolve(id: string): Promise<AddressMatch> {
    return this.withSlug((slug) => this.api.invoke(resolveAddress, { slug, id }));
  }

  /** Sen concello cargado non hai onde buscar: compórtase coma o buscador caído. */
  private withSlug<T>(call: (slug: string) => Promise<T>): Promise<T> {
    const slug = this.municipality.slug();
    return slug ? call(slug) : Promise.reject(new ApiProblem(503, 'common.unexpected', {}, [], undefined, ''));
  }
}
