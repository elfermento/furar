// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { InMemoryWebStorage, UserManager, WebStorageStateStore } from 'oidc-client-ts';

/**
 * Cliente OIDC (código + PKCE) contra Keycloak na mesma orixe (ADR-0007). Cárgase só cando
 * fai falta (login, volta de Keycloak ou sesión que recuperar): o visitante anónimo non o descarga.
 * - Tokens só en memoria (userStore en memoria). O state e o code_verifier de PKCE van a
 *   sessionStorage porque teñen que sobrevivir á redirección; non son tokens.
 * - Renovación co refresh token, tamén en memoria (sen iframe nin cookies de terceiros).
 * - Nada depende do despregue: o emisor sae de location.origin (a mesma imaxe vale para todos).
 */
export function createUserManager(origin: string, session: Storage): UserManager {
  return new UserManager({
    authority: `${origin}/auth/realms/furar`,
    client_id: 'furar-web',
    redirect_uri: `${origin}/`,
    post_logout_redirect_uri: `${origin}/`,
    response_type: 'code',
    scope: 'openid profile',
    loadUserInfo: false,
    monitorSession: false,
    automaticSilentRenew: true,
    accessTokenExpiringNotificationTimeInSeconds: 30,
    userStore: new WebStorageStateStore({ store: new InMemoryWebStorage() }),
    stateStore: new WebStorageStateStore({ store: session, prefix: 'furar.oidc.' }),
  });
}

export type { User, UserManager } from 'oidc-client-ts';
