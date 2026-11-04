// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import {
  activityGuard,
  bookingsGuard,
  configGuard,
  managerGuard,
  personGuard,
  platformGuard,
} from './core/auth/guards';
import { bookingStylesResolver } from './core/layout/booking-styles';
import { indicatorStylesResolver } from './core/layout/indicator-styles';
import { managementStylesResolver } from './core/layout/management-styles';
import { LEGAL_PAGES, LEGAL_PAGE_PATHS } from './core/municipality/legal-pages';
import { leaveMunicipalityResolver, municipalityResolver } from './core/municipality/municipality.resolver';

/** Páxina de erro, tamén á parte: non entra na carga inicial. */
const errorPage = () => import('./core/layout/error.page').then((m) => m.ErrorPage);

/** Definida polo build: false en produción, así o catálogo nin se empaqueta (dead code). */
declare const ngDevMode: unknown;

/** Catálogo de compoñentes de shared/ui (docs/deseno.md §8): só en desenvolvemento e nos tests. */
const devRoutes: Routes =
  typeof ngDevMode === 'undefined' || ngDevMode
    ? [
        {
          path: 'catalogo-ui',
          title: 'titles.catalog',
          resolve: {
            leave: leaveMunicipalityResolver,
            styles: managementStylesResolver,
            booking: bookingStylesResolver,
            indicators: indicatorStylesResolver,
          },
          loadComponent: () => import('./dev/catalog.page').then((m) => m.CatalogPage),
        },
      ]
    : [];

/**
 * Rutas visibles en galego (ADR-0003): /, /plataforma/..., /:concello/..., /:concello/xestion/...
 * Cada funcionalidade cárgase á parte (lazy).
 */
export const routes: Routes = [
  {
    path: '',
    pathMatch: 'full',
    resolve: { leave: leaveMunicipalityResolver },
    // Sen concello, a portada de exemplo a todo o ancho; cun só, a súa portada (ADR-0028).
    data: { layout: 'bleed' },
    title: 'titles.home',
    loadComponent: () =>
      import('./features/municipality/municipality-picker.page').then((m) => m.MunicipalityPickerPage),
  },
  // Rexistro da plataforma (D4-16): as súas accións en todos os concellos e as que non teñen
  // concello. Antes ca `plataforma`, que leva as súas rutas noutra feature.
  {
    path: 'plataforma/rexistro',
    title: 'titles.platformActivity',
    data: { scope: 'platform' },
    canActivate: [activityGuard],
    resolve: { leave: leaveMunicipalityResolver, styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/staff/activity/activity.page').then((m) => m.ActivityPage),
  },
  {
    path: 'plataforma',
    // O alta dun concello leva furar-select (o idioma), que vai na folla das reservas.
    resolve: { leave: leaveMunicipalityResolver, booking: bookingStylesResolver },
    canActivate: [platformGuard],
    loadChildren: () => import('./features/municipality/platform.routes').then((m) => m.platformRoutes),
  },
  { path: 'erro/:status', loadComponent: errorPage, title: 'titles.error' },
  ...devRoutes,
  {
    path: ':municipality',
    resolve: { municipality: municipalityResolver },
    runGuardsAndResolvers: 'paramsChange',
    children: [
      {
        path: 'xestion/instalacions',
        canActivate: [configGuard],
        resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
        loadChildren: () => import('./features/facility/facility.routes').then((m) => m.facilityRoutes),
      },
      // Reservas do concello (maqueta 26): a xestión e o persoal do mostrador.
      {
        path: 'xestion/reservas',
        title: 'titles.manageBookings',
        canActivate: [bookingsGuard],
        resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
        loadComponent: () => import('./features/booking/manage/manage-bookings.page').then((m) => m.ManageBookingsPage),
      },
      // Calendario das reservas (maqueta 27): os mesmos permisos ca «Reservas».
      {
        path: 'xestion/calendario',
        title: 'titles.bookingsCalendar',
        canActivate: [bookingsGuard],
        resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
        loadComponent: () =>
          import('./features/booking/calendar/bookings-calendar.page').then((m) => m.BookingsCalendarPage),
      },
      // Indicadores (maqueta 31, D3-08): só a xestión; os filtros van no URL.
      {
        path: 'xestion/indicadores',
        title: 'titles.indicators',
        canActivate: [managerGuard],
        resolve: {
          styles: managementStylesResolver,
          booking: bookingStylesResolver,
          indicators: indicatorStylesResolver,
        },
        loadComponent: () => import('./features/indicators/indicators.page').then((m) => m.IndicatorsPage),
      },
      // Concello (maqueta 49): unha sección con pestanas, cada unha co seu URL. As catro primeiras
      // son formularios co seu «Gardar» (saír cun cambio sen gardar pregunta); Persoal (D4-14),
      // Campos personalizados e Rexistro de actividade (D4-16) son as súas páxinas de sempre.
      // O xestor e a plataforma (ADR-0028, revisión do 2026-11-03); o persoal do mostrador, non.
      {
        path: 'xestion/concello',
        canActivate: [configGuard],
        resolve: { styles: managementStylesResolver, booking: bookingStylesResolver },
        loadComponent: () =>
          import('./features/municipality/section/municipality-section.page').then((m) => m.MunicipalitySectionPage),
        loadChildren: () => import('./municipality-section.routes').then((m) => m.municipalitySectionRoutes),
      },
      // Os enderezos de antes da sección «Concello» (ligazóns gardadas e correos): ao seu sitio novo.
      // «Datos do concello» (`/xestion`, coa páxina legal no fragmento: `#aviso-legal`): aquí e non
      // nas rutas fillas de '' (alí `xestion/concello` non existe e daría 404).
      {
        path: 'xestion',
        pathMatch: 'full',
        redirectTo: ({ fragment }) => {
          const page = LEGAL_PAGES.map((key) => LEGAL_PAGE_PATHS[key]).find((path) => path === fragment);
          return page ? `xestion/concello/legal/${page}` : 'xestion/concello';
        },
      },
      { path: 'xestion/persoal', pathMatch: 'full', redirectTo: 'xestion/concello/persoal' },
      { path: 'xestion/rexistro', pathMatch: 'full', redirectTo: 'xestion/concello/rexistro' },
      { path: 'xestion/campos', pathMatch: 'full', redirectTo: 'xestion/concello/campos' },
      { path: 'xestion/campos/:id', redirectTo: 'xestion/concello/campos/:id' },
      // Aceptar unha invitación ao persoal (D4-15): pública; o token vén no fragmento (#…).
      {
        path: 'convite',
        title: 'titles.staffInvitation',
        resolve: { styles: managementStylesResolver },
        loadComponent: () =>
          import('./features/staff/invitation/staff-invitation.page').then((m) => m.StaffInvitationPage),
      },
      // Listado público (sen sesión): a el levan o buscador da portada e «Instalacións» na barra.
      {
        path: 'instalacions',
        pathMatch: 'full',
        title: 'titles.facilities',
        resolve: { booking: bookingStylesResolver },
        loadComponent: () => import('./features/facility/public/list/public-list.page').then((m) => m.PublicListPage),
      },
      // Páxina de reservar (sen sesión ata «Continuar»): o escollido vai no URL (?uso=&data=&hora=…).
      {
        path: 'instalacions/:id/reservar',
        title: 'titles.reserve',
        resolve: { booking: bookingStylesResolver },
        loadComponent: () => import('./features/booking/reserve/reserve.page').then((m) => m.ReservePage),
      },
      // As miñas reservas (con sesión, de calquera concello): Próximas e Historial.
      {
        path: 'reservas',
        title: 'titles.myBookings',
        canActivate: [personGuard],
        resolve: { booking: bookingStylesResolver },
        loadComponent: () => import('./features/booking/mine/my-bookings.page').then((m) => m.MyBookingsPage),
      },
      // Ficha pública dunha instalación (sen sesión): a ela levan as tarxetas da portada.
      {
        path: 'instalacions/:id',
        title: 'titles.publicFacility',
        resolve: { booking: bookingStylesResolver },
        loadComponent: () =>
          import('./features/facility/public/public-facility.page').then((m) => m.PublicFacilityPage),
      },
      // Páxinas legais públicas (RD 1112/2018, RGPD art. 13 e LSSI art. 10), ligadas desde o pé.
      {
        path: 'accesibilidade',
        title: 'titles.accessibility',
        data: { page: 'accessibility' },
        loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
      },
      {
        path: 'privacidade',
        title: 'titles.privacy',
        data: { page: 'privacy' },
        loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
      },
      {
        path: 'aviso-legal',
        title: 'titles.legalNotice',
        data: { page: 'legalNotice' },
        loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
      },
      // novo-modulo: scripts/novo-modulo.sh insire as rutas dos módulos novos enriba desta liña.
      {
        path: '',
        loadChildren: () => import('./features/municipality/municipality.routes').then((m) => m.municipalityRoutes),
      },
      { path: '**', loadComponent: errorPage, data: { status: '404' }, title: 'titles.error' },
    ],
  },
  { path: '**', loadComponent: errorPage, data: { status: '404' }, title: 'titles.error' },
];
