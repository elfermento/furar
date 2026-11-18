// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { CurrentMunicipality } from '../municipality/current-municipality';
import type { InstallHintSetting } from './install-prompt';
import { InstallPrompt, keepBrowserInstallBar } from './install-prompt';

const OFF: InstallHintSetting = { slug: 'naron', installHint: false };
const ON: InstallHintSetting = { slug: 'naron', installHint: true };

describe('keepBrowserInstallBar: a barriña do navegador só na portada dun concello co aviso apagado', () => {
  it('portada do concello co aviso apagado: si (tamén con barra final ou codificada)', () => {
    expect(keepBrowserInstallBar('/naron', OFF)).toBe(true);
    expect(keepBrowserInstallBar('/naron/', OFF)).toBe(true);
    expect(keepBrowserInstallBar('/nar%6Fn', OFF)).toBe(true);
  });

  it('co aviso aceso, noutra páxina, noutro concello, na portada común ou sen concello cargado: non', () => {
    expect(keepBrowserInstallBar('/naron', ON)).toBe(false);
    expect(keepBrowserInstallBar('/naron/instalacions', OFF)).toBe(false);
    expect(keepBrowserInstallBar('/ferrol', OFF)).toBe(false);
    expect(keepBrowserInstallBar('/', OFF)).toBe(false);
    expect(keepBrowserInstallBar('/naron', null)).toBe(false);
    expect(keepBrowserInstallBar('/%E0%A4%A', OFF)).toBe(false);
  });
});

describe('InstallPrompt: preventDefault de beforeinstallprompt segundo a páxina', () => {
  const municipality = signal<InstallHintSetting | null>(null);
  const originalPath = location.pathname;

  beforeEach(() => {
    municipality.set(null);
    TestBed.configureTestingModule({
      providers: [{ provide: CurrentMunicipality, useValue: { value: municipality.asReadonly() } }],
    });
  });

  afterEach(() => history.replaceState(null, '', originalPath));

  function fire(): Event {
    const event = new Event('beforeinstallprompt', { cancelable: true });
    Object.assign(event, { prompt: vi.fn(), userChoice: Promise.resolve({ outcome: 'dismissed' }) });
    window.dispatchEvent(event);
    return event;
  }

  it('portada dun concello co aviso apagado: o navegador mostra a súa barriña e o evento gárdase igual', () => {
    const prompt = TestBed.inject(InstallPrompt);
    history.replaceState(null, '', '/naron');
    municipality.set(OFF);
    expect(fire().defaultPrevented).toBe(false);
    expect(prompt.available()).toBe(true);
  });

  it('portada co aviso aceso, outra páxina do concello, portada común ou concello sen cargar: sen barriña', () => {
    const prompt = TestBed.inject(InstallPrompt);
    history.replaceState(null, '', '/naron');
    municipality.set(ON);
    expect(fire().defaultPrevented).toBe(true);

    history.replaceState(null, '', '/naron/instalacions');
    municipality.set(OFF);
    expect(fire().defaultPrevented).toBe(true);

    history.replaceState(null, '', '/');
    municipality.set(null);
    expect(fire().defaultPrevented).toBe(true);

    history.replaceState(null, '', '/naron');
    expect(fire().defaultPrevented, 'aínda sen concello: coma sempre').toBe(true);
    expect(prompt.available()).toBe(true);
  });
});
