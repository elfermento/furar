// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import type { HttpTestingController } from '@angular/common/http/testing';
import type { BlockResponse } from './blocks.store';

export const FACILITY_URL = '/api/v1/municipalities/naron/facilities/f1';
export const BLOCKS_URL = `${FACILITY_URL}/blocks`;

/** Bloqueo de proba (o da maqueta): martes 3/11/2026 de 8:00 a 12:00, mantemento, versión 2. */
export function block(overrides: Partial<BlockResponse> = {}): BlockResponse {
  return {
    id: 'b1',
    facilityId: 'f1',
    date: '2026-11-03',
    startMinute: 480,
    endMinute: 720,
    reason: 'Mantemento do céspede',
    version: 2,
    createdAt: '2026-10-25T10:00:00Z',
    updatedAt: '2026-10-25T10:00:00Z',
    ...overrides,
  };
}

/** Todo o día, sen motivo: sábado 7/11/2026, versión 1. */
export const ALL_DAY = block({
  id: 'b2',
  date: '2026-11-07',
  startMinute: 0,
  endMinute: 1440,
  reason: undefined,
  version: 1,
});

/**
 * A sección Bloqueos (D1-24) pide os seus ao cargar a ficha: os specs doutras seccións
 * respóndenlle sen bloqueos con isto, antes de `verify()`.
 */
export function answerBlocks(backend: HttpTestingController, blocks: BlockResponse[] = []) {
  backend.match({ method: 'GET', url: BLOCKS_URL }).forEach((request) => request.flush(blocks));
}
