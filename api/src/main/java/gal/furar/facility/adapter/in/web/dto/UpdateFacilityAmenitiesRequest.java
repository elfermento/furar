// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Sección Servizos e accesibilidade (con {@code If-Match}): a lista enteira, sen orde. */
public record UpdateFacilityAmenitiesRequest(@NotNull List<@NotNull AmenityValue> amenities) {}
