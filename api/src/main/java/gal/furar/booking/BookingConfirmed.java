// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.UUID;

/**
 * Evento público: unha persoa reservou (ADR-0025, punto 15). Publícase dentro da transacción e os
 * escoitadores (o correo) reciben despois do commit; len o resto con {@link BookingQueries}. Leva
 * sempre o concello (ADR-0006).
 */
public record BookingConfirmed(UUID municipalityId, UUID bookingId) implements TenantAware {}
