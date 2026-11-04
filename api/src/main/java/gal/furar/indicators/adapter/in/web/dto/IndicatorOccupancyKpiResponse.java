// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.List;

/**
 * A ocupación media: o período, o anterior, os dous tramos da variación e a evolución (como en
 * {@link IndicatorCountKpiResponse}).
 */
public record IndicatorOccupancyKpiResponse(
    IndicatorOccupancyResponse value,
    IndicatorOccupancyResponse previous,
    IndicatorOccupancyResponse valueToDate,
    IndicatorOccupancyResponse previousToDate,
    List<IndicatorOccupancyPointResponse> evolution) {}
