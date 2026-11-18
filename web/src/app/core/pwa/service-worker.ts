// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
//
// Chunk diferido: `@angular/service-worker` non vai na carga inicial (D27). `deferred-service-worker`
// impórtao cando a aplicación xa está estable.
import type { EnvironmentInjector } from '@angular/core';
import { createEnvironmentInjector } from '@angular/core';
import type { Event } from '@angular/router';
import { NavigationStart, Router } from '@angular/router';
import { SwUpdate, provideServiceWorker } from '@angular/service-worker';
import type { Observable } from 'rxjs';
import { filter } from 'rxjs';
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
  const updates = injector.get(SwUpdate);
  watchUnrecoverable(updates, view);
  watchNewVersion(updates, view, injector.get(Router, null)?.events ?? null);
  return (
    sw
      .register(SERVICE_WORKER_SCRIPT)
      // O worker xa comproba ao abrir, pero antes de que isto escoitase: pregúntase outra vez.
      .then(() => (updates.isEnabled ? updates.checkForUpdate().catch(() => false) : false))
      .catch((error: unknown) => {
        console.error('Furar: non se puido rexistrar o service worker', error);
      })
  );
}

/**
 * O *service worker* xa non pode servir esta versión (`unrecoverable`: borrou da caché ficheiros que
 * a páxina aberta aínda precisa): recargar (unha vez) é o que recomenda Angular.
 */
export function watchUnrecoverable(updates: SwUpdate, view: Window | null): void {
  if (updates.isEnabled) updates.unrecoverable.subscribe(() => reloadOnce(view));
}

/**
 * Versión nova publicada (`VERSION_READY`): ninguén queda nunha versión vella ata a segunda carga.
 * Se a persoa aínda non tocou nada (acaba de abrir a páxina), recárgase xa; se xa está a usala
 * (pode ter un formulario a medias), a versión nova colle na seguinte navegación, que se fai como
 * carga completa do mesmo enderezo.
 */
export function watchNewVersion(updates: SwUpdate, view: Window | null, navigations: Observable<Event> | null): void {
  if (!updates.isEnabled || !view) return;
  let touched = false;
  let pending = false;
  const touch = () => (touched = true);
  view.addEventListener?.('pointerdown', touch, { once: true, capture: true });
  view.addEventListener?.('keydown', touch, { once: true, capture: true });
  updates.versionUpdates.pipe(filter((e) => e.type === 'VERSION_READY')).subscribe(() => {
    if (!touched) reloadOnce(view);
    else pending = true;
  });
  navigations?.pipe(filter((e): e is NavigationStart => e instanceof NavigationStart)).subscribe((e) => {
    if (pending) {
      pending = false;
      view.location.assign(e.url);
    }
  });
}
