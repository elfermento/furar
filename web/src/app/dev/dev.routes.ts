// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { bookingStylesResolver } from '../core/layout/booking-styles';
import { indicatorStylesResolver } from '../core/layout/indicator-styles';
import { managementStylesResolver } from '../core/layout/management-styles';
import { leaveMunicipalityResolver } from '../core/municipality/municipality.resolver';

/** Definida polo build: false en produción, así o catálogo nin se empaqueta (dead code). */
declare const ngDevMode: unknown;

/** Catálogo de compoñentes de shared/ui (docs/deseno.md §8): só en desenvolvemento e nos tests. */
export const devRoutes: Routes =
  typeof ngDevMode === 'undefined' || ngDevMode
    ? [
        {
          path: 'catalogo-ui',
          title: 'titles.catalog',
          resolve: {
            leave: leaveMunicipalityResolver,
            styles: managementStylesResolver,
            booking: bookingStylesResolver,
            indicators: indicatorStylesResolver,
          },
          loadComponent: () => import('./catalog.page').then((m) => m.CatalogPage),
        },
      ]
    : [];
