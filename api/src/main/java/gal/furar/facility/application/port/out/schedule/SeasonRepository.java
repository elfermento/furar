// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.out.schedule;

import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.Season;
import gal.furar.facility.domain.schedule.SeasonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Tempadas coa súa semana, sempre do concello actual. */
public interface SeasonRepository {

  /** As tempadas da instalación por orde de datas. */
  List<Season> findByFacility(FacilityId facilityId);

  /** As tempadas desas instalacións por orde de datas, nunha lectura (sen N+1). */
  List<Season> findByFacilities(Collection<FacilityId> facilityIds);

  Optional<Season> findById(FacilityId facilityId, SeasonId seasonId);

  Season insert(Season season);

  /** Garda nome, datas e a semana enteira; sobe a versión. */
  Season update(Season season);

  void delete(Season season);
}
