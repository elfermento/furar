// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Cambiar unha reserva propia a outro oco da mesma instalación (plan do dominio 2, §2.2 e §1.2.8).
 *
 * @param expectedVersion a do {@code If-Match}
 * @param useId o uso novo (pode ser o mesmo), da mesma instalación (P2)
 * @param start inicio da primeira franxa (un {@code start} da dispoñibilidade)
 * @param slots franxas seguidas (1–16)
 * @param partId a parte escollida; sen ela nun uso por partes, «tanto me ten»
 */
public record ChangeBookingCommand(
    BookingId id,
    long expectedVersion,
    UUID useId,
    Instant start,
    int slots,
    @Nullable UUID partId) {

  public ChangeBookingCommand {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
  }
}
