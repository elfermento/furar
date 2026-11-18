// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.time.LocalDate;

/**
 * As cancelacións dun día, unha semana ou un mes, cortado polo período.
 *
 * @param inTime canceladas pola persoa antes do límite
 * @param late canceladas pola persoa desde o límite
 * @param byMunicipality canceladas polo concello
 * @param partial se o tramo contén hoxe
 */
public record IndicatorCancellationsResponse(
    LocalDate from, LocalDate to, long inTime, long late, long byMunicipality, boolean partial) {}
