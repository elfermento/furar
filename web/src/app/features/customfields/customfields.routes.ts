// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';

/** /:concello/xestion/concello/campos, pestana da sección «Concello» (a garda está en app.routes). */
export const customFieldsRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'titles.customFields',
    loadComponent: () => import('./custom-fields-admin.page').then((m) => m.CustomFieldsAdminPage),
  },
  {
    path: 'novo',
    title: 'titles.customFieldNew',
    loadComponent: () => import('./custom-field-edit.page').then((m) => m.CustomFieldEditPage),
  },
  {
    path: ':id',
    title: 'titles.customFieldEdit',
    loadComponent: () => import('./custom-field-edit.page').then((m) => m.CustomFieldEditPage),
  },
];
