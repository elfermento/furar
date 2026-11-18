// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsoDate } from '../../../shared/ui/calendar/local-date';
import { addDays, formatShort } from '../../../shared/ui/calendar/local-date';
import { inMadrid } from '../../../shared/ui/calendar/madrid-time';
import type { ChipItem } from '../../../shared/ui/chip-list/chip-list.component';
import type { ColumnFilterOption } from '../../../shared/ui/column-filter/column-filter.component';
import type { ActivityEventResponse, ActivityScope, RoleChip } from './activity';
import { KNOWN_ACTIONS, actorView, roleChip, roleChips } from './activity';
import type { ActivityFilters } from './activity-filters';
import { ROLES } from './activity-filters';
import type { Translate } from './activity-rows';
import { changeView, facilityName, facilityOf, groupSummary, placeText, summaryText } from './activity-rows';
import type { StaffMemberResponse } from './activity.store';

/*
 * O que pinta a páxina do rexistro, xa en texto: as columnas cos seus
 * filtros, as filas e o detalle dunha fila. Funcións puras: a páxina dálles os datos e o tradutor.
 */

/** As columnas que filtran. */
export type ActivityColumn = 'date' | 'person' | 'role' | 'action' | 'place' | 'outcome';

/** Atallos da columna Data. */
export type DatePreset = 'today' | 'yesterday' | 'week';
export const DATE_PRESETS: readonly DatePreset[] = ['today', 'yesterday', 'week'];

/** Os días dun atallo (sen atallo, o último ano: sen período). */
export function presetRange(preset: DatePreset | '', today: IsoDate): { from: IsoDate | null; to: IsoDate | null } {
  switch (preset) {
    case 'today':
      return { from: today, to: today };
    case 'yesterday':
      return { from: addDays(today, -1), to: addDays(today, -1) };
    case 'week':
      return { from: addDays(today, -6), to: today };
    default:
      return { from: null, to: null };
  }
}

/** O atallo do período escollido, se o é. */
export function presetOf(filters: Pick<ActivityFilters, 'from' | 'to'>, today: IsoDate): DatePreset | null {
  if (!filters.from || !filters.to) return null;
  return (
    DATE_PRESETS.find((p) => {
      const range = presetRange(p, today);
      return range.from === filters.from && range.to === filters.to;
    }) ?? null
  );
}

/** O período en texto: o atallo («Hoxe») ou «01/10/2026 – 28/10/2026». */
export function periodText(filters: ActivityFilters, today: IsoDate, t: Translate): string {
  const preset = presetOf(filters, today);
  if (preset) return t('activity.filters.presets.' + preset);
  const { from, to } = filters;
  if (!from || !to) return '';
  return from === to ? formatShort(from) : `${formatShort(from)} – ${formatShort(to)}`;
}

/**
 * Unha persoa do filtro: a pertenza, con que se recoñecen as súas accións, o texto (o correo, coma
 * na táboa) e, debaixo, o nome.
 */
export interface PersonOption {
  readonly id: string;
  readonly name: string | null;
  readonly email: string | null;
  readonly label: string;
  readonly hint: string | null;
}

/**
 * O persoal que xa entrou (activo ou desactivado): quen nunca aceptou non fixo nada. Polo correo
 * (o nome debaixo); sen correo, polo nome. A anonimizada sae como «Persoa anonimizada»; a de
 * identidade bloqueada, «Persoa desactivada».
 */
export function peopleOptions(members: readonly StaffMemberResponse[], t: Translate): PersonOption[] {
  return members
    .filter((m) => m.status === 'ACTIVE' || (m.status === 'DEACTIVATED' && !!m.acceptedAt))
    .map((m) => {
      const hiddenIdentity = !!m.anonymized || !!m.blocked;
      const realName = hiddenIdentity ? null : m.name?.trim() || null;
      const email = hiddenIdentity ? null : m.email?.trim() || null;
      const name = m.anonymized
        ? t('activity.actor.anonymized')
        : m.blocked
          ? t('activity.actor.blocked')
          : email || realName || '';
      return {
        id: m.id,
        name: realName,
        email,
        label: m.status === 'DEACTIVATED' ? t('activity.filters.deactivated', { name }) : name,
        hint: email ? realName : null,
      };
    });
}

function isPerson(event: ActivityEventResponse, person: PersonOption): boolean {
  if (event.actor.kind !== 'MEMBER') return false;
  return person.email ? event.actor.email === person.email : !!person.name && event.actor.name === person.name;
}

/** Unha columna da táboa: o seu filtro, se o ten. */
export interface ActivityColumnView {
  readonly key: ActivityColumn | 'municipality';
  readonly label: string;
  /** Sen filtro (a plataforma non filtra por persoa nin por concello): só o texto. */
  readonly filter: boolean;
  readonly options: readonly ColumnFilterOption[];
  readonly selected: readonly string[];
  readonly marked: boolean;
  readonly searchable: boolean;
}

export interface ColumnsContext {
  readonly filters: ActivityFilters;
  readonly scope: ActivityScope;
  readonly today: IsoDate;
  /** As accións cargadas que pasan os filtros que non fai a API (para as contas). */
  readonly events: readonly ActivityEventResponse[];
  readonly people: readonly PersonOption[];
  readonly facilities: ReadonlyMap<string, string>;
  readonly t: Translate;
}

/**
 * As columnas (Data, Persoa, Rol, Acción, Onde, Resultado) cos seus filtros. Cada opción leva
 * cantas accións cargadas a teñen, só se a columna non filtra (se filtra, a API xa só trouxo o
 * escollido). Na plataforma, Persoa e Concello van sen filtro.
 */
export function buildColumns(ctx: ColumnsContext): ActivityColumnView[] {
  const { filters: f, t, events } = ctx;
  const count = (active: boolean, predicate: (e: ActivityEventResponse) => boolean) =>
    active ? null : events.filter(predicate).length;
  const column = (
    key: ActivityColumn,
    options: ColumnFilterOption[],
    selected: readonly string[],
    searchable = false,
    marked = selected.length > 0,
  ): ActivityColumnView => ({
    key,
    label: t('activity.col.' + key),
    filter: true,
    options,
    selected,
    marked,
    searchable,
  });
  const plain = (key: ActivityColumnView['key']): ActivityColumnView => ({
    key,
    label: t('activity.col.' + key),
    filter: false,
    options: [],
    selected: [],
    marked: false,
    searchable: false,
  });
  const inPreset = (e: ActivityEventResponse, preset: DatePreset) => {
    const { from, to } = presetRange(preset, ctx.today);
    const day = inMadrid(e.at).date;
    return !!from && !!to && day >= from && day <= to;
  };
  const preset = presetOf(f, ctx.today);
  const dated = !!f.from;
  const dates = column(
    'date',
    [
      { value: '', label: t('activity.filters.lastYear'), count: count(dated, () => true) },
      ...DATE_PRESETS.map((p) => ({
        value: p,
        label: t('activity.filters.presets.' + p),
        count: count(dated, (e) => inPreset(e, p)),
      })),
    ],
    preset ? [preset] : dated ? [] : [''],
    false,
    dated,
  );
  const people = ctx.people.map((p) => ({
    value: p.id,
    label: p.label,
    hint: p.hint,
    count: count(f.people.length > 0, (e) => isPerson(e, p)),
  }));
  const roles = ROLES.map((role) => ({
    value: role,
    ...roleChip(role, t),
    count: count(f.roles.length > 0, (e) => e.actor.roles.includes(role)),
  }));
  const codes = ctx.scope === 'platform' ? KNOWN_ACTIONS : KNOWN_ACTIONS.filter((c) => !c.startsWith('platform.'));
  const actions = codes
    .map((code) => ({
      value: code,
      label: t('activity.actions.' + code),
      count: count(f.actions.length > 0, (e) => e.action === code),
    }))
    .sort((a, b) => (b.count ?? 0) - (a.count ?? 0) || a.label.localeCompare(b.label));
  const places = [...ctx.facilities].map(([id, name]) => ({
    value: id,
    label: name,
    count: count(f.places.length > 0, (e) => facilityOf(e) === id),
  }));
  const outcomes = (['SUCCESS', 'FAILURE'] as const).map((outcome) => ({
    value: outcome,
    label: t('activity.outcome.' + outcome),
    count: count(!!f.outcome, (e) => e.outcome === outcome),
  }));
  const platform = ctx.scope === 'platform';
  return [
    dates,
    platform ? plain('person') : column('person', people, f.people, true),
    column('role', roles, f.roles),
    column('action', actions, f.actions, true),
    platform ? plain('municipality') : column('place', places, f.places, true),
    column('outcome', outcomes, f.outcome ? [f.outcome] : []),
  ];
}

/** Un chip dun filtro posto: a columna e o valor que quita. */
export type FilterChip = ChipItem & { readonly column: ActivityColumn; readonly value: string };

/** Os chips dos filtros postos, na orde das columnas («Acción: Cancelar unha reserva»). */
export function filterChips(columns: readonly ActivityColumnView[], period: string): FilterChip[] {
  const chips: FilterChip[] = [];
  for (const column of columns) {
    if (!column.filter || !column.marked) continue;
    const key = column.key as ActivityColumn;
    if (key === 'date') {
      chips.push({ key: 'date', column: key, value: '', label: `${column.label}: ${period}` });
      continue;
    }
    for (const value of column.selected) {
      const label = column.options.find((o) => o.value === value)?.label ?? value;
      chips.push({ key: `${key}:${value}`, column: key, value, label: `${column.label}: ${label}` });
    }
  }
  return chips;
}

/** Unha fila xa preparada: unha acción ou un grupo seguido da mesma persoa na mesma instalación. */
export interface ActivityRow {
  readonly id: string;
  readonly events: readonly ActivityEventResponse[];
  /** Instante ISO da acción máis nova (para `<time datetime>`). */
  readonly at: string;
  /** Só o día, «10/11/2026» en Europe/Madrid: a hora vai no detalle. */
  readonly when: string;
  /** A persoa na táboa: o correo (máis doado de ler e de buscar); sen correo, o nome ou o texto. */
  readonly person: string;
  /** O nome, para o detalle (sen nome, o correo ou o texto: «Persoa anonimizada»). */
  readonly name: string;
  /** O correo debaixo do nome no detalle, se o nome non é xa o correo. */
  readonly email: string | null;
  /** Os roles en texto (para buscar). */
  readonly role: string;
  /** Os roles en chip, cada un co seu ton; sen rol, ningún (vai «—»). */
  readonly roles: readonly RoleChip[];
  readonly summary: string;
  /** Accións do grupo (1 se é unha soa). */
  readonly count: number;
  readonly place: string | null;
  readonly outcome: ActivityEventResponse['outcome'];
  /** Os outros textos da fila (resumos do grupo, cambios, petición), para «Buscar no rexistro». */
  readonly search: readonly string[];
}

export function buildRow(
  events: readonly ActivityEventResponse[],
  scope: ActivityScope,
  facilities: ReadonlyMap<string, string>,
  t: Translate,
): ActivityRow {
  const first = events[0];
  const actor = actorView(first.actor);
  const grouped = events.length > 1;
  const changes = events.flatMap((e) => e.changes.map((c) => changeView(c, t)));
  const name = actor.name ?? t(actor.nameKey);
  const roles = roleChips(first, t);
  return {
    id: first.id,
    events,
    at: first.at,
    when: formatShort(inMadrid(first.at).date),
    person: actor.email ?? name,
    name,
    email: actor.email,
    role: roles.map((r) => r.label).join(', '),
    roles,
    summary: grouped ? groupSummary(events, facilityName(events, facilities), t) : summaryText(first, t),
    count: events.length,
    place: placeText(first, scope, facilities, t),
    outcome: events.some((e) => e.outcome === 'FAILURE') ? 'FAILURE' : 'SUCCESS',
    search: [
      ...(grouped ? events.map((e) => summaryText(e, t)) : []),
      ...changes.flatMap((c) => [c.field, c.before, c.after]),
      first.requestId,
    ].filter((s): s is string => !!s),
  };
}
