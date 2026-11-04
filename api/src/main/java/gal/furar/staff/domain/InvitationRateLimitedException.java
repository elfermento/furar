// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.domain;

import gal.furar.shared.domain.DomainException;
import java.time.Duration;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * 429 {@code staff.invitation-rate-limited} (plan do dominio 4, §1.2.7) co tempo que falta para
 * poder enviar outra vez, para a cabeceira {@code Retry-After}. Sen tempo cando a pertenza xa
 * gastou os seus envíos: esperar non o arranxa.
 */
public final class InvitationRateLimitedException extends DomainException {

  private static final long serialVersionUID = 1L;

  private final transient @Nullable Duration retryAfter;

  public InvitationRateLimitedException(@Nullable Duration retryAfter) {
    super(StaffError.INVITATION_RATE_LIMITED);
    this.retryAfter = retryAfter;
  }

  /** Canto esperar, en segundos enteiros cara arriba (mínimo 1), se esperar serve. */
  public Optional<Long> retryAfterSeconds() {
    if (retryAfter == null) {
      return Optional.empty();
    }
    long seconds = retryAfter.toSeconds() + (retryAfter.toNanosPart() > 0 ? 1 : 0);
    return Optional.of(Math.max(1, seconds));
  }
}
