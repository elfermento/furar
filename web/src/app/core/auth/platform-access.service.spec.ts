// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Api } from '../../api/api';
import { PlatformAccessService } from './platform-access.service';
import { SessionService } from './session.service';

describe('PlatformAccessService', () => {
  const authenticated = signal(true);
  const subject = signal<string | undefined>('sub-1');
  const api = { invoke: vi.fn(() => Promise.resolve({ platformAdmin: true })) };

  function create(): PlatformAccessService {
    TestBed.configureTestingModule({
      providers: [
        { provide: Api, useValue: api },
        { provide: SessionService, useValue: { authenticated, subject } },
      ],
    });
    return TestBed.inject(PlatformAccessService);
  }

  beforeEach(() => {
    authenticated.set(true);
    subject.set('sub-1');
    api.invoke.mockClear();
    api.invoke.mockImplementation(() => Promise.resolve({ platformAdmin: true }));
  });

  it('pregunta á API unha vez por persoa (o rol vive na base, ADR-0028)', async () => {
    const service = create();
    expect(await service.load()).toBe(true);
    expect(await service.load()).toBe(true);
    expect(api.invoke).toHaveBeenCalledTimes(1);
    expect(service.platformAdmin()).toBe(true);

    subject.set('sub-2');
    expect(service.platformAdmin()).toBe(false);
    await service.load();
    expect(api.invoke).toHaveBeenCalledTimes(2);
  });

  it('sen sesión ou se a API falla, non é administración', async () => {
    const service = create();
    authenticated.set(false);
    expect(await service.load()).toBe(false);
    expect(api.invoke).not.toHaveBeenCalled();

    authenticated.set(true);
    api.invoke.mockImplementation(() => Promise.reject(new Error('403')));
    expect(await service.load()).toBe(false);
    expect(service.platformAdmin()).toBe(false);
  });

  it('un fallo non se garda: a seguinte lectura volve preguntar (a primeira entrada da plataforma)', async () => {
    const service = create();
    api.invoke.mockImplementationOnce(() => Promise.reject(new Error('rede')));
    expect(await service.load()).toBe(false);
    expect(await service.load()).toBe(true);
    expect(service.platformAdmin()).toBe(true);
  });
});
