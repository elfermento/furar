// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import org.jspecify.annotations.Nullable;

/** As catro normas do uso; nula = non se aplica. Os rangos son os de {@link UseRulesRequest}. */
public record UseRulesResponse(
    @Nullable Integer minAdvanceHours,
    @Nullable Integer maxAdvanceDays,
    @Nullable Integer maxSlots,
    @Nullable Integer capCount,
    @Nullable CapPeriodValue capPeriod,
    @Nullable Integer cancelHours) {}
