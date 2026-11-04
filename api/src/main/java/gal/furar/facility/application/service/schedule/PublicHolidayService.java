// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service.schedule;

import gal.furar.facility.application.port.in.schedule.PublicHolidaysUseCase;
import gal.furar.facility.application.port.out.schedule.PublicHolidayRepository;
import gal.furar.facility.domain.schedule.PublicHoliday;
import gal.furar.facility.domain.schedule.ScheduleError;
import gal.furar.shared.domain.DomainException;
import gal.furar.shared.security.PublicUseCase;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Festivos de Galicia dun ano (plan §1.2.12): datos públicos da plataforma, sen sesión nin
 * concello. Un ano sen festivos sementados devolve a lista baleira (P11: só os publicados).
 */
@Service
public class PublicHolidayService implements PublicHolidaysUseCase {

  static final int YEAR_MIN = 2000;
  static final int YEAR_MAX = 2100;

  private final PublicHolidayRepository holidays;

  public PublicHolidayService(PublicHolidayRepository holidays) {
    this.holidays = holidays;
  }

  @Override
  @Transactional(readOnly = true)
  @PublicUseCase
  public List<PublicHoliday> holidays(int year) {
    int checked = checkedYear(year);
    return holidays.findBetween(LocalDate.of(checked, 1, 1), LocalDate.of(checked, 12, 31));
  }

  static int checkedYear(int year) {
    if (year < YEAR_MIN || year > YEAR_MAX) {
      throw new DomainException(
          ScheduleError.HOLIDAY_YEAR_INVALID, Map.of("min", YEAR_MIN, "max", YEAR_MAX));
    }
    return year;
  }
}
