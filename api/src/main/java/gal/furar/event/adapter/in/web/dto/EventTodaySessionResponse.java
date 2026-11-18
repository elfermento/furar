// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha sesión de hoxe para pasar lista desde o móbil (ADR-0041): a actividade, a hora, onde, cantas
 * persoas teñen praza e se a lista xa está pasada; con ela pasada, cantas estaban e faltaron.
 */
public record EventTodaySessionResponse(
    UUID eventId,
    EventTextResponse name,
    UUID sessionId,
    Instant startsAt,
    Instant endsAt,
    @Nullable EventFacilityResponse facility,
    @Nullable String place,
    int registered,
    boolean attendanceTaken,
    int present,
    int absent) {}
