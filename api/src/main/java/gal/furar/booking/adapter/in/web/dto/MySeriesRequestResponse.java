// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha solicitude de tempada da entidade (ADR-0039, punto 10).
 *
 * @param weekdays días da semana ISO (luns = 1)
 * @param rejectionMessage a mensaxe do concello, se a rexeitou
 */
public record MySeriesRequestResponse(
    UUID id,
    UUID facilityId,
    String facilityName,
    UUID useId,
    String useName,
    @Nullable UUID partId,
    @Nullable String partName,
    List<Integer> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate,
    @Nullable String note,
    SeriesRequestStatusValue status,
    @Nullable String rejectionMessage,
    Instant createdAt) {}
