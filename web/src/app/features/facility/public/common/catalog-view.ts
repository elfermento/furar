// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { SearchCriteria, SearchSport } from '../../../../shared/facility-search/facility-search';
import { sportKey } from '../../../../shared/facility-search/facility-search';
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';
import type { MapMarker } from '../../../../shared/ui/map/map-point';
import { pointOf } from '../../../../shared/ui/map/map-point';
import type { SearchFiltersResult } from '../../../../shared/facility-search/search-filters-dialog.component';
import type { CatalogFacility, CatalogFilters } from './common-home';
import { splitSportFilter } from '../../../../shared/facility-search/facility-search';

export { splitSportFilter };

/** Filtros postos da folla (deportes e «Cuberta»): o número do botón «Filtros» (o día súmao o buscador). */
export function filterCount({ sports, covered }: CatalogFilters): number {
  return sports.length + (covered ? 1 : 0);
}

/** O que cambia «Ver N instalacións» da folla «Filtros»: sen data, a de hoxe (sen escoller). */
export function sheetChange(result: SearchFiltersResult, today: IsoDate): Partial<CatalogFilters> {
  return { ...splitSportFilter(result.value), date: result.date ?? today, dateChosen: result.date !== null };
}

/** Unha marca por instalación con punto no enderezo, co seu nome e o seu concello. */
export function catalogMarkers(facilities: readonly CatalogFacility[]): MapMarker[] {
  return facilities.flatMap((f) => {
    const point = pointOf(f.address);
    return point ? [{ id: f.id, point, label: `${f.name} · ${f.municipality.name}` }] : [];
  });
}

/**
 * Buscar: unha instalación leva á súa ficha (`facility`); se non, os filtros que cambian. O texto
 * escrito sen escoller suxestión filtra polo texto (`busca`); un concello deixa só as súas; un deporte márcao no filtro, coa súa chave (o buscador xa agrupa con
 * ela; un código ou id solto tamén se traduce); sen nada, todos os concellos. Sempre co día; a data
 * da folla do móbil segue escollida mentres non se cambie.
 */
export function searchChange(
  criteria: SearchCriteria,
  filters: CatalogFilters,
  facilities: readonly CatalogFacility[],
  key: (sport: SearchSport) => string,
): { readonly facility: CatalogFacility } | { readonly change: Partial<CatalogFilters> } | null {
  const choice = criteria.choice;
  if (choice?.kind === 'facility') {
    const facility = facilities.find((f) => f.id === choice.id);
    return facility ? { facility } : null;
  }
  const sport =
    choice?.kind === 'sport' ? facilities.flatMap((f) => f.sports).find((s) => sportKey(s) === choice.key) : undefined;
  return {
    change: {
      date: criteria.date,
      dateChosen: !!filters.dateChosen && criteria.date === filters.date,
      municipality: choice?.kind === 'municipality' ? choice.slug : null,
      text: choice ? '' : (criteria.text?.trim() ?? ''),
      ...(choice?.kind === 'sport' ? { sports: [sport ? key(sport) : choice.key] } : {}),
    },
  };
}

/** A clave do número do listado: «N instalacións» ou, coa zona do mapa, «N instalacións nesta zona». */
export function countKey(count: number, inZone: boolean): string {
  const many = count === 1 ? 'One' : 'Other';
  return inZone ? `facility.catalog.inZone.${many.toLowerCase()}` : `facility.publicList.count${many}`;
}

/** A do título da folla do mapa do móbil: coma o listado ou, cun filtro e sen zona, «N resultados». */
export function sheetKey(count: number, inZone: boolean, filtered: boolean): string {
  return inZone || !filtered ? countKey(count, inZone) : `facility.catalog.results.${count === 1 ? 'one' : 'other'}`;
}

/** O número da pílula «Buscar» (escritorio, ao baixar): o texto ou o concello, o día e os da folla. */
export function pillCount(
  { text, municipality, date }: Pick<CatalogFilters, 'text' | 'municipality' | 'date'>,
  sheet: number,
  today: IsoDate,
): number {
  return sheet + (text || municipality ? 1 : 0) + (date !== today ? 1 : 0);
}

/**
 * As instalacións das que se ensinan as actividades e as partidas: as do listado, só se hai un
 * filtro delas (texto, deportes, «Cuberta» ou zona do mapa); `null`, todas. O concello vai á parte
 * (tamén leva os eventos sen instalación, nun lugar do concello).
 */
export function sectionIds(
  { text, sports, covered }: CatalogFilters,
  zone: boolean,
  shown: readonly CatalogFacility[],
): ReadonlySet<string> | null {
  return text || sports.length || covered || zone ? new Set(shown.map((f) => f.id)) : null;
}

/** O resumo do buscado (a pílula do móbil): o texto ou o concello, os deportes marcados e o día. */
export function catalogSummary(
  { text, municipality, date }: CatalogFilters,
  municipalities: readonly { readonly slug: string; readonly name: string }[],
  options: readonly { readonly value: string; readonly label: string }[],
  value: readonly string[],
  today: IsoDate,
): { where: string | undefined; date: IsoDate; today: IsoDate; sports: string[] } {
  const where = text || municipalities.find((m) => m.slug === municipality)?.name;
  return { where, date, today, sports: options.filter((o) => value.includes(o.value)).map((o) => o.label) };
}
