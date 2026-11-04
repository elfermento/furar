// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { HttpErrorResponse } from '@angular/common/http';
import type { Problem } from '../../api/models/problem';

/** Códigos só da web (sen resposta da API). */
export type ClientErrorCode = 'common.network' | 'common.unexpected';
export type ProblemCode = Problem['code'] | ClientErrorCode;

export interface ApiFieldError {
  /** Ruta do campo tal como a manda a API, p. ex. "name" ou "customFields.phone". */
  readonly field: string;
  readonly code: ProblemCode;
  readonly params: Readonly<Record<string, unknown>>;
}

/**
 * Erro da API normalizado (RFC 9457, ADR-0008). O interceptor converte calquera
 * HttpErrorResponse nun ApiProblem; as pantallas só traballan con este tipo.
 */
export class ApiProblem extends Error {
  constructor(
    readonly status: number,
    readonly code: ProblemCode,
    readonly params: Readonly<Record<string, unknown>>,
    readonly fieldErrors: readonly ApiFieldError[],
    readonly traceId: string | undefined,
    /** Texto xa traducido ao idioma activo no momento do erro. */
    readonly translated: string,
    /**
     * Membros de extensión RFC 9457 que non son os de sempre (p. ex. `affected[]` de
     * `facility.affected-bookings`). Lese na feature que coñece o seu código.
     */
    readonly extensions: Readonly<Record<string, unknown>> = {},
  ) {
    super(code);
    this.name = 'ApiProblem';
  }

  /** Clave de tradución do código. */
  get messageKey(): string {
    return problemKey(this.code);
  }

  /** Erros dun campo concreto ("name", "customFields.phone"...). */
  errorsFor(field: string): readonly ApiFieldError[] {
    return this.fieldErrors.filter((error) => error.field === field);
  }
}

export function problemKey(code: string): string {
  return `errors.${code}`;
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value);
}

function readFieldErrors(value: unknown): ApiFieldError[] {
  if (!Array.isArray(value)) {
    return [];
  }
  return value.filter(isRecord).map((error) => ({
    field: typeof error['field'] === 'string' ? error['field'] : '',
    code: (typeof error['code'] === 'string' ? error['code'] : 'common.unexpected') as ProblemCode,
    params: isRecord(error['params']) ? error['params'] : {},
  }));
}

/** Converte unha resposta de erro en ApiProblem (sen traducir). */
export interface ParsedProblem {
  readonly status: number;
  readonly code: ProblemCode;
  readonly params: Readonly<Record<string, unknown>>;
  readonly fieldErrors: readonly ApiFieldError[];
  readonly traceId: string | undefined;
  readonly extensions: Readonly<Record<string, unknown>>;
}

/** Membros comúns de todo problema (RFC 9457 + ADR-0008): o resto son extensións. */
const STANDARD_MEMBERS = new Set([
  'type',
  'title',
  'status',
  'detail',
  'instance',
  'code',
  'params',
  'errors',
  'traceId',
]);

function readExtensions(body: Record<string, unknown>): Record<string, unknown> {
  return Object.fromEntries(Object.entries(body).filter(([key]) => !STANDARD_MEMBERS.has(key)));
}

/**
 * `Retry-After` en segundos (RFC 9110 §10.2.3) como extensión `retryAfter`, para dicir canto
 * esperar (p. ex. `staff.invitation-rate-limited`). Só a forma en segundos, que é a que manda a API.
 */
function retryAfter(response: HttpErrorResponse): { retryAfter?: number } {
  const value = response.headers?.get('Retry-After')?.trim();
  return value && /^\d+$/.test(value) ? { retryAfter: Number(value) } : {};
}

export function parseProblem(response: HttpErrorResponse): ParsedProblem {
  if (response.status === 0) {
    return { status: 0, code: 'common.network', params: {}, fieldErrors: [], traceId: undefined, extensions: {} };
  }
  const body: unknown = response.error;
  if (isRecord(body) && typeof body['code'] === 'string') {
    return {
      status: response.status,
      code: body['code'] as ProblemCode,
      params: isRecord(body['params']) ? body['params'] : {},
      fieldErrors: readFieldErrors(body['errors']),
      traceId: typeof body['traceId'] === 'string' ? body['traceId'] : undefined,
      extensions: { ...readExtensions(body), ...retryAfter(response) },
    };
  }
  return {
    status: response.status,
    code: 'common.unexpected',
    params: {},
    fieldErrors: [],
    traceId: undefined,
    extensions: {},
  };
}
