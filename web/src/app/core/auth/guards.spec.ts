// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  type ActivatedRouteSnapshot,
  RedirectCommand,
  type RouterStateSnapshot,
  convertToParamMap,
} from '@angular/router';
import {
  activityGuard,
  bookingsGuard,
  configGuard,
  managerGuard,
  personGuard,
  platformGuard,
  staffGuard,
} from './guards';
import type { StaffRole } from './roles';
import { PlatformAccessService } from './platform-access.service';
import { SessionService } from './session.service';
import type { StaffAccess } from './staff-access.service';
import { StaffAccessService } from './staff-access.service';

describe('gardas', () => {
  const authenticated = signal(false);
  const platformAdmin = signal(false);
  const session = {
    whenReady: () => Promise.resolve(),
    authenticated,
    login: vi.fn().mockResolvedValue(undefined),
  };
  /** A administración da plataforma sae da API (`GET /api/v1/me/platform-access`, ADR-0028). */
  const platformAccess = { load: vi.fn(() => Promise.resolve(platformAdmin())) };
  /** Roles da API por concello (`GET …/me/staff`): Narón xestión, Ares persoal do mostrador. */
  let roles: Record<string, StaffRole> = {};
  const staffAccess = {
    load: vi.fn((slug: string): Promise<StaffAccess | null> =>
      Promise.resolve({ role: roles[slug] ?? null, previousSignInAt: null, workAccount: false }),
    ),
  };
  const route = (slug: string | null) =>
    ({
      pathFromRoot: [
        { paramMap: convertToParamMap({}) },
        { paramMap: convertToParamMap(slug ? { municipality: slug } : {}) },
      ],
    }) as unknown as ActivatedRouteSnapshot;
  const state = { url: '/naron/xestion' } as RouterStateSnapshot;
  const run = (guard: typeof managerGuard, slug: string | null) =>
    TestBed.runInInjectionContext(() => guard(route(slug), state) as Promise<boolean | RedirectCommand>);
  const denied = (result: boolean | RedirectCommand) =>
    result instanceof RedirectCommand && result.redirectTo.toString() === '/erro/403';

  beforeEach(() => {
    roles = { naron: 'MANAGER', ares: 'STAFF' };
    platformAdmin.set(false);
    session.login.mockClear();
    staffAccess.load.mockClear();
    TestBed.configureTestingModule({
      providers: [
        { provide: SessionService, useValue: session },
        { provide: StaffAccessService, useValue: staffAccess },
        { provide: PlatformAccessService, useValue: platformAccess },
      ],
    });
  });

  it('sen sesión → Keycloak e volta á mesma páxina, sen preguntar o rol', async () => {
    authenticated.set(false);
    expect(await run(managerGuard, 'naron')).toBe(false);
    expect(session.login).toHaveBeenCalledWith('/naron/xestion');
    expect(await run(personGuard, 'naron')).toBe(false);
    expect(session.login).toHaveBeenLastCalledWith('/naron/xestion');
    expect(staffAccess.load).not.toHaveBeenCalled();
  });

  it('xestor do concello (rol da API) pasa; doutro concello → páxina 403 sen cambiar o URL', async () => {
    authenticated.set(true);
    expect(await run(managerGuard, 'naron')).toBe(true);
    expect(staffAccess.load).toHaveBeenCalledWith('naron');
    const result = await run(managerGuard, 'ferrol');
    expect(denied(result)).toBe(true);
    expect((result as RedirectCommand).navigationBehaviorOptions?.skipLocationChange).toBe(true);
    expect(denied(await run(platformGuard, 'naron'))).toBe(true);
  });

  it('a API sen resposta (null) → 403: o claim do token xa non dá a xestión', async () => {
    authenticated.set(true);
    staffAccess.load.mockResolvedValueOnce(null);
    expect(denied(await run(managerGuard, 'naron'))).toBe(true);
  });

  it('as miñas reservas: calquera persoa con sesión, de calquera concello; sen sesión → login', async () => {
    authenticated.set(true);
    expect(await run(personGuard, 'ferrol')).toBe(true);
    authenticated.set(false);
    expect(await run(personGuard, 'naron')).toBe(false);
    expect(session.login).toHaveBeenCalledWith('/naron/xestion');
  });

  it('reservas do concello: a xestión e o persoal do mostrador; o resto → 403', async () => {
    authenticated.set(true);
    expect(await run(bookingsGuard, 'naron')).toBe(true);
    expect(await run(bookingsGuard, 'ares')).toBe(true);
    expect(denied(await run(bookingsGuard, 'ferrol'))).toBe(true);
  });

  it('a plataforma configura calquera concello (datos, instalacións, campos, persoal, rexistro); nin reservas nin indicadores', async () => {
    authenticated.set(true);
    platformAdmin.set(true);
    roles = {};
    expect(await run(staffGuard, 'ferrol')).toBe(true);
    expect(await run(configGuard, 'ferrol')).toBe(true);
    expect(await run(activityGuard, 'ferrol')).toBe(true);
    expect(await run(activityGuard, null)).toBe(true);
    expect(await run(platformGuard, null)).toBe(true);
    expect(denied(await run(managerGuard, 'ferrol'))).toBe(true);
    expect(denied(await run(bookingsGuard, 'ferrol'))).toBe(true);
  });

  it('a configuración do concello: o xestor si; o persoal do mostrador e outro concello non', async () => {
    authenticated.set(true);
    expect(await run(configGuard, 'naron')).toBe(true);
    expect(denied(await run(configGuard, 'ares'))).toBe(true);
    expect(denied(await run(configGuard, 'ferrol'))).toBe(true);
  });

  it('o persoal do mostrador non entra en «Persoal» nin no rexistro; o xestor si', async () => {
    authenticated.set(true);
    expect(denied(await run(staffGuard, 'ares'))).toBe(true);
    expect(denied(await run(activityGuard, 'ares'))).toBe(true);
    expect(denied(await run(activityGuard, null))).toBe(true);
    expect(await run(staffGuard, 'naron')).toBe(true);
    expect(await run(activityGuard, 'naron')).toBe(true);
  });
});
