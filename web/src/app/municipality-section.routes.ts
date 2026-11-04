// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { activityGuard, staffGuard } from './core/auth/guards';
import { leaveTabGuard } from './core/unsaved-changes/leave-tab.guard';

/**
 * Pestanas da sección «Concello» (/:concello/xestion/concello/…, maqueta 49), cargadas á parte coa
 * sección: cada unha co seu URL. Xeral, Aparencia, Servizo e Legal son formularios co seu
 * «Gardar» (saír cun cambio sen gardar pregunta, `leaveTabGuard`); Persoal (D4-14), Campos
 * personalizados e Rexistro de actividade (D4-16) son as súas páxinas de sempre (`data.section`:
 * o h1 é o da sección). Aquí, e non nunha feature, porque cargan páxinas de varias.
 */
export const municipalitySectionRoutes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'xeral' },
  {
    path: 'xeral',
    title: 'titles.municipalityGeneral',
    canDeactivate: [leaveTabGuard],
    loadComponent: () => import('./features/municipality/section/general.tab').then((m) => m.GeneralTab),
  },
  {
    path: 'aparencia',
    title: 'titles.municipalityAppearance',
    canDeactivate: [leaveTabGuard],
    loadComponent: () => import('./features/municipality/section/appearance.tab').then((m) => m.AppearanceTab),
  },
  {
    path: 'servizo',
    title: 'titles.municipalityService',
    canDeactivate: [leaveTabGuard],
    loadComponent: () => import('./features/municipality/section/service.tab').then((m) => m.ServiceTab),
  },
  { path: 'legal', pathMatch: 'full', redirectTo: 'legal/privacidade' },
  {
    path: 'legal/:page',
    title: 'titles.municipalityLegal',
    canDeactivate: [leaveTabGuard],
    loadComponent: () => import('./features/municipality/section/legal.tab').then((m) => m.LegalTab),
  },
  {
    path: 'persoal',
    title: 'titles.staff',
    canActivate: [staffGuard],
    data: { section: true },
    loadComponent: () => import('./features/staff/members/staff-members.page').then((m) => m.StaffMembersPage),
  },
  {
    path: 'campos',
    data: { section: true },
    loadChildren: () => import('./features/customfields/customfields.routes').then((m) => m.customFieldsRoutes),
  },
  {
    path: 'rexistro',
    title: 'titles.activity',
    canActivate: [activityGuard],
    data: { section: true },
    loadComponent: () => import('./features/staff/activity/activity.page').then((m) => m.ActivityPage),
  },
];
