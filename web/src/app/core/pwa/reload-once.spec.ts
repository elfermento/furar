// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { TestBed } from '@angular/core/testing';
import { NavigationError } from '@angular/router';
import { RELOAD_WINDOW_MS, isChunkLoadError, reloadOnChunkError, reloadOnce } from './reload-once';

/** Xanela falsa: `sessionStorage` en memoria e `location.reload` espiado. */
function fakeWindow(storage: Storage | null = memoryStorage()) {
  const reload = vi.fn();
  const view = {
    location: { reload },
    get sessionStorage(): Storage {
      if (!storage) throw new DOMException('bloqueado', 'SecurityError');
      return storage;
    },
  } as unknown as Window;
  return { view, reload };
}

function memoryStorage(): Storage {
  const data = new Map<string, string>();
  return {
    getItem: (key: string) => data.get(key) ?? null,
    setItem: (key: string, value: string) => void data.set(key, value),
  } as Storage;
}

describe('reload-once', () => {
  it('recoñece os erros de carga dun chunk de Chromium, Firefox e Safari', () => {
    expect(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: /a-B.js'))).toBe(true);
    expect(isChunkLoadError(new TypeError('error loading dynamically imported module: /a-B.js'))).toBe(true);
    expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true);
    expect(isChunkLoadError(new Error('Http failure response for /api/v1: 500'))).toBe(false);
    expect(isChunkLoadError(undefined)).toBe(false);
  });

  it('recarga unha vez e non volve dentro da xanela (sen bucle); despois, si', () => {
    const { view, reload } = fakeWindow();
    expect(reloadOnce(view, 1_000)).toBe(true);
    expect(reloadOnce(view, 1_000 + RELOAD_WINDOW_MS - 1)).toBe(false);
    expect(reload).toHaveBeenCalledTimes(1);
    expect(reloadOnce(view, 1_000 + RELOAD_WINDOW_MS)).toBe(true);
    expect(reload).toHaveBeenCalledTimes(2);
  });

  it('sen sessionStorage non recarga: non podería evitar o bucle', () => {
    const { view, reload } = fakeWindow(null);
    expect(reloadOnce(view)).toBe(false);
    expect(reloadOnce(null)).toBe(false);
    expect(reload).not.toHaveBeenCalled();
  });

  it('un erro de navegación por un chunk perdido recarga; outro erro, non', () => {
    const { view, reload } = fakeWindow();
    TestBed.configureTestingModule({ providers: [{ provide: DOCUMENT, useValue: { defaultView: view } }] });
    const error = (cause: unknown) => new NavigationError(1, '/naron/xestion/indicadores', cause);
    TestBed.runInInjectionContext(() => reloadOnChunkError(error(new Error('sen permiso'))));
    expect(reload).not.toHaveBeenCalled();
    TestBed.runInInjectionContext(() =>
      reloadOnChunkError(error(new TypeError('Failed to fetch dynamically imported module: /x.js'))),
    );
    expect(reload).toHaveBeenCalledTimes(1);
  });
});
