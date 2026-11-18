// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out.schedule;

import gal.furar.facility.domain.schedule.PublicHoliday;
import java.time.LocalDate;
import java.util.List;

/** Catálogo de festivos de Galicia (plataforma, só lectura). */
public interface PublicHolidayRepository {

  /** Os festivos entre as dúas datas, incluídas, por orde. */
  List<PublicHoliday> findBetween(LocalDate from, LocalDate to);
}
