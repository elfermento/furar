// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import java.util.List;

/** Unha fila da semana cos seus tramos en orde; baleira = pechado. */
public record DayHoursResponse(ScheduleDayValue day, List<TimeRangeResponse> intervals) {}
