// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { HttpTestingController } from '@angular/common/http/testing';

/**
 * A tarxeta Datos (D1-21d) pide os campos propios das instalacións ao cargar a ficha: os specs
 * doutras seccións respóndenlle sen campos con isto, antes de `verify()`.
 */
export function answerCustomFields(backend: HttpTestingController, definitions: unknown[] = []) {
  backend
    .match((request) => request.method === 'GET' && request.url.endsWith('/custom-fields'))
    .forEach((request) => request.flush(definitions));
}
