// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
/** A API está na mesma orixe baixo /api (ADR-0014): o cliente xerado usa rutas relativas. */
export const API_PREFIX = '/api/';

export function isApiRequest(url: string): boolean {
  return url.startsWith(API_PREFIX);
}
