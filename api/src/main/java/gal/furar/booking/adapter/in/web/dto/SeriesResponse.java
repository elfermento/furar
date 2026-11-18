// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha tempada (ADR-0039, punto 11).
 *
 * @param weekdays días da semana ISO (luns = 1)
 * @param sessions sesións confirmadas
 * @param cancelled sesións canceladas
 * @param endedFrom desde que día se rematou
 * @param requestId a solicitude da que saíu
 */
public record SeriesResponse(
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
    SeriesPaymentStatusValue paymentStatus,
    int sessions,
    int cancelled,
    @Nullable LocalDate endedFrom,
    @Nullable UUID requestId,
    long version) {}
