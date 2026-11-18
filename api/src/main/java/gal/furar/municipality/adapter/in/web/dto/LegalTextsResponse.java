// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Textos propios das páxinas legais, xa saneados; {@code null} = a páxina non ten texto propio.
 * Gárdanse aínda que a páxina non estea no modo {@code TEXT}.
 */
@Schema(description = "Textos propios das páxinas legais do concello")
public record LegalTextsResponse(
    @Nullable LegalTextResponse privacy,
    @Nullable LegalTextResponse accessibility,
    @Nullable LegalTextResponse legalNotice) {}
