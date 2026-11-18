// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { User, UserManager } from 'oidc-client-ts';
import { vi } from 'vitest';

/** JWT sen asinar (a web non verifica: faino a API). Os roles non van no token (ADR-0028). */
export function fakeJwt(payload: Record<string, unknown>): string {
  const encode = (value: unknown) =>
    btoa(String.fromCharCode(...new TextEncoder().encode(JSON.stringify(value))))
      .replace(/\+/g, '-')
      .replace(/\//g, '_')
      .replace(/=+$/, '');
  return `${encode({ alg: 'none' })}.${encode(payload)}.`;
}

export function fakeUser(name = 'Xestora de Narón'): User {
  return {
    access_token: fakeJwt({ sub: 'user-1' }),
    id_token: 'id-token',
    profile: { name, sub: 'user-1', iss: 'x', aud: 'furar-web', exp: 0, iat: 0 },
  } as unknown as User;
}

export interface FakeUserManager {
  readonly manager: UserManager;
  readonly signinRedirect: ReturnType<typeof vi.fn>;
  readonly signinRedirectCallback: ReturnType<typeof vi.fn>;
  readonly signoutRedirect: ReturnType<typeof vi.fn>;
  readonly removeUser: ReturnType<typeof vi.fn>;
  readonly getUser: ReturnType<typeof vi.fn>;
  readonly listeners: { userLoaded?: (user: User) => void; expired?: () => void; renewError?: () => void };
}

export function fakeUserManager(user: User | Error = fakeUser()): FakeUserManager {
  const listeners: FakeUserManager['listeners'] = {};
  const fns = {
    signinRedirect: vi.fn().mockResolvedValue(undefined),
    signinRedirectCallback: vi
      .fn()
      .mockImplementation(() => (user instanceof Error ? Promise.reject(user) : Promise.resolve(user))),
    signoutRedirect: vi.fn().mockResolvedValue(undefined),
    removeUser: vi.fn().mockResolvedValue(undefined),
    getUser: vi.fn().mockResolvedValue(user instanceof Error ? null : user),
  };
  const manager = {
    ...fns,
    events: {
      addUserLoaded: (cb: (user: User) => void) => (listeners.userLoaded = cb),
      addAccessTokenExpired: (cb: () => void) => (listeners.expired = cb),
      addSilentRenewError: (cb: () => void) => (listeners.renewError = cb),
    },
  } as unknown as UserManager;
  return { manager, listeners, ...fns };
}
