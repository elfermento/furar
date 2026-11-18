// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.util.UUID;

/** Un uso do filtro. */
public record IndicatorFilterUseResponse(UUID id, UUID facilityId, String name, boolean archived) {}
