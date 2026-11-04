// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Location } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { CurrentMunicipality } from '../../../core/municipality/current-municipality';
import { backend, expectRequest, fakeSession, flushProblem, provideTestApi } from '../../../../testing/api';
import { expectNoAxeViolations } from '../../../../testing/axe';
import { municipality } from '../../../../testing/fixtures';
import { StaffInvitationPage } from './staff-invitation.page';
import { StaffAccessService } from '../../../core/auth/staff-access.service';
import { ToastService } from '../../../shared/ui/toast/toast.service';

// Token de proba co formato dun real (43 caracteres base64url), claramente falso.
const TOKEN = 'test-invitation-token'.padEnd(43, '0');
const BASE = '/api/v1/municipalities/naron';
const PREVIEW = `${BASE}/staff-invitations/preview`;
const ACCEPT = `${BASE}/staff-invitations/accept`;
const ME = `${BASE}/me/staff`;
const KEY = 'furar.invitation';

const PREVIEW_BODY = {
  municipalityName: 'Narón',
  role: 'STAFF',
  email: 'Xestora@naron.example',
  expiresAt: '2026-11-04T09:00:00Z',
  // Proba de mentira co formato dunha real (a firma vale só para Keycloak).
  registrationProof: 'v1.proba-de-test.0.eGVzdG9yYQ.firma-de-test',
};

@Component({ template: '' })
class Blank {}

type Session = ReturnType<typeof fakeSession>;

function progress(value: Record<string, unknown> = {}) {
  sessionStorage.setItem(KEY, JSON.stringify({ slug: 'naron', token: TOKEN, ...value }));
}
const stored = () => JSON.parse(sessionStorage.getItem(KEY) ?? 'null') as Record<string, unknown> | null;
const text = (node: Element | null | undefined) => node?.textContent?.replace(/\s+/g, ' ').trim();

async function render(url: string, session: Session = fakeSession()) {
  TestBed.configureTestingModule({
    providers: [
      ...provideTestApi(session),
      provideRouter([
        { path: 'naron/convite', component: StaffInvitationPage },
        { path: 'naron/xestion/concello', component: Blank },
        { path: 'naron/xestion/reservas', component: Blank },
      ]),
    ],
  });
  TestBed.inject(CurrentMunicipality).set(municipality());
  const harness = await RouterTestingHarness.create(url);
  const el = harness.routeNativeElement!;
  const settle = async () => {
    await harness.fixture.whenStable();
    harness.detectChanges();
  };
  /** Espera a que o estado teña ese título e que o foco estea nel. */
  const title = async (expected: string) => {
    await vi.waitFor(async () => {
      await settle();
      const h1 = el.querySelector('h1');
      expect(text(h1)).toBe(expected);
      expect(document.activeElement).toBe(h1);
    });
  };
  return { harness, el, settle, title, session };
}

const anonymous = () => {
  const session = fakeSession({});
  session.authenticated.set(false);
  return session;
};

async function flushPreview() {
  const request = await expectRequest((r) => r.url === PREVIEW && r.method === 'POST');
  expect(request.request.body).toEqual({ token: TOKEN });
  request.flush(PREVIEW_BODY);
}

function click(el: HTMLElement, selector: string) {
  const button = el.querySelector<HTMLElement>(selector);
  if (!button) throw new Error(`sen ${selector}`);
  button.click();
}

/** Ao volver con sesión, a aceptación sae soa despois de ver a invitación: só o token. */
async function expectAccept() {
  await flushPreview();
  const accept = await expectRequest((r) => r.url === ACCEPT && r.method === 'POST');
  expect(accept.request.body).toEqual({ token: TOKEN });
  return accept;
}

describe('Invitación ao persoal (maqueta 38)', () => {
  beforeEach(() => {
    sessionStorage.clear();
    history.replaceState(null, '', '/');
  });
  afterEach(() => {
    backend().verify();
    vi.restoreAllMocks();
  });

  it('o token do fragmento vai a sessionStorage e sae do URL; sen sesión, directamente ao rexistro co correo e a proba', async () => {
    // O historial do navegador nos tests é o de Angular (MockPlatformLocation): espíase `Location`.
    const go = vi.spyOn(Location.prototype, 'go');
    const replace = vi.spyOn(Location.prototype, 'replaceState');
    const { el, session } = await render(`/naron/convite#${TOKEN}`, anonymous());
    await flushPreview();
    await vi.waitFor(() =>
      expect(session.login).toHaveBeenCalledWith('/naron/convite', {
        prompt: 'create',
        loginHint: 'Xestora@naron.example',
        invitationProof: PREVIEW_BODY.registrationProof,
      }),
    );
    expect(session.login).toHaveBeenCalledTimes(1);
    expect(stored()).toEqual({ slug: 'naron', token: TOKEN, stage: 'keycloak' });
    // A carga co token (a navegación da proba) substitúese polo mesmo camiño sen fragmento.
    expect(go.mock.calls.map((call) => call[0])).toEqual([`/naron/convite#${TOKEN}`]);
    expect(replace.mock.lastCall?.[0]).toBe('/naron/convite');
    expect(TestBed.inject(Router).url).toBe('/naron/convite');
    expect(location.href).not.toContain(TOKEN);
    // Sen pantalla intermedia: mentres vai a Keycloak, só a carga.
    expect(el.querySelector('h1')).toBeNull();
  });

  it('volve de Keycloak sen sesión (cancelou): non redirixe outra vez soa; o botón leva de novo; axe', async () => {
    progress({ stage: 'keycloak' });
    const { el, title, session } = await render('/naron/convite', anonymous());
    await flushPreview();
    await title('Invitación ao persoal de Narón');
    expect(session.login).not.toHaveBeenCalled();
    expect(text(el.querySelector('.f-page-header__intro'))).toBe(
      'Narón invitoute a formar parte do seu persoal co rol Recepción.',
    );
    expect(text(el.querySelector('[data-invited-email]'))).toBe(
      'Para aceptala, crea a túa conta con Xestora@naron.example ou entra con ela.',
    );
    await expectNoAxeViolations(el);
    click(el, '[data-sign-in]');
    expect(session.login).toHaveBeenCalledWith('/naron/convite', {
      prompt: 'create',
      loginHint: 'Xestora@naron.example',
      invitationProof: PREVIEW_BODY.registrationProof,
    });
  });

  it('con sesión acepta soa, sen capa nin casiña: toast e o persoal do mostrador vai ás reservas', async () => {
    progress({ stage: 'keycloak' });
    const { el } = await render('/naron/convite');
    const accept = await expectAccept();
    // Mentres acepta, só a carga: nin capa, nin casiña, nin obrigas.
    expect(el.querySelector('h1')).toBeNull();
    expect(document.querySelector('furar-dialog-container')).toBeNull();
    expect(el.querySelector('input[type="checkbox"]')).toBeNull();
    // O acceso gardado era o de antes de aceptar: volve preguntalo antes de ir ás reservas.
    const reload = vi.spyOn(TestBed.inject(StaffAccessService), 'reload');
    accept.flush({ role: 'STAFF' });
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/naron/xestion/reservas'));
    expect(reload).toHaveBeenCalledWith('naron');
    expect(TestBed.inject(ToastService).current()).toMatchObject({
      key: 'staff.invitation.accepted',
      params: { ofName: 'de Narón' },
    });
    expect(stored()).toBeNull();
  });

  it('a persoa xestora vai aos datos do concello', async () => {
    progress();
    await render('/naron/convite');
    (await expectAccept()).flush({ role: 'MANAGER' });
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/naron/xestion/concello'));
  });

  it('correo distinto: «Xa iniciaches sesión con outra conta.», sen ningún correo; pechar sesión volve á invitación', async () => {
    progress({ stage: 'keycloak' });
    const session = fakeSession();
    session.email.set('outra@example.org');
    const { el, title } = await render('/naron/convite', session);
    await flushPreview();
    await title('Xa iniciaches sesión con outra conta.');
    // Nin o correo invitado nin o da sesión (maqueta 50).
    expect(text(el)).not.toContain('@');
    expect(text(el.querySelector('[data-action]'))).toBe('Pechar sesión e continuar');
    expect(text(el.querySelector('[data-back]'))).toBe('Volver');
    await expectNoAxeViolations(el);
    click(el, '[data-action]');
    await vi.waitFor(() => expect(session.logout).toHaveBeenCalledWith('/naron/convite'));
    // A invitación segue nesta lapela, coma nova: ao volver leva outra vez ao rexistro.
    expect(stored()).toEqual({ slug: 'naron', token: TOKEN });
  });

  it('outra conta, «Volver»: deixa a invitación e vai á súa xestión', async () => {
    progress();
    const session = fakeSession();
    session.email.set('outra@example.org');
    const { el, title } = await render('/naron/convite', session);
    await flushPreview();
    await title('Xa iniciaches sesión con outra conta.');
    click(el, '[data-back]');
    (await expectRequest(ME)).flush({ role: 'MANAGER' });
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/naron/xestion/concello'));
    expect(stored()).toBeNull();
    expect(session.logout).not.toHaveBeenCalled();
  });

  it('caducada e inválida: o seu texto, ir ao inicio e a invitación esquecida', async () => {
    progress();
    const expired = await render('/naron/convite', anonymous());
    flushProblem(await expectRequest(PREVIEW), 410, 'staff.invitation-expired');
    await expired.title('A invitación caducou');
    expect(expired.el.querySelector('[data-action]')?.getAttribute('href')).toBe('/naron');
    expect(stored()).toBeNull();
    await expectNoAxeViolations(expired.el);

    TestBed.resetTestingModule();
    progress();
    const invalid = await render('/naron/convite', anonymous());
    flushProblem(await expectRequest(PREVIEW), 404, 'staff.invitation-invalid');
    await invalid.title('Esta invitación non serve');
    expect(text(invalid.el)).toContain('Abre a ligazón do último correo');
    expect(stored()).toBeNull();
  });

  it('sen token: inválida, sen chamar á API', async () => {
    const { title } = await render('/naron/convite', anonymous());
    await title('Esta invitación non serve');
  });

  it('xa usada por quen entrou: xa é persoal e vai á súa xestión', async () => {
    progress();
    const { el, title } = await render('/naron/convite');
    flushProblem(await expectRequest(PREVIEW), 404, 'staff.invitation-invalid');
    (await expectRequest(ME)).flush({ role: 'MANAGER' });
    await title('Xa formas parte do persoal');
    expect(el.querySelector('[data-action]')?.getAttribute('href')).toBe('/naron/xestion/concello');
  });

  it('erros ao aceptar: correo sen verificar, outra conta, xa membro e un xenérico con reintento', async () => {
    const accept = async (status: number, code: string) => {
      TestBed.resetTestingModule();
      progress();
      const view = await render('/naron/convite');
      flushProblem(await expectAccept(), status, code);
      return view;
    };

    const unverified = await accept(403, 'auth.email-not-verified');
    await unverified.title('Verifica o teu correo');
    expect(text(unverified.el)).toContain('volve a esta páxina e preme «Continuar»');
    await expectNoAxeViolations(unverified.el);
    click(unverified.el, '[data-action]');
    await vi.waitFor(() => expect(unverified.session.login).toHaveBeenCalledWith('/naron/convite'));

    // Outro correo, a conta da plataforma ou a de quen invitou: a mesma pantalla, sen bucle de erro.
    for (const code of [
      'staff.invitation-email-mismatch',
      'staff.invitation-platform-account',
      'staff.invitation-own',
    ]) {
      const other = await accept(403, code);
      await other.title('Xa iniciaches sesión con outra conta.');
      expect(text(other.el)).not.toContain('@');
      expect(other.el.querySelector('[data-back]')).not.toBeNull();
    }

    const member = await accept(409, 'staff.already-member');
    (await expectRequest(ME)).flush({ role: 'STAFF' });
    await member.title('Xa formas parte do persoal');
    await vi.waitFor(async () => {
      await member.settle();
      expect(member.el.querySelector('[data-action]')?.getAttribute('href')).toBe('/naron/xestion/reservas');
    });

    const generic = await accept(500, 'common.unexpected');
    await generic.title('Non se puido continuar');
    expect(TestBed.inject(ToastService).current()).toBeNull();
    click(generic.el, '[data-action]');
    (await expectAccept()).flush({ role: 'MANAGER' });
    await vi.waitFor(() => expect(TestBed.inject(Router).url).toBe('/naron/xestion/concello'));
  });

  it('segunda aceptación (404 despois dun 200): xa forma parte do persoal', async () => {
    progress();
    const { title } = await render('/naron/convite');
    flushProblem(await expectAccept(), 404, 'staff.invitation-invalid');
    (await expectRequest(ME)).flush({ role: 'MANAGER' });
    await title('Xa formas parte do persoal');
    expect(stored()).toBeNull();
  });
});
