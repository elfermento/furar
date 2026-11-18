// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * A lista dunha sesión (ADR-0041). Sen gardar ({@code taken} falso), todas as persoas veñen
 * presentes por defecto; gardada, coa súa marca ({@code present} nulo: sen marcar), e cando e quen
 * a gardou por última vez ({@code takenAt}, {@code takenByName}: «Pasada ás 17:10 por …»).
 */
public record EventAttendanceResponse(
    UUID sessionId,
    LocalDate date,
    Instant startsAt,
    Instant endsAt,
    boolean taken,
    @Nullable Instant takenAt,
    @Nullable String takenByName,
    List<AttendeeResponse> attendees) {

  /** Unha persoa con praza: quen a apuntou e, se é unha persoa a cargo, o seu nome. */
  public record AttendeeResponse(
      UUID registrationId,
      @Nullable String name,
      @Nullable String dependentName,
      @Nullable Boolean present) {}
}
