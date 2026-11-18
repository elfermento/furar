// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Unha persoa apuntada, pedindo praza ou na lista de espera, para Xestión e Recepción: o nome (ou,
 * sen el, o correo) de quen a apuntou e, se é unha persoa a cargo, o seu nome; nun evento con
 * límite de idade, o ano de nacemento (da ficha da persoa a cargo ou da data da propia persoa) e a
 * data que deu a propia persoa; e cando. O pago, só nun evento con prezo e con praza. Na lista de
 * espera, o posto e, se está avisada, ata cando é só súa. Anonimizada, sen nome nin correo.
 */
public record EventRegistrationResponse(
    UUID id,
    EventRegistrationStatusValue status,
    @Nullable String name,
    @Nullable String email,
    @Nullable String dependentName,
    @Nullable Integer birthYear,
    @Nullable LocalDate birthDate,
    @Nullable EventPaymentStatusValue payment,
    @Nullable Integer position,
    @Nullable Instant exclusiveUntil,
    Instant createdAt,
    @Nullable Instant decidedAt) {}
