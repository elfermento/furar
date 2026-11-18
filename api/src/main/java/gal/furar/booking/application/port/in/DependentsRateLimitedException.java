// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingError;
import gal.furar.shared.domain.DomainException;
import java.util.Map;

/**
 * Demasiadas lecturas das persoas a cargo desde o mostrador: 429 {@code
 * booking.dependents-rate-limited} con {@code Retry-After} (ADR-0031, punto 7).
 */
public class DependentsRateLimitedException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final long retryAfterSeconds;

  public DependentsRateLimitedException(long retryAfterSeconds) {
    super(BookingError.DEPENDENTS_RATE_LIMITED, Map.of());
    this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
  }

  /** Segundos ata que se pode volver consultar. */
  public long retryAfterSeconds() {
    return retryAfterSeconds;
  }
}
