// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * Un punto da evolución da valoración media.
 *
 * @param partial se é a unidade en curso (contén hoxe)
 */
public record IndicatorRatingPointResponse(
    LocalDate from, LocalDate to, IndicatorRatingResponse value, boolean partial) {}
