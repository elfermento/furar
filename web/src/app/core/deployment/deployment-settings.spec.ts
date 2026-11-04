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
    expect(TestBed.inject(DeploymentSettings)).toBe(settings);
    backend().expectNone('/api/v1/deployment-settings');
  });

  it('sen mapa no despregue, ou se a API non responde: sen mapa', async () => {
    const settings = create();
    flushProblem(await expectRequest('/api/v1/deployment-settings'), 500, 'common.internal');
    await vi.waitFor(() => expect(settings.map()).not.toBeNull());
    expect(settings.mapEnabled()).toBe(false);
  });
});
