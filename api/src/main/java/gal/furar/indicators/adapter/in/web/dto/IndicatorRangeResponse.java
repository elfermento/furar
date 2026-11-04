// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.indicators.adapter.in.web.dto;

import java.time.LocalDate;

/** Un tramo de días de Europe/Madrid, cos dous extremos incluídos. */
public record IndicatorRangeResponse(LocalDate from, LocalDate to) {}
