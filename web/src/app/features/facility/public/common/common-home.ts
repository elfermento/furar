// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { Language } from '../../../../core/i18n/languages';
import type {
  SearchCriteria,
  SearchMunicipality,
  SearchSport,
} from '../../../../shared/facility-search/facility-search';
import { matchesText, sportName } from '../../../../shared/facility-search/facility-search';
import { foldText } from '../../../../shared/ui/combobox/combobox-search';
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';
import type { MapPoint } from '../../../../shared/ui/map/map-point';
import { pointOf } from '../../../../shared/ui/map/map-point';
import type { ListFilters, SportChip } from '../list/public-list';
import { filterParams, matchesCovered, readFilters } from '../list/public-list';
import type { PublicFacilityResponse } from '../list/public-list.store';

/** Unha instalación da portada común: a do listado público co seu concello. */
export type CatalogFacility = PublicFacilityResponse & { readonly municipality: SearchMunicipality };

/**
 * Filtros da portada común, no URL (`?busca=…&deporte=…&cuberta=si&data=…&concello=…`). O mapa non vai
 * no URL: ábreo o botón «Mapa».
 */
export interface CatalogFilters extends Omit<ListFilters, 'view'> {
  /** Só as dun concello (o seu slug), escollido no buscador; `null`, todos. */
  readonly municipality: string | null;
  /**
   * A data escolleuse nos filtros do móbil (vai no URL aínda que sexa hoxe). Sen ela, o chip
   * «Escoller data» vai baleiro e os ocos son os de hoxe.
   */
  readonly dateChosen?: boolean;
}

export { COVERED_OPTION } from '../../../../shared/facility-search/facility-search';

/** Desde este ancho, a lista e o mapa van un ao lado do outro. */
export const SPLIT_QUERY = '(min-width: 900px)';

export function readCatalogFilters(params: Params, today: IsoDate): CatalogFilters {
  const { sports, covered, date, text } = readFilters(params, today);
  const municipality = params['concello'] as string | string[] | undefined;
  return {
    sports,
    covered,
    date,
    municipality: typeof municipality === 'string' && municipality ? municipality : null,
    text,
    dateChosen: params['data'] === date,
  };
}

/** Parámetros do URL para uns filtros: só o que non é o valor por defecto. */
export function catalogParams(filters: CatalogFilters, today: IsoDate): Params {
  const { deporte, cuberta, data, busca } = filterParams({ ...filters, view: 'list' }, today);
  return {
    busca,
    deporte,
    cuberta,
    data: data ?? (filters.dateChosen ? filters.date : null),
    concello: filters.municipality,
  };
}

/**
 * Pasa os filtros da lista: deportes (coa chave do filtro), «Cuberta», concello e o texto buscado
 * (`matchesText`, no idioma `lang`).
 */
export function matchesCatalog(
  facility: CatalogFacility,
  filters: Pick<CatalogFilters, 'sports' | 'covered' | 'municipality'> & { readonly text?: string },
  key: (sport: SearchSport) => string,
  lang: Language = 'gl',
): boolean {
  return (
    matchesCatalogSports(facility, filters.sports, key) &&
    matchesCovered(facility, filters.covered) &&
    matchesMunicipality(facility, filters.municipality) &&
    matchesText(facility, filters.text ?? '', lang)
  );
}

/** O buscador abre co día do URL e, se hai un concello escollido ou un texto buscado, con el escrito. */
export function catalogCriteria({ municipality, date, text }: CatalogFilters): SearchCriteria {
  return { choice: municipality ? { kind: 'municipality', slug: municipality } : null, date, text };
}

/** Sen concello escollido, todas; con el, só as súas. */
export function matchesMunicipality(facility: CatalogFacility, municipality: string | null): boolean {
  return municipality === null || facility.municipality.slug === municipality;
}

/** Un carrusel da portada sen filtros: un concello e as súas instalacións. */
export interface MunicipalityRail {
  readonly municipality: SearchMunicipality;
  readonly facilities: readonly CatalogFacility[];
}

/** As instalacións por concello, na orde na que veñen (a API xa as dá concello a concello). */
export function railsByMunicipality(facilities: readonly CatalogFacility[]): MunicipalityRail[] {
  const rails = new Map<string, { municipality: SearchMunicipality; facilities: CatalogFacility[] }>();
  for (const facility of facilities) {
    const slug = facility.municipality.slug;
    const rail = rails.get(slug) ?? { municipality: facility.municipality, facilities: [] };
    rail.facilities.push(facility);
    rails.set(slug, rail);
  }
  return [...rails.values()];
}

/**
 * Hai algún filtro posto (texto, concello, deporte, «Cuberta» ou un día): a portada ensina os
 * resultados en vez dos carruseis.
 */
export function hasCatalogFilter(filters: CatalogFilters, today: IsoDate): boolean {
  const { text, municipality, sports, covered, date } = filters;
  // Escoller hoxe non é un filtro: é o día de sempre.
  return !!text || municipality !== null || sports.length > 0 || covered || date !== today;
}

/** Prefixo da chave dun deporte propio dos concellos: o seu nome, non o id de cada concello. */
const OWN_SPORT_PREFIX = '~';

/**
 * A chave de cada deporte no filtro «Deporte» da portada común. Os do catálogo común únense polo
 * seu código; os propios de cada concello, polo nome no idioma activo, sen maiúsculas nin acentos
 * (dous concellos co seu «Bádminton» son un só no filtro). Un propio que se chama coma un do
 * catálogo vai con el.
 */
export function catalogSportKeys(
  facilities: readonly CatalogFacility[],
  lang: Language,
): (sport: SearchSport) => string {
  const codes = new Map<string, string>();
  for (const facility of facilities) {
    for (const sport of facility.sports) {
      const name = foldText(sportName(sport, lang).trim());
      if (sport.code && !codes.has(name)) codes.set(name, sport.code);
    }
  }
  return (sport) => {
    if (sport.code) return sport.code;
    const name = foldText(sportName(sport, lang).trim());
    return codes.get(name) ?? (name ? OWN_SPORT_PREFIX + name : '');
  };
}

/**
 * As claves reais (código do catálogo ou id do deporte de cada concello) dos deportes do filtro
 * nestas instalacións: as que entenden a API e a páxina de reservar. Con `codes`, os códigos do
 * filtro van tal cal, aínda que as instalacións non estean cargadas.
 */
export function realSports(
  facilities: readonly CatalogFacility[],
  wanted: readonly string[],
  key: (sport: SearchSport) => string,
  codes = true,
): string[] {
  if (!wanted.length) return [];
  const real = new Set(codes ? wanted.filter((k) => !k.startsWith(OWN_SPORT_PREFIX)) : []);
  for (const facility of facilities) {
    for (const sport of facility.sports) {
      if (wanted.includes(key(sport))) real.add(sport.code ?? sport.customSportId ?? '');
    }
  }
  real.delete('');
  return [...real];
}

/** Os deportes de todas as instalacións, unha vez cada un e en orde alfabética do idioma. */
export function catalogSportOptions(
  facilities: readonly CatalogFacility[],
  lang: Language,
  key: (sport: SearchSport) => string,
): SportChip[] {
  const sports = new Map<string, string>();
  for (const facility of facilities) {
    for (const sport of facility.sports) {
      const k = key(sport);
      if (k && !sports.has(k)) sports.set(k, sportName(sport, lang));
    }
  }
  const collator = new Intl.Collator(lang);
  return [...sports.entries()].map(([k, name]) => ({ key: k, name })).sort((a, b) => collator.compare(a.name, b.name));
}

/** Sen deportes marcados, todas; se non, as que teñen algún deles, sexan do concello que sexan. */
export function matchesCatalogSports(
  facility: CatalogFacility,
  sports: readonly string[],
  key: (sport: SearchSport) => string,
): boolean {
  return !sports.length || facility.sports.some((sport) => sports.includes(key(sport)));
}

/** Radio medio da Terra (km), o da fórmula do haversine. */
const EARTH_KM = 6371;

/** Distancia en liña recta (km) entre dous puntos (haversine: abonda para «a 1,2 km»). */
export function distanceKm(a: MapPoint, b: MapPoint): number {
  const rad = (deg: number) => (deg * Math.PI) / 180;
  const dLat = rad(b.latitude - a.latitude);
  const dLon = rad(b.longitude - a.longitude);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.latitude)) * Math.cos(rad(b.latitude)) * Math.sin(dLon / 2) ** 2;
  return 2 * EARTH_KM * Math.asin(Math.min(1, Math.sqrt(h)));
}

/**
 * «350 m», «1,2 km», «26 km»: menos dun quilómetro, en metros de 50 en 50; ata 10, cun decimal; e
 * despois, en quilómetros enteiros. Os números, no formato do idioma.
 */
export { formatDistance } from '../../../../shared/facility-card/facility-card';

/**
 * Preto de min: as instalacións da máis próxima á máis lonxana, coa distancia de cada unha. As que
 * non teñen punto no mapa van ao final, na orde na que estaban.
 */
export function byDistance<T extends { readonly address: CatalogFacility['address'] }>(
  facilities: readonly T[],
  here: MapPoint,
): { readonly facility: T; readonly km: number | null }[] {
  return facilities
    .map((facility) => {
      const point = pointOf(facility.address);
      return { facility, km: point ? distanceKm(here, point) : null };
    })
    .sort((a, b) => (a.km === null ? (b.km === null ? 0 : 1) : b.km === null ? -1 : a.km - b.km));
}
