// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Un peche próximo da ficha pública: un día ou varios seguidos (os dous extremos incluídos). Sen
 * {@code startMinute} nin {@code endMinute}, todo o día; con eles, as horas pechadas (minutos
 * 0–1440). {@code reason}: o motivo que escribiu o concello, se o hai; {@code holiday}: o festivo
 * dese día, se o é.
 */
public record PublicClosureResponse(
    LocalDate from,
    LocalDate to,
    @Nullable Integer startMinute,
    @Nullable Integer endMinute,
    @Nullable String reason,
    @Nullable ClosureHolidayResponse holiday) {}
