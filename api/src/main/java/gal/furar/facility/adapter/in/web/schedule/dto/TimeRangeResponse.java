// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

/** Tramo en minutos desde a medianoite (1440 = 24:00); o fin non se inclúe. */
public record TimeRangeResponse(int startMinute, int endMinute) {}
