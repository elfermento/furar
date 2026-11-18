// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Definición dun campo. As opcións van en orde alfabética no idioma de quen pregunta. Un campo
 * úsase nun só sitio: {@code usages} leva un elemento (ADR-0011, revisión do 2026-11-04).
 *
 * @param hasValues só para o xestor: se algunha entidade ten valor neste campo (entón non se pode
 *     eliminar nin cambiar de sitio); nulo nas listas públicas e na exportación
 */
public record CustomFieldDefinitionResponse(
    UUID id,
    String code,
    @Schema(
            allowableValues = {
              "TEXT",
              "LONG_TEXT",
              "NUMBER",
              "DATE",
              "BOOLEAN",
              "SELECT",
              "MULTI_SELECT"
            })
        String type,
    LocalizedTextResponse label,
    @Nullable LocalizedTextResponse help,
    FieldRulesResponse rules,
    List<FieldOptionResponse> options,
    boolean personalData,
    List<FieldUsageResponse> usages,
    @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status,
    @Schema(description = "Só para o xestor: ten valores gardados (non se pode eliminar)")
        @Nullable Boolean hasValues,
    long version,
    Instant createdAt,
    Instant updatedAt) {}
