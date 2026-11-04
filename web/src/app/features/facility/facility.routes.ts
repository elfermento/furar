// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { unsavedChangesGuard } from '../../core/unsaved-changes/unsaved-changes.guard';
import { wizardLeaveGuard } from './wizard/wizard-leave.guard';

const wizard = () => import('./wizard/facility-wizard.page').then((m) => m.FacilityWizardPage);

/**
 * /:concello/xestion/instalacions (a garda de xestor está en app.routes): listado, asistente
 * «Nova instalación» (`nova`, e `nova/:id` para seguir cun borrador) e ficha.
 */
export const facilityRoutes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    title: 'titles.facilities',
    loadComponent: () => import('./list/facility-list.page').then((m) => m.FacilityListPage),
  },
  { path: 'nova', title: 'titles.newFacility', canDeactivate: [wizardLeaveGuard], loadComponent: wizard },
  { path: 'nova/:id', title: 'titles.newFacility', canDeactivate: [wizardLeaveGuard], loadComponent: wizard },
  {
    path: ':id',
    title: 'titles.facility',
    canDeactivate: [unsavedChangesGuard],
    loadComponent: () => import('./detail/facility-detail.page').then((m) => m.FacilityDetailPage),
  },
];
