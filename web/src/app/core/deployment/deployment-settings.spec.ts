// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { TestBed } from '@angular/core/testing';
import { backend, expectRequest, flushProblem, provideTestApi } from '../../../testing/api';
import { IGN_MAP } from '../../../testing/deployment';
import { DeploymentSettings } from './deployment-settings';

describe('DeploymentSettings', () => {
  afterEach(() => backend().verify());

  function create() {
    TestBed.configureTestingModule({
      // provideTestApi trae un DeploymentSettings falso: aquí, o de verdade.
      providers: [...provideTestApi(), { provide: DeploymentSettings, useClass: DeploymentSettings }],
    });
    return TestBed.inject(DeploymentSettings);
  }

  it('pide o mapa do despregue unha vez, sen sesión', async () => {
    const settings = create();
    expect(settings.map()).toBeNull();
    expect(settings.mapEnabled()).toBe(false);
    const request = await expectRequest('/api/v1/deployment-settings');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({ map: IGN_MAP });
    await vi.waitFor(() => expect(settings.map()).toEqual(IGN_MAP));
    expect(settings.mapEnabled()).toBe(true);
    expect(settings.supportEmail()).toBeNull();
    expect(TestBed.inject(DeploymentSettings)).toBe(settings);
    backend().expectNone('/api/v1/deployment-settings');
  });

  it('co correo de soporte do despregue, dálle o valor; baleiro ou sen el, ningún', async () => {
    const settings = create();
    expect(settings.supportEmail()).toBeNull();
    (await expectRequest('/api/v1/deployment-settings')).flush({ map: IGN_MAP, supportEmail: ' soporte@example.org ' });
    await vi.waitFor(() => expect(settings.supportEmail()).toBe('soporte@example.org'));
    TestBed.resetTestingModule();
    const empty = create();
    (await expectRequest('/api/v1/deployment-settings')).flush({ map: IGN_MAP, supportEmail: '' });
    await vi.waitFor(() => expect(empty.map()).not.toBeNull());
    expect(empty.supportEmail()).toBeNull();
  });

  it('sen mapa no despregue, ou se a API non responde: sen mapa nin correo de soporte', async () => {
    const settings = create();
    flushProblem(await expectRequest('/api/v1/deployment-settings'), 500, 'common.internal');
    await vi.waitFor(() => expect(settings.map()).not.toBeNull());
    expect(settings.mapEnabled()).toBe(false);
    expect(settings.supportEmail()).toBeNull();
  });

  it('«¿Es un concello?» só se a API di que existe; mentres carga, non', async () => {
    const settings = create();
    expect(settings.joinRequests()).toBe(false);
    (await expectRequest('/api/v1/deployment-settings')).flush({ map: IGN_MAP, joinRequests: true });
    await settings.ready;
    expect(settings.joinRequests()).toBe(true);
    TestBed.resetTestingModule();
    const failed = create();
    flushProblem(await expectRequest('/api/v1/deployment-settings'), 500, 'common.internal');
    await failed.ready;
    expect(failed.joinRequests()).toBe(false);
  });
});
