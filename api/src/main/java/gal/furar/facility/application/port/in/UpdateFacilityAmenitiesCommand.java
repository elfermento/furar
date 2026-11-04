// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.Amenity;
import gal.furar.facility.domain.FacilityId;
import java.util.Set;

/**
 * Sección Servizos e accesibilidade (lista enteira): {@code expectedVersion} do {@code If-Match}.
 */
public record UpdateFacilityAmenitiesCommand(
    FacilityId id, long expectedVersion, Set<Amenity> amenities) {

  public UpdateFacilityAmenitiesCommand {
    amenities = amenities == null ? Set.of() : Set.copyOf(amenities);
  }
}
