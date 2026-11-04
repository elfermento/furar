// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import localeGl from '@angular/common/locales/gl';
import type { EnvironmentProviders } from '@angular/core';
import { LOCALE_ID, inject, isDevMode, makeEnvironmentProviders, provideAppInitializer } from '@angular/core';
import { provideTransloco } from '@jsverse/transloco';
import { LanguageService } from './language.service';
import { DEFAULT_LANGUAGE, LANGUAGES } from './languages';
import { BundledTranslocoLoader } from './transloco-loader';

registerLocaleData(localeGl);
registerLocaleData(localeEs);

/** Transloco gl/es, LOCALE_ID inicial e carga do idioma antes de pintar. */
export function provideFurarI18n(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideTransloco({
      config: {
        availableLangs: [...LANGUAGES],
        defaultLang: DEFAULT_LANGUAGE,
        fallbackLang: DEFAULT_LANGUAGE,
        missingHandler: { useFallbackTranslation: true, logMissingKey: isDevMode() },
        reRenderOnLangChange: true,
        prodMode: !isDevMode(),
      },
      loader: BundledTranslocoLoader,
    }),
    { provide: LOCALE_ID, useFactory: () => inject(LanguageService).detect() },
    provideAppInitializer(() => {
      const language = inject(LanguageService);
      return language.use(language.detect());
    }),
  ]);
}
