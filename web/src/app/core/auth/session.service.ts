// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, InjectionToken, computed, inject, signal } from '@angular/core';
import { LanguageService } from '../i18n/language.service';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { FirstRun } from '../municipality/first-run';
import { LEGAL_PAGE_PATHS, legalPagePublished } from '../municipality/legal-pages';
import type { User, UserManager } from './oidc';

/** Marca (sen datos) de que había sesión: ao recargar, recupérase con prompt=none. */
const HINT_KEY = 'furar.session';
const RETURN_KEY = 'furar.returnUrl';
/** Onde volver despois de pechar sesión (Keycloak sempre volve á raíz, `post_logout_redirect_uri`). */
const LOGOUT_RETURN_KEY = 'furar.logoutReturnUrl';
/** Xa se volveu tentar entrar en silencio despois dunha volta fallida: só unha vez, sen bucles. */
const RETRY_KEY = 'furar.loginRetry';
/** A ida a Keycloak é un acceso da persoa (non recuperar a sesión ao recargar): ao volver, a entrada. */
const INTERACTIVE_KEY = 'furar.loginInteractive';

/** Un camiño desta orixe (`/…`, non `//…`): o único sitio ao que se volve. */
const isLocalPath = (url: string) => url.startsWith('/') && !url.startsWith('//');

/**
 * Como se vai a Keycloak: en silencio ou ao rexistro (`create`). `loginHint` pon ese correo no
 * formulario de acceso ou de rexistro (a invitación do persoal). `invitationProof` é a proba
 * firmada da invitación (parámetro `furar_invitation`): co mesmo correo, a conta nace co correo
 * verificado (ADR-0027, revisión do 2026-11-02). Mentres non hai ningún concello (`FirstRun`),
 * vai tamén `furar_setup=1`: o rexistro de Keycloak non pon a frase de privacidade (ADR-0028).
 * Desde un concello vai `furar_privacy` co camiño da súa páxina «Privacidade» (`/naron/privacidade`):
 * a frase do rexistro liga a ela (ADR-0017, revisión do 2026-11-03); sen concello, non hai frase.
 */
export interface LoginOptions {
  readonly prompt?: 'none' | 'create';
  readonly loginHint?: string;
  readonly invitationProof?: string;
}

interface SessionState {
  readonly accessToken: string | null;
  /** O `sub`: identifica a persoa para non mesturar datos de dúas sesións. */
  readonly subject: string | undefined;
  readonly name: string | undefined;
  readonly email: string | undefined;
}

const ANONYMOUS: SessionState = {
  accessToken: null,
  subject: undefined,
  name: undefined,
  email: undefined,
};

/** Realm de Keycloak na mesma orixe (ADR-0007): a consola de conta vive en `…/account`. */
export const REALM_PATH = '/auth/realms/furar';

/** Crea o cliente OIDC; por defecto carga oidc-client-ts á parte (lazy). Os tests poñen un dobre. */
export const USER_MANAGER_FACTORY = new InjectionToken<(origin: string, session: Storage) => Promise<UserManager>>(
  'USER_MANAGER_FACTORY',
  {
    providedIn: 'root',
    factory: () => (origin, session) =>
      import('./oidc').then(({ createUserManager }) => createUserManager(origin, session)),
  },
);

/**
 * Sesión do usuario (ADR-0007). Ao recargar, os tokens (en memoria) pérdense: se había
 * sesión, a web vai a Keycloak con prompt=none e volve xa autenticada sen pedir nada
 * (cookie SSO de primeira parte). Se non había sesión, non carga nin consulta Keycloak.
 */
@Injectable({ providedIn: 'root' })
export class SessionService {
  private readonly document = inject(DOCUMENT);
  private readonly language = inject(LanguageService);
  private readonly firstRun = inject(FirstRun);
  private readonly municipality = inject(CurrentMunicipality);
  private readonly createManager = inject(USER_MANAGER_FACTORY);
  private readonly state = signal<SessionState>(ANONYMOUS);
  private manager: Promise<UserManager> | undefined;
  private ready: Promise<void> | undefined;
  /** A páxina de onde saíu quen acaba de entrar (non ao recuperar a sesión); `null` se non. */
  private entered: string | null = null;

  readonly authenticated = computed(() => this.state().accessToken !== null);
  readonly accessToken = computed(() => this.state().accessToken);
  readonly subject = computed(() => this.state().subject);
  readonly name = computed(() => this.state().name);
  readonly email = computed(() => this.state().email);

  /** Consola de conta de Keycloak (datos e contrasinal), na mesma orixe. */
  accountUrl(): string {
    return `${this.document.defaultView?.location.origin ?? ''}${REALM_PATH}/account`;
  }

  /** «Seguridade da conta» na consola de Keycloak: o contrasinal. */
  securityUrl(): string {
    return `${this.accountUrl()}/account-security/signing-in`;
  }

  /** Arranque (APP_INITIALIZER): procesa a volta de Keycloak antes de que o router lea o URL. */
  init(): Promise<void> {
    this.ready ??= this.start().catch(() => this.clear());
    return this.ready;
  }

  whenReady(): Promise<void> {
    return this.init();
  }

  /**
   * Se esta carga é a volta dun acceso (Entrar, crear a conta ou verificar o correo noutra lapela),
   * a páxina de onde saíu; `null` se non ou se xa se colleu. Ao recuperar a sesión cun recargar,
   * `null`: a entrada da xestión (`login-landing.ts`) só se decide unha vez, ao entrar.
   */
  takeEntered(): string | null {
    const entered = this.entered;
    this.entered = null;
    return entered;
  }

  /** Vai a Keycloak e volve a `returnUrl`. */
  async login(returnUrl: string, options: LoginOptions = {}): Promise<void> {
    this.storage('session')?.setItem(RETURN_KEY, returnUrl);
    if (options.prompt !== 'none') this.storage('session')?.setItem(INTERACTIVE_KEY, '1');
    const manager = await this.userManager();
    const slug = this.municipality.slug();
    const extra = {
      ...(options.invitationProof ? { furar_invitation: options.invitationProof } : {}),
      ...(this.firstRun.noMunicipality() ? { furar_setup: '1' } : {}),
      // Sen páxina de privacidade (modo «Sen páxina»), o rexistro non leva a ligazón.
      ...(slug && legalPagePublished(this.municipality.value()?.legal, 'privacy')
        ? { furar_privacy: `/${slug}/${LEGAL_PAGE_PATHS.privacy}` }
        : {}),
    };
    await manager.signinRedirect({
      ...(options.prompt ? { prompt: options.prompt } : {}),
      ...(options.loginHint ? { login_hint: options.loginHint } : {}),
      ...(Object.keys(extra).length ? { extraQueryParams: extra } : {}),
      ui_locales: this.language.lang(),
    });
  }

  /**
   * Pecha a sesión en Keycloak. Con `returnUrl` (p. ex. a invitación, para seguir con outra conta),
   * a web volve alí en canto Keycloak a devolve á raíz, nesta lapela.
   */
  async logout(returnUrl?: string): Promise<void> {
    this.storage('local')?.removeItem(HINT_KEY);
    if (returnUrl && isLocalPath(returnUrl)) this.storage('session')?.setItem(LOGOUT_RETURN_KEY, returnUrl);
    const manager = await this.userManager();
    const user = await manager.getUser();
    this.clear();
    await manager.removeUser();
    await manager.signoutRedirect({
      id_token_hint: user?.id_token,
      extraQueryParams: { ui_locales: this.language.lang() },
    });
  }

  private async start(): Promise<void> {
    const location = this.document.defaultView?.location;
    if (!location) {
      return;
    }
    const afterLogout = this.storage('session')?.getItem(LOGOUT_RETURN_KEY);
    if (afterLogout) {
      // De volta de pechar sesión (na raíz): á páxina pedida, antes de que arrinque o router.
      this.storage('session')?.removeItem(LOGOUT_RETURN_KEY);
      if (location.pathname === '/' && isLocalPath(afterLogout)) {
        this.document.defaultView?.history.replaceState(null, '', afterLogout);
        return;
      }
    }
    const params = new URLSearchParams(location.search);
    if (params.has('state') && (params.has('code') || params.has('error'))) {
      await this.finishLogin(location.href);
    } else if (this.storage('local')?.getItem(HINT_KEY) === '1') {
      // Consómese antes de ir: se Keycloak non responde, a seguinte carga non volve tentalo.
      this.storage('local')?.setItem(HINT_KEY, '0');
      // Recuperar a sesión non é entrar (unha marca dun acceso abandonado nesta lapela, fóra).
      this.storage('session')?.removeItem(INTERACTIVE_KEY);
      const returnUrl = location.pathname + location.search + location.hash;
      // O fragmento (p. ex. o token dunha invitación) volve desde sessionStorage, non do historial.
      if (location.hash) this.document.defaultView?.history.replaceState(null, '', returnUrl.split('#')[0]);
      await this.login(returnUrl, { prompt: 'none' });
      // signinRedirect navega fóra: a aplicación non debe seguir arrincando.
      await new Promise<never>(() => undefined);
    }
  }

  private async finishLogin(url: string): Promise<void> {
    const returnUrl = this.storage('session')?.getItem(RETURN_KEY) ?? '/';
    this.storage('session')?.removeItem(RETURN_KEY);
    const retried = this.storage('session')?.getItem(RETRY_KEY) === '1';
    this.storage('session')?.removeItem(RETRY_KEY);
    const interactive = this.storage('session')?.getItem(INTERACTIVE_KEY) === '1';
    this.storage('session')?.removeItem(INTERACTIVE_KEY);
    // O router aínda non arrincou: lerá directamente a páxina de orixe, sen ?code=...
    const safe = isLocalPath(returnUrl) ? returnUrl : '/';
    try {
      const manager = await this.userManager();
      this.apply(await manager.signinRedirectCallback(url));
      this.storage('local')?.setItem(HINT_KEY, '1');
      if (interactive) this.entered = safe;
    } catch {
      // login_required (sen sesión en Keycloak), cancelado ou caducado: segue anónimo.
      this.clear();
      if (!retried && finishedElsewhere(new URL(url).searchParams)) {
        // O acceso rematou noutra lapela (p. ex. a ligazón do correo de verificación): Keycloak xa
        // ten a sesión, así que entrar outra vez en silencio crea a desta lapela.
        this.storage('session')?.setItem(RETRY_KEY, '1');
        // Tamén é un acceso (o da conta nova): ao volver, a mesma entrada ca con «Entrar».
        this.storage('session')?.setItem(INTERACTIVE_KEY, '1');
        await this.login(safe, { prompt: 'none' });
        // signinRedirect navega fóra: a aplicación non debe seguir arrincando.
        await new Promise<never>(() => undefined);
      }
    }
    this.document.defaultView?.history.replaceState(null, '', safe);
  }

  private userManager(): Promise<UserManager> {
    const view = this.document.defaultView;
    this.manager ??= this.createManager(
      view?.location.origin ?? '',
      view?.sessionStorage ?? sessionStorageFallback(),
    ).then((manager) => {
      manager.events.addUserLoaded((user) => this.apply(user));
      manager.events.addAccessTokenExpired(() => this.clear());
      manager.events.addSilentRenewError(() => this.clear());
      return manager;
    });
    return this.manager;
  }

  private apply(user: User): void {
    const name = user.profile['name'] ?? user.profile['preferred_username'];
    const email = user.profile['email'];
    const subject = user.profile['sub'];
    this.state.set({
      accessToken: user.access_token,
      subject: typeof subject === 'string' ? subject : undefined,
      name: typeof name === 'string' ? name : undefined,
      email: typeof email === 'string' ? email : undefined,
    });
  }

  private clear(): void {
    this.state.set(ANONYMOUS);
  }

  private storage(kind: 'local' | 'session'): Storage | undefined {
    try {
      const view = this.document.defaultView;
      return kind === 'local' ? view?.localStorage : view?.sessionStorage;
    } catch {
      return undefined;
    }
  }
}

/**
 * Se a volta de Keycloak fallou porque o acceso rematou noutra lapela do mesmo navegador. Pasa ao
 * verificar o correo: a ligazón ábrese noutra lapela e Keycloak segue alí o acceso.
 * - Esta lapela, a do rexistro, volve con `temporarily_unavailable` / `authentication_expired`:
 *   Keycloak pídelle ao cliente que volva tentalo, porque o acceso xa rematou.
 * - A lapela da ligazón volve cun `code` que non casa con ningún `state` gardado (o `state` está
 *   no sessionStorage da outra lapela): non se pode canxear, pero a sesión de Keycloak xa existe.
 * `login_required` (sen sesión) ou `access_denied` (cancelado) non: quedan anónimos.
 */
function finishedElsewhere(params: URLSearchParams): boolean {
  if (params.has('code')) return true;
  return (
    params.get('error') === 'temporarily_unavailable' && params.get('error_description') === 'authentication_expired'
  );
}

function sessionStorageFallback(): Storage {
  return {
    length: 0,
    clear: () => undefined,
    getItem: () => null,
    key: () => null,
    removeItem: () => undefined,
    setItem: () => undefined,
  };
}
