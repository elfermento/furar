// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Excepción dun día: pechado ({@code open} falso, sen tramo) ou aberto cun tramo en minutos de
 * reloxo de parede. {@code version} é a do {@code If-Match} da excepción.
 */
public record ScheduleExceptionResponse(
    UUID id,
    UUID facilityId,
    LocalDate date,
    boolean open,
    @Nullable Integer startMinute,
    @Nullable Integer endMinute,
    @Nullable String reason,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
