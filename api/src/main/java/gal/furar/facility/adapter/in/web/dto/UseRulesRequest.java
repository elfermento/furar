// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/**
 * As catro normas do uso; baleira = non se aplica.
 *
 * @param minAdvanceHours antelación mínima en horas (0–720)
 * @param maxAdvanceDays antelación máxima en días (1–365)
 * @param maxSlots duración máxima en franxas seguidas (1–16)
 * @param capCount límite por persoa (1–50), con {@code capPeriod}
 * @param cancelHours cancelar sen custo ata N horas antes (0–720)
 */
public record UseRulesRequest(
    @Nullable Integer minAdvanceHours,
    @Nullable Integer maxAdvanceDays,
    @Nullable Integer maxSlots,
    @Nullable Integer capCount,
    @Nullable CapPeriodValue capPeriod,
    @Nullable Integer cancelHours) {}
