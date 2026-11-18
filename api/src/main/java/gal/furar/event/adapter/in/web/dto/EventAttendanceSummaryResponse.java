// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O resumo da asistencia dunha persoa con praza (ADR-0041): sesións con lista pasada, presenzas e
 * faltas. Sen penalización.
 */
public record EventAttendanceSummaryResponse(
    UUID registrationId,
    @Nullable String name,
    @Nullable String dependentName,
    int sessions,
    int present,
    int absent) {}
