// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.List;

/**
 * Os filtros do panel, por nome: as instalacións (as de baixa, só con reservas) e os seus usos (os
 * arquivados, só con reservas).
 */
public record IndicatorFiltersResponse(
    List<IndicatorFilterFacilityResponse> facilities, List<IndicatorFilterUseResponse> uses) {}
