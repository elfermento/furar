// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Api } from '../../api/api';
import { SessionService } from './session.service';
import { StaffMembershipsService } from './staff-memberships.service';

describe('StaffMembershipsService', () => {
  const authenticated = signal(true);
  const subject = signal<string | undefined>('sub-1');
  const api = {
    invoke: vi.fn(() =>
      Promise.resolve([
        { slug: 'ares', name: 'Ares', role: 'STAFF' },
        { slug: 'naron', name: 'Narón', role: 'MANAGER' },
      ]),
    ),
  };

  function create(): StaffMembershipsService {
    TestBed.configureTestingModule({
      providers: [
        { provide: Api, useValue: api },
        { provide: SessionService, useValue: { authenticated, subject } },
      ],
    });
    return TestBed.inject(StaffMembershipsService);
  }

  beforeEach(() => {
    authenticated.set(true);
    subject.set('sub-1');
    api.invoke.mockClear();
  });

  it('carga unha vez por persoa e baleira sen sesión', async () => {
    const service = create();
    expect(await service.load()).toHaveLength(2);
    await service.load();
    expect(api.invoke).toHaveBeenCalledTimes(1);
    expect(service.memberships().map((m) => m.slug)).toEqual(['ares', 'naron']);
    authenticated.set(false);
    expect(service.memberships()).toEqual([]);
    expect(await service.load()).toEqual([]);
  });

  it('outra persoa volve preguntar', async () => {
    const service = create();
    await service.load();
    subject.set('sub-2');
    expect(service.memberships()).toEqual([]);
    await service.load();
    expect(api.invoke).toHaveBeenCalledTimes(2);
  });

  it('se a API falla, sen concellos', async () => {
    api.invoke.mockRejectedValueOnce(new Error('403'));
    expect(await create().load()).toEqual([]);
  });
});
