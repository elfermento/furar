// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.application.port.in;

import gal.furar.notification.domain.MailKind;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Que correo dunha reserva enviar, desde un evento de {@code booking}.
 *
 * @param eventId distingue dous correos do mesmo tipo e reserva (a versión de cada cambio)
 * @param previousStart só ao cambiar: o inicio de antes
 * @param previousEnd só ao cambiar: o fin de antes
 * @param noShow só na falta: cantas leva a persoa e cando se bloquea
 */
public record BookingMailCommand(
    UUID bookingId,
    MailKind kind,
    String eventId,
    @Nullable Instant previousStart,
    @Nullable Instant previousEnd,
    @Nullable NoShowFacts noShow) {

  /** Un correo que non é o dunha falta. */
  public BookingMailCommand(
      UUID bookingId,
      MailKind kind,
      String eventId,
      @Nullable Instant previousStart,
      @Nullable Instant previousEnd) {
    this(bookingId, kind, eventId, previousStart, previousEnd, null);
  }

  public BookingMailCommand {
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(eventId, "eventId");
    if (!kind.ofBooking()) {
      throw new IllegalArgumentException("Not the mail of a booking: " + kind);
    }
    if ((kind == MailKind.CHANGED) != (previousStart != null && previousEnd != null)) {
      throw new IllegalArgumentException("Only a change has the previous period");
    }
    if ((kind == MailKind.NO_SHOW) != (noShow != null)) {
      throw new IllegalArgumentException("Only a no-show mail has the faults");
    }
  }
}
