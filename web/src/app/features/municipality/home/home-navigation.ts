// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import type { SearchCriteria } from '../../../shared/facility-search/facility-search';
import { madridToday } from '../../../shared/ui/calendar/madrid-time';

/** O filtro «Deporte» da barra da portada: os deportes marcados e «Cuberta». */
export interface HomeFilters {
  readonly sports: readonly string[];
  readonly covered: boolean;
}

const NO_FILTERS: HomeFilters = { sports: [], covered: false };

/**
 * Único punto onde a portada leva a outras pantallas:
 * - `facilityLink`: a ficha pública da instalación (`/:concello/instalacions/:id`); as tarxetas e
 *   as suxestións do buscador son ligazóns a ela.
 * - `search`: se se escolleu unha instalación, leva á súa ficha; se non, ao listado público
 *   (`/:concello/instalacions`) co deporte (`deporte`) ou o texto escrito sen escoller suxestión
 *   (`busca`), o filtro «Deporte» da barra (`deporte`, `cuberta`; un deporte escollido no texto vai no lugar dos marcados) e
 *   o día (`data`, se non é hoxe) no URL.
 */
@Injectable({ providedIn: 'root' })
export class HomeNavigation {
  private readonly router = inject(Router);

  /** Ruta da ficha pública dunha instalación. */
  readonly facilityLink = (slug: string, facilityId: string): readonly string[] => [
    '/',
    slug,
    'instalacions',
    facilityId,
  ];

  search(slug: string, criteria: SearchCriteria, filters: HomeFilters = NO_FILTERS): Promise<boolean> {
    const choice = criteria.choice;
    if (choice?.kind === 'facility') return this.router.navigate([...this.facilityLink(slug, choice.id)]);
    const queryParams: Record<string, string | string[]> = {};
    const sports = choice?.kind === 'sport' ? [choice.key] : filters.sports;
    if (sports.length) queryParams['deporte'] = sports.length === 1 ? sports[0] : [...sports];
    if (filters.covered) queryParams['cuberta'] = 'si';
    const text = choice ? '' : (criteria.text?.trim() ?? '');
    if (text) queryParams['busca'] = text;
    if (criteria.date !== madridToday()) queryParams['data'] = criteria.date;
    return this.router.navigate(['/', slug, 'instalacions'], { queryParams });
  }
}
