// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { bookingStylesResolver } from '../../core/layout/booking-styles';

/** «Datos abertos» (ADR-0034): a mesma páxina para un concello e para todos. */
export const openDataRoutes: Routes = [
  {
    path: '',
    title: 'titles.openData',
    resolve: { booking: bookingStylesResolver },
    loadComponent: () => import('./open-data.page').then((m) => m.OpenDataPage),
  },
];
