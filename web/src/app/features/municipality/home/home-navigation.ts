// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable, inject } from '@angular/core';
import { Router } from '@angular/router';
import type { SearchCriteria } from '../../../shared/facility-search/facility-search';
import { localToday } from '../../../shared/ui/calendar/local-date';

/**
 * Único punto onde a portada leva a outras pantallas:
 * - `facilityLink`: a ficha pública da instalación (`/:concello/instalacions/:id`); as tarxetas e
 *   as suxestións do buscador son ligazóns a ela.
 * - `search`: se se escolleu unha instalación, leva á súa ficha; se non, ao listado público
 *   (`/:concello/instalacions`) co deporte (`deporte`) e o día (`data`, se non é hoxe) no URL.
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

  search(slug: string, criteria: SearchCriteria): Promise<boolean> {
    const choice = criteria.choice;
    if (choice?.kind === 'facility') return this.router.navigate([...this.facilityLink(slug, choice.id)]);
    const queryParams: Record<string, string> = {};
    if (choice?.kind === 'sport') queryParams['deporte'] = choice.key;
    if (criteria.date !== localToday()) queryParams['data'] = criteria.date;
    return this.router.navigate(['/', slug, 'instalacions'], { queryParams });
  }
}
