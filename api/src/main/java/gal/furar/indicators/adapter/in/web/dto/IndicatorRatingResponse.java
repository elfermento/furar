// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

/**
 * Valoracións en bruto (ADR-0037): a media é {@code starsSum / reviews} (sen valor se {@code
 * reviews} é 0).
 */
public record IndicatorRatingResponse(long reviews, long starsSum) {}
