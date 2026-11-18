// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un evento na xestión: todo o que se edita, o estado e cantas persoas teñen praza, a piden, foron
 * avisadas dunha praza libre e esperan (só números). A versión vai no {@code ETag}. Nunha
 * actividade (ADR-0041), {@code startsAt} é a primeira sesión e {@code endsAt} o fin da última, e
 * {@code useName} e {@code partName} os nomes de agora do uso e da parte.
 */
public record EventResponse(
    UUID id,
    EventTextResponse name,
    @Nullable EventTextResponse description,
    EventKindValue kind,
    Instant startsAt,
    Instant endsAt,
    @Nullable EventFacilityResponse facility,
    @Nullable String place,
    @Nullable EventScheduleResponse schedule,
    @Nullable String useName,
    @Nullable String partName,
    @Nullable Integer capacity,
    @Nullable Integer priceCents,
    PricePeriodValue pricePeriod,
    @Nullable Instant registrationDeadline,
    boolean published,
    boolean approval,
    @Nullable Integer birthYearFrom,
    @Nullable Integer birthYearTo,
    @Nullable Instant cancelledAt,
    int confirmed,
    int pending,
    int notified,
    int waiting,
    long version) {}
