// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Cambiar unha reserva propia a outro oco da mesma instalación.
 *
 * @param expectedVersion a do {@code If-Match}
 * @param useId o uso novo (pode ser o mesmo), da mesma instalación
 * @param start inicio da primeira franxa (un {@code start} da dispoñibilidade)
 * @param slots franxas seguidas (1–16)
 * @param partId a parte escollida; sen ela nun uso por partes, «tanto me ten»
 * @param participants para quen é despois do cambio, nos usos por prazas (ADR-0031): así se quita
 *     ou se engade unha persoa; nulo = as mesmas persoas
 */
public record ChangeBookingCommand(
    BookingId id,
    long expectedVersion,
    UUID useId,
    Instant start,
    int slots,
    @Nullable UUID partId,
    @Nullable ParticipantSelection participants) {

  /** Sen tocar para quen é. */
  public ChangeBookingCommand(
      BookingId id,
      long expectedVersion,
      UUID useId,
      Instant start,
      int slots,
      @Nullable UUID partId) {
    this(id, expectedVersion, useId, start, slots, partId, null);
  }

  public ChangeBookingCommand {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
  }
}
