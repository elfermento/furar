// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * Persoas distintas con reservas confirmadas nun mes.
 *
 * @param partial se é o mes en curso
 */
public record IndicatorPeopleResponse(LocalDate from, LocalDate to, long people, boolean partial) {}
