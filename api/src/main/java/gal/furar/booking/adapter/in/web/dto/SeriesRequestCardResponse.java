// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha solicitude de tempada para a xestión (ADR-0039, punto 11): a tarxeta e o detalle.
 *
 * @param weekdays días da semana ISO (luns = 1)
 * @param requestedByEmail o correo de quen a pediu (nulo tras a baixa da súa conta)
 * @param seriesId a tempada creada ao aprobala
 */
public record SeriesRequestCardResponse(
    UUID id,
    UUID organizationId,
    String organizationName,
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
    SeriesRequestStatusValue status,
    Instant createdAt,
    @Nullable String note,
    @Nullable String requestedByEmail,
    @Nullable String rejectionMessage,
    @Nullable UUID seriesId) {}
