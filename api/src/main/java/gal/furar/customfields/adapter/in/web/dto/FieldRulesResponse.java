// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.customfields.adapter.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

public record FieldRulesResponse(
    @Nullable Integer minLength,
    @Nullable Integer maxLength,
    @Nullable BigDecimal min,
    @Nullable BigDecimal max,
    @Nullable Integer decimals,
    @Nullable LocalDate minDate,
    @Nullable LocalDate maxDate,
    @Nullable Integer minItems,
    @Nullable Integer maxItems,
    @Nullable @Schema(allowableValues = {"EMAIL", "PHONE", "URL"}) String format) {}
