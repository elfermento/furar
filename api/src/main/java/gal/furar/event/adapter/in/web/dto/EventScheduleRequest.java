// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.event.adapter.in.web.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * O horario dunha actividade (ADR-0041). Nunha instalación, {@code useId} e, nos usos por partes,
 * {@code partId}; noutro sitio, ningún. Días ISO (luns = 1); minutos desde a medianoite, de 15 en
 * 15 ({@code 1440}: a medianoite do día seguinte). {@code skip}: os días con choque que viu o
 * xestor na vista previa (só ao crear).
 */
public record EventScheduleRequest(
    @Nullable UUID useId,
    @Nullable UUID partId,
    @NotNull List<Integer> weekdays,
    int startMinute,
    int endMinute,
    @NotNull LocalDate firstDate,
    @NotNull LocalDate lastDate,
    @Nullable List<LocalDate> skip) {}
