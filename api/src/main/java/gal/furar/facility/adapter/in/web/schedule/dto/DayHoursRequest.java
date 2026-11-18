// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Unha fila da semana: sen tramos (ou sen {@code intervals}) está pechada. */
public record DayHoursRequest(
    @NotNull ScheduleDayValue day,
    @Nullable @Size(max = 10) List<@Valid @NotNull TimeRangeRequest> intervals) {}
