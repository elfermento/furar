// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';

/** /:concello (a portada). */
export const municipalityRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'titles.home',
    // Portada a todo o ancho: cada sección pon a súa columna (ShellComponent).
    data: { layout: 'bleed' },
    loadComponent: () => import('./municipality-home.page').then((m) => m.MunicipalityHomePage),
  },
];
