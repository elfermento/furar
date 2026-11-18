// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Mapa da web (ADR-0023, revisión do 2026-11-03). */
public record MapSettingsResponse(
    @Schema(description = "Se a web mostra mapas (FURAR_MAP); sen mapa, só o enderezo")
        boolean enabled,
    @Schema(
            description = "Recoñecemento á vista ao pé do mapa (FURAR_MAP_ATTRIBUTION)",
            example = "CC BY 4.0 scne.es")
        String attribution,
    @Schema(
            description = "Ligazón do recoñecemento (FURAR_MAP_ATTRIBUTION_URL); null se non hai",
            example = "https://www.scne.es")
        @Nullable String attributionUrl) {}
