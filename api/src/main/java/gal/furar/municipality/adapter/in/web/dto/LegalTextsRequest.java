// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import org.jspecify.annotations.Nullable;

/**
 * Textos propios das tres páxinas legais (con {@code If-Match} do concello). Substitúen os
 * gardados: unha páxina sen texto queda sen el, sexa cal sexa o seu modo.
 */
@Schema(description = "Textos propios das páxinas legais do concello")
public record LegalTextsRequest(
    @Nullable @Valid LegalTextRequest privacy,
    @Nullable @Valid LegalTextRequest accessibility,
    @Nullable @Valid LegalTextRequest legalNotice) {}
