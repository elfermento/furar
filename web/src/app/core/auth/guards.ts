// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { inject } from '@angular/core';
import type { CanActivateFn } from '@angular/router';
import { RedirectCommand, Router } from '@angular/router';
import { canConfigure, canManage, canViewBookings } from './roles';
import { PlatformAccessService } from './platform-access.service';
import { SessionService } from './session.service';
import type { StaffAccess } from './staff-access.service';
import { StaffAccessService } from './staff-access.service';

/** O que decide cada garda: o acceso da API no concello do URL e se é da plataforma. */
interface GuardContext {
  readonly slug: string | null;
  readonly access: StaffAccess | null;
  readonly platformAdmin: boolean;
}

/**
 * Gardas de interface (a API decide sempre). Sen sesión → login e volta; sen rol → páxina
 * «sen permiso» mantendo o URL. As da xestión (`staff`) len o rol da API (`GET …/me/staff`) e
 * todas, a administración da plataforma (`GET /api/v1/me/platform-access`, ADR-0028).
 */
function guard(allowed: (context: GuardContext) => boolean, staff = true): CanActivateFn {
  return async (route, state) => {
    const session = inject(SessionService);
    const staffAccess = inject(StaffAccessService);
    const platformAccess = inject(PlatformAccessService);
    const router = inject(Router);
    await session.whenReady();
    if (!session.authenticated()) {
      await session.login(state.url);
      return false;
    }
    const slug = route.pathFromRoot.map((r) => r.paramMap.get('municipality')).find((value) => value !== null) ?? null;
    const access = staff && slug !== null ? await staffAccess.load(slug) : null;
    const platformAdmin = await platformAccess.load();
    if (allowed({ slug, access, platformAdmin })) {
      return true;
    }
    return new RedirectCommand(router.parseUrl('/erro/403'), { skipLocationChange: true });
  };
}

const role = (context: GuardContext) => context.access?.role ?? null;

/** Só o xestor: os indicadores (a plataforma non ve datos da veciñanza). */
export const managerGuard: CanActivateFn = guard((context) => canManage(role(context)));
/**
 * Configuración do concello (datos, instalacións, campos personalizados): o xestor ou a
 * plataforma (ADR-0028, revisión do 2026-11-03).
 */
export const configGuard: CanActivateFn = guard((context) => canConfigure(role(context), context.platformAdmin));
/** Reservas do concello: a xestión e o persoal do mostrador (STAFF). */
export const bookingsGuard: CanActivateFn = guard((context) => canViewBookings(role(context)));
/** «Persoal» (`/:concello/xestion/persoal`): o xestor ou a plataforma (alta e recuperación do concello). */
export const staffGuard: CanActivateFn = guard((context) => canConfigure(role(context), context.platformAdmin));
/**
 * «Rexistro de actividade»: no concello (`/:concello/xestion/rexistro`), o xestor ou a plataforma
 * (ADR-0028, revisión do 2026-11-03); fóra del (`/plataforma/rexistro`), só a plataforma.
 */
export const activityGuard: CanActivateFn = guard((context) =>
  context.slug === null ? context.platformAdmin : canConfigure(role(context), context.platformAdmin),
);
export const platformGuard: CanActivateFn = guard((context) => context.platformAdmin, false);
/** Calquera persoa con sesión (as súas reservas): a API filtra polo `sub`. */
export const personGuard: CanActivateFn = guard(() => true, false);
