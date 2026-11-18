// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.domain.schedule;

import java.time.LocalDate;
import org.jspecify.annotations.Nullable;

/**
 * Data, aberto ou pechado, tramo e motivo dunha excepción tal como os escribe o xestor, sen
 * comprobar ({@link ScheduleException} valídaos).
 */
public record ExceptionSpec(
    @Nullable LocalDate date,
    boolean open,
    @Nullable Integer startMinute,
    @Nullable Integer endMinute,
    @Nullable String reason) {}
