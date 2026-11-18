// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import java.time.LocalDate;

/** Rango de datas cos dous extremos incluídos. */
public record DateSpanResponse(LocalDate from, LocalDate to) {}
