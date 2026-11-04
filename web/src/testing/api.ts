// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { type HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, type TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { computed, inject, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideApiConfiguration } from '../app/api/api-configuration';
import type { Role, StaffRole } from '../app/core/auth/roles';
import { PlatformAccessService } from '../app/core/auth/platform-access.service';
import { SessionService } from '../app/core/auth/session.service';
import type { StaffAccess } from '../app/core/auth/staff-access.service';
import { StaffAccessService } from '../app/core/auth/staff-access.service';
import type { StaffMembership } from '../app/core/auth/staff-memberships.service';
import { StaffMembershipsService } from '../app/core/auth/staff-memberships.service';
import { CurrentMunicipality } from '../app/core/municipality/current-municipality';
import { problemInterceptor } from '../app/core/errors/problem.interceptor';
import { provideDeploymentSettings } from './deployment';
import { provideTestI18n } from './i18n';

export interface FakeRoles {
  readonly manager?: string;
  readonly staff?: string;
  readonly platform?: boolean;
  /** Acceso anterior que devolve `GET …/me/staff` (ISO 8601). */
  readonly previousSignInAt?: string;
  /** Conta de traballo nalgún concello (`workAccount` de `GET …/me/staff`). */
  readonly workAccount?: boolean;
  /** Os concellos como persoal (`GET /api/v1/me/staff-memberships`); por defecto, ningún. */
  readonly memberships?: readonly StaffMembership[];
}

/**
 * Sesión falsa: xestor de Narón por defecto. Os roles son os que devolvería a API: os de concello
 * (`fakeStaffAccess`) e a plataforma (`fakePlatformAccess`, desde `platformAdmin`).
 */
export function fakeSession(roles: FakeRoles = { manager: 'naron' }) {
  const staffRole = (slug: string | null): StaffRole | null =>
    slug !== null && slug === roles.manager ? 'MANAGER' : slug !== null && slug === roles.staff ? 'STAFF' : null;
  return {
    roles,
    staffRole,
    authenticated: signal(true),
    subject: signal('user-1'),
    name: signal('Xestora Narón'),
    email: signal('xestora@naron.example'),
    accessToken: signal('token'),
    platformAdmin: signal(roles.platform ?? false),
    accountUrl: () => 'http://localhost/auth/realms/furar/account',
    securityUrl: () => 'http://localhost/auth/realms/furar/account/account-security/signing-in',
    whenReady: () => Promise.resolve(),
    login: vi.fn().mockResolvedValue(undefined),
    logout: vi.fn().mockResolvedValue(undefined),
  };
}

/** `StaffAccessService` falso: responde co rol da sesión falsa no concello actual, sen API. */
export function fakeStaffAccess(session: ReturnType<typeof fakeSession>, municipality: CurrentMunicipality) {
  const access = (slug: string | null): StaffAccess | null =>
    session.authenticated() && slug !== null
      ? {
          role: session.staffRole(slug),
          previousSignInAt: session.staffRole(slug) ? (session.roles.previousSignInAt ?? null) : null,
          workAccount: session.roles.workAccount ?? false,
        }
      : null;
  const current = computed(() => access(municipality.slug()));
  const role = computed(() => current()?.role ?? null);
  return {
    current,
    role,
    previousSignInAt: computed(() => current()?.previousSignInAt ?? null),
    canManage: computed(() => role() === 'MANAGER'),
    canViewBookings: computed(() => role() !== null),
    mainRole: computed<Role | null>(() => (session.platformAdmin() ? 'PLATFORM_ADMIN' : role())),
    workAccount: computed(() => current()?.workAccount ?? false),
    load: (slug: string) => Promise.resolve(access(slug)),
    reload: (slug: string) => Promise.resolve(access(slug)),
  };
}

/** `PlatformAccessService` falso: a plataforma da sesión falsa, sen API. */
export function fakePlatformAccess(session: ReturnType<typeof fakeSession>) {
  const platformAdmin = computed(() => session.authenticated() && session.platformAdmin());
  return {
    platformAdmin,
    load: () => Promise.resolve(platformAdmin()),
  };
}

/** `StaffMembershipsService` falso: os concellos da sesión falsa, sen API. */
export function fakeStaffMemberships(session: ReturnType<typeof fakeSession>) {
  const memberships = computed(() => (session.authenticated() ? (session.roles.memberships ?? []) : []));
  return {
    memberships,
    load: () => Promise.resolve(memberships()),
  };
}

/** HttpClient de proba co interceptor de erros real, router, textos e o mapa do IGN. */
export function provideTestApi(session = fakeSession()) {
  return [
    provideDeploymentSettings(),
    provideTestI18n(),
    provideHttpClient(withInterceptors([problemInterceptor])),
    provideHttpClientTesting(),
    provideApiConfiguration(''),
    provideRouter([]),
    { provide: SessionService, useValue: session },
    { provide: StaffAccessService, useFactory: () => fakeStaffAccess(session, inject(CurrentMunicipality)) },
    { provide: StaffMembershipsService, useFactory: () => fakeStaffMemberships(session) },
    { provide: PlatformAccessService, useFactory: () => fakePlatformAccess(session) },
  ];
}

export function backend(): HttpTestingController {
  return TestBed.inject(HttpTestingController);
}

/** Responde cun problem+json. */
export function flushProblem(request: TestRequest, status: number, code: string, extra: Record<string, unknown> = {}) {
  request.flush(
    { type: 'about:blank', title: 'x', status, code, params: {}, traceId: 'trace-1', ...extra },
    { status, statusText: 'x' },
  );
}

/** Espera a que haxa unha petición pendente (as páxinas piden en ngOnInit, de forma asíncrona). */
export async function expectRequest(match: Parameters<HttpTestingController['expectOne']>[0]): Promise<TestRequest> {
  let found: TestRequest | undefined;
  await vi.waitFor(() => {
    found = backend().expectOne(match);
  });
  if (!found) throw new Error('sen petición');
  return found;
}

/** Responde con `body` a todas as peticións pendentes que cumpran `match`; se non hai ningunha, nada. */
export function flushMatching(match: (request: HttpRequest<unknown>) => boolean, body: object | null): void {
  for (const request of backend().match(match)) {
    request.flush(body);
  }
}
