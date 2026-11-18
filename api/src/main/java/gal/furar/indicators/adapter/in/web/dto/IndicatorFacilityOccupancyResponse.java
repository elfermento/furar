// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.UUID;

/** A ocupación dunha instalación con horas abertas no período. */
public record IndicatorFacilityOccupancyResponse(
    UUID facilityId, String name, double reservedMinutes, long openMinutes) {}
