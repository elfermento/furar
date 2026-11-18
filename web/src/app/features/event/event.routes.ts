// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { bookingsGuard } from '../../core/auth/guards';
import { bookingStylesResolver } from '../../core/layout/booking-styles';
import { managementStylesResolver } from '../../core/layout/management-styles';

/**
 * Os eventos do concello (ADR-0038, ADR-0041), baixo /:concello: a ficha pública (sen sesión ata
 * «Apuntarme»), «Instalacións › Eventos e actividades» na xestión (Recepción tamén, só para ler) e
 * «Pasar lista» dunha sesión (Xestión e Recepción).
 */
export const eventRoutes: Routes = [
  {
    path: 'eventos/:id',
    title: 'titles.event',
    resolve: { booking: bookingStylesResolver },
    loadComponent: () => import('./public/event.page').then((m) => m.EventPage),
  },
  {
    path: 'xestion/eventos',
    title: 'titles.manageEvents',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./events.page').then((m) => m.EventsPage),
  },
  {
    path: 'xestion/eventos/:id/sesions/:sessionId',
    title: 'titles.attendance',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./attendance.page').then((m) => m.AttendancePage),
  },
];
