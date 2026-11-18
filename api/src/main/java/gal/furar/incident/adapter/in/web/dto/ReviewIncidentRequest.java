// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.incident.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * «Gardar» no detalle: o estado, a nota interna (ata 1000) e, se queda resolta, a mensaxe para a
 * persoa que avisou (ata 500; noutro estado ignórase).
 */
public record ReviewIncidentRequest(
    @NotNull IncidentStatusValue status,
    @Size(max = 1000) @Nullable String internalNote,
    @Size(max = 500) @Nullable String resolutionMessage) {}
