// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.municipality.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Texto propio dunha páxina legal en gl e es: HTML que o servidor sanea cunha lista branca (h2, h3,
 * p, strong, em, ul, ol, li, br e a con https ou mailto). Baleiro = sen texto nese idioma.
 */
public record LegalTextRequest(
    @Nullable @Size(max = 30_000) @Schema(description = "HTML en galego") String gl,
    @Nullable @Size(max = 30_000) @Schema(description = "HTML en castelán") String es) {}
