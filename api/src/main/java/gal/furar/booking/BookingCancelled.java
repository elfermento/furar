// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Evento público: unha reserva cancelouse (ADR-0025, puntos 12 e 16). Publícase dentro da
 * transacción e os escoitadores (o correo) reciben despois do commit; len o resto con {@link
 * BookingQueries}. Leva sempre o concello (ADR-0006).
 *
 * @param by quen cancelou
 * @param channels por onde decidiu avisar o concello; baleiro cando cancela a persoa
 */
public record BookingCancelled(UUID municipalityId, UUID bookingId, By by, Set<Channel> channels)
    implements TenantAware {

  public BookingCancelled {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(by, "by");
    channels = channels == null ? Set.of() : Set.copyOf(channels);
  }

  /** A persoa cancelou a súa reserva. */
  public static BookingCancelled byPerson(UUID municipalityId, UUID bookingId) {
    return new BookingCancelled(municipalityId, bookingId, By.PERSON, Set.of());
  }

  /** Quen cancelou. */
  public enum By {
    PERSON,
    MUNICIPALITY
  }

  /** Canle do aviso dunha cancelación polo concello. */
  public enum Channel {
    EMAIL,
    WEB
  }
}
