// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.adapter.in.web.schedule.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Horario da instalación: as tempadas por orde de datas (cada unha coa súa versión), a vixente hoxe
 * en Europe/Madrid (nula se hoxe non hai tempada), os festivos de Galicia de {@code year}, as
 * excepcións por orde de data (cada unha coa súa versión), hoxe e agora en Europe/Madrid para
 * «Hoxe» e «Agora» (o navegador non calcula zonas) e os días sen tempada.
 *
 * @param now minuto do día de agora en Europe/Madrid (0–1439)
 * @param gaps días sen tempada de hoxe en diante (365 días contando hoxe), como rangos ordenados
 */
public record ScheduleResponse(
    List<SeasonResponse> seasons,
    @Nullable UUID currentSeasonId,
    int year,
    List<PublicHolidayResponse> holidays,
    List<ScheduleExceptionResponse> exceptions,
    LocalDate today,
    int now,
    List<DateSpanResponse> gaps) {}
