// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { IsActiveMatchOptions } from '@angular/router';

/** Activa só coa ruta e o fragmento exactos (sen mirar os filtros do URL): «Inicio» non se marca no listado nin nunha sección da portada. */
export const EXACT_WITH_FRAGMENT: IsActiveMatchOptions = {
  paths: 'exact',
  queryParams: 'ignored',
  matrixParams: 'ignored',
  fragment: 'exact',
};

/** Quen ve o menú da xestión dun concello: Xestión ou Recepción (rol da API) ou a plataforma. */
export type ManageKind = 'manager' | 'staff' | 'platform';

export interface ManageLink {
  readonly key: string;
  readonly icon: 'grid' | 'calendar-month' | 'chart' | 'shield' | 'pencil' | 'user' | 'schedule';
  readonly path: string[];
  readonly exact: boolean;
}

/**
 * Destinos da xestión: os mesmos na barra superior e na inferior do móbil, nesta orde. Xestión ve
 * Instalacións · Reservas · Indicadores · Concello; Recepción, só Reservas (a súa entrada; maqueta
 * 50); a plataforma, Instalacións · Concello: configura o concello enteiro pero non ve datos da
 * veciñanza (ADR-0028, revisión do 2026-11-03).
 * «Concello» é unha sección con pestanas (Xeral, Aparencia, Servizo, Legal, Persoal, Campos
 * personalizados e Rexistro; maqueta 49), activa en calquera delas.
 */
export function manageLinks(slug: string, kind: ManageKind): ManageLink[] {
  const to = (...path: string[]) => ['/', slug, 'xestion', ...path];
  if (kind === 'staff') return [{ key: 'nav.bookings', icon: 'calendar-month', path: to('reservas'), exact: false }];
  const links: (ManageLink | false)[] = [
    { key: 'nav.facilities', icon: 'grid', path: to('instalacions'), exact: false },
    kind === 'manager' && { key: 'nav.bookings', icon: 'calendar-month', path: to('reservas'), exact: false },
    kind === 'manager' && { key: 'nav.indicators', icon: 'chart', path: to('indicadores'), exact: false },
    { key: 'nav.municipality', icon: 'shield', path: to('concello'), exact: false },
  ];
  return links.filter((link): link is ManageLink => link !== false);
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
