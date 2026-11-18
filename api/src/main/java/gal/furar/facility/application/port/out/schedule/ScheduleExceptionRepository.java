// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Excepcións do horario, sempre do concello actual. */
public interface ScheduleExceptionRepository {

  /** As excepcións da instalación por orde de data. */
  List<ScheduleException> findByFacility(FacilityId facilityId);

  /** As excepcións da instalación entre as dúas datas, incluídas, por orde de data. */
  List<ScheduleException> findBetween(FacilityId facilityId, LocalDate from, LocalDate to);

  /** As excepcións desas instalacións entre as dúas datas, incluídas, nunha lectura (sen N+1). */
  List<ScheduleException> findBetween(
      Collection<FacilityId> facilityIds, LocalDate from, LocalDate to);

  Optional<ScheduleException> findById(FacilityId facilityId, ScheduleExceptionId id);

  /** Garda unha excepción nova. Data xa usada na instalación → {@code exception-date-taken}. */
  ScheduleException insert(ScheduleException exception);

  /** Garda os cambios se a versión coincide e sobe a versión; mesmos erros ca {@link #insert}. */
  ScheduleException update(ScheduleException exception);

  void delete(ScheduleException exception);
}
