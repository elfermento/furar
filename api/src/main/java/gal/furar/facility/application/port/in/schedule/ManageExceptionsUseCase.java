// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.ScheduleException;
import gal.furar.facility.domain.schedule.ScheduleExceptionId;

/** Alta, lectura, edición e borrado das excepcións do horario, sempre baixo o bloqueo. */
public interface ManageExceptionsUseCase {

  ScheduleException exception(FacilityId facilityId, ScheduleExceptionId exceptionId);

  ScheduleException create(CreateExceptionCommand command);

  ScheduleException update(UpdateExceptionCommand command);

  void delete(DeleteExceptionCommand command);
}
