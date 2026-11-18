// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.service;

import gal.furar.booking.domain.ReminderOptOutToken;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Dá e comproba a ligazón «Non quero recordatorios» (ADR-0035) coa clave da pegada do rexistro de
 * actividade ({@code furar.audit.hash-secret}): sen outro segredo que configurar.
 */
@Component
class ReminderLinks {

  static final int MIN_SECRET_LENGTH = 32;

  private final String secret;

  ReminderLinks(@Value("${furar.audit.hash-secret}") String secret) {
    if (secret == null || secret.strip().length() < MIN_SECRET_LENGTH) {
      throw new IllegalStateException(
          "furar.audit.hash-secret (FURAR_AUDIT_HASH_SECRET) must have at least "
              + MIN_SECRET_LENGTH
              + " characters (ADR-0035)");
    }
    this.secret = secret.strip();
  }

  String issue(UUID municipalityId, UUID bookingId, Instant now) {
    return ReminderOptOutToken.issue(secret, municipalityId, bookingId, now);
  }

  ReminderOptOutToken.Check verify(UUID municipalityId, @Nullable String token, Instant now) {
    return ReminderOptOutToken.verify(secret, municipalityId, token, now);
  }
}
