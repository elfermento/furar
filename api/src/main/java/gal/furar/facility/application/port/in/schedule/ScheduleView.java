// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.Season.DateSpan;
import gal.furar.facility.domain.schedule.SeasonId;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Horario dunha instalación para a ficha: as tempadas por orde de datas coa súa semana, a vixente
 * hoxe, as excepcións por orde de data, os festivos de Galicia do ano pedido, hoxe e agora en
 * Europe/Madrid (para «Hoxe» e «Agora», ADR-0022 punto 8) e os días sen tempada dos próximos 365.
 *
 * @param nowMinute minuto do día de agora en Europe/Madrid (0–1439)
 * @param gaps días de hoxe en diante (365 contando hoxe) que ningunha tempada cobre
 */
public record ScheduleView(
    List<Season> seasons,
    @Nullable SeasonId currentSeasonId,
    int year,
    List<PublicHoliday> holidays,
    List<ScheduleException> exceptions,
    LocalDate today,
    int nowMinute,
    List<DateSpan> gaps) {

  public ScheduleView {
    seasons = List.copyOf(seasons);
    holidays = List.copyOf(holidays);
    exceptions = List.copyOf(exceptions);
    gaps = List.copyOf(gaps);
  }
}
