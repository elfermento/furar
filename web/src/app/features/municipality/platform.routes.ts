// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';

/** /plataforma/... (só PLATFORM_ADMIN; a garda está en app.routes). */
export const platformRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'concellos/novo' },
  {
    path: 'concellos/novo',
    title: 'titles.create',
    loadComponent: () => import('./municipality-create.page').then((m) => m.MunicipalityCreatePage),
  },
];
