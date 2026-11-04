// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { Injectable } from '@angular/core';
import type { Translation, TranslocoLoader } from '@jsverse/transloco';
import type { Observable } from 'rxjs';
import { from } from 'rxjs';
import type { Language } from './languages';
import { isLanguage } from './languages';

/** Os textos van no propio build (un chunk por idioma): sen petición extra nin problemas de CSP. */
const LOADERS: Record<Language, () => Promise<{ default: Translation }>> = {
  gl: () => import('../../../i18n/gl.json'),
  es: () => import('../../../i18n/es.json'),
};

@Injectable({ providedIn: 'root' })
export class BundledTranslocoLoader implements TranslocoLoader {
  getTranslation(lang: string): Observable<Translation> {
    const loader = isLanguage(lang) ? LOADERS[lang] : LOADERS.gl;
    return from(loader().then((module) => module.default));
  }
}
