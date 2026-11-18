// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Data, tramo e motivo dun bloqueo tal como os escribe o xestor, sen comprobar ({@link Block}
 * valídaos).
 */
public record BlockSpec(
    @Nullable LocalDate date,
    @Nullable Integer startMinute,
    @Nullable Integer endMinute,
    @Nullable String reason) {}
