// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O horario dunha actividade (ADR-0041): uso e parte (nunha instalación), días ISO, minutos, datas
 * e, se rematou antes, desde que día xa non hai sesións.
 */
public record EventScheduleResponse(
    @Nullable UUID useId,
    @Nullable UUID partId,
    List<Integer> weekdays,
    int startMinute,
    int endMinute,
    LocalDate firstDate,
    LocalDate lastDate,
    @Nullable LocalDate endedFrom) {}
