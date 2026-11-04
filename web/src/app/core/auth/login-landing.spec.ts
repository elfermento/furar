// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injector } from '@angular/core';
import { CurrentMunicipality } from '../municipality/current-municipality';
import { MunicipalityDirectory } from '../municipality/municipality-directory';
import { OpeningStatus } from '../municipality/opening';
import type { LandingFacts } from './login-landing';
import { apiLandingFacts, loginLanding } from './login-landing';
import { PlatformAccessService } from './platform-access.service';
import { StaffAccessService } from './staff-access.service';

describe('entrada despois de iniciar sesión (maqueta 56)', () => {
  function facts(over: Partial<{ platform: boolean; count: number; pending: boolean }> = {}) {
    const { platform = false, count = 2, pending = false } = over;
    const spies = {
      platformAdmin: vi.fn(() => Promise.resolve(platform)),
      municipalityCount: vi.fn(() => Promise.resolve(count)),
      pendingOpening: vi.fn<LandingFacts['pendingOpening']>(() => Promise.resolve(pending)),
    };
    return spies satisfies LandingFacts;
  }

  it('a plataforma sen ningún concello vai á alta, desde a raíz ou desde calquera outra páxina', async () => {
    expect(await loginLanding('/', facts({ platform: true, count: 0 }))).toBe('/plataforma/concellos/novo');
    expect(await loginLanding('/?x=1#y', facts({ platform: true, count: 0 }))).toBe('/plataforma/concellos/novo');
  });

  it('a plataforma con concellos, na raíz: queda', async () => {
    const f = facts({ platform: true, count: 3 });
    expect(await loginLanding('/', f)).toBeNull();
    expect(f.pendingOpening).not.toHaveBeenCalled();
  });

  it('as páxinas da plataforma e de erro quedan como están, sen preguntar nada', async () => {
    for (const from of ['/plataforma/rexistro', '/plataforma/concellos/novo', '/erro/404']) {
      const f = facts({ platform: true, count: 0 });
      expect(await loginLanding(from, f)).toBeNull();
      expect(f.platformAdmin).not.toHaveBeenCalled();
    }
  });

  it('cun paso pendente, desde a web pública do concello: á pestana «Xeral»', async () => {
    const f = facts({ pending: true });
    expect(await loginLanding('/naron', f)).toBe('/naron/xestion/concello/xeral');
    expect(await loginLanding('/naron/instalacions?data=2026-11-04', f)).toBe('/naron/xestion/concello/xeral');
    expect(f.pendingOpening).toHaveBeenCalledWith('naron', false);
    expect(await loginLanding('/naron', facts({ platform: true, pending: true }))).toBe(
      '/naron/xestion/concello/xeral',
    );
  });

  it('sen pendentes, volve onde estaba', async () => {
    expect(await loginLanding('/naron', facts())).toBeNull();
  });

  it('quen ía á xestión ou a aceptar unha invitación segue alí', async () => {
    for (const from of ['/naron/xestion/reservas', '/naron/convite#token']) {
      const f = facts({ pending: true });
      expect(await loginLanding(from, f)).toBeNull();
      expect(f.pendingOpening).not.toHaveBeenCalled();
    }
  });

  describe('os datos, desde os servizos', () => {
    function injector(role: 'MANAGER' | 'STAFF' | null, platform: boolean, pending: boolean) {
      const opening = { pending: vi.fn(() => Promise.resolve(pending)) };
      const current = { load: vi.fn((slug: string) => Promise.resolve({ slug, legal: {} })) };
      const value = Injector.create({
        providers: [
          { provide: PlatformAccessService, useValue: { load: () => Promise.resolve(platform) } },
          { provide: MunicipalityDirectory, useValue: { list: () => Promise.resolve([]) } },
          {
            provide: StaffAccessService,
            useValue: { load: () => Promise.resolve({ role, previousSignInAt: null, workAccount: role !== null }) },
          },
          { provide: CurrentMunicipality, useValue: current },
          { provide: OpeningStatus, useValue: opening },
        ],
      });
      return { value, opening, current };
    }

    it('Xestión ou a plataforma len os pasos do concello; Recepción e a veciñanza, non', async () => {
      const manager = injector('MANAGER', false, true);
      expect(await apiLandingFacts(manager.value).pendingOpening('naron', false)).toBe(true);
      expect(manager.opening.pending).toHaveBeenCalledWith({ slug: 'naron', legal: {} });

      const platform = injector(null, true, true);
      expect(await apiLandingFacts(platform.value).pendingOpening('naron', true)).toBe(true);

      for (const role of ['STAFF', null] as const) {
        const other = injector(role, false, true);
        expect(await apiLandingFacts(other.value).pendingOpening('naron', false)).toBe(false);
        expect(other.current.load).not.toHaveBeenCalled();
      }
      expect(await apiLandingFacts(manager.value).municipalityCount()).toBe(0);
    });
  });
});
