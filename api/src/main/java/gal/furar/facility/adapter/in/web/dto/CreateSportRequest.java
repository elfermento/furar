// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/** «+ crear» un deporte do concello: nome en gl e es (P26). */
public record CreateSportRequest(
    @Nullable @Size(max = 60) String gl, @Nullable @Size(max = 60) String es) {}
