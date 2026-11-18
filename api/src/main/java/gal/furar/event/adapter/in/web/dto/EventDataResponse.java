// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha fila da sección {@code event} do ficheiro furar-export: o evento, sen persoas. Desde a
 * versión 5 do formato (ADR-0041), o tipo, o fin, o horario dunha actividade (sen as súas sesións,
 * que son reservas e non van no ficheiro), os nomes do uso e da parte (informativos: a importación
 * le os ids), o límite de idade e cada canto se paga o prezo.
 */
public record EventDataResponse(
    UUID id,
    EventTextResponse name,
    @Nullable EventTextResponse description,
    @Nullable EventKindValue kind,
    Instant startsAt,
    @Nullable Instant endsAt,
    @Nullable UUID facilityId,
    @Nullable String place,
    @Nullable EventScheduleResponse schedule,
    @Nullable String useName,
    @Nullable String partName,
    @Nullable Integer capacity,
    @Nullable Integer priceCents,
    @Nullable PricePeriodValue pricePeriod,
    @Nullable Instant registrationDeadline,
    boolean published,
    boolean approval,
    @Nullable Integer birthYearFrom,
    @Nullable Integer birthYearTo,
    @Nullable Instant cancelledAt,
    Instant createdAt,
    Instant updatedAt) {}
