// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Language } from '../../core/i18n/languages';
import type { IsoDate } from '../ui/calendar/local-date';
import { addDays, formatWeekdayDay } from '../ui/calendar/local-date';
import type { ComboboxOption } from '../ui/combobox/combobox.component';
import { foldText } from '../ui/combobox/combobox-search';

/** Deporte dunha instalación pública (a forma de `SportResponse` da API). */
export interface SearchSport {
  readonly code?: string;
  readonly customSportId?: string;
  readonly label: { readonly gl?: string; readonly es?: string };
}

/** O que o buscador precisa dunha instalación publicada (a forma de `PublicFacilityResponse`). */
export interface SearchFacility {
  readonly id: string;
  readonly name: string;
  readonly address: { readonly street: string; readonly streetNumber?: string };
  readonly sports: readonly SearchSport[];
  readonly cover: { readonly thumb: string };
}

/** O que se escolle no buscador: un deporte (código ou id do concello) ou unha instalación. */
export type SearchChoice =
  { readonly kind: 'sport'; readonly key: string } | { readonly kind: 'facility'; readonly id: string };

/** Criterios dunha busca (o día sempre; o que, se se escolleu). */
export interface SearchCriteria {
  readonly choice: SearchChoice | null;
  readonly date: IsoDate;
}

/** Títulos dos grupos de suxestións, xa traducidos. */
export interface SuggestionGroups {
  readonly sports: string;
  readonly facilities: string;
}

export const SPORTS_MAX = 5;
export const FACILITIES_MAX = 6;

export function sportKey(sport: SearchSport): string {
  return sport.code ?? sport.customSportId ?? '';
}

export function sportName(sport: SearchSport, lang: Language): string {
  return (lang === 'es' ? sport.label.es || sport.label.gl : sport.label.gl || sport.label.es) ?? '';
}

/** «Rúa Buxo, 12» (sen número, só a vía). */
export function addressLine(facility: Pick<SearchFacility, 'address'>): string {
  const { street, streetNumber } = facility.address;
  return streetNumber ? `${street}, ${streetNumber}` : street;
}

/**
 * Suxestións para o texto escrito, sen distinguir acentos nin maiúsculas: primeiro os deportes que
 * ten algunha instalación (en orde alfabética do idioma), despois as instalacións cuxo nome ou
 * deportes coinciden (na orde do servidor), coa portada en miniatura e o enderezo.
 */
export function suggestions(
  facilities: readonly SearchFacility[],
  query: string,
  lang: Language,
  groups: SuggestionGroups,
): ComboboxOption<SearchChoice>[] {
  const q = foldText(query.trim());
  if (!q) {
    return [];
  }
  const sports = new Map<string, string>();
  for (const facility of facilities) {
    for (const sport of facility.sports) {
      const name = sportName(sport, lang);
      if (foldText(name).includes(q)) {
        sports.set(sportKey(sport), name);
      }
    }
  }
  const collator = new Intl.Collator(lang);
  const sportOptions = [...sports.entries()]
    .sort(([, a], [, b]) => collator.compare(a, b))
    .slice(0, SPORTS_MAX)
    .map(([key, name]): ComboboxOption<SearchChoice> => ({
      value: { kind: 'sport', key },
      label: name,
      group: groups.sports,
      icon: 'search',
    }));
  const facilityOptions = facilities
    .filter((f) => foldText([f.name, ...f.sports.map((s) => sportName(s, lang))].join(' ')).includes(q))
    .slice(0, FACILITIES_MAX)
    .map((f): ComboboxOption<SearchChoice> => ({
      value: { kind: 'facility', id: f.id },
      label: f.name,
      detail: addressLine(f),
      group: groups.facilities,
      image: f.cover.thumb,
    }));
  return [...sportOptions, ...facilityOptions];
}

/** Texto do día no botón: «Hoxe», «Mañá» ou o día da semana curto e o número («xov 29»). */
export function dayLabel(
  date: IsoDate,
  today: IsoDate,
  lang: Language,
  words: { today: string; tomorrow: string },
): string {
  if (date === today) {
    return words.today;
  }
  if (date === addDays(today, 1)) {
    return words.tomorrow;
  }
  return formatWeekdayDay(date, lang);
}
