// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import gal.furar.shared.tenancy.TenantAware;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Evento público: a persoa (ou o persoal) cambiou a reserva a outro oco (ADR-0025, punto 8). Mesma
 * reserva (id e código), con versión nova. Publícase dentro da transacción e os escoitadores (o
 * correo) reciben despois do commit; len o resto con {@link BookingQueries}. Leva sempre o concello
 * (ADR-0006).
 *
 * @param previousStart inicio de antes, para o aviso «cambiouse de … a …»
 * @param previousEnd fin de antes
 * @param version versión da reserva despois do cambio: distingue un cambio doutro (o correo de cada
 *     un envíase unha vez)
 * @param silent sen correo á persoa: só cando cambia o persoal e desmarca «Avisar por correo»
 *     (falso nos eventos gardados antes de existir, que se avisan coma sempre)
 */
public record BookingChanged(
    UUID municipalityId,
    UUID bookingId,
    Instant previousStart,
    Instant previousEnd,
    long version,
    boolean silent)
    implements TenantAware {

  public BookingChanged {
    Objects.requireNonNull(municipalityId, "municipalityId");
    Objects.requireNonNull(bookingId, "bookingId");
    Objects.requireNonNull(previousStart, "previousStart");
    Objects.requireNonNull(previousEnd, "previousEnd");
  }

  /** Cambiou a persoa: sempre se lle avisa. */
  public BookingChanged(
      UUID municipalityId,
      UUID bookingId,
      Instant previousStart,
      Instant previousEnd,
      long version) {
    this(municipalityId, bookingId, previousStart, previousEnd, version, false);
  }
}
