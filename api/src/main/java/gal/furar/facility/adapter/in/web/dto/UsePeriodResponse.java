// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import java.time.LocalDate;

/** Rango de datas dun uso, cos dous días incluídos. */
public record UsePeriodResponse(LocalDate from, LocalDate to) {}
