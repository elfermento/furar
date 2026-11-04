// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.portability.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Bloqueo no ficheiro furar-export: os campos da API e quen o creou ({@code createdBy}, o {@code
 * sub} da conta de xestión). Ao importar, {@code start} e {@code end} calcúlanse de novo desde a
 * data e os minutos (Europe/Madrid).
 */
public record BlockExportResponse(
    UUID id,
    UUID facilityId,
    LocalDate date,
    int startMinute,
    int endMinute,
    Instant start,
    Instant end,
    @Nullable String reason,
    String createdBy,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
