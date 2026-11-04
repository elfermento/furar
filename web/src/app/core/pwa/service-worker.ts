// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Chunk diferido: `@angular/service-worker` non vai na carga inicial (D27). `deferred-service-worker`
// impórtao cando a aplicación xa está estable.
import type { EnvironmentInjector } from '@angular/core';
import { createEnvironmentInjector } from '@angular/core';
import { SwUpdate, provideServiceWorker } from '@angular/service-worker';
import { reloadOnce } from './reload-once';

export const SERVICE_WORKER_SCRIPT = 'ngsw-worker.js';

/**
 * Rexistra o *service worker* (ADR-0014) e escoita `unrecoverable`. Fai o mesmo que o
 * inicializador de `provideServiceWorker` (que nun inxector fillo non corre): avisar ao worker
 * cando colle o control (`INITIALIZE`) e rexistrar o script.
 */
export function startServiceWorker(parent: EnvironmentInjector, view: Window): Promise<unknown> {
  const sw = view.navigator.serviceWorker;
  sw.addEventListener('controllerchange', () => sw.controller?.postMessage({ action: 'INITIALIZE' }));
  const injector = createEnvironmentInjector([provideServiceWorker(SERVICE_WORKER_SCRIPT, { enabled: true })], parent);
  watchUnrecoverable(injector.get(SwUpdate), view);
  return sw.register(SERVICE_WORKER_SCRIPT).catch((error: unknown) => {
    console.error('Furar: non se puido rexistrar o service worker', error);
  });
}

/**
 * O *service worker* xa non pode servir esta versión (`unrecoverable`: borrou da caché ficheiros que
 * a páxina aberta aínda precisa): recargar (unha vez) é o que recomenda Angular.
 */
export function watchUnrecoverable(updates: SwUpdate, view: Window | null): void {
  if (updates.isEnabled) updates.unrecoverable.subscribe(() => reloadOnce(view));
}
