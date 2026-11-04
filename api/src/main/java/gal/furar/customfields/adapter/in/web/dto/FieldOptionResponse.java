// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Opción dun campo de selección.
 *
 * @param hasValues só para o xestor: se algún valor a escolleu (entón non se pode eliminar, só
 *     desactivar); nulo nas listas públicas e na exportación
 */
public record FieldOptionResponse(
    String code,
    LocalizedTextResponse label,
    boolean inactive,
    @Schema(description = "Só para o xestor: ten valores gardados (non se pode eliminar)")
        @Nullable Boolean hasValues) {}
