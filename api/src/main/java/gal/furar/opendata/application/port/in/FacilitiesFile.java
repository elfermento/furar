// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.opendata.application.port.in;

import gal.furar.facility.PublicFacilityData;
import java.time.LocalDate;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** O conxunto «Instalacións»: un anaco por concello (un só nun concello). */
public record FacilitiesFile(List<Part> parts, @Nullable LocalDate updatedOn) {

  public FacilitiesFile {
    parts = List.copyOf(parts);
  }

  /** As instalacións dun concello. */
  public record Part(OpenDataPublisher publisher, PublicFacilityData data) {}
}
