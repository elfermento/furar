// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha sesión dunha actividade (ADR-0041): o día e as horas, se está cancelada, se xa se pasou
 * lista e cantas persoas estaban e cantas faltaron.
 */
public record EventSessionResponse(
    UUID id,
    LocalDate date,
    Instant startsAt,
    Instant endsAt,
    @Nullable Instant cancelledAt,
    boolean attendanceTaken,
    int present,
    int absent) {}
