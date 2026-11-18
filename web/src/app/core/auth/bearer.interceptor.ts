// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { isApiRequest } from '../api-url';
import { SessionService } from './session.service';

/** Engade o token só ás chamadas á propia API (mesma orixe); nunca a terceiros. */
export const bearerInterceptor: HttpInterceptorFn = (request, next) => {
  const token = inject(SessionService).accessToken();
  if (!token || !isApiRequest(request.url) || request.headers.has('Authorization')) {
    return next(request);
  }
  return next(request.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};
