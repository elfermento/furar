// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { type FakeUserManager, fakeUser, fakeUserManager } from '../../../testing/fake-user-manager';
import { municipality } from '../../../testing/fixtures';
import { provideTestI18n } from '../../../testing/i18n';
import type { ApiProblem } from '../errors/api-problem';
import { problemInterceptor } from '../errors/problem.interceptor';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { FirstRun } from '../municipality/first-run';
import { bearerInterceptor } from './bearer.interceptor';
import { SessionService, USER_MANAGER_FACTORY } from './session.service';

describe('SessionService', () => {
  let fake: FakeUserManager;
  let factory: ReturnType<typeof vi.fn>;

  function setup(user: ReturnType<typeof fakeUser> | Error = fakeUser()) {
    fake = fakeUserManager(user);
    factory = vi.fn().mockResolvedValue(fake.manager);
    TestBed.configureTestingModule({
      providers: [
        provideTestI18n(),
        provideHttpClient(withInterceptors([bearerInterceptor, problemInterceptor])),
        provideHttpClientTesting(),
        { provide: USER_MANAGER_FACTORY, useValue: factory },
      ],
    });
    return TestBed.inject(SessionService);
  }

  beforeEach(() => {
    localStorage.clear();
    sessionStorage.clear();
    history.replaceState(null, '', '/');
  });

  it('visitante anónimo: non carga o cliente OIDC', async () => {
    const session = setup();
    await session.init();
    expect(session.authenticated()).toBe(false);
    expect(factory).not.toHaveBeenCalled();
  });

  it('volta de Keycloak: crea a sesión, garda a marca e volve á páxina de orixe sen ?code', async () => {
    sessionStorage.setItem('furar.returnUrl', '/naron/xestion');
    history.replaceState(null, '', '/?code=abc&state=xyz');
    const session = setup();
    await session.init();
    expect(fake.signinRedirectCallback).toHaveBeenCalled();
    expect(session.authenticated()).toBe(true);
    expect(session.name()).toBe('Xestora de Narón');
    expect(session.subject()).toBe('user-1');
    expect(localStorage.getItem('furar.session')).toBe('1');
    expect(location.pathname + location.search).toBe('/naron/xestion');
  });

  it('entrar (non recuperar a sesión): a volta lembra unha vez a páxina de orixe para a entrada', async () => {
    const first = setup();
    await first.login('/naron');
    expect(sessionStorage.getItem('furar.loginInteractive')).toBe('1');
    history.replaceState(null, '', '/?code=abc&state=xyz');
    TestBed.resetTestingModule();
    const session = setup();
    await session.init();
    expect(session.takeEntered()).toBe('/naron');
    expect(session.takeEntered()).toBeNull();
    expect(sessionStorage.getItem('furar.loginInteractive')).toBeNull();
  });

  it('recuperar a sesión ao recargar (prompt=none) non é entrar: sen entrada', async () => {
    // Unha marca dun acceso abandonado nesta lapela non conta.
    sessionStorage.setItem('furar.loginInteractive', '1');
    localStorage.setItem('furar.session', '1');
    history.replaceState(null, '', '/naron');
    const restoring = setup();
    void restoring.init();
    await vi.waitFor(() => expect(fake.signinRedirect).toHaveBeenCalled());
    expect(sessionStorage.getItem('furar.loginInteractive')).toBeNull();
    history.replaceState(null, '', '/?code=abc&state=xyz');
    TestBed.resetTestingModule();
    const session = setup();
    await session.init();
    expect(session.authenticated()).toBe(true);
    expect(session.takeEntered()).toBeNull();
  });

  it('volta con erro (login_required): segue anónimo e non deixa ir a outra orixe', async () => {
    sessionStorage.setItem('furar.returnUrl', '//malo.example');
    history.replaceState(null, '', '/?error=login_required&state=xyz');
    const session = setup(new Error('login_required'));
    await session.init();
    expect(session.authenticated()).toBe(false);
    expect(location.pathname).toBe('/');
  });

  it('verificar o correo noutra lapela: a lapela do rexistro (authentication_expired) entra en silencio e volve á súa páxina', async () => {
    sessionStorage.setItem('furar.returnUrl', '/naron/reservas');
    history.replaceState(
      null,
      '',
      '/?error=temporarily_unavailable&error_description=authentication_expired&state=xyz',
    );
    const session = setup(new Error('authentication_expired'));
    void session.init();
    await vi.waitFor(() => expect(fake.signinRedirect).toHaveBeenCalled());
    expect(fake.signinRedirect).toHaveBeenCalledWith({ prompt: 'none', ui_locales: 'gl' });
    expect(sessionStorage.getItem('furar.returnUrl')).toBe('/naron/reservas');
    expect(sessionStorage.getItem('furar.loginRetry')).toBe('1');
    // Ao volver é unha entrada (a da conta nova), coma con «Entrar».
    expect(sessionStorage.getItem('furar.loginInteractive')).toBe('1');
  });

  it('verificar o correo noutra lapela: a lapela da ligazón (code sen state gardado) entra en silencio', async () => {
    history.replaceState(null, '', '/?code=abc&state=doutra-lapela');
    const session = setup(new Error('No matching state found in storage'));
    void session.init();
    await vi.waitFor(() => expect(fake.signinRedirect).toHaveBeenCalled());
    expect(fake.signinRedirect).toHaveBeenCalledWith({ prompt: 'none', ui_locales: 'gl' });
    expect(sessionStorage.getItem('furar.returnUrl')).toBe('/');
  });

  it('volver tentar en silencio só unha vez: se falla outra vez, anónimo e sen bucle', async () => {
    sessionStorage.setItem('furar.loginRetry', '1');
    sessionStorage.setItem('furar.returnUrl', '/naron');
    history.replaceState(null, '', '/?code=abc&state=xyz');
    const session = setup(new Error('invalid_grant'));
    await session.init();
    expect(fake.signinRedirect).not.toHaveBeenCalled();
    expect(session.authenticated()).toBe(false);
    expect(sessionStorage.getItem('furar.loginRetry')).toBeNull();
    expect(location.pathname + location.search).toBe('/naron');
  });

  it('cancelado (access_denied): non volve tentar', async () => {
    history.replaceState(null, '', '/?error=access_denied&state=xyz');
    const session = setup(new Error('access_denied'));
    await session.init();
    expect(fake.signinRedirect).not.toHaveBeenCalled();
    expect(session.authenticated()).toBe(false);
  });

  it('con marca de sesión vai a Keycloak con prompt=none e consome a marca', async () => {
    localStorage.setItem('furar.session', '1');
    history.replaceState(null, '', '/naron?x=1');
    const session = setup();
    void session.init();
    await vi.waitFor(() => expect(fake.signinRedirect).toHaveBeenCalled());
    expect(fake.signinRedirect).toHaveBeenCalledWith({ prompt: 'none', ui_locales: 'gl' });
    expect(sessionStorage.getItem('furar.returnUrl')).toBe('/naron?x=1');
    expect(localStorage.getItem('furar.session')).toBe('0');
  });

  it('login lembra a páxina e manda o idioma; logout limpa e pecha en Keycloak', async () => {
    const session = setup();
    await session.login('/ferrol');
    expect(sessionStorage.getItem('furar.returnUrl')).toBe('/ferrol');
    expect(fake.signinRedirect).toHaveBeenCalledWith({ ui_locales: 'gl' });
    fake.listeners.userLoaded?.(fakeUser());
    // Ningún rol sae do token (ADR-0028): a sesión só garda quen é.
    expect(session.authenticated()).toBe(true);
    expect('platformAdmin' in session).toBe(false);
    await session.logout();
    expect(session.authenticated()).toBe(false);
    expect(fake.removeUser).toHaveBeenCalled();
    expect(fake.signoutRedirect).toHaveBeenCalledWith({
      id_token_hint: 'id-token',
      extraQueryParams: { ui_locales: 'gl' },
    });
  });

  it('logout cun camiño de volta: Keycloak devolve á raíz e a web vai alí (a invitación con outra conta)', async () => {
    const session = setup();
    await session.logout('/naron/convite');
    expect(sessionStorage.getItem('furar.logoutReturnUrl')).toBe('/naron/convite');
    TestBed.resetTestingModule();
    history.replaceState(null, '', '/');
    await setup().init();
    expect(location.pathname).toBe('/naron/convite');
    expect(sessionStorage.getItem('furar.logoutReturnUrl')).toBeNull();
    // Só camiños desta orixe.
    TestBed.resetTestingModule();
    await setup().logout('//malo.example');
    expect(sessionStorage.getItem('furar.logoutReturnUrl')).toBeNull();
  });

  it('sen ningún concello, o acceso leva furar_setup=1 (rexistro sen a frase de privacidade, ADR-0028)', async () => {
    const session = setup();
    TestBed.inject(FirstRun).set(true);
    await session.login('/');
    expect(fake.signinRedirect).toHaveBeenCalledWith({ extraQueryParams: { furar_setup: '1' }, ui_locales: 'gl' });
    TestBed.inject(FirstRun).set(false);
    await session.login('/');
    expect(fake.signinRedirect).toHaveBeenLastCalledWith({ ui_locales: 'gl' });
  });

  it('desde un concello, o acceso leva o camiño da súa páxina de privacidade (furar_privacy)', async () => {
    const session = setup();
    TestBed.inject(CurrentMunicipality).set(municipality());
    await session.login('/naron/reservas', { prompt: 'create' });
    expect(fake.signinRedirect).toHaveBeenLastCalledWith({
      prompt: 'create',
      extraQueryParams: { furar_privacy: '/naron/privacidade' },
      ui_locales: 'gl',
    });
  });

  it('token caducado ou renovación fallida → anónimo', async () => {
    const session = setup();
    await session.login('/');
    fake.listeners.userLoaded?.(fakeUser());
    expect(session.authenticated()).toBe(true);
    fake.listeners.expired?.();
    expect(session.authenticated()).toBe(false);
    fake.listeners.userLoaded?.(fakeUser());
    fake.listeners.renewError?.();
    expect(session.authenticated()).toBe(false);
  });

  it('o token vai só á propia API', async () => {
    const session = setup();
    await session.login('/');
    const user = fakeUser();
    fake.listeners.userLoaded?.(user);
    const http = TestBed.inject(HttpClient);
    const backend = TestBed.inject(HttpTestingController);
    void firstValueFrom(http.get('/api/v1/municipalities'));
    expect(backend.expectOne('/api/v1/municipalities').request.headers.get('Authorization')).toBe(
      `Bearer ${user.access_token}`,
    );
    void firstValueFrom(http.get('https://terceiro.example/api/v1/x'));
    expect(backend.expectOne('https://terceiro.example/api/v1/x').request.headers.has('Authorization')).toBe(false);
    backend.verify();
  });

  it('invitación: login_hint co correo e, para crear a conta, prompt=create', async () => {
    const session = setup();
    await session.login('/naron/convite', { loginHint: 'iago@naron.example' });
    expect(fake.signinRedirect).toHaveBeenLastCalledWith({ login_hint: 'iago@naron.example', ui_locales: 'gl' });
    await session.login('/naron/convite', { prompt: 'create', loginHint: 'iago@naron.example' });
    expect(fake.signinRedirect).toHaveBeenLastCalledWith({
      prompt: 'create',
      login_hint: 'iago@naron.example',
      ui_locales: 'gl',
    });
    // A proba da invitación vai nun parámetro propio (furar_invitation), para o rexistro de Keycloak.
    await session.login('/naron/convite', {
      prompt: 'create',
      loginHint: 'iago@naron.example',
      invitationProof: 'v1.x',
    });
    expect(fake.signinRedirect).toHaveBeenLastCalledWith({
      prompt: 'create',
      login_hint: 'iago@naron.example',
      extraQueryParams: { furar_invitation: 'v1.x' },
      ui_locales: 'gl',
    });
  });

  it('recuperar a sesión cun fragmento: o historial queda sen el e volve desde sessionStorage', async () => {
    localStorage.setItem('furar.session', '1');
    history.replaceState(null, '', '/naron/convite#segredo');
    const session = setup();
    void session.init();
    await vi.waitFor(() => expect(fake.signinRedirect).toHaveBeenCalled());
    expect(location.hash).toBe('');
    expect(location.pathname).toBe('/naron/convite');
    expect(sessionStorage.getItem('furar.returnUrl')).toBe('/naron/convite#segredo');
  });

  it('seguridade da conta: a páxina de acceso da consola de Keycloak', () => {
    expect(setup().securityUrl()).toBe(`${location.origin}/auth/realms/furar/account/account-security/signing-in`);
  });

  it('un 403 da API tradúcese e non leva a Keycloak (sen segundo factor)', async () => {
    const session = setup();
    await session.login('/');
    fake.listeners.userLoaded?.(fakeUser());
    fake.signinRedirect.mockClear();
    const http = TestBed.inject(HttpClient);
    const backend = TestBed.inject(HttpTestingController);
    const result = firstValueFrom(http.get('/api/v1/municipalities/naron')).catch((e: unknown) => e);
    backend
      .expectOne('/api/v1/municipalities/naron')
      .flush({ status: 403, code: 'auth.forbidden', params: {} }, { status: 403, statusText: 'Forbidden' });
    const error = (await result) as ApiProblem;
    expect(error.code).toBe('auth.forbidden');
    expect(error.translated).not.toBe('');
    expect(fake.signinRedirect).not.toHaveBeenCalled();
    backend.verify();
  });
});
