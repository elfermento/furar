// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { ApplicationInitStatus, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MunicipalityDirectory } from '../municipality/municipality-directory';
import { provideFurarAuth } from './auth.providers';
import { PlatformAccessService } from './platform-access.service';
import { SessionService } from './session.service';

describe('arranque: a entrada de quen acaba de entrar, antes do router', () => {
  beforeEach(() => history.replaceState(null, '', '/'));

  async function boot(entered: string | null, platformAdmin: boolean) {
    const session = {
      init: vi.fn(() => Promise.resolve()),
      authenticated: signal(true),
      takeEntered: vi.fn(() => entered),
    };
    TestBed.configureTestingModule({
      providers: [
        provideFurarAuth(),
        { provide: SessionService, useValue: session },
        { provide: PlatformAccessService, useValue: { load: () => Promise.resolve(platformAdmin) } },
        { provide: MunicipalityDirectory, useValue: { list: () => Promise.resolve([]) } },
      ],
    });
    await TestBed.inject(ApplicationInitStatus).donePromise;
    return session;
  }

  it('a plataforma sen concellos, ao volver de entrar: o URL xa é o da alta', async () => {
    const session = await boot('/', true);
    expect(session.init).toHaveBeenCalled();
    expect(location.pathname).toBe('/plataforma/concellos/novo');
  });

  it('sen entrada (recuperar a sesión ou anónimo): o URL non cambia', async () => {
    await boot(null, true);
    expect(location.pathname).toBe('/');
  });

  it('quen non é da plataforma, na raíz: queda', async () => {
    await boot('/', false);
    expect(location.pathname).toBe('/');
  });
});
