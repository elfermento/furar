// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import type { EnvironmentProviders } from '@angular/core';
import { ApplicationRef, EnvironmentInjector, inject, provideAppInitializer } from '@angular/core';

/** Como `registerWhenStable:30000`: cando a aplicación está estable ou, como moito, aos 30 s. */
export const REGISTER_TIMEOUT_MS = 30_000;

/**
 * *Service worker* diferido (ADR-0014, D27): `@angular/service-worker` vai nun chunk que se pide
 * despois do arranque, no mesmo momento en que antes se rexistraba. O worker precarga ese chunk
 * co resto de `app`, así que sen conexión tamén está.
 */
export function provideDeferredServiceWorker(enabled: boolean): EnvironmentProviders {
  return provideAppInitializer(() => {
    const view = inject(DOCUMENT).defaultView;
    if (!enabled || !view || !('serviceWorker' in view.navigator)) return;
    const injector = inject(EnvironmentInjector);
    const appRef = inject(ApplicationRef);
    const timeout = new Promise<void>((resolve) => setTimeout(resolve, REGISTER_TIMEOUT_MS));
    // Sen `return`: o arranque non espera polo worker.
    void Promise.race([appRef.whenStable(), timeout])
      .then(() => (appRef.destroyed ? undefined : import('./service-worker')))
      .then((sw) => sw?.startServiceWorker(injector, view));
  });
}
