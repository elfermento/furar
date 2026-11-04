// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.service.schedule;

import gal.furar.facility.FacilityChange;
import gal.furar.facility.OpeningInterval;
import gal.furar.facility.domain.FacilityId;
import gal.furar.facility.domain.schedule.OpeningHours.Slot;
import java.util.Comparator;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** O {@link FacilityChange.ScheduleChanged} dos tramos perdidos (P6), ou nada se non se perde. */
final class ScheduleChanges {

  private ScheduleChanges() {}

  static FacilityChange.@Nullable ScheduleChanged of(FacilityId facilityId, List<Slot> lost) {
    if (lost.isEmpty()) {
      return null;
    }
    return new FacilityChange.ScheduleChanged(
        facilityId.value(),
        lost.stream()
            .sorted(Comparator.comparing(Slot::start))
            .map(
                s ->
                    new OpeningInterval(
                        s.date(),
                        s.range().startMinute(),
                        s.range().endMinute(),
                        s.start(),
                        s.end()))
            .toList());
  }
}
