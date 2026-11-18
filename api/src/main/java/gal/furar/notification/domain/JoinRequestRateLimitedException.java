// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.notification.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Duration;

/**
 * 429 {@code notification.join-request-rate-limited} co tempo que falta para a cabeceira {@code
 * Retry-After}.
 */
public final class JoinRequestRateLimitedException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final transient Duration retryAfter;

  public JoinRequestRateLimitedException(Duration retryAfter) {
    super(JoinRequestError.RATE_LIMITED);
    this.retryAfter = retryAfter;
  }

  /** Canto esperar, en segundos enteiros cara arriba (mínimo 1). */
  public long retryAfterSeconds() {
    long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
    return Math.max(1, seconds);
  }
}
