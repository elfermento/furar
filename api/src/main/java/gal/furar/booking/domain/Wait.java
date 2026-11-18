// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.domain;

import gal.furar.shared.domain.Language;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Unha espera (ADR-0033, punto 1): unha conta pide que se lle avise se queda libre unha franxa dun
 * uso ({@code [start, end)}), sen número de franxas e sen parte. Non garda nome: o aviso non saúda.
 *
 * @param subject {@code sub} da conta
 * @param emailKey a clave do correo verificado da conta ({@code EmailKey.of}); a el vai o aviso
 * @param language idioma do aviso
 */
public record Wait(
    UUID id,
    UUID facilityId,
    UUID useId,
    Instant start,
    Instant end,
    String subject,
    String emailKey,
    Language language,
    Instant createdAt) {

  public Wait {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(facilityId, "facilityId");
    Objects.requireNonNull(useId, "useId");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    Objects.requireNonNull(subject, "subject");
    Objects.requireNonNull(emailKey, "emailKey");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(createdAt, "createdAt");
    if (!end.isAfter(start)) {
      throw new IllegalArgumentException("Empty slot: " + start + " - " + end);
    }
  }

  /** Se a súa franxa pisa {@code [from, to)}. */
  public boolean overlaps(Instant from, Instant to) {
    return start.isBefore(to) && end.isAfter(from);
  }
}
