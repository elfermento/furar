// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha persoa dunha reserva por prazas («Para quen»).
 *
 * @param name nome e apelidos; en {@code SELF}, o de quen ten a reserva
 * @param birthYear ano de nacemento das persoas a cargo e dos acompañantes; só para o persoal
 * @param dependentId a ficha da persoa a cargo, para volver marcala ao cambiar a reserva
 */
public record BookingParticipantResponse(
    ParticipantKindValue kind,
    String name,
    @Nullable Integer birthYear,
    @Nullable UUID dependentId) {}
