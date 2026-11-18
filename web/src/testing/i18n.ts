// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { EnvironmentProviders, Provider } from '@angular/core';
import { importProvidersFrom } from '@angular/core';
import { TranslocoTestingModule } from '@jsverse/transloco';
import es from '../i18n/es.json';
import gl from '../i18n/gl.json';

/** Transloco cos textos reais (gl por defecto) para os tests. */
export function provideTestI18n(lang: 'gl' | 'es' = 'gl'): (Provider | EnvironmentProviders)[] {
  return [
    importProvidersFrom(
      TranslocoTestingModule.forRoot({
        langs: { gl, es },
        translocoConfig: { availableLangs: ['gl', 'es'], defaultLang: lang, reRenderOnLangChange: true },
        preloadLangs: true,
      }),
    ),
  ];
}
