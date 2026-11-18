// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.staff.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.jspecify.annotations.Nullable;

/**
 * Un campo que cambiou nunha acción do rexistro (ADR-0020, revisión do 2026-11-10). Os valores son
 * curtos e nunca persoais: un dato persoal ou un texto longo vai con {@code hidden} e sen valores.
 *
 * @param field o código do campo; a web ten un texto para cada un e outro de reserva
 * @param before o valor de antes; nulo se non había ou se está oculto
 * @param after o valor de despois; nulo se xa non hai ou se está oculto
 * @param hidden os valores non se gardan: só que cambiou
 */
public record ActivityChangeResponse(
    @Schema(example = "season.endDate") String field,
    @Nullable String before,
    @Nullable String after,
    boolean hidden) {}
