// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
import { AVAILABILITY_URL, BLOCK_URL, FIELDS_URL, NOT_BLOCKED } from '../app/features/booking/reserve/reserve.fixture';
import { backend, flushMatching } from './api';

/** Responde á pregunta do bloqueo por faltas, se está pendente (con sesión): ningún. */
export const flushNotBlocked = () => flushMatching((r) => r.url === BLOCK_URL, NOT_BLOCKED);

/** Responde ás entidades das que a persoa é responsable (ADR-0039), se se pediron: ningunha. */
export const flushNoOrganizations = () => flushMatching((r) => r.url.endsWith('/me/organizations'), []);

/**
 * Os campos propios da reserva, se se pediron (con sesión): os indicados ou ningún. E, con eles, o
 * bloqueo por faltas: ningún.
 */
export function flushFields(definitions: object[] = []): void {
  flushNotBlocked();
  flushMatching((r) => r.url === FIELDS_URL && r.params.get('target') === 'BOOKING', definitions);
}

/**
 * Espera polos ocos e responde a todos os pedidos con `body`: os do uso e, nun uso enteiro, os dos
 * outros usos que valen («Só unha parte»).
 */
export async function flushAvailabilities(body: object): Promise<void> {
  await vi.waitFor(() => {
    const requests = backend().match((r) => r.url === AVAILABILITY_URL);
    expect(requests.length).toBeGreaterThan(0);
    for (const request of requests) request.flush(body);
  });
}
