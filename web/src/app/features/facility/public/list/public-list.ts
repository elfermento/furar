// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { Language } from '../../../../core/i18n/languages';
import type { SearchFacility } from '../../../../shared/facility-search/facility-search';
import { sportKey, sportName } from '../../../../shared/facility-search/facility-search';
import type { IsoDate } from '../../../../shared/ui/calendar/local-date';

/** Vista do listado: reixa de tarxetas ou mapa (maqueta 16, opción A). */
export type ListView = 'list' | 'map';

/** Filtros do listado, que viven no URL (`?deporte=…&deporte=…&cuberta=si&data=…&vista=mapa`). */
export interface ListFilters {
  /** Deportes marcados (chave: código ou id do concello); baleiro, todos. */
  readonly sports: readonly string[];
  /** Só as cubertas. */
  readonly covered: boolean;
  /** Día dos ocos (nunca antes de hoxe). */
  readonly date: IsoDate;
  readonly view: ListView;
}

/** Un chip de filtro de deporte. */
export interface SportChip {
  readonly key: string;
  readonly name: string;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

/** Le os filtros do URL; o que non vale (data pasada ou mal escrita, vista descoñecida) queda no valor por defecto. */
export function readFilters(params: Params, today: IsoDate): ListFilters {
  const raw = params['deporte'] as string | string[] | undefined;
  const sports = [...new Set((Array.isArray(raw) ? raw : raw ? [raw] : []).filter(Boolean))];
  const date = params['data'] as string | undefined;
  return {
    sports,
    covered: params['cuberta'] === 'si',
    date: date && ISO_DATE.test(date) && date > today ? date : today,
    view: params['vista'] === 'mapa' ? 'map' : 'list',
  };
}

/** Parámetros do URL para uns filtros: só o que non é o valor por defecto (sen nada, `/instalacions`). */
export function filterParams(filters: ListFilters, today: IsoDate): Params {
  return {
    deporte: filters.sports.length ? [...filters.sports] : null,
    cuberta: filters.covered ? 'si' : null,
    data: filters.date !== today ? filters.date : null,
    vista: filters.view === 'map' ? 'mapa' : null,
  };
}

/** Os deportes das instalacións, sen repetir e en orde alfabética do idioma (os chips). */
export function sportChips(facilities: readonly SearchFacility[], lang: Language): SportChip[] {
  const sports = new Map<string, string>();
  for (const facility of facilities) {
    for (const sport of facility.sports) {
      const key = sportKey(sport);
      if (key && !sports.has(key)) sports.set(key, sportName(sport, lang));
    }
  }
  const collator = new Intl.Collator(lang);
  return [...sports.entries()].map(([key, name]) => ({ key, name })).sort((a, b) => collator.compare(a.name, b.name));
}

/** Sen deportes marcados, todas; se non, as que teñen algún deles. */
export function matchesSports(facility: SearchFacility, sports: readonly string[]): boolean {
  return !sports.length || facility.sports.some((sport) => sports.includes(sportKey(sport)));
}

/** Hai chip «Cuberta» se algunha instalación di que é cuberta (o dato é opcional). */
export function hasCoveredChip(facilities: readonly { covered?: boolean }[]): boolean {
  return facilities.some((facility) => facility.covered === true);
}

/** Sen o filtro, todas; con el, só as que din que son cubertas. */
export function matchesCovered(facility: { covered?: boolean }, covered: boolean): boolean {
  return !covered || facility.covered === true;
}

/** «09:30» a partir do minuto do día (reloxo de parede en Europe/Madrid, como o dá a API). */
export function slotTime(startMinute: number): string {
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${pad(Math.floor(startMinute / 60))}:${pad(startMinute % 60)}`;
}
