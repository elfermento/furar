// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.UUID;

/** Unha instalación do filtro. */
public record IndicatorFilterFacilityResponse(
    UUID id, String name, IndicatorFacilityStatusValue status) {}
