// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** Sección Descrición (con {@code If-Match}): gl e es, ou os dous baleiros para quitala. */
public record UpdateFacilityDescriptionRequest(
    @Nullable @Size(max = 2000) String gl, @Nullable @Size(max = 2000) String es) {}
