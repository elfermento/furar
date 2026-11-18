// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.booking.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/**
 * Rematar a tempada (ADR-0039, punto 11).
 *
 * @param from desde que día (de hoxe á última data): as sesións desde ese día cancélanse
 */
public record EndSeriesRequest(@NotNull LocalDate from) {}
