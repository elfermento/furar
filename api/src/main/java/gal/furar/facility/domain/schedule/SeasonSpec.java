// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Nome e datas que escribe o xestor nunha tempada, sen comprobar ({@link Season} valídaos). Sen
 * {@code endDate}, a tempada non ten fin.
 */
public record SeasonSpec(String name, LocalDate startDate, @Nullable LocalDate endDate) {}
