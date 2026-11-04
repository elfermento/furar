// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Tempada coa súa semana: sempre as 8 filas, de luns a domingo e festivos. {@code version} é a do
 * {@code If-Match} da tempada. Sen {@code endDate}, a tempada non ten fin.
 */
public record SeasonResponse(
    UUID id,
    UUID facilityId,
    String name,
    LocalDate startDate,
    @Nullable LocalDate endDate,
    List<DayHoursResponse> week,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
