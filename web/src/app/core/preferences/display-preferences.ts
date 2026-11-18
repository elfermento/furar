// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { InjectionToken } from '@angular/core';

/**
 * Preferencias de visualización da persoa: tamaño do texto, alto contraste e menos
 * movemento. Valen para toda a web e aplícanse como atributos en `<html>` (`data-text-size`,
 * `data-contrast`, `data-motion`), que len os estilos globais (styles/base.css).
 *
 * O mesmo cálculo faino o script de `index.html` antes do primeiro pintado (sen parpadeo);
 * `display-preferences.spec.ts` comproba que os dous din o mesmo.
 */
export type TextSize = 'normal' | 'large' | 'x-large';

export const TEXT_SIZES: readonly TextSize[] = ['normal', 'large', 'x-large'];

/**
 * O que escolleu a persoa. `null` nun interruptor = segue o sistema (`prefers-contrast: more`,
 * `prefers-reduced-motion: reduce`); `true` ou `false` = a súa elección, que gaña ao sistema.
 */
export interface DisplayPreferences {
  readonly textSize: TextSize;
  readonly highContrast: boolean | null;
  readonly reducedMotion: boolean | null;
}

export const DEFAULT_PREFERENCES: DisplayPreferences = { textSize: 'normal', highContrast: null, reducedMotion: null };

/** O que pide o sistema ou o navegador. */
export interface SystemPreferences {
  readonly moreContrast: boolean;
  readonly reducedMotion: boolean;
}

/** O que se aplica de verdade (preferencia da persoa ou, se non hai, a do sistema). */
export interface EffectiveDisplay {
  readonly textSize: TextSize;
  readonly contrast: 'more' | 'no-preference';
  readonly motion: 'reduce' | 'no-preference';
}

/** Clave en `localStorage` (a mesma no script de `index.html`). */
export const DISPLAY_STORAGE_KEY = 'furar.display';

/**
 * De onde saíron os axustes gardados no dispositivo: `device`, escollidos aquí; `account`,
 * copiados da conta. Só os `device` son «os seus» do dispositivo e gañan aos da conta.
 */
export type PreferencesOrigin = 'device' | 'account';

export interface StoredPreferences extends DisplayPreferences {
  readonly origin: PreferencesOrigin;
}

export function effectiveDisplay(preferences: DisplayPreferences, system: SystemPreferences): EffectiveDisplay {
  return {
    textSize: preferences.textSize,
    contrast: (preferences.highContrast ?? system.moreContrast) ? 'more' : 'no-preference',
    motion: (preferences.reducedMotion ?? system.reducedMotion) ? 'reduce' : 'no-preference',
  };
}

const isTextSize = (value: unknown): value is TextSize => TEXT_SIZES.includes(value as TextSize);
const choice = (value: unknown): boolean | null => (typeof value === 'boolean' ? value : null);

/** Le preferencias dun obxecto descoñecido (almacenamento ou servidor): o que non vale, por defecto. */
export function parsePreferences(value: unknown): DisplayPreferences {
  const raw = (value && typeof value === 'object' ? value : {}) as Record<string, unknown>;
  return {
    textSize: isTextSize(raw['textSize']) ? raw['textSize'] : 'normal',
    highContrast: choice(raw['highContrast']),
    reducedMotion: choice(raw['reducedMotion']),
  };
}

/** Le o gardado no dispositivo; `null` se non hai nada (ou non se pode ler). */
export function readStored(storage: Storage | null | undefined): StoredPreferences | null {
  try {
    const text = storage?.getItem(DISPLAY_STORAGE_KEY);
    if (!text) return null;
    const raw = JSON.parse(text) as unknown;
    if (!raw || typeof raw !== 'object') return null;
    const origin = (raw as Record<string, unknown>)['origin'] === 'account' ? 'account' : 'device';
    return { ...parsePreferences(raw), origin };
  } catch {
    return null;
  }
}

export function writeStored(storage: Storage | null | undefined, stored: StoredPreferences): void {
  try {
    storage?.setItem(DISPLAY_STORAGE_KEY, JSON.stringify(stored));
  } catch {
    // Sen almacenamento (modo privado ou bloqueado): os axustes valen só para esta visita.
  }
}

/** Aplica o efectivo en `<html>`: o mesmo que fai o script de `index.html`. */
export function applyDisplay(root: HTMLElement, display: EffectiveDisplay): void {
  root.setAttribute('data-text-size', display.textSize);
  root.setAttribute('data-contrast', display.contrast);
  root.setAttribute('data-motion', display.motion);
}

/**
 * Porto para gardar os axustes na conta da persoa (`provideAccountDisplayPreferences`, na
 * configuración da aplicación; sen provedor, só se gardan no navegador). `load` devolve `null` se a conta non ten
 * axustes gardados.
 */
export interface DisplayPreferencesRemote {
  load(): Promise<DisplayPreferences | null>;
  save(preferences: DisplayPreferences): Promise<void>;
}

export const DISPLAY_PREFERENCES_REMOTE = new InjectionToken<DisplayPreferencesRemote | null>(
  'DISPLAY_PREFERENCES_REMOTE',
  { providedIn: 'root', factory: () => null },
);
