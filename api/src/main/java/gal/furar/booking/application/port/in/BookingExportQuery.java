// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.application.port.in;

import gal.furar.booking.domain.BookingStatus;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Que reservas se descargan: as que empezan entre {@code from} e {@code to} (días de Europe/Madrid,
 * os dous incluídos), destas instalacións e nestes estados. Un conxunto baleiro non filtra.
 */
public record BookingExportQuery(
    LocalDate from, LocalDate to, Set<UUID> facilityIds, Set<BookingStatus> statuses) {

  public BookingExportQuery {
    Objects.requireNonNull(from, "from");
    Objects.requireNonNull(to, "to");
    facilityIds = facilityIds == null ? Set.of() : Set.copyOf(facilityIds);
    statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
  }
}
