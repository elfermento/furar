// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Regras de validación; cada tipo admite as súas (as outras deben ir baleiras). */
public record FieldRulesRequest(
    Integer minLength,
    Integer maxLength,
    BigDecimal min,
    BigDecimal max,
    Integer decimals,
    LocalDate minDate,
    LocalDate maxDate,
    Integer minItems,
    Integer maxItems,
    @Schema(allowableValues = {"EMAIL", "PHONE", "URL"}) String format) {}
