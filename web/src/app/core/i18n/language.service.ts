// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import type { Language } from './languages';
import { DEFAULT_LANGUAGE, isLanguage, pickLanguage } from './languages';

const STORAGE_KEY = 'furar.lang';

/**
 * Idioma activo (ADR-0014): preferencia gardada → navigator.languages → gl.
 * O idioma non vai no URL. Cambia <html lang> e o idioma de Transloco.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly document = inject(DOCUMENT);
  private readonly transloco = inject(TranslocoService);
  private readonly current = signal<Language>(DEFAULT_LANGUAGE);

  /** Idioma activo como signal de só lectura. */
  readonly lang = this.current.asReadonly();

  /** Idioma inicial sen efectos (para LOCALE_ID e o arranque). */
  detect(): Language {
    const stored = this.readStored();
    if (stored) {
      return stored;
    }
    const nav = this.document.defaultView?.navigator;
    const candidates = nav?.languages?.length ? nav.languages : [nav?.language ?? ''];
    return pickLanguage(candidates) ?? DEFAULT_LANGUAGE;
  }

  /** Carga os textos e aplica o idioma. Usado no arranque e ao cambiar. */
  async use(lang: Language, remember = false): Promise<void> {
    this.current.set(lang);
    this.document.documentElement.lang = lang;
    if (remember) {
      this.store(lang);
    }
    await firstValueFrom(this.transloco.load(lang));
    this.transloco.setActiveLang(lang);
  }

  private readStored(): Language | undefined {
    try {
      const value = this.document.defaultView?.localStorage.getItem(STORAGE_KEY);
      return isLanguage(value) ? value : undefined;
    } catch {
      return undefined;
    }
  }

  private store(lang: Language): void {
    try {
      this.document.defaultView?.localStorage.setItem(STORAGE_KEY, lang);
    } catch {
      // Sen almacenamento (modo privado): o idioma vale só para esta visita.
    }
  }
}
