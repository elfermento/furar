// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Params } from '@angular/router';
import type { TagTone } from '../../../shared/ui/tag/tag.component';
import type { Translate } from './activity-rows';
import type { ActivityEventResponse } from './activity.store';

export type { ActivityEventResponse };
export type ActivityActor = ActivityEventResponse['actor'];
export type ActivityTarget = NonNullable<ActivityEventResponse['target']>;
export type ActivityOutcome = ActivityEventResponse['outcome'];

/** De quen é o rexistro: o do concello (a xestión) ou o da plataforma. */
export type ActivityScope = 'municipality' | 'platform';

/**
 * Días como moito nun período escollido. A API admite 366 días de duración exacta; de medianoite
 * a medianoite en Europe/Madrid, un período que colle o cambio de hora de outubro dura unha hora
 * máis, así que o calendario queda nun día menos.
 */
export const MAX_RANGE_DAYS = 365;

/**
 * Todos os códigos de acción que se rexistran hoxe (inventario dos `static final String` de
 * auditoría en `api/`), por área: cada un ten `activity.actions.<código>` e `activity.done.<código>`
 * en gl e es (a proba desta carpeta compara o inventario cos servizos da API). Un código que non
 * estea aquí (dunha versión máis nova da API) sae co texto de reserva, nunca baleiro.
 */
const ACTIONS_BY_AREA: Readonly<Record<string, readonly string[]>> = {
  audit: ['export-blocked'],
  booking: [
    'book-for',
    'change',
    'cancel',
    'payment',
    'anonymize',
    'export',
    'dependents-read',
    'validate',
    'no-show',
    'attendance-undo',
    'waits-removed',
    'review-hide',
    'review-show',
    'open-match-hide',
    'open-match-show',
    'organization-create',
    'organization-update',
    'organization-archive',
    'organization-delete',
    'series-create',
    'series-end',
    'series-payment',
    'series-request-reject',
  ],
  customfield: ['create', 'update', 'deactivate', 'activate', 'delete'],
  event: [
    'create',
    'update',
    'cancel',
    'delete',
    'accept',
    'reject',
    'payment',
    'anonymize',
    'session-cancel',
    'end',
    'attendance',
    'waits-remove',
  ],
  incident: ['create', 'status', 'resolve', 'delete', 'photo-delete'],
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
  // Unha conta eliminada desde a web (ADR-0043): só cantas filas, nunca quen.
  people: ['account-close'],
  municipality: [
    'create',
    'update',
    'legal-texts',
    'sports-contact',
    'payment-notes',
    'no-show-policy',
    'open-match-policy',
    'install-hint',
    'open-data',
    'export',
    'import',
  ],
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
    // A plataforma faise xestora (ADR-0028, revisión do 2026-11-11).
    'join',
  ],
};
/** Códigos de acción escritos por área: a área, un punto e o nome. */
export function codes(byArea: Readonly<Record<string, readonly string[]>>): string[] {
  return Object.entries(byArea).flatMap(([area, names]) => names.map((name) => `${area}.${name}`));
}

export const KNOWN_ACTIONS: readonly string[] = codes(ACTIONS_BY_AREA);

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

/**
 * O ton do chip de cada rol na columna Rol e no detalle (fondo do ton e letra branca en negra):
 * Plataforma, Xestión e Recepción, cada un o seu, e ningún o do resultado («Feito» ou «Fallou»).
 */
export const ROLE_TONE: Readonly<Record<ActivityActor['roles'][number], TagTone>> = {
  PLATFORM_ADMIN: 'primary',
  MANAGER: 'info',
  STAFF: 'warn',
};

export const OUTCOME_TONE: Readonly<Record<ActivityOutcome, TagTone>> = { SUCCESS: 'success', FAILURE: 'danger' };

/** Un rol en chip: o texto e o ton. */
export interface RoleChip {
  readonly label: string;
  readonly tone: TagTone;
}

/**
 * Os roles en chip: os da persoa nese momento («Xestión», «Recepción», «Plataforma»); unha conta da
 * plataforma, «Plataforma»; sen rol, ningún.
 */
export function roleChips(event: ActivityEventResponse, t: Translate): RoleChip[] {
  const { actor } = event;
  const roles = actor.kind === 'MEMBER' ? [...new Set(actor.roles)] : [];
  if (!roles.length && actorView(actor).platform) roles.push('PLATFORM_ADMIN');
  return roles.map((role) => roleChip(role, t));
}

/** O chip dun rol: o mesmo na táboa, no detalle e nas opcións do filtro Rol. */
export function roleChip(role: ActivityActor['roles'][number], t: Translate): RoleChip {
  return {
    label: t(role === 'PLATFORM_ADMIN' ? 'activity.actor.platformTag' : 'staff.members.role.' + role),
    tone: ROLE_TONE[role],
  };
}
