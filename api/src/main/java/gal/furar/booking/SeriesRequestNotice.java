// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha solicitude de tempada rexeitada tal como a conta o correo á entidade (ADR-0039, punto 12): o
 * que se pediu e a mensaxe do concello, tal cal.
 *
 * @param language {@code gl} ou {@code es}: o primeiro idioma do concello
 * @param weekdays días da semana ISO (luns = 1)
 */
public record SeriesRequestNotice(
    UUID requestId,
    String organizationName,
    String email,
    String language,
    String facilityName,
    String useName,
    @Nullable String partName,
    List<Integer> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate,
    String message) {

  public SeriesRequestNotice {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(organizationName, "organizationName");
    Objects.requireNonNull(email, "email");
    Objects.requireNonNull(language, "language");
    Objects.requireNonNull(facilityName, "facilityName");
    Objects.requireNonNull(useName, "useName");
    Objects.requireNonNull(message, "message");
    weekdays = List.copyOf(weekdays);
  }
}
