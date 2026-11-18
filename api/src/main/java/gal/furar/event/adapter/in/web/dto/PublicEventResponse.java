// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Un evento como o ve a veciñanza: a tarxeta da portada e a ficha. {@code free} nulo: sen límite de
 * prazas. {@code open}: agora admite inscricións directas (publicado, antes de «Apuntarse ata», con
 * praza e sen lista de espera) ou, «Con aprobación», solicitudes aínda que estea completo. {@code
 * waitlist}: quen chega agora entra na lista de espera (ADR-0041); {@code waiting}, cantas persoas
 * hai nela. Nunha actividade, o horario resumido e as datas, o nome do uso e da parte (para «Onde»)
 * e a próxima sesión sen cancelar ({@code nextSession}; nula se xa non queda ningunha ou nun evento
 * dun día). Sen datos de persoas.
 */
public record PublicEventResponse(
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
    @Nullable EventNextSessionResponse nextSession,
    @Nullable Integer capacity,
    @Nullable Integer free,
    @Nullable Integer priceCents,
    PricePeriodValue pricePeriod,
    @Nullable Instant registrationDeadline,
    boolean approval,
    @Nullable Integer birthYearFrom,
    @Nullable Integer birthYearTo,
    boolean open,
    boolean waitlist,
    int waiting,
    EventCoverResponse cover) {}
