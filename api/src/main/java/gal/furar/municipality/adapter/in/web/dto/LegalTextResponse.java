// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/** Texto propio dunha páxina legal, xa saneado; {@code null} = sen texto nese idioma. */
public record LegalTextResponse(
    @Nullable @Schema(description = "HTML saneado en galego") String gl,
    @Nullable @Schema(description = "HTML saneado en castelán") String es) {}
