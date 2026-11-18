// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { formatShort } from '../../../shared/ui/calendar/local-date';
import type { ActivityActor, ActivityEventResponse, ActivityScope } from './activity';
import { actionKey, codes } from './activity';

/*
 * As filas do rexistro: o resumo curto de cada acción, o agrupado das
 * accións seguidas na mesma instalación, «Onde» e o texto de cada cambio (antes → despois).
 */

/** Traduce unha clave (a de Transloco, ou unha falsa nas probas). */
export type Translate = (key: string, params?: Record<string, unknown>) => string;

/**
 * Accións cuxo resumo leva o nome do obxecto (`label`, gardado desde o 2026-11-10): «Publicou
 * «Pista 1»», «Cambiou a tempada «Inverno»». As outras accións da instalación non o levan: o nome
 * xa vai en «Onde».
 */
const NAMED_ACTIONS: ReadonlySet<string> = new Set(
  codes({
    facility: [
      'create',
      'rename',
      'publish',
      'status',
      'delete',
      'use-create',
      'use-update',
      'use-archive',
      'season-create',
      'season-update',
      'season-delete',
      'sport-create',
    ],
    customfield: ['create', 'update', 'deactivate', 'activate', 'delete'],
  }),
);

/** Accións cuxo `label` é o nome da propia instalación (non dun uso ou dunha tempada). */
const FACILITY_LABEL_ACTIONS: ReadonlySet<string> = new Set(
  codes({
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
    ],
  }),
);

/** Accións sen lugar: entrar, aceptar a invitación e engadirse como xestor. */
const NO_PLACE: ReadonlySet<string> = new Set(codes({ staff: ['sign-in', 'accept', 'join'] }));

const lowerFirst = (text: string) => text.charAt(0).toLocaleLowerCase() + text.slice(1);

/**
 * O resumo curto dunha acción, en pasado: «Cancelou unha reserva», «Cambiou a tempada «Inverno»»;
 * se fallou, «Intentou cancelar unha reserva»; un código descoñecido, o texto de reserva.
 */
export function summaryText(event: ActivityEventResponse, t: Translate): string {
  const key = actionKey(event.action);
  if (!key) return t('activity.unknownAction', { code: event.action });
  if (event.outcome === 'FAILURE') return t('activity.failed', { action: lowerFirst(t(key)) });
  const label = event.label?.trim();
  if (label && NAMED_ACTIONS.has(event.action)) return t('activity.doneNamed.' + event.action, { label });
  return t('activity.done.' + event.action);
}

/** A instalación dunha acción: a dos detalles (desde o 2026-11-10) ou o obxecto, se o é. */
export function facilityOf(event: ActivityEventResponse): string | null {
  return event.facilityId ?? (event.target?.kind === 'FACILITY' ? event.target.id : null);
}

/** O nome da instalación: o actual da lista ou, se xa non está, o gardado coa acción. */
export function facilityName(
  events: readonly ActivityEventResponse[],
  facilities: ReadonlyMap<string, string>,
): string | null {
  const id = events.map(facilityOf).find((f) => !!f);
  const current = id ? facilities.get(id) : undefined;
  if (current) return current;
  return events.find((e) => FACILITY_LABEL_ACTIONS.has(e.action) && e.label?.trim())?.label?.trim() ?? null;
}

/** Ata canto entre dúas accións seguidas dun grupo. */
export const GROUP_GAP_MINUTES = 30;

const actorKey = (actor: ActivityActor) =>
  [actor.kind, actor.name ?? '', actor.email ?? '', actor.blocked, [...actor.roles].sort().join(',')].join('|');

/**
 * Agrupa as accións seguidas (na orde da lista, da máis nova á máis vella) da mesma persoa na
 * mesma instalación con menos de {@link GROUP_GAP_MINUTES} minutos entre cada unha: «Creou e
 * publicou «Pista 1» · 9 cambios». Faise sobre as filas xa cargadas, así que un grupo partido entre
 * dúas páxinas xúntase ao cargar a seguinte.
 */
export function groupEvents(events: readonly ActivityEventResponse[]): ActivityEventResponse[][] {
  const groups: ActivityEventResponse[][] = [];
  for (const event of events) {
    const group = groups.at(-1);
    const previous = group?.at(-1);
    if (group && previous && together(previous, event)) group.push(event);
    else groups.push([event]);
  }
  return groups;
}

function together(newer: ActivityEventResponse, older: ActivityEventResponse): boolean {
  const facility = facilityOf(older);
  return (
    newer.action.startsWith('facility.') &&
    older.action.startsWith('facility.') &&
    !!facility &&
    facility === facilityOf(newer) &&
    newer.municipality === older.municipality &&
    actorKey(newer.actor) === actorKey(older.actor) &&
    Date.parse(newer.at) - Date.parse(older.at) <= GROUP_GAP_MINUTES * 60_000
  );
}

/** O resumo dun grupo: «Creou e publicou «X»», «Creou «X»» ou «Cambiou «X»». */
export function groupSummary(events: readonly ActivityEventResponse[], name: string | null, t: Translate): string {
  const actions = new Set(events.map((e) => e.action));
  const [create, publish] = codes({ facility: ['create', 'publish'] });
  const key = actions.has(create) ? (actions.has(publish) ? 'createdPublished' : 'created') : 'changed';
  return t('activity.group.' + key, { name: name ?? t('activity.target.FACILITY') });
}

/** Onde: a instalación, «Persoal», «Concello»…; na plataforma, o concello. */
export function placeText(
  event: ActivityEventResponse,
  scope: ActivityScope,
  facilities: ReadonlyMap<string, string>,
  t: Translate,
): string | null {
  if (scope === 'platform') return event.municipality ?? null;
  if (facilityOf(event)) return facilityName([event], facilities) ?? t('activity.target.FACILITY');
  if (NO_PLACE.has(event.action)) return null;
  const area = event.action.slice(0, event.action.indexOf('.'));
  const key = 'activity.areas.' + area;
  const translated = t(key);
  return translated === key ? null : translated;
}

/**
 * Un cambio xa en texto: o campo, o valor de antes e o de despois. `created`: antes non había nada
 * (vai «—» sen riscar). `hidden`: dato persoal ou texto longo, só «cambiou». `more`: «E 3 cambios
 * máis», só o texto.
 */
export interface ChangeView {
  readonly field: string;
  readonly before: string;
  readonly after: string;
  readonly created: boolean;
  readonly hidden: boolean;
  readonly more: boolean;
}

const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;
const DATE_INSIDE = /\b(\d{4})-(\d{2})-(\d{2})\b/g;
const CODES = /^[A-Z][A-Z_]*(,[A-Z][A-Z_]*)*$/;

/** O texto dun valor gardado: códigos traducidos, datas en dd/mm/aaaa, «—» se non había. */
export function valueText(field: string, value: string | null | undefined, t: Translate): string {
  if (value === null || value === undefined || value === '') return '—';
  const lookup = (code: string) => {
    for (const key of [`activity.values.${field}.${code}`, `activity.values.${code}`]) {
      const translated = t(key);
      if (translated !== key) return translated;
    }
    return code;
  };
  if (value === 'true' || value === 'false') return lookup(value);
  if (CODES.test(value)) return value.split(',').map(lookup).join(', ');
  if (ISO_DATE.test(value)) return formatShort(value as IsoDate);
  return value.replace(DATE_INSIDE, (_, y: string, m: string, d: string) => `${d}/${m}/${y}`);
}

/** O texto dun cambio; un campo descoñecido (dunha versión máis nova) sae como «Outro dato». */
export function changeView(change: ActivityEventResponse['changes'][number], t: Translate): ChangeView {
  const key = 'activity.fields.' + change.field;
  const translated = t(key, change.field === 'more' ? { count: change.after } : undefined);
  const name = translated === key ? t('activity.fields.other') : translated;
  const more = change.field === 'more';
  const shown = !change.hidden && !more;
  return {
    field: name,
    before: shown ? valueText(change.field, change.before, t) : '',
    after: shown ? valueText(change.field, change.after, t) : '',
    created: shown && (change.before === null || change.before === undefined || change.before === ''),
    // Oculto (dato persoal ou texto longo): só «Teléfono · cambiou».
    hidden: !!change.hidden && !more,
    more,
  };
}
