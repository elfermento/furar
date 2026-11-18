// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { SessionService } from '../auth/session.service';
import type { DisplayPreferences, DisplayPreferencesRemote, SystemPreferences } from './display-preferences';
import {
  DISPLAY_PREFERENCES_REMOTE,
  DISPLAY_STORAGE_KEY,
  effectiveDisplay,
  parsePreferences,
  readStored,
} from './display-preferences';
import { DisplayPreferencesService } from './display-preferences.service';

/** `matchMedia` falso: o sistema pide (ou non) contraste e menos movemento, e pode cambiar. */
function fakeSystem(initial: SystemPreferences) {
  let current = initial;
  const listeners = new Set<() => void>();
  const original = Object.getOwnPropertyDescriptor(window, 'matchMedia');
  Object.defineProperty(window, 'matchMedia', {
    configurable: true,
    value: (query: string) => ({
      get matches() {
        return query.includes('contrast') ? current.moreContrast : current.reducedMotion;
      },
      addEventListener: (_: string, listener: () => void) => listeners.add(listener),
      removeEventListener: (_: string, listener: () => void) => listeners.delete(listener),
    }),
  });
  return {
    change(next: SystemPreferences) {
      current = next;
      listeners.forEach((listener) => listener());
    },
    restore() {
      if (original) Object.defineProperty(window, 'matchMedia', original);
      else Reflect.deleteProperty(window, 'matchMedia');
    },
  };
}

const root = () => document.documentElement;
const attributes = () => [
  root().getAttribute('data-text-size'),
  root().getAttribute('data-contrast'),
  root().getAttribute('data-motion'),
];

describe('preferencias de visualización', () => {
  let system: ReturnType<typeof fakeSystem>;
  const authenticated = signal(false);

  function service(remote: DisplayPreferencesRemote | null = null) {
    TestBed.configureTestingModule({
      providers: [
        { provide: SessionService, useValue: { authenticated } },
        { provide: DISPLAY_PREFERENCES_REMOTE, useValue: remote },
      ],
    });
    return TestBed.inject(DisplayPreferencesService);
  }

  beforeEach(() => {
    localStorage.clear();
    authenticated.set(false);
    system = fakeSystem({ moreContrast: false, reducedMotion: false });
  });

  afterEach(() => {
    system.restore();
    localStorage.clear();
  });

  it('sen nada gardado: texto normal e o que diga o sistema', () => {
    system.restore();
    system = fakeSystem({ moreContrast: true, reducedMotion: true });
    const prefs = service();
    expect(prefs.textSize()).toBe('normal');
    expect(prefs.highContrast()).toBe(true);
    expect(prefs.reducedMotion()).toBe(true);
    expect(attributes()).toEqual(['normal', 'more', 'reduce']);
  });

  it('se a persoa apaga o que pide o sistema, gaña a súa elección (e queda gardada)', () => {
    system.restore();
    system = fakeSystem({ moreContrast: true, reducedMotion: true });
    const prefs = service();
    prefs.setHighContrast(false);
    prefs.setReducedMotion(false);
    expect(attributes()).toEqual(['normal', 'no-preference', 'no-preference']);
    expect(readStored(localStorage)).toEqual({
      textSize: 'normal',
      highContrast: false,
      reducedMotion: false,
      origin: 'device',
    });
  });

  it('aplica ao momento cada axuste e os tres á vez', () => {
    const prefs = service();
    prefs.setTextSize('x-large');
    expect(attributes()).toEqual(['x-large', 'no-preference', 'no-preference']);
    prefs.setHighContrast(true);
    prefs.setReducedMotion(true);
    expect(attributes()).toEqual(['x-large', 'more', 'reduce']);
    prefs.setTextSize('large');
    expect(attributes()).toEqual(['large', 'more', 'reduce']);
  });

  it('sen elección propia, segue o sistema se cambia coa páxina aberta', () => {
    const prefs = service();
    prefs.setTextSize('large');
    system.change({ moreContrast: true, reducedMotion: false });
    expect(prefs.highContrast()).toBe(true);
    expect(attributes()).toEqual(['large', 'more', 'no-preference']);
    prefs.setHighContrast(false);
    system.change({ moreContrast: true, reducedMotion: true });
    expect(attributes()).toEqual(['large', 'no-preference', 'reduce']);
  });

  it('le o gardado ao arrancar e descarta valores raros', () => {
    localStorage.setItem(DISPLAY_STORAGE_KEY, JSON.stringify({ textSize: 'large', highContrast: true }));
    const prefs = service();
    expect(prefs.preferences()).toEqual({ textSize: 'large', highContrast: true, reducedMotion: null });
    expect(parsePreferences({ textSize: 'huge', highContrast: 'yes', reducedMotion: false })).toEqual({
      textSize: 'normal',
      highContrast: null,
      reducedMotion: false,
    });
    localStorage.setItem(DISPLAY_STORAGE_KEY, '{roto');
    expect(readStored(localStorage)).toBeNull();
  });

  describe('na conta (porto remoto)', () => {
    const account: DisplayPreferences = { textSize: 'x-large', highContrast: true, reducedMotion: null };

    function remote(stored: DisplayPreferences | null) {
      return { load: vi.fn().mockResolvedValue(stored), save: vi.fn().mockResolvedValue(undefined) };
    }

    it('dispositivo novo: ao entrar aplícanse os da conta', async () => {
      const port = remote(account);
      const prefs = service(port);
      expect(prefs.savesToAccount()).toBe(false);
      authenticated.set(true);
      TestBed.tick();
      await vi.waitFor(() => expect(attributes()).toEqual(['x-large', 'more', 'no-preference']));
      expect(prefs.savesToAccount()).toBe(true);
      expect(readStored(localStorage)?.origin).toBe('account');
    });

    it('se o dispositivo xa ten os seus, quedan eses e non se pide a conta', async () => {
      localStorage.setItem(
        DISPLAY_STORAGE_KEY,
        JSON.stringify({ textSize: 'large', highContrast: false, reducedMotion: null, origin: 'device' }),
      );
      const port = remote(account);
      service(port);
      authenticated.set(true);
      TestBed.tick();
      await Promise.resolve();
      expect(port.load).not.toHaveBeenCalled();
      expect(attributes()).toEqual(['large', 'no-preference', 'no-preference']);
    });

    it('os copiados da conta non son do dispositivo: na seguinte entrada volven pedirse', async () => {
      localStorage.setItem(DISPLAY_STORAGE_KEY, JSON.stringify({ textSize: 'large', origin: 'account' }));
      const port = remote(account);
      service(port);
      authenticated.set(true);
      TestBed.tick();
      await vi.waitFor(() => expect(port.load).toHaveBeenCalledOnce());
      await vi.waitFor(() => expect(attributes()[0]).toBe('x-large'));
    });

    it('con sesión, cada cambio gárdase tamén na conta; sen sesión, só no navegador', () => {
      const port = remote(null);
      const prefs = service(port);
      prefs.setTextSize('large');
      expect(port.save).not.toHaveBeenCalled();
      authenticated.set(true);
      prefs.setReducedMotion(true);
      expect(port.save).toHaveBeenCalledWith({ textSize: 'large', highContrast: null, reducedMotion: true });
    });

    it('se a conta falla, quedan os do navegador', async () => {
      const port = { load: vi.fn().mockRejectedValue(new Error('rede')), save: vi.fn() };
      const prefs = service(port);
      authenticated.set(true);
      await prefs.loadFromAccount();
      expect(attributes()).toEqual(['normal', 'no-preference', 'no-preference']);
    });
  });
});

describe('script de index.html (antes do primeiro pintado)', () => {
  const html = readFileSync(resolve(process.cwd(), 'src/index.html'), 'utf8');
  const code = /<script>([\s\S]*?)<\/script>/.exec(html)?.[1] ?? '';

  function run(stored: string | null, system: SystemPreferences) {
    const element = document.createElement('html');
    const fakeDocument = { documentElement: element };
    const storage = { getItem: () => stored };
    const matchMedia = (query: string) => ({
      matches: query.includes('contrast') ? system.moreContrast : system.reducedMotion,
    });
    new Function('document', 'localStorage', 'matchMedia', code)(fakeDocument, storage, matchMedia);
    return {
      textSize: element.getAttribute('data-text-size'),
      contrast: element.getAttribute('data-contrast'),
      motion: element.getAttribute('data-motion'),
    };
  }

  const cases: [string | null, SystemPreferences][] = [
    [null, { moreContrast: false, reducedMotion: false }],
    [null, { moreContrast: true, reducedMotion: true }],
    ['{roto', { moreContrast: true, reducedMotion: false }],
    ['{"textSize":"large","highContrast":false}', { moreContrast: true, reducedMotion: true }],
    ['{"textSize":"x-large","highContrast":true,"reducedMotion":false}', { moreContrast: false, reducedMotion: true }],
    ['{"textSize":"enorme","highContrast":"si","reducedMotion":true}', { moreContrast: false, reducedMotion: false }],
  ];

  it.each(cases)('gardado %s: o mesmo ca o servizo', (stored, system) => {
    const parsed = (() => {
      try {
        return JSON.parse(stored ?? '{}') as unknown;
      } catch {
        return {};
      }
    })();
    expect(code).toContain(DISPLAY_STORAGE_KEY);
    expect(run(stored, system)).toEqual(effectiveDisplay(parsePreferences(parsed), system));
  });
});
