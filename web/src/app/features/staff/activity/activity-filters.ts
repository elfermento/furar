// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, isIsoDate, toUtc } from '../../../shared/ui/calendar/local-date';
import { madridWall } from '../../../shared/ui/calendar/madrid-time';
import type { ActivityActor, ActivityEventResponse, ActivityOutcome, ActivityScope } from './activity';
import { KNOWN_ACTIONS, MAX_RANGE_DAYS } from './activity';
import type { ListActivity$Params } from './activity.store';

/*
 * Os filtros do rexistro: no URL, na API e, o que a API non fai, nas filas
 * cargadas.
 */

/** Rol de quen actuou (o filtro «Rol»). */
export type ActivityRole = ActivityActor['roles'][number];
export const ROLES: readonly ActivityRole[] = ['MANAGER', 'STAFF', 'PLATFORM_ADMIN'];

/**
 * Os filtros tal como van no URL (en galego, ADR-0003): `?desde=&ata=&persoa=&rol=&accion=&onde=
 * &resultado=&buscar=`. Persoa, rol, acción e onde poden repetirse.
 */
export interface ActivityFilters {
  /** Primeiro día (incluído, Europe/Madrid); sen el, o último ano (o que dá a API). */
  readonly from: IsoDate | null;
  /** Último día (incluído). */
  readonly to: IsoDate | null;
  /** Pertenzas (`memberId`), tamén desactivadas. */
  readonly people: readonly string[];
  readonly roles: readonly ActivityRole[];
  /** Códigos de acción. */
  readonly actions: readonly string[];
  /** Instalacións (`facilityId`). */
  readonly places: readonly string[];
  readonly outcome: ActivityOutcome | null;
  /** «Buscar no rexistro»: o texto das filas cargadas. */
  readonly text: string;
}

export const NO_FILTERS: ActivityFilters = {
  from: null,
  to: null,
  people: [],
  roles: [],
  actions: [],
  places: [],
  outcome: null,
  text: '',
};

const OUTCOME_PARAM: Readonly<Record<ActivityOutcome, string>> = { SUCCESS: 'feito', FAILURE: 'fallou' };

const text = (value: unknown): string | null => (typeof value === 'string' && value.trim() ? value.trim() : null);

/** Os valores dun parámetro que pode repetirse, sen baleiros nin repetidos. */
const many = (value: unknown): string[] => {
  const list = Array.isArray(value) ? value : value === null || value === undefined ? [] : [value];
  return [...new Set(list.map(text).filter((v): v is string => !!v))];
};

/** Días dun período, contando os dous extremos. */
export function rangeDays(from: IsoDate, to: IsoDate): number {
  return Math.round((toUtc(to).getTime() - toUtc(from).getTime()) / 86_400_000) + 1;
}

export function validRange(from: unknown, to: unknown): from is IsoDate {
  return isIsoDate(from) && isIsoDate(to) && from <= to && rangeDays(from, to) <= MAX_RANGE_DAYS;
}

/**
 * Os filtros dun URL; o que non vale cae no valor por defecto. A plataforma non filtra por persoa
 * nin por instalación (o seu rexistro é de todos os concellos). `fallidos=1`, o URL de antes, é
 * «Fallou».
 */
export function filtersFromParams(params: Params, scope: ActivityScope): ActivityFilters {
  const range = validRange(params['desde'], params['ata']);
  const outcome =
    params['resultado'] === OUTCOME_PARAM.SUCCESS
      ? 'SUCCESS'
      : params['resultado'] === OUTCOME_PARAM.FAILURE || params['fallidos'] === '1'
        ? 'FAILURE'
        : null;
  return {
    from: range ? (params['desde'] as IsoDate) : null,
    to: range ? (params['ata'] as IsoDate) : null,
    people: scope === 'platform' ? [] : many(params['persoa']),
    roles: many(params['rol']).filter((r): r is ActivityRole => ROLES.includes(r as ActivityRole)),
    actions: many(params['accion']).filter((a) => KNOWN_ACTIONS.includes(a)),
    places: scope === 'platform' ? [] : many(params['onde']),
    outcome,
    text: text(params['buscar']) ?? '',
  };
}

/** O URL duns filtros, sen os valores por defecto. */
export function filtersToParams(filters: ActivityFilters): Params {
  const list = (values: readonly string[]) => (values.length ? [...values] : null);
  return {
    desde: filters.from,
    ata: filters.from ? filters.to : null,
    persoa: list(filters.people),
    rol: list(filters.roles),
    accion: list(filters.actions),
    onde: list(filters.places),
    resultado: filters.outcome ? OUTCOME_PARAM[filters.outcome] : null,
    buscar: filters.text.trim() || null,
    fallidos: null,
  };
}

/** Hai algún filtro das columnas ou do texto. */
export function hasFilters(filters: ActivityFilters): boolean {
  return (
    !!filters.from ||
    !!filters.outcome ||
    !!filters.text.trim() ||
    filters.people.length + filters.roles.length + filters.actions.length + filters.places.length > 0
  );
}

/** Un valor máis ou un menos dunha lista de filtros. */
export function toggle<T extends string>(values: readonly T[], value: T): T[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value];
}

/** Reloxo de parede de Europe/Madrid dun instante, en milisegundos «como se fose UTC». */
function madridWallMs(instant: number): number {
  const w = madridWall(instant);
  return Date.UTC(+w.year, +w.month - 1, +w.day, +w.hour, +w.minute, +w.second);
}

/**
 * A medianoite dun día en Europe/Madrid como instante ISO (o que pide a API): UTC+1 no inverno e
 * UTC+2 no verán. En Madrid a hora cambia ás 2 ou ás 3, nunca á medianoite, así que unha volta
 * abonda.
 */
export function madridMidnight(day: IsoDate): string {
  const guess = toUtc(day).getTime();
  return new Date(guess - (madridWallMs(guess) - guess)).toISOString();
}

/**
 * Os parámetros da API: `to` é excluínte, así que vai a medianoite do día seguinte ao último. A
 * plataforma só leva o período: o resto fíltrase na páxina (`matchesLocally`).
 */
export function apiParams(
  filters: ActivityFilters,
  scope: ActivityScope = 'municipality',
): Omit<ListActivity$Params, 'slug' | 'cursor'> {
  const period = {
    ...(filters.from ? { from: madridMidnight(filters.from) } : {}),
    ...(filters.from && filters.to ? { to: madridMidnight(addDays(filters.to, 1)) } : {}),
  };
  if (scope === 'platform') return period;
  return {
    ...period,
    ...(filters.people.length ? { memberId: [...filters.people] } : {}),
    ...(filters.roles.length ? { role: [...filters.roles] } : {}),
    ...(filters.actions.length ? { action: [...filters.actions] } : {}),
    ...(filters.places.length ? { facilityId: [...filters.places] } : {}),
    ...(filters.outcome ? { outcome: filters.outcome } : {}),
  };
}

/**
 * O que a API non filtra e se mira nas filas cargadas: na plataforma, rol, acción e resultado (a
 * súa API só ten o período). O texto vai á parte (`matchesText`).
 */
export function matchesLocally(event: ActivityEventResponse, filters: ActivityFilters, scope: ActivityScope): boolean {
  if (scope !== 'platform') return true;
  if (filters.outcome && event.outcome !== filters.outcome) return false;
  if (filters.actions.length && !filters.actions.includes(event.action)) return false;
  if (filters.roles.length && !event.actor.roles.some((r) => filters.roles.includes(r))) return false;
  return true;
}

/** Sen maiúsculas nin acentos. */
export function normalizeText(value: string): string {
  return value
    .normalize('NFD')
    .replace(/\p{Diacritic}/gu, '')
    .toLowerCase();
}

/** «Buscar no rexistro»: todas as palabras están nalgún dos textos da fila. */
export function matchesText(texts: readonly (string | null | undefined)[], query: string): boolean {
  const words = normalizeText(query).split(/\s+/).filter(Boolean);
  if (!words.length) return true;
  const haystack = normalizeText(texts.filter(Boolean).join(' '));
  return words.every((word) => haystack.includes(word));
}
