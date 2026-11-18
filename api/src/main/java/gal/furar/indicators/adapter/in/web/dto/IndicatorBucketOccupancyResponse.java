// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * A ocupación dun día, unha semana ou un mes, cortado polo período.
 *
 * @param partial se o tramo contén hoxe
 */
public record IndicatorBucketOccupancyResponse(
    LocalDate from, LocalDate to, double reservedMinutes, long openMinutes, boolean partial) {}
