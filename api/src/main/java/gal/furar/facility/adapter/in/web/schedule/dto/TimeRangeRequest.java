// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import jakarta.validation.constraints.NotNull;

/**
 * Tramo en minutos desde a medianoite, de reloxo de parede en Europe/Madrid: 0 ≤ inicio < fin ≤
 * 1440 (1440 = 24:00), múltiplos de 15. O fin non se inclúe.
 */
public record TimeRangeRequest(@NotNull Integer startMinute, @NotNull Integer endMinute) {}
