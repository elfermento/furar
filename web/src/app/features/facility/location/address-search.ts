// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { InjectionToken, inject } from '@angular/core';
import { ApiProblem } from '../../../core/errors/api-problem';
import { AddressSearchStore } from './address-search.store';

/** Unha suxestión do buscador (CartoCiudad polo servidor, ADR-0023): id opaco e texto. */
export interface AddressSuggestion {
  readonly id: string;
  readonly label: string;
}

/**
 * Enderezo que propón o buscador para encher o formulario. Pode vir incompleto (unha vía sen
 * número): o xestor complétao e quen valida é a API ao gardar.
 */
export interface AddressMatch {
  readonly street?: string | null;
  readonly streetNumber?: string | null;
  readonly postalCode?: string | null;
  readonly locality?: string | null;
  readonly ineCode?: string | null;
  readonly latitude?: number | null;
  readonly longitude?: number | null;
}

/**
 * Busca de enderezos do concello (`GET …/address-search?q=` e `…/address-search/{id}`, D1-14).
 * Rexeita co `ApiProblem` da API; un 503 (geocoder-unavailable) leva a encher a man.
 */
export interface AddressSearch {
  suggest(query: string): Promise<readonly AddressSuggestion[]>;
  resolve(id: string): Promise<AddressMatch>;
}

/** O buscador non responde: o mesmo ca un 503 da API. */
export const geocoderUnavailable = (): ApiProblem => new ApiProblem(503, 'common.unexpected', {}, [], undefined, '');

/** Por defecto, a busca real do concello actual (`AddressSearchStore`); os tests dan a súa. */
export const ADDRESS_SEARCH = new InjectionToken<AddressSearch>('ADDRESS_SEARCH', {
  providedIn: 'root',
  factory: () => inject(AddressSearchStore),
});
