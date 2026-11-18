// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { managerGuard } from '../../core/auth/guards';

/** /:concello/@route@ (lazy desde app.routes.ts). Alta e edición só para quen xestiona (a API decide). */
export const @name@Routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'titles.@name@',
    loadComponent: () => import('./@name-kebab@-list.page').then((m) => m.@Name@ListPage),
  },
  {
    path: 'novo',
    canActivate: [managerGuard],
    title: 'titles.@name@New',
    loadComponent: () => import('./@name-kebab@-edit.page').then((m) => m.@Name@EditPage),
  },
  {
    path: ':id',
    canActivate: [managerGuard],
    title: 'titles.@name@Edit',
    loadComponent: () => import('./@name-kebab@-edit.page').then((m) => m.@Name@EditPage),
  },
];
