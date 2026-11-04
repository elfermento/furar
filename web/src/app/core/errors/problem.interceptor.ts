// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { HttpInterceptorFn } from '@angular/common/http';
import { HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { TranslocoService } from '@jsverse/transloco';
import { catchError, throwError } from 'rxjs';
import { isApiRequest } from '../api-url';
import { ApiProblem, parseProblem, problemKey } from './api-problem';

/**
 * Traduce os erros RFC 9457 da API (ADR-0008): o `code` estable pasa a texto en gl/es
 * (`errors.<code>` en src/i18n). Código descoñecido → texto xenérico, nunca o texto en bruto.
 */
export const problemInterceptor: HttpInterceptorFn = (request, next) => {
  if (!isApiRequest(request.url)) {
    return next(request);
  }
  const transloco = inject(TranslocoService);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (!(error instanceof HttpErrorResponse)) {
        return throwError(() => error);
      }
      const problem = parseProblem(error);
      const key = problemKey(problem.code);
      const translated = transloco.translate(key, problem.params);
      const text = translated === key ? transloco.translate(problemKey('common.unexpected')) : translated;
      return throwError(
        () =>
          new ApiProblem(
            problem.status,
            problem.code,
            problem.params,
            problem.fieldErrors,
            problem.traceId,
            text,
            problem.extensions,
          ),
      );
    }),
  );
};

/** Garante un ApiProblem para calquera erro (p. ex. lanzado fóra do HttpClient). */
export function asProblem(error: unknown): ApiProblem {
  if (error instanceof ApiProblem) {
    return error;
  }
  return new ApiProblem(0, 'common.unexpected', {}, [], undefined, '');
}
