// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * A inscrición da conta nun evento, co evento. Sen inscrición, {@code status} nulo (na ficha).
 * Desde ADR-0041: para quen é ({@code dependent}, nula se é a propia persoa); na lista de espera, o
 * seu posto, se está avisada ({@code NOTIFIED}) ata cando a praza é só súa ({@code exclusiveUntil})
 * e, se volveu a esperar porque outra persoa cubriu a praza da que se avisara, {@code seatCovered}.
 * Nunha actividade con horario, as próximas sesións sen cancelar ({@code nextSessions}, como moito
 * cinco; baleira nun evento dun día).
 */
public record MyEventRegistrationResponse(
    @Nullable EventRegistrationStatusValue status,
    @Nullable EventPaymentStatusValue payment,
    @Nullable EventDependentResponse dependent,
    @Nullable Integer position,
    @Nullable Instant notifiedAt,
    @Nullable Instant exclusiveUntil,
    boolean seatCovered,
    List<EventNextSessionResponse> nextSessions,
    PublicEventResponse event) {}
