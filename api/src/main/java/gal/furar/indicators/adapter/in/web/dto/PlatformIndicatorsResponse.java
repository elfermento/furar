// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.List;

/**
 * Os indicadores da plataforma: o período, a comparación, os totais e unha fila por concello.
 * Números en bruto, coma no panel do concello.
 *
 * @param municipalities un por concello, por nome
 */
public record PlatformIndicatorsResponse(
    IndicatorPeriodResponse period,
    IndicatorRangeResponse previous,
    IndicatorComparisonResponse comparison,
    PlatformIndicatorKpisResponse kpis,
    List<PlatformMunicipalityIndicatorsResponse> municipalities) {}
