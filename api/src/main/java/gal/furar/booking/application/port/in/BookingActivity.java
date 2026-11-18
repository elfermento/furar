// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A actividade do concello da que é sesión unha reserva (ADR-0041): o evento e, se {@code event} a
 * coñece, a sesión.
 */
public record BookingActivity(UUID eventId, @Nullable UUID sessionId) {

  public BookingActivity {
    Objects.requireNonNull(eventId, "eventId");
  }
}
