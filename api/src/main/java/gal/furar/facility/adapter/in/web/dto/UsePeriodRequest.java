// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** Rango de datas dun uso «só unhas datas», cos dous días incluídos. */
public record UsePeriodRequest(@NotNull LocalDate from, @NotNull LocalDate to) {}
