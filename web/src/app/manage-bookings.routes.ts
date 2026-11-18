// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { bookingsGuard, managerGuard } from './core/auth/guards';
import { bookingStylesResolver } from './core/layout/booking-styles';
import { managementStylesResolver } from './core/layout/management-styles';

/**
 * O apartado «Reservas» da xestión, baixo /:concello: as reservas do concello, reservar
 * no nome doutra persoa e cambiar unha reserva, as incidencias, validar o QR e o calendario. Todas
 * cos permisos de «Reservas» (Xestión e Recepción). Aquí, e non nunha feature, porque cargan
 * páxinas de varias.
 */
export const manageBookingsRoutes: Routes = [
  // Reservas do concello: a xestión e o persoal do mostrador.
  {
    path: 'xestion/reservas',
    title: 'titles.manageBookings',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/booking/manage/manage-bookings.page').then((m) => m.ManageBookingsPage),
  },
  // Reservar no nome doutra persoa e cambiar unha reserva: o mesmo formulario ca a
  // veciñanza.
  {
    path: 'xestion/reservas/nova',
    title: 'titles.bookFor',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/booking/manage/book-for.page').then((m) => m.BookForPage),
  },
  {
    path: 'xestion/reservas/:id/cambiar',
    title: 'titles.changeBooking',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/booking/manage/book-for.page').then((m) => m.BookForPage),
  },
  // Incidencias (ADR-0036).
  {
    path: 'xestion/incidencias',
    title: 'titles.incidents',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/incident/incidents.page').then((m) => m.IncidentsPage),
  },
  // Validar QR (ADR-0032).
  {
    path: 'xestion/validar',
    title: 'titles.validate',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/booking/validate/validate.page').then((m) => m.ValidatePage),
  },
  // Entidades e as súas solicitudes de tempada (ADR-0039): só a xestión.
  {
    path: 'xestion/entidades',
    title: 'titles.organizations',
    canActivate: [managerGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/booking/seasons/organizations.page').then((m) => m.OrganizationsPage),
  },
  {
    path: 'xestion/entidades/solicitudes/:id',
    title: 'titles.seriesRequest',
    canActivate: [managerGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/booking/seasons/series-request.page').then((m) => m.SeriesRequestPage),
  },
  // Calendario das reservas.
  {
    path: 'xestion/calendario',
    title: 'titles.bookingsCalendar',
    canActivate: [bookingsGuard],
    resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () =>
      import('./features/booking/calendar/bookings-calendar.page').then((m) => m.BookingsCalendarPage),
  },
];
