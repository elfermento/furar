// SPDX-FileCopyrightText: 2026 Santiago Herrero Padín e colaboradores de Furar
// SPDX-License-Identifier: EUPL-1.2
package gal.furar.facility.application.port.in;

import gal.furar.facility.domain.FacilityId;
import java.util.Map;

/**
 * Sección Datos enteira, campos propios incluídos: {@code expectedVersion} sae do {@code If-Match};
 * {@code customFields} substitúe os valores (un campo que non vén queda sen valor).
 */
public record UpdateFacilityDetailsCommand(
    FacilityId id, long expectedVersion, DetailsInput details, Map<String, ?> customFields) {

  public UpdateFacilityDetailsCommand {
    customFields = customFields == null ? Map.of() : customFields;
  }

  /** Sen campos propios. */
  public UpdateFacilityDetailsCommand(FacilityId id, long expectedVersion, DetailsInput details) {
    this(id, expectedVersion, details, Map.of());
  }
}
