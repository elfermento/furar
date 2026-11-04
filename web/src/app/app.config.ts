// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { provideHttpClient, withFetch, withInterceptors } from '@angular/common/http';
import type { ApplicationConfig } from '@angular/core';
import { isDevMode, provideAppInitializer, provideBrowserGlobalErrorListeners } from '@angular/core';
import {
  TitleStrategy,
  provideRouter,
  withComponentInputBinding,
  withInMemoryScrolling,
  withNavigationErrorHandler,
} from '@angular/router';
import { provideApiConfiguration } from './api/api-configuration';
import { routes } from './app.routes';
import { provideFurarAuth } from './core/auth/auth.providers';
import { bearerInterceptor } from './core/auth/bearer.interceptor';
import { problemInterceptor } from './core/errors/problem.interceptor';
import { acceptLanguageInterceptor } from './core/i18n/accept-language.interceptor';
import { provideFurarI18n } from './core/i18n/i18n.providers';
import { TranslatedTitleStrategy } from './core/layout/translated-title.strategy';
import { provideDeferredServiceWorker } from './core/pwa/deferred-service-worker';
import { reloadOnChunkError } from './core/pwa/reload-once';
import { loadLazySymbols } from './shared/ui/icon/icon.component';
import { provideCdkGlobalStyles } from './shared/ui/overlay/cdk-styles';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(
      routes,
      withComponentInputBinding(),
      withInMemoryScrolling({ scrollPositionRestoration: 'top', anchorScrolling: 'enabled' }),
      // Tras un despregue, o chunk da versión aberta xa non existe: recargar (unha vez).
      withNavigationErrorHandler(reloadOnChunkError),
    ),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
    // A orde importa: idioma e token antes de saír; o erro tradúcese ao volver.
    provideHttpClient(
      withFetch(),
      withInterceptors([acceptLanguageInterceptor, bearerInterceptor, problemInterceptor]),
    ),
    // Rutas relativas á mesma orixe (/api/v1/...): Caddy ou o proxy de ng serve.
    provideApiConfiguration(''),
    provideFurarI18n(),
    provideFurarAuth(),
    provideCdkGlobalStyles(),
    // O service worker (e o recargar se queda sen remedio) chega nun chunk diferido (D27).
    provideDeferredServiceWorker(!isDevMode()),
    // Todas as páxinas son lazy: as iconas fóra do marco piden xa o seu chunk, en paralelo co da
    // primeira páxina e sen bloquear o arranque (orzamento inicial, D27).
    provideAppInitializer(() => void loadLazySymbols()),
  ],
};
