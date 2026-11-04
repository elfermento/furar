// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, isIsoDate, toUtc } from '../../../shared/ui/calendar/local-date';
import { madridWall } from '../../../shared/ui/calendar/madrid-time';
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import type { ActivityEventResponse, ListActivity$Params } from './activity.store';

export type { ActivityEventResponse };
export type ActivityActor = ActivityEventResponse['actor'];
export type ActivityTarget = NonNullable<ActivityEventResponse['target']>;
export type ActivityOutcome = ActivityEventResponse['outcome'];

/** De quen é o rexistro: o do concello (a xestión) ou o da plataforma. */
export type ActivityScope = 'municipality' | 'platform';

/** Área do filtro: o prefixo do código da acción (o que admite a API). */
export type ActivityArea = 'staff' | 'facility' | 'booking' | 'municipality' | 'customfield' | 'audit';
export const AREAS: readonly ActivityArea[] = ['staff', 'facility', 'booking', 'municipality', 'customfield', 'audit'];

/**
 * Días como moito nun período escollido. A API admite 366 días de duración exacta; de medianoite
 * a medianoite en Europe/Madrid, un período que colle o cambio de hora de outubro dura unha hora
 * máis, así que o calendario queda nun día menos.
 */
export const MAX_RANGE_DAYS = 365;

/**
 * Todos os códigos de acción que se rexistran hoxe (inventario dos `static final String` de
 * auditoría en `api/`), por área: cada un ten `activity.actions.<código>` en gl e es. Un código que non estea
 * aquí (dunha versión máis nova da API) sae co texto de reserva, nunca baleiro.
 */
const ACTIONS_BY_AREA: Readonly<Record<string, readonly string[]>> = {
  audit: ['export-blocked'],
  booking: ['cancel', 'payment', 'anonymize'],
  customfield: ['create', 'update', 'deactivate', 'activate', 'delete'],
  facility: [
    'create',
    'rename',
    'status',
    'publish',
    'details',
    'description',
    'house-rules',
    'payment-notes',
    'address',
    'amenities',
    'delete',
    'use-create',
    'use-update',
    'use-archive',
    'season-create',
    'season-update',
    'season-delete',
    'exception-create',
    'exception-update',
    'exception-delete',
    'block-create',
    'block-update',
    'block-delete',
    'sport-create',
    'photo.upload',
    'photo.discard',
    'images.arrange',
  ],
  municipality: ['create', 'update', 'legal-texts', 'sports-contact', 'payment-notes', 'export', 'import'],
  // Sen filtro propio: só sae no rexistro da plataforma (ADR-0028).
  platform: ['first-admin'],
  // `revoke`: o nome antigo de `deactivate` (antes do 2026-10-30), para os eventos xa gardados.
  staff: [
    'invite',
    'invite-resend',
    'role-change',
    'deactivate',
    'reactivate',
    'revoke',
    'accept',
    'sign-in',
    'anonymize',
  ],
};
export const KNOWN_ACTIONS: readonly string[] = Object.entries(ACTIONS_BY_AREA).flatMap(([area, names]) =>
  names.map((name) => `${area}.${name}`),
);

/** Os filtros tal como van no URL: `?desde=&ata=&area=&persoa=&fallidos=1` (en galego, ADR-0003). */
export interface ActivityFilters {
  /** Primeiro día (incluído, Europe/Madrid); sen el, o último ano (o que dá a API). */
  readonly from: IsoDate | null;
  /** Último día (incluído). */
  readonly to: IsoDate | null;
  readonly area: ActivityArea | null;
  /** Pertenza (`memberId`), tamén desactivada. */
  readonly memberId: string | null;
  readonly failedOnly: boolean;
}

export const NO_FILTERS: ActivityFilters = { from: null, to: null, area: null, memberId: null, failedOnly: false };

const text = (value: unknown): string | null => (typeof value === 'string' && value.trim() ? value.trim() : null);

/** Días dun período, contando os dous extremos. */
export function rangeDays(from: IsoDate, to: IsoDate): number {
  return Math.round((toUtc(to).getTime() - toUtc(from).getTime()) / 86_400_000) + 1;
}

export function validRange(from: unknown, to: unknown): from is IsoDate {
  return isIsoDate(from) && isIsoDate(to) && from <= to && rangeDays(from, to) <= MAX_RANGE_DAYS;
}

/**
 * Os filtros dun URL; o que non vale cae no valor por defecto. A plataforma só filtra polo período
 * (a súa API non ten área, persoa nin resultado).
 */
export function filtersFromParams(params: Params, scope: ActivityScope): ActivityFilters {
  const range = validRange(params['desde'], params['ata']);
  const period = { from: range ? (params['desde'] as IsoDate) : null, to: range ? (params['ata'] as IsoDate) : null };
  if (scope === 'platform') return { ...NO_FILTERS, ...period };
  const area = text(params['area']);
  return {
    ...period,
    area: AREAS.includes(area as ActivityArea) ? (area as ActivityArea) : null,
    memberId: text(params['persoa']),
    failedOnly: params['fallidos'] === '1',
  };
}

/** O URL duns filtros, sen os valores por defecto. */
export function filtersToParams(filters: ActivityFilters): Params {
  return {
    desde: filters.from,
    ata: filters.from ? filters.to : null,
    area: filters.area,
    persoa: filters.memberId,
    fallidos: filters.failedOnly ? '1' : null,
  };
}

export function sameFilters(a: ActivityFilters, b: ActivityFilters): boolean {
  return (
    a.from === b.from &&
    a.to === b.to &&
    a.area === b.area &&
    a.memberId === b.memberId &&
    a.failedOnly === b.failedOnly
  );
}

export function hasFilters(filters: ActivityFilters): boolean {
  return !sameFilters(filters, NO_FILTERS);
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

/** Os parámetros da API: `to` é excluínte, así que vai a medianoite do día seguinte ao último. */
export function apiParams(filters: ActivityFilters): Omit<ListActivity$Params, 'slug' | 'cursor'> {
  return {
    ...(filters.from ? { from: madridMidnight(filters.from) } : {}),
    ...(filters.from && filters.to ? { to: madridMidnight(addDays(filters.to, 1)) } : {}),
    ...(filters.area ? { area: filters.area } : {}),
    ...(filters.memberId ? { memberId: filters.memberId } : {}),
    ...(filters.failedOnly ? { outcome: 'FAILURE' as const } : {}),
  };
}

const ACTION_PREFIX = 'activity.actions.';
const TARGET_PREFIX = 'activity.target.';

/** A clave do texto dunha acción ou, se o código é descoñecido, `null` (vai o texto de reserva). */
export function actionKey(code: string): string | null {
  return KNOWN_ACTIONS.includes(code) ? ACTION_PREFIX + code : null;
}

/** Quen fixo a acción, para pintar: un nome literal ou unha clave, e os roles. */
export interface ActorView {
  /** Nome (ou correo) da persoa; `null`, vai o texto de `nameKey`. */
  readonly name: string | null;
  readonly nameKey: string;
  /** Correo debaixo do nome, se o nome non é xa o correo. */
  readonly email: string | null;
  readonly roleKeys: readonly string[];
  /** Acción feita como administración da plataforma (márcase na vista do concello). */
  readonly platform: boolean;
}

const ROLE_KEYS: Readonly<Record<ActivityActor['roles'][number], string>> = {
  MANAGER: 'staff.members.role.MANAGER',
  STAFF: 'staff.members.role.STAFF',
  PLATFORM_ADMIN: 'activity.actor.platform',
};

/**
 * Persoa e rol: a do persoal co seu nome (ou o correo; sen os dous, «Persoa anonimizada»; coa
 * identidade bloqueada, «Persoa desactivada») e os roles que tiña nese momento; unha conta da plataforma, «Administración da plataforma»; unha
 * tarefa de Furar (a conservación do persoal), «Furar (tarefa automática)»; outra conta, «Conta
 * sen rol».
 */
export function actorView(actor: ActivityActor): ActorView {
  const platform = actor.kind === 'PLATFORM' || actor.roles.includes('PLATFORM_ADMIN');
  if (actor.kind !== 'MEMBER') {
    const nameKey =
      actor.kind === 'PLATFORM'
        ? 'activity.actor.platform'
        : actor.kind === 'SYSTEM'
          ? 'activity.actor.system'
          : 'activity.actor.other';
    return { name: null, nameKey, email: null, roleKeys: [], platform };
  }
  const roleKeys = actor.roles.map((role) => ROLE_KEYS[role]);
  if (actor.blocked) {
    return { name: null, nameKey: 'activity.actor.blocked', email: null, roleKeys, platform };
  }
  const name = actor.name?.trim() || actor.email?.trim() || null;
  const email = actor.name?.trim() && actor.email ? actor.email : null;
  return { name, nameKey: 'activity.actor.anonymized', email, roleKeys, platform };
}

/** Sobre que foi: o tipo, un texto (nome da instalación, correo) e unha ligazón se a hai. */
export interface TargetView {
  readonly kindKey: string;
  readonly text: string | null;
  readonly link: readonly string[] | null;
  /** Os parámetros da ligazón, se os leva (a reserva que abrir). */
  readonly query?: Params;
}

/**
 * A instalación leva o seu nome (da lista do concello) e ligazón á súa ficha, só na vista do
 * concello e se aínda existe; a persoa do persoal, o seu correo; o concello, na vista da
 * plataforma, o seu slug. A reserva, só na vista do concello e para quen ve as reservas (o xestor;
 * a plataforma le o rexistro do concello pero non ve reservas, ADR-0028), leva a «Reservas» co seu detalle
 * aberto (`?reserva=`), porque non ten páxina propia; só se a acción saíu ben: unha que fallou pode
 * levar o id dunha reserva que non existe, e na fase 1 as reservas nin se borran nin se anonimizan.
 * A foto, só o tipo.
 */
export function targetView(
  event: ActivityEventResponse,
  scope: ActivityScope,
  slug: string | null,
  facilities: ReadonlyMap<string, string>,
  bookingsLink = true,
): TargetView {
  const target = event.target;
  if (!target) return { kindKey: TARGET_PREFIX + 'none', text: null, link: null };
  const kindKey = TARGET_PREFIX + target.kind;
  switch (target.kind) {
    case 'FACILITY': {
      const name = facilities.get(target.id) ?? null;
      const link = scope === 'municipality' && slug && name ? ['/', slug, 'xestion', 'instalacions', target.id] : null;
      return { kindKey, text: name, link };
    }
    case 'STAFF_MEMBER':
      return { kindKey, text: target.memberEmail ?? null, link: null };
    case 'BOOKING': {
      const linked = bookingsLink && scope === 'municipality' && slug && event.outcome === 'SUCCESS';
      return linked
        ? { kindKey, text: null, link: ['/', slug, 'xestion', 'reservas'], query: { reserva: target.id } }
        : { kindKey, text: null, link: null };
    }
    case 'MUNICIPALITY':
      return { kindKey, text: scope === 'platform' ? (event.municipality ?? null) : null, link: null };
    default:
      return { kindKey, text: null, link: null };
  }
}

export const OUTCOME_TONE: Readonly<Record<ActivityOutcome, TagTone>> = { SUCCESS: 'success', FAILURE: 'danger' };
