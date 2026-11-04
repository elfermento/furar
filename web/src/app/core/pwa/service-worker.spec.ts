// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { ApplicationRef, EnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import type { SwUpdate } from '@angular/service-worker';
import { Subject } from 'rxjs';
import { REGISTER_TIMEOUT_MS, provideDeferredServiceWorker } from './deferred-service-worker';
import { SERVICE_WORKER_SCRIPT, startServiceWorker, watchUnrecoverable } from './service-worker';

/** `navigator.serviceWorker` falso: rexistro espiado e eventos a man. */
function fakeContainer() {
  const listeners = new Map<string, () => void>();
  const container = {
    controller: { postMessage: vi.fn() },
    register: vi.fn(() => Promise.resolve({})),
    getRegistration: vi.fn(() => Promise.resolve(undefined)),
    addEventListener: vi.fn((type: string, listener: () => void) => listeners.set(type, listener)),
    removeEventListener: vi.fn(),
  };
  const data = new Map<string, string>();
  const reload = vi.fn();
  const view = {
    navigator: { serviceWorker: container },
    location: { reload },
    sessionStorage: {
      getItem: (key: string) => data.get(key) ?? null,
      setItem: (key: string, value: string) => void data.set(key, value),
    },
  } as unknown as Window;
  return { container, view, reload, fire: (type: string) => listeners.get(type)?.() };
}

describe('service worker diferido', () => {
  afterEach(() => vi.useRealTimers());

  it('rexistra o worker e avisa ao novo controlador (INITIALIZE)', async () => {
    const { container, view, fire } = fakeContainer();
    await startServiceWorker(TestBed.inject(EnvironmentInjector), view);
    expect(container.register).toHaveBeenCalledWith(SERVICE_WORKER_SCRIPT);
    fire('controllerchange');
    expect(container.controller.postMessage).toHaveBeenCalledWith({ action: 'INITIALIZE' });
  });

  it('un rexistro fallido non rompe a aplicación', async () => {
    const { container, view } = fakeContainer();
    container.register.mockReturnValueOnce(Promise.reject(new Error('sen https')));
    const error = vi.spyOn(console, 'error').mockImplementation(() => undefined);
    await expect(startServiceWorker(TestBed.inject(EnvironmentInjector), view)).resolves.toBeUndefined();
    expect(error).toHaveBeenCalled();
  });

  it('o service worker sen remedio (unrecoverable) recarga a páxina unha vez', () => {
    const { view, reload } = fakeContainer();
    const unrecoverable = new Subject<{ type: 'UNRECOVERABLE_STATE'; reason: string }>();
    watchUnrecoverable({ isEnabled: true, unrecoverable } as unknown as SwUpdate, view);
    unrecoverable.next({ type: 'UNRECOVERABLE_STATE', reason: 'hash mismatch' });
    unrecoverable.next({ type: 'UNRECOVERABLE_STATE', reason: 'outra vez' });
    expect(reload).toHaveBeenCalledTimes(1);
  });

  it('só pide o chunk cando a aplicación está estable ou, como moito, aos 30 s', async () => {
    vi.useFakeTimers();
    const { container, view } = fakeContainer();
    const never = new Promise<void>(() => undefined);
    TestBed.configureTestingModule({
      providers: [
        { provide: DOCUMENT, useValue: { defaultView: view } },
        { provide: ApplicationRef, useValue: { whenStable: () => never, destroyed: false } },
        provideDeferredServiceWorker(true),
      ],
    });
    TestBed.inject(EnvironmentInjector);
    await vi.advanceTimersByTimeAsync(REGISTER_TIMEOUT_MS - 1);
    expect(container.register).not.toHaveBeenCalled();
    await vi.advanceTimersByTimeAsync(1);
    await vi.waitFor(() => expect(container.register).toHaveBeenCalledWith(SERVICE_WORKER_SCRIPT));
  });

  it('desactivado (desenvolvemento) non rexistra nada', async () => {
    vi.useFakeTimers();
    const { container, view } = fakeContainer();
    TestBed.configureTestingModule({
      providers: [{ provide: DOCUMENT, useValue: { defaultView: view } }, provideDeferredServiceWorker(false)],
    });
    TestBed.inject(EnvironmentInjector);
    await vi.advanceTimersByTimeAsync(REGISTER_TIMEOUT_MS);
    expect(container.register).not.toHaveBeenCalled();
  });
});
