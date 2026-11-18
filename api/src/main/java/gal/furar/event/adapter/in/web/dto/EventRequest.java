// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Alta ou edición dun evento. Lugar: {@code facilityId} ou {@code place} (outro sitio), un dos
 * dous. Baleiros: {@code capacity} sen límite, {@code priceCents} gratis, {@code
 * registrationDeadline} ata que empeza (unha actividade, ata que remata), {@code kind} un día,
 * {@code pricePeriod} unha vez, os anos de nacemento sen límite. Un evento dun día leva {@code
 * startsAt}; unha actividade ({@code SCHEDULED}, ADR-0041), {@code schedule}: o seu horario, datas
 * e lugar non se cambian ao editar.
 */
public record EventRequest(
    @Nullable String nameGl,
    @Nullable String nameEs,
    @Nullable String descriptionGl,
    @Nullable String descriptionEs,
    @Nullable EventKindValue kind,
    @Nullable Instant startsAt,
    @Nullable UUID facilityId,
    @Nullable String place,
    @Valid @Nullable EventScheduleRequest schedule,
    @Nullable Integer capacity,
    @Nullable Integer priceCents,
    @Nullable PricePeriodValue pricePeriod,
    @Nullable Instant registrationDeadline,
    boolean published,
    boolean approval,
    @Nullable Integer birthYearFrom,
    @Nullable Integer birthYearTo) {}
