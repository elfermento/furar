// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Bloqueo dun día: o tramo en minutos de reloxo de parede (Europe/Madrid) tal como o escribiu o
 * xestor e o período real que bloquea ({@code start} incluído, {@code end} excluído). {@code
 * version} é a do {@code If-Match} do bloqueo.
 */
public record BlockResponse(
    UUID id,
    UUID facilityId,
    LocalDate date,
    int startMinute,
    int endMinute,
    Instant start,
    Instant end,
    @Nullable String reason,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
