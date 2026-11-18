// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { ProviderToken, Signal } from '@angular/core';
import type { IsActiveMatchOptions } from '@angular/router';
import type { IconName } from '../../shared/ui/icon/icons';
import { PendingEventRequests } from '../events/pending-event-requests';
import { NewIncidents } from '../incidents/new-incidents';
import { PendingSeriesRequests } from '../organizations/pending-series-requests';

/** Activa só coa ruta e o fragmento exactos (sen mirar os filtros do URL): «Inicio» non se marca no listado nin nunha sección da portada. */
export const EXACT_WITH_FRAGMENT: IsActiveMatchOptions = {
  paths: 'exact',
  queryParams: 'ignored',
  matrixParams: 'ignored',
  fragment: 'exact',
};

/** Quen ve o menú da xestión dun concello: Xestión ou Recepción (rol da API) ou a plataforma. */
export type ManageKind = 'manager' | 'staff' | 'platform';

/**
 * Contador dunha pestana (p. ex. as incidencias abertas): un servizo `providedIn: 'root'` en
 * `core/` cun sinal. `null` ou 0, sen número. O apartado leva a suma dos das súas pestanas.
 */
export interface ManageCounter {
  readonly count: Signal<number | null>;
}

/** Unha pestana da configuración: unha páxina baixo `/:concello/xestion/`. */
export interface ManageTabConfig {
  /** Texto (clave de tradución): na pestana e, se é un apartado directo, no menú. */
  readonly key: string;
  /** Icona na barra inferior, cando a pestana é un apartado directo (Recepción). */
  readonly icon: IconName;
  /** Ruta baixo `xestion/` (un ou máis segmentos). */
  readonly path: string;
  /** Quen a ve (por defecto, os mesmos ca o seu apartado). */
  readonly kinds?: readonly ManageKind[];
  /** Outras páxinas que marcan esta pestana como a actual no menú (sen franxa de pestanas). */
  readonly also?: readonly string[];
  /** Número ao lado do texto (opcional). */
  readonly counter?: ProviderToken<ManageCounter>;
  /**
   * Que conta o número, para o lector de pantalla (clave `nav.count.<isto>One`/`Other`, co
   * número): «Reservas, 3 incidencias novas». Obrigatorio se hai `counter`.
   */
  readonly counterText?: string;
  /** «Validar QR»: no móbil, o botón central da barra inferior; non vai na franxa nin na barra. */
  readonly qr?: true;
}

/** Un apartado do menú da xestión: as súas pestanas, na orde da franxa. */
export interface ManageSectionConfig {
  readonly key: string;
  readonly icon: IconName;
  readonly kinds: readonly ManageKind[];
  readonly tabs: readonly ManageTabConfig[];
}

/**
 * O menú da xestión: apartados con pestanas de liña (`furar-tab-nav`) debaixo da
 * barra, só cando o apartado ten dúas ou máis pestanas á vista. Para engadir unha pestana abonda
 * unha liña aquí (e a súa ruta en app.routes.ts):
 *
 * ```ts
 * { key: <texto>, icon: 'warn', path: 'incidencias', counter: OpenIncidents },
 * ```
 *
 * Xestión ve Instalacións (Instalacións · Eventos) · Reservas (Reservas · Incidencias · Validar QR ·
 * Entidades) · Indicadores · Concello. Recepción non ten pestanas: cada pestana que ve é un apartado directo (Reservas ·
 * Validar QR). A plataforma, Instalacións · Concello: configura o concello enteiro pero non ve
 * datos da veciñanza (ADR-0028, revisión do 2026-11-03). «Concello» ten as súas propias pestanas
 *, dentro da súa páxina.
 */
export const MANAGE_SECTIONS: readonly ManageSectionConfig[] = [
  {
    key: 'nav.facilities',
    icon: 'grid',
    kinds: ['manager', 'platform'],
    tabs: [
      { key: 'nav.facilities', icon: 'grid', path: 'instalacions' },
      {
        key: 'nav.events',
        icon: 'local-activity',
        path: 'eventos',
        kinds: ['manager'],
        counter: PendingEventRequests,
        counterText: 'eventRequests',
      },
    ],
  },
  {
    key: 'nav.bookings',
    icon: 'calendar-month',
    kinds: ['manager', 'staff'],
    tabs: [
      { key: 'nav.bookings', icon: 'calendar-month', path: 'reservas', also: ['calendario'] },
      { key: 'nav.incidents', icon: 'warn', path: 'incidencias', counter: NewIncidents, counterText: 'incidents' },
      { key: 'nav.validate', icon: 'qr', path: 'validar', qr: true },
      {
        key: 'nav.organizations',
        icon: 'group',
        path: 'entidades',
        kinds: ['manager'],
        counter: PendingSeriesRequests,
        counterText: 'seriesRequests',
      },
    ],
  },
  {
    key: 'nav.indicators',
    icon: 'chart',
    kinds: ['manager'],
    tabs: [{ key: 'nav.indicators', icon: 'chart', path: 'indicadores' }],
  },
  {
    key: 'nav.municipality',
    icon: 'shield',
    kinds: ['manager', 'platform'],
    tabs: [{ key: 'nav.municipality', icon: 'shield', path: 'concello' }],
  },
];

/** Unha pestana xa resolta para un concello. */
export interface ManageTab {
  readonly key: string;
  readonly icon: IconName;
  readonly path: readonly string[];
  /** As rutas que a marcan como actual: a súa e as de `also`. */
  readonly paths: readonly (readonly string[])[];
  readonly counter: ProviderToken<ManageCounter> | null;
  /** Que conta, para o lector de pantalla (`nav.count.<isto>One`/`Other`). */
  readonly counterText: string | null;
  readonly qr: boolean;
}

/** Un apartado xa resolto: leva á súa primeira pestana. */
export interface ManageSection {
  readonly key: string;
  readonly icon: IconName;
  readonly path: readonly string[];
  readonly tabs: readonly ManageTab[];
}

/** Os apartados que ve `kind` no concello `slug`. Recepción: cada pestana, un apartado directo. */
export function manageSections(
  slug: string,
  kind: ManageKind,
  config: readonly ManageSectionConfig[] = MANAGE_SECTIONS,
): ManageSection[] {
  const to = (path: string) => ['/', slug, 'xestion', ...path.split('/')];
  const sections = config
    .filter((section) => section.kinds.includes(kind))
    .map((section) => ({
      key: section.key,
      icon: section.icon,
      tabs: section.tabs
        .filter((tab) => (tab.kinds ?? section.kinds).includes(kind))
        .map<ManageTab>((tab) => ({
          key: tab.key,
          icon: tab.icon,
          path: to(tab.path),
          paths: [tab.path, ...(tab.also ?? [])].map(to),
          counter: tab.counter ?? null,
          counterText: tab.counterText ?? null,
          qr: tab.qr === true,
        })),
    }))
    .filter((section) => section.tabs.length > 0);
  const flat =
    kind === 'staff'
      ? sections.flatMap((section) => section.tabs.map((tab) => ({ key: tab.key, icon: tab.icon, tabs: [tab] })))
      : sections;
  return flat.flatMap((section) => {
    const [first] = section.tabs;
    return first ? [{ ...section, path: first.path }] : [];
  });
}

/** O menú da xestión que toca no concello actual: o de Xestión, o de Recepción, o da plataforma ou ningún. */
export function manageKind(
  authenticated: boolean,
  role: 'MANAGER' | 'STAFF' | null,
  platformAdmin: boolean,
): ManageKind | null {
  if (!authenticated) return null;
  if (role === 'MANAGER') return 'manager';
  if (platformAdmin) return 'platform';
  return role === 'STAFF' ? 'staff' : null;
}

/** A ruta sen busca nin fragmento, en segmentos (`/naron/xestion/reservas?x=1` → naron, xestion, reservas). */
function segments(url: string): string[] {
  return (url.split(/[?#]/)[0] ?? '').split('/').filter(Boolean);
}

const joined = (path: readonly string[]) => path.filter((part) => part !== '/').join('/');

/** A pestana desta páxina: a súa ruta exacta (non unha ficha nin un formulario debaixo dela). */
export function currentTab(sections: readonly ManageSection[], url: string): ManageTab | null {
  const here = segments(url).join('/');
  for (const section of sections) {
    const tab = section.tabs.find((candidate) => joined(candidate.path) === here);
    if (tab) return tab;
  }
  return null;
}

/** O apartado actual: o dunha pestana súa ou de calquera páxina debaixo dela (ou de `also`). */
export function currentSection(sections: readonly ManageSection[], url: string): ManageSection | null {
  const here = segments(url);
  const under = (path: readonly string[]) => {
    const parts = path.filter((part) => part !== '/');
    return parts.length <= here.length && parts.every((part, i) => here[i] === part);
  };
  return sections.find((section) => section.tabs.some((tab) => tab.paths.some(under))) ?? null;
}
