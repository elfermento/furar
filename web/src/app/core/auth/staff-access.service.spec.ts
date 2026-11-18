// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, fakeSession, flushProblem, provideTestApi } from '../../../testing/api';
import { municipality } from '../../../testing/fixtures';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { StaffAccessService } from './staff-access.service';

describe('StaffAccessService', () => {
  const url = (slug: string) => `/api/v1/municipalities/${slug}/me/staff`;
  let session: ReturnType<typeof fakeSession>;

  function setup(authenticated = true) {
    session = fakeSession({});
    session.authenticated.set(authenticated);
    TestBed.configureTestingModule({ providers: provideTestApi(session) });
    // O servizo real, non o falso de provideTestApi.
    TestBed.overrideProvider(StaffAccessService, { useFactory: () => new StaffAccessService() });
    return { access: TestBed.inject(StaffAccessService), current: TestBed.inject(CurrentMunicipality) };
  }

  afterEach(() => backend().verify());

  it('sen sesión: non pregunta á API e non hai rol', async () => {
    const { access, current } = setup(false);
    current.set(municipality());
    TestBed.tick();
    expect(await access.load('naron')).toBeNull();
    expect(access.role()).toBeNull();
    backend().expectNone(url('naron'));
  });

  it('co concello actual: pregunta unha vez e dá o rol, o acceso anterior e a conta de traballo', async () => {
    const { access, current } = setup();
    current.set(municipality());
    TestBed.tick();
    const pending = access.load('naron');
    backend().expectOne(url('naron')).flush({
      role: 'MANAGER',
      previousSignInAt: '2026-10-27T17:05:00Z',
      workAccount: true,
    });
    expect(await pending).toEqual({
      role: 'MANAGER',
      previousSignInAt: '2026-10-27T17:05:00Z',
      workAccount: true,
    });
    expect(access.workAccount()).toBe(true);
    expect(access.role()).toBe('MANAGER');
    expect(access.canManage()).toBe(true);
    expect(access.canViewBookings()).toBe(true);
    expect(access.mainRole()).toBe('MANAGER');
    expect(access.previousSignInAt()).toBe('2026-10-27T17:05:00Z');
    // Xa cargado: sen outra petición.
    expect((await access.load('naron'))?.role).toBe('MANAGER');
  });

  it('reload: volve preguntar aínda que xa estea cargado (despois de aceptar unha invitación)', async () => {
    const { access, current } = setup();
    current.set(municipality());
    TestBed.tick();
    const first = access.load('naron');
    backend().expectOne(url('naron')).flush({ role: null });
    expect((await first)?.role).toBeNull();
    const again = access.reload('naron');
    backend().expectOne(url('naron')).flush({ role: 'STAFF' });
    expect((await again)?.role).toBe('STAFF');
    expect(access.role()).toBe('STAFF');
  });

  it('a plataforma: rol nulo da API, non xestiona, pero a barra mostra a plataforma', async () => {
    const { access, current } = setup();
    session.platformAdmin.set(true);
    current.set(municipality());
    TestBed.tick();
    backend().expectOne(url('naron')).flush({ workAccount: false });
    await vi.waitFor(() => expect(access.current()).not.toBeNull());
    expect(access.role()).toBeNull();
    expect(access.canManage()).toBe(false);
    expect(access.canViewBookings()).toBe(false);
    expect(access.mainRole()).toBe('PLATFORM_ADMIN');
    expect(access.previousSignInAt()).toBeNull();
  });

  it('outro concello ou outra persoa invalidan o que había', async () => {
    const { access, current } = setup();
    current.set(municipality());
    TestBed.tick();
    backend().expectOne(url('naron')).flush({ role: 'STAFF' });
    await vi.waitFor(() => expect(access.role()).toBe('STAFF'));

    current.set(municipality({ slug: 'ferrol', name: 'Ferrol' }));
    expect(access.role()).toBeNull();
    TestBed.tick();
    backend().expectOne(url('ferrol')).flush({ role: 'MANAGER' });
    await vi.waitFor(() => expect(access.role()).toBe('MANAGER'));

    session.subject.set('user-2');
    expect(access.role()).toBeNull();
    TestBed.tick();
    backend().expectOne(url('ferrol')).flush({ workAccount: false });
    await vi.waitFor(() => expect(access.current()).not.toBeNull());
    expect(access.role()).toBeNull();
  });

  it('peticións simultáneas (garda e barra) comparten unha soa chamada; un erro é «sen rol»', async () => {
    const { access } = setup();
    const first = access.load('naron');
    const second = access.load('naron');
    flushProblem(backend().expectOne(url('naron')), 403, 'auth.forbidden');
    expect(await first).toBeNull();
    expect(await second).toBeNull();
  });
});
