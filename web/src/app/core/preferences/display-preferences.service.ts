// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { SessionService } from '../auth/session.service';
import type { DisplayPreferences, StoredPreferences, SystemPreferences, TextSize } from './display-preferences';
import {
  DEFAULT_PREFERENCES,
  DISPLAY_PREFERENCES_REMOTE,
  applyDisplay,
  effectiveDisplay,
  readStored,
  writeStored,
} from './display-preferences';

const CONTRAST_QUERY = '(prefers-contrast: more)';
const MOTION_QUERY = '(prefers-reduced-motion: reduce)';

/**
 * Preferencias de visualización: o que escolle a persoa, o que pide o sistema e o
 * que se aplica en `<html>` ao momento, en toda a web.
 *
 * Regras:
 * - Sen elección propia, os interruptores seguen o sistema (`prefers-contrast: more`,
 *   `prefers-reduced-motion: reduce`), tamén se cambia mentres a páxina está aberta.
 * - Se a persoa os acende ou apaga, gaña a súa elección.
 * - Gárdase no navegador (`localStorage`, sen cookies). Con conta e un porto
 *   `DISPLAY_PREFERENCES_REMOTE`, tamén na conta: ao entrar nun dispositivo sen axustes propios,
 *   aplícanse os da conta; se o dispositivo xa ten os seus (escollidos nel), quedan eses.
 */
@Injectable({ providedIn: 'root' })
export class DisplayPreferencesService {
  private readonly document = inject(DOCUMENT);
  private readonly remote = inject(DISPLAY_PREFERENCES_REMOTE);
  private readonly session = inject(SessionService);
  private readonly storage = (() => {
    try {
      return this.document.defaultView?.localStorage ?? null;
    } catch {
      return null;
    }
  })();

  private readonly stored = signal<StoredPreferences | null>(readStored(this.storage));
  private readonly system = signal<SystemPreferences>({ moreContrast: false, reducedMotion: false });

  /** O que escolleu a persoa (`null` nun interruptor = segue o sistema). */
  readonly preferences = computed<DisplayPreferences>(() => {
    const stored = this.stored();
    return stored
      ? { textSize: stored.textSize, highContrast: stored.highContrast, reducedMotion: stored.reducedMotion }
      : DEFAULT_PREFERENCES;
  });
  readonly effective = computed(() => effectiveDisplay(this.preferences(), this.system()));
  readonly textSize = computed(() => this.effective().textSize);
  readonly highContrast = computed(() => this.effective().contrast === 'more');
  readonly reducedMotion = computed(() => this.effective().motion === 'reduce');
  /** Hai onde gardalos na conta e a persoa entrou. */
  readonly savesToAccount = computed(() => this.remote !== null && this.session.authenticated());

  constructor() {
    const view = this.document.defaultView;
    const contrast = view?.matchMedia?.(CONTRAST_QUERY) ?? null;
    const motion = view?.matchMedia?.(MOTION_QUERY) ?? null;
    const readSystem = () =>
      this.system.set({ moreContrast: contrast?.matches ?? false, reducedMotion: motion?.matches ?? false });
    readSystem();
    this.apply();
    const onChange = () => {
      readSystem();
      this.apply();
    };
    contrast?.addEventListener?.('change', onChange);
    motion?.addEventListener?.('change', onChange);
    inject(DestroyRef).onDestroy(() => {
      contrast?.removeEventListener?.('change', onChange);
      motion?.removeEventListener?.('change', onChange);
    });
    effect(() => {
      if (this.session.authenticated() && this.remote) untracked(() => void this.loadFromAccount());
    });
  }

  setTextSize(textSize: TextSize): void {
    this.change({ textSize });
  }

  setHighContrast(on: boolean): void {
    this.change({ highContrast: on });
  }

  setReducedMotion(on: boolean): void {
    this.change({ reducedMotion: on });
  }

  /** Ao entrar: os da conta, salvo que o dispositivo xa teña os seus. */
  async loadFromAccount(): Promise<void> {
    if (!this.remote || this.stored()?.origin === 'device') return;
    const fromAccount = await this.remote.load().catch(() => null);
    // Pode que a persoa escollese algo aquí mentres chegaba a resposta: gaña o seu.
    if (!fromAccount || this.stored()?.origin === 'device') return;
    this.store({ ...fromAccount, origin: 'account' });
  }

  private change(patch: Partial<DisplayPreferences>): void {
    const next = { ...this.preferences(), ...patch };
    this.store({ ...next, origin: 'device' });
    if (this.remote && this.session.authenticated()) {
      this.remote.save(next).catch(() => undefined);
    }
  }

  private store(stored: StoredPreferences): void {
    this.stored.set(stored);
    writeStored(this.storage, stored);
    this.apply();
  }

  private apply(): void {
    applyDisplay(this.document.documentElement, this.effective());
  }
}
