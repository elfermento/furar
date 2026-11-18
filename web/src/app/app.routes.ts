// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { Routes } from '@angular/router';
import { joinRequestsMatch } from './core/deployment/join-requests.match';
import { activityGuard, configGuard, managerGuard, personGuard, platformGuard } from './core/auth/guards';
import { bookingStylesResolver } from './core/layout/booking-styles';
import { homeStylesResolver } from './core/layout/home-styles';
import { indicatorStylesResolver } from './core/layout/indicator-styles';
import { managementStylesResolver } from './core/layout/management-styles';
import { severalMunicipalitiesMatch } from './core/municipality/common-home.match';
import { LEGAL_PAGES, LEGAL_PAGE_PATHS } from './core/municipality/legal-pages';
import { leaveMunicipalityResolver, municipalityResolver } from './core/municipality/municipality.resolver';
import { restOfUrl } from './core/routing/route-group';
import { devRoutes } from './dev/dev.routes';

/** Páxina de erro, tamén á parte: non entra na carga inicial. */
const errorPage = () => import('./core/layout/error.page').then((m) => m.ErrorPage);

/**
 * Rutas visibles en galego (ADR-0003): /, /plataforma/..., /:concello/..., /:concello/xestion/...
 * A raíz ten dúas rutas: a portada común (varios concellos) e, se non, a de sempre.
 * Cada funcionalidade cárgase á parte (lazy).
 */
export const routes: Routes = [
  // Con varios concellos, a portada común: as instalacións de todos, sen escoller concello (ADR-0030).
  {
    path: '',
    pathMatch: 'full',
    canMatch: [severalMunicipalitiesMatch],
    resolve: { leave: leaveMunicipalityResolver, home: homeStylesResolver, booking: bookingStylesResolver },
    data: { layout: 'bleed' },
    title: 'titles.home',
    loadComponent: () => import('./features/facility/public/common/common-home.page').then((m) => m.CommonHomePage),
  },
  {
    path: '',
    pathMatch: 'full',
    resolve: { leave: leaveMunicipalityResolver, home: homeStylesResolver, booking: bookingStylesResolver },
    // Sen concello, a portada de exemplo a todo o ancho; cun só, a súa portada (ADR-0028).
    data: { layout: 'bleed' },
    title: 'titles.home',
    loadComponent: () =>
      import('./features/municipality/municipality-picker.page').then((m) => m.MunicipalityPickerPage),
  },
  // Rexistro da plataforma: as súas accións en todos os concellos e as que non teñen
  // concello. Antes ca `plataforma`, que leva as súas rutas noutra feature.
  {
    path: 'plataforma/rexistro',
    title: 'titles.platformActivity',
    data: { scope: 'platform' },
    canActivate: [activityGuard],
    resolve: { leave: leaveMunicipalityResolver, styles: managementStylesResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/staff/activity/activity.page').then((m) => m.ActivityPage),
  },
  // Indicadores da plataforma (ADR-0026, revisión do 2026-11-11): as cifras de todos os
  // concellos, sen datos de ningunha persoa. Antes ca `plataforma`, como o rexistro.
  {
    path: 'plataforma/indicadores',
    title: 'titles.platformIndicators',
    canActivate: [platformGuard],
    resolve: {
      leave: leaveMunicipalityResolver,
      styles: managementStylesResolver,
      booking: bookingStylesResolver,
      indicators: indicatorStylesResolver,
    },
    loadComponent: () =>
      import('./features/indicators/platform/platform-indicators.page').then((m) => m.PlatformIndicatorsPage),
  },
  {
    path: 'plataforma',
    // O alta dun concello leva furar-select (o idioma), que vai na folla das reservas.
    resolve: { leave: leaveMunicipalityResolver, booking: bookingStylesResolver },
    canActivate: [platformGuard],
    loadChildren: () => import('./features/municipality/platform.routes').then((m) => m.platformRoutes),
  },
  // Datos abertos de toda a instalación (ADR-0034), xunto á portada común: só con varios concellos.
  {
    path: 'datos-abertos',
    canMatch: [severalMunicipalitiesMatch],
    resolve: { leave: leaveMunicipalityResolver },
    data: { scope: 'all' },
    loadChildren: () => import('./features/opendata/open-data.routes').then((m) => m.openDataRoutes),
  },
  // «¿Es un concello?» (ADR-0042): a solicitude de alta dun concello, sen concello nin sesión. Só se
  // o despregue ten FURAR_JOIN_REQUEST_EMAIL; sen ela, cae en «non atopado».
  {
    path: 'alta-concello',
    canMatch: [joinRequestsMatch],
    resolve: { leave: leaveMunicipalityResolver, booking: bookingStylesResolver },
    title: 'titles.joinRequest',
    loadComponent: () =>
      import('./features/municipality/join-request/join-request.page').then((m) => m.JoinRequestPage),
  },
  // A súa privacidade (revisión do 2026-11-17): do despregue, non dun concello; coa páxina legal.
  {
    path: 'alta-concello/privacidade',
    canMatch: [joinRequestsMatch],
    resolve: { leave: leaveMunicipalityResolver },
    data: { page: 'joinRequests' },
    title: 'titles.privacy',
    loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
  },
  // «A miña conta» sen concello (o persoal de varios concellos que aínda non escolleu ningún).
  {
    path: 'conta',
    title: 'titles.account',
    canActivate: [personGuard],
    resolve: { leave: leaveMunicipalityResolver, booking: bookingStylesResolver },
    loadComponent: () => import('./features/account/account.page').then((m) => m.AccountPage),
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
      // O apartado «Reservas» (reservas, incidencias, validar o QR e calendario), en
      // manage-bookings.routes.ts.
      {
        path: '',
        canMatch: [restOfUrl],
        loadChildren: () => import('./manage-bookings.routes').then((m) => m.manageBookingsRoutes),
      },
      // Indicadores: só a xestión; os filtros van no URL.
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
      // Concello: unha sección con pestanas, cada unha co seu URL. As catro primeiras
      // son formularios co seu «Gardar» (saír cun cambio sen gardar pregunta); Persoal,
      // Campos personalizados e Rexistro de actividade son as súas páxinas de sempre.
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
      // Aceptar unha invitación ao persoal: pública; o token vén no fragmento (#…).
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
      // «A miña conta» (con sesión): quen é, os seus concellos, o último acceso, a seguridade e eliminala.
      {
        path: 'conta',
        title: 'titles.account',
        canActivate: [personGuard],
        resolve: { booking: bookingStylesResolver },
        loadComponent: () => import('./features/account/account.page').then((m) => m.AccountPage),
      },
      // «Non quero recordatorios» desde o correo (ADR-0035): sen sesión; a proba vai en ?t=.
      {
        path: 'recordatorios/baixa',
        title: 'titles.reminderOptOut',
        resolve: { booking: bookingStylesResolver },
        loadComponent: () =>
          import('./features/booking/reminders/reminder-opt-out.page').then((m) => m.ReminderOptOutPage),
      },
      // Unha partida aberta (ADR-0040): pública; «Unirme» sen sesión leva a entrar e volve.
      {
        path: 'partidas/:id',
        title: 'titles.openMatch',
        resolve: { booking: bookingStylesResolver },
        loadComponent: () => import('./features/booking/open-match/open-match.page').then((m) => m.OpenMatchPage),
      },
      // Ficha pública dunha instalación (sen sesión): a ela levan as tarxetas da portada.
      {
        path: 'instalacions/:id',
        title: 'titles.publicFacility',
        resolve: { booking: bookingStylesResolver },
        loadComponent: () =>
          import('./features/facility/public/public-facility.page').then((m) => m.PublicFacilityPage),
      },
      // Páxinas legais públicas (RD 1112/2018, RGPD art. 13 e LSSI art. 10), ligadas desde o pé (co pé enteiro).
      {
        path: 'accesibilidade',
        title: 'titles.accessibility',
        data: { page: 'accessibility', footer: 'full' },
        loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
      },
      {
        path: 'privacidade',
        title: 'titles.privacy',
        data: { page: 'privacy', footer: 'full' },
        loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
      },
      {
        path: 'aviso-legal',
        title: 'titles.legalNotice',
        data: { page: 'legalNotice', footer: 'full' },
        loadComponent: () => import('./features/legal/legal.page').then((m) => m.LegalPage),
      },
      // Datos abertos do concello (ADR-0034): 404 se o concello os ten apagados.
      {
        path: 'datos-abertos',
        loadChildren: () => import('./features/opendata/open-data.routes').then((m) => m.openDataRoutes),
      },
      // Eventos do concello (ADR-0038): a ficha pública (`eventos/:id`) e a pestana da xestión
      // (`xestion/eventos`); as rutas, os gardas e as follas de estilo, en event.routes.ts.
      {
        path: '',
        canMatch: [restOfUrl],
        loadChildren: () => import('./features/event/event.routes').then((m) => m.eventRoutes),
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
