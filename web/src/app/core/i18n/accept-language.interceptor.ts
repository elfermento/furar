// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { isApiRequest } from '../api-url';
import { LanguageService } from './language.service';

/** Manda o idioma activo á API para que os erros e textos veñan nel (ADR-0014). */
export const acceptLanguageInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiRequest(request.url) || request.headers.has('Accept-Language')) {
    return next(request);
  }
  const lang = inject(LanguageService).lang();
  return next(request.clone({ setHeaders: { 'Accept-Language': lang } }));
};
