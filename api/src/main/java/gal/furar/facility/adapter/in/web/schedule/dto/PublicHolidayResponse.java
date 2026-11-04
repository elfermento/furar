// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import java.time.LocalDate;

/** Festivo de Galicia, co nome en gl e es e a súa fonte (BOE e DOG). */
public record PublicHolidayResponse(LocalDate date, HolidayNameResponse name, String source) {}
