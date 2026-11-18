// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import org.jspecify.annotations.Nullable;

/** Lectura do horario dunha instalación (xestión). */
public interface ReadScheduleUseCase {

  /**
   * @param year ano dos festivos; nulo = o de hoxe en Europe/Madrid
   */
  ScheduleView schedule(FacilityId facilityId, @Nullable Integer year);

  Season season(FacilityId facilityId, SeasonId seasonId);
}
