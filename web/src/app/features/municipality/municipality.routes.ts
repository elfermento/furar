// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { bookingStylesResolver } from '../../core/layout/booking-styles';
import { homeStylesResolver } from '../../core/layout/home-styles';

/** /:concello (a portada). */
export const municipalityRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'titles.home',
    // Portada a todo o ancho: cada sección pon a súa columna (ShellComponent).
    data: { layout: 'bleed' },
    // Os estilos da portada van nunha folla á parte (D27): a ruta agarda por ela. A das reservas
    // trae a folla «Filtros» e o resto de diálogos da portada.
    resolve: { home: homeStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./municipality-home.page').then((m) => m.MunicipalityHomePage),
  },
];
