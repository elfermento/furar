// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { EnvironmentProviders } from '@angular/core';
import { Injector, inject, makeEnvironmentProviders, provideAppInitializer } from '@angular/core';
import { SessionService } from './session.service';

/**
 * Sesión OIDC: procesa a volta de Keycloak ou recupera a sesión antes da primeira navegación. Se
 * a persoa acaba de entrar, decide a súa entrada (`login-landing.ts`, cargado á parte) antes de que
 * o router lea o URL: así a primeira páxina xa é a boa, sen pasar pola de orixe.
 */
export function provideFurarAuth(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideAppInitializer(async () => {
      const session = inject(SessionService);
      const injector = inject(Injector);
      const view = inject(DOCUMENT).defaultView;
      await session.init();
      const from = session.authenticated() ? session.takeEntered() : null;
      if (from === null || !view) return;
      try {
        const { apiLandingFacts, loginLanding } = await import('./login-landing');
        const to = await loginLanding(from, apiLandingFacts(injector));
        if (to !== null) view.history.replaceState(null, '', to);
      } catch {
        // Sen rede ou sen API: volve á páxina de onde saíu, coma sempre.
      }
    }),
  ]);
}
