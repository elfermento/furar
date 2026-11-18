// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { DOCUMENT, registerLocaleData } from '@angular/common';
import { Injectable, inject, signal } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';
import type { Language } from './languages';
import { DEFAULT_LANGUAGE, isLanguage } from './languages';

const STORAGE_KEY = 'furar.lang';

/**
 * Datos de formato de Angular de cada idioma: cárganse co idioma (coma os textos), non na carga
 * inicial. O de arranque rexístrase antes de pintar (`provideAppInitializer`).
 */
const LOCALES: Record<Language, () => Promise<{ default: unknown }>> = {
  gl: () => import('@angular/common/locales/gl'),
  es: () => import('@angular/common/locales/es'),
};

/**
 * Idioma activo (ADR-0014): preferencia gardada → gl. O idioma do navegador non conta: quen
 * entra por primeira vez ve a web en galego.
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
    return DEFAULT_LANGUAGE;
  }

  /** Carga os textos e aplica o idioma. Usado no arranque e ao cambiar. */
  async use(lang: Language, remember = false): Promise<void> {
    this.current.set(lang);
    this.document.documentElement.lang = lang;
    if (remember) {
      this.store(lang);
    }
    await Promise.all([firstValueFrom(this.transloco.load(lang)), registerLocale(lang)]);
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

async function registerLocale(lang: Language): Promise<void> {
  registerLocaleData((await LOCALES[lang]()).default);
}
