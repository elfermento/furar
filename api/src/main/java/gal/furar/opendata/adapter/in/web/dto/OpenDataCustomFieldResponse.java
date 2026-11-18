// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Un campo personalizado público das instalacións: a columna {@code campo_<code>}. */
public record OpenDataCustomFieldResponse(
    String code,
    OpenDataTextResponse label,
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
        String type) {}
