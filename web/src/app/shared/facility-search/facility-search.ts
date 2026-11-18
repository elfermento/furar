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
  /** O seu concello, só na portada común (ADR-0030): búscase por el e vai diante do enderezo. */
  readonly municipality?: SearchMunicipality;
}

/** O concello dunha instalación na portada común. */
export interface SearchMunicipality {
  readonly slug: string;
  readonly name: string;
}

/**
 * O que se escolle no buscador: un deporte (código ou id do concello), unha instalación ou, na
 * portada común, un concello.
 */
export type SearchChoice =
  | { readonly kind: 'sport'; readonly key: string }
  | { readonly kind: 'facility'; readonly id: string }
  | { readonly kind: 'municipality'; readonly slug: string };

/**
 * Criterios dunha busca: o día sempre; o que, se se escolleu unha suxestión; se non, o texto
 * escrito (`text`, sen espazos nos extremos), que filtra a lista ({@link matchesText}).
 */
export interface SearchCriteria {
  readonly choice: SearchChoice | null;
  readonly date: IsoDate;
  readonly text?: string;
}

/** Títulos dos grupos de suxestións, xa traducidos. */
export interface SuggestionGroups {
  readonly sports: string;
  readonly facilities: string;
  /** Só na portada común; sen el, os concellos non se suxiren. */
  readonly municipalities?: string;
}

export const SPORTS_MAX = 5;
export const MUNICIPALITIES_MAX = 5;
export const FACILITIES_MAX = 6;

export function sportKey(sport: SearchSport): string {
  return sport.code ?? sport.customSportId ?? '';
}

export function sportName(sport: SearchSport, lang: Language): string {
  return (lang === 'es' ? sport.label.es || sport.label.gl : sport.label.gl || sport.label.es) ?? '';
}

/** O texto dunha escolla do buscador (a instalación, o concello ou o deporte), para o campo. */
export function choiceText(
  choice: SearchChoice,
  facilities: readonly SearchFacility[],
  lang: Language,
  key: (sport: SearchSport) => string = sportKey,
): string {
  if (choice.kind === 'facility') return facilities.find((f) => f.id === choice.id)?.name ?? '';
  if (choice.kind === 'municipality') {
    return facilities.find((f) => f.municipality?.slug === choice.slug)?.municipality?.name ?? '';
  }
  const sport = facilities.flatMap((f) => f.sports).find((s) => key(s) === choice.key);
  return sport ? sportName(sport, lang) : '';
}

/** «Rúa Buxo, 12» (sen número, só a vía). */
export function addressLine(facility: Pick<SearchFacility, 'address'>): string {
  const { street, streetNumber } = facility.address;
  return streetNumber ? `${street}, ${streetNumber}` : street;
}

/** «Narón · Rúa Buxo, 12» na portada común; sen concello, só o enderezo. */
export function placeLine(facility: Pick<SearchFacility, 'address' | 'municipality'>): string {
  const address = addressLine(facility);
  const town = facility.municipality?.name;
  // «Vilardevós · Vilardevós» dise unha vez (sen mirar maiúsculas nin acentos).
  if (!town || foldText(town) === foldText(address)) return address;
  return `${town} · ${address}`;
}

/** Os concellos das instalacións, sen repetir e en orde alfabética do idioma. */
export function municipalitiesOf(facilities: readonly SearchFacility[], lang: Language): SearchMunicipality[] {
  const found = new Map<string, SearchMunicipality>();
  for (const { municipality } of facilities) {
    if (municipality && !found.has(municipality.slug)) found.set(municipality.slug, municipality);
  }
  const collator = new Intl.Collator(lang);
  return [...found.values()].sort((a, b) => collator.compare(a.name, b.name));
}

/**
 * Suxestións para o texto escrito, sen distinguir acentos nin maiúsculas: primeiro os deportes que
 * ten algunha instalación (en orde alfabética do idioma), despois as instalacións cuxo nome ou
 * deportes coinciden (na orde do servidor), coa portada en miniatura e o enderezo. Na portada común
 * (`groups.municipalities`), tamén os concellos cuxo nome coincide, entre os deportes e as
 * instalacións, e as instalacións atópanse tamén polo nome do seu concello.
 *
 * Cada deporte sae unha vez por chave (`key`): por defecto, o código ou o id do concello; a portada
 * común pasa a súa, que une os propios co mesmo nome de varios concellos (queda o primeiro nome).
 */
export function suggestions(
  facilities: readonly SearchFacility[],
  query: string,
  lang: Language,
  groups: SuggestionGroups,
  key: (sport: SearchSport) => string = sportKey,
): ComboboxOption<SearchChoice>[] {
  const q = foldText(query.trim());
  if (!q) {
    return [];
  }
  const sports = new Map<string, string>();
  for (const facility of facilities) {
    for (const sport of facility.sports) {
      const name = sportName(sport, lang);
      if (foldText(name).includes(q) && !sports.has(key(sport))) {
        sports.set(key(sport), name);
      }
    }
  }
  const collator = new Intl.Collator(lang);
  const sportOptions = [...sports.entries()]
    .sort(([, a], [, b]) => collator.compare(a, b))
    .slice(0, SPORTS_MAX)
    .map(([chosen, name]): ComboboxOption<SearchChoice> => ({
      value: { kind: 'sport', key: chosen },
      label: name,
      group: groups.sports,
      icon: 'search',
    }));
  const municipalityOptions =
    groups.municipalities === undefined
      ? []
      : municipalitiesOf(facilities, lang)
          .filter((m) => foldText(m.name).includes(q))
          .slice(0, MUNICIPALITIES_MAX)
          .map((m): ComboboxOption<SearchChoice> => ({
            value: { kind: 'municipality', slug: m.slug },
            label: m.name,
            group: groups.municipalities,
            icon: 'location',
          }));
  const facilityOptions = facilities
    .filter((f) =>
      foldText([f.name, f.municipality?.name ?? '', ...f.sports.map((s) => sportName(s, lang))].join(' ')).includes(q),
    )
    .slice(0, FACILITIES_MAX)
    .map((f): ComboboxOption<SearchChoice> => ({
      value: { kind: 'facility', id: f.id },
      label: f.name,
      detail: placeLine(f),
      group: groups.facilities,
      image: f.cover.thumb,
    }));
  return [...sportOptions, ...municipalityOptions, ...facilityOptions];
}

/**
 * Texto libre do buscador («torre»): cada palabra ten que estar no nome, no concello, no enderezo
 * ou nalgún deporte da instalación, sen distinguir acentos nin maiúsculas. Sen texto, todas.
 */
export function matchesText(
  facility: Pick<SearchFacility, 'name' | 'address' | 'sports' | 'municipality'>,
  text: string,
  lang: Language,
): boolean {
  const words = foldText(text).split(/\s+/).filter(Boolean);
  if (!words.length) return true;
  const haystack = foldText(
    [facility.name, placeLine(facility), ...facility.sports.map((s) => sportName(s, lang))].join(' '),
  );
  return words.every((word) => haystack.includes(word));
}

/** O texto buscado como chip que se quita (`furar-chip-list`): «torre» ×; sen texto, ningún. */
export function searchTextChips(text: string): { readonly key: string; readonly label: string }[] {
  return text ? [{ key: 'text', label: `«${text}»` }] : [];
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

/** Un chip de filtro de deporte. */
export interface SportChip {
  readonly key: string;
  readonly name: string;
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

/** Valor de «Cuberta» no filtro «Deporte» da barra: non pode coincidir co código nin co id dun deporte. */
export const COVERED_OPTION = ':covered';

/** As opcións marcadas do filtro «Deporte»: os deportes e «Cuberta». */
export function splitSportFilter(values: readonly string[]): { sports: readonly string[]; covered: boolean } {
  return { sports: values.filter((value) => value !== COVERED_OPTION), covered: values.includes(COVERED_OPTION) };
}

/** As opcións marcadas a partir dos deportes e de «Cuberta» (o valor do filtro «Deporte»). */
export function sportFilterValue(sports: readonly string[], covered: boolean): string[] {
  return covered ? [...sports, COVERED_OPTION] : [...sports];
}

/**
 * Opcións do filtro «Deporte» da barra: os deportes (chips) e, ao final, «Cuberta» se algunha
 * instalación o di (`coveredLabel`, xa traducido; `null`, sen ela).
 */
export function sportFilterOptions(
  chips: readonly SportChip[],
  coveredLabel: string | null,
): { readonly value: string; readonly label: string }[] {
  const options = chips.map((chip) => ({ value: chip.key, label: chip.name }));
  return coveredLabel === null ? options : [...options, { value: COVERED_OPTION, label: coveredLabel }];
}
